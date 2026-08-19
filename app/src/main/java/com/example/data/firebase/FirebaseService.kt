package com.example.data.firebase

import android.net.Uri
import android.util.Log
import com.example.data.firebase.models.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.*

fun DocumentSnapshot.toSafeUser(defaultUid: String = ""): User {
    val rawUser = try {
        this.toObject(User::class.java)
    } catch (e: Exception) {
        Log.w("FirebaseService", "toObject(User::class.java) failed for doc ${this.id}, using safe manual extraction: ${e.localizedMessage}")
        null
    }

    val docId = if (defaultUid.isNotBlank()) defaultUid else this.id
    val uid = rawUser?.uid?.ifBlank { docId } ?: docId
    val rawName = getString("name") ?: getString("fullName") ?: getString("displayName") ?: ""
    val resolvedName = rawUser?.name?.ifBlank { rawUser.fullName.ifBlank { rawUser.displayName.ifBlank { rawName } } } ?: rawName
    val email = rawUser?.email?.ifBlank { getString("email") ?: "" } ?: (getString("email") ?: "")
    val rawRole = (rawUser?.role?.ifBlank { getString("role") ?: "" } ?: (getString("role") ?: "")).trim().lowercase()
    val normalizedRole = if (rawRole == "charity") "ngo" else rawRole
    val phone = rawUser?.phone?.ifBlank { getString("phone") ?: "" } ?: (getString("phone") ?: "")
    val profileImage = rawUser?.profileImage?.ifBlank { getString("profileImage") ?: "" } ?: (getString("profileImage") ?: "")

    val createdAtLong = try {
        get("createdAt")?.let { raw ->
            when (raw) {
                is Long -> raw
                is Number -> raw.toLong()
                is String -> {
                    try { raw.toLong() }
                    catch (e: Exception) {
                        try {
                            java.time.Instant.parse(raw).toEpochMilli()
                        } catch (e2: Exception) { System.currentTimeMillis() }
                    }
                }
                is com.google.firebase.Timestamp -> raw.toDate().time
                else -> System.currentTimeMillis()
            }
        } ?: System.currentTimeMillis()
    } catch (e: Exception) {
        System.currentTimeMillis()
    }

    return User(
        uid = uid,
        name = resolvedName.ifBlank { "User" },
        displayName = getString("displayName") ?: resolvedName.ifBlank { "User" },
        fullName = getString("fullName") ?: resolvedName.ifBlank { "User" },
        email = email,
        phone = phone,
        role = normalizedRole.ifBlank { "donor" },
        profileImage = profileImage,
        impactScore = getLong("impactScore")?.toInt() ?: rawUser?.impactScore ?: 10,
        mealsSaved = getLong("mealsSaved")?.toInt() ?: rawUser?.mealsSaved ?: 0,
        co2OffsetKg = getDouble("co2OffsetKg") ?: rawUser?.co2OffsetKg ?: 0.0,
        totalDonations = getLong("totalDonations")?.toInt() ?: rawUser?.totalDonations ?: 0,
        createdAt = createdAtLong,
        fcmToken = getString("fcmToken") ?: rawUser?.fcmToken ?: "",
        missionStatement = getString("missionStatement") ?: rawUser?.missionStatement ?: "Helping redistribute surplus food efficiently across the city.",
        licenseNumber = getString("licenseNumber") ?: rawUser?.licenseNumber ?: "",
        registrationId = getString("registrationId") ?: rawUser?.registrationId ?: "",
        address = getString("address") ?: rawUser?.address ?: "",
        operatingCities = getString("operatingCities") ?: rawUser?.operatingCities ?: "",
        vehicleFleetCount = getLong("vehicleFleetCount")?.toInt() ?: rawUser?.vehicleFleetCount ?: 0,
        volunteerCount = getLong("volunteerCount")?.toInt() ?: rawUser?.volunteerCount ?: 0,
        foodCategories = getString("foodCategories") ?: rawUser?.foodCategories ?: "",
        operatingHours = getString("operatingHours") ?: rawUser?.operatingHours ?: "",
        emergencyContact = getString("emergencyContact") ?: rawUser?.emergencyContact ?: "",
        serviceArea = getString("serviceArea") ?: rawUser?.serviceArea ?: "",
        contactPerson = getString("contactPerson") ?: rawUser?.contactPerson ?: "",
        city = getString("city") ?: rawUser?.city ?: "",
        state = getString("state") ?: rawUser?.state ?: "",
        pincode = getString("pincode") ?: rawUser?.pincode ?: "",
        description = getString("description") ?: rawUser?.description ?: ""
    )
}

class FirebaseService {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    // --- Authentication ---
    fun getCurrentUserUid(): String? = auth.currentUser?.uid

    suspend fun loginWithDetails(email: String, password: String): Result<String> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val uid = result.user?.uid ?: return Result.failure(Exception("User ID is missing from Auth result."))
            if (uid.isNotBlank()) saveFcmToken(uid)
            Result.success(uid)
        } catch (e: Exception) {
            Log.e("FirebaseService", "login exception: ${e.localizedMessage}", e)
            Result.failure(e)
        }
    }

    suspend fun login(email: String, password: String): String? {
        return loginWithDetails(email, password).getOrNull()
    }

    suspend fun registerWithDetails(email: String, password: String, name: String, role: String): Result<String> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val uid = result.user?.uid ?: return Result.failure(Exception("User ID is missing from Auth result."))
            
            val normalizedRole = if (role.trim().lowercase() == "charity") "ngo" else role.trim().lowercase()
            val user = User(
                uid = uid,
                name = name,
                displayName = name,
                fullName = name,
                email = email,
                role = normalizedRole
            )
            firestore.collection("users").document(uid).set(user, SetOptions.merge()).await()
            saveFcmToken(uid)
            Result.success(uid)
        } catch (e: Exception) {
            Log.e("FirebaseService", "register exception: ${e.localizedMessage}", e)
            Result.failure(e)
        }
    }

    suspend fun register(email: String, password: String, name: String, role: String): String? {
        return registerWithDetails(email, password, name, role).getOrNull()
    }

    private suspend fun saveFcmToken(uid: String) {
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            firestore.collection("users").document(uid).update("fcmToken", token).await()
        } catch (e: Exception) {
            Log.w("FirebaseService", "saveFcmToken failed: ${e.localizedMessage}")
        }
    }

    fun logout() {
        try {
            auth.signOut()
        } catch (e: Exception) {
            Log.e("FirebaseService", "logout exception: ${e.localizedMessage}")
        }
    }

    suspend fun sendPasswordResetEmail(email: String) {
        try {
            auth.sendPasswordResetEmail(email).await()
        } catch (e: Exception) {
            Log.e("FirebaseService", "sendPasswordResetEmail exception: ${e.localizedMessage}", e)
        }
    }

    // --- User Profile ---
    suspend fun getUserProfile(uid: String, fallbackEmail: String? = null): User? {
        return try {
            // 1. Try canonical location users/{uid}
            val doc = firestore.collection("users").document(uid).get().await()
            if (doc.exists()) {
                val user = doc.toSafeUser(uid)
                return user
            }

            // 2. If doc not found at users/{uid} and fallbackEmail is provided, query by email
            val searchEmail = fallbackEmail?.ifBlank { auth.currentUser?.email } ?: auth.currentUser?.email
            if (!searchEmail.isNullOrBlank()) {
                Log.d("FirebaseService", "users/$uid not found. Querying users by email: $searchEmail")
                val querySnap = firestore.collection("users")
                    .whereEqualTo("email", searchEmail.trim())
                    .limit(1)
                    .get()
                    .await()

                if (!querySnap.isEmpty) {
                    val foundDoc = querySnap.documents[0]
                    val user = foundDoc.toSafeUser(uid).copy(uid = uid)
                    Log.d("FirebaseService", "Found user by email in doc ${foundDoc.id}. Migrating profile to users/$uid")
                    try {
                        firestore.collection("users").document(uid).set(user, SetOptions.merge()).await()
                    } catch (e: Exception) {
                        Log.w("FirebaseService", "Failed to migrate user doc to users/$uid: ${e.localizedMessage}")
                    }
                    return user
                }

                // 3. Profile still missing: Auto-create minimal profile at users/{uid}
                Log.w("FirebaseService", "No profile found for email $searchEmail. Auto-creating profile at users/$uid")
                val newName = searchEmail.substringBefore("@").ifBlank { "User" }
                val newUser = User(
                    uid = uid,
                    email = searchEmail,
                    name = newName,
                    displayName = newName,
                    fullName = newName,
                    role = "donor"
                )
                try {
                    firestore.collection("users").document(uid).set(newUser, SetOptions.merge()).await()
                } catch (e: Exception) {
                    Log.e("FirebaseService", "Auto-create profile failed: ${e.localizedMessage}")
                }
                return newUser
            }

            null
        } catch (e: Exception) {
            Log.e("FirebaseService", "getUserProfile exception: ${e.localizedMessage}", e)
            null
        }
    }

    suspend fun updateUserProfile(user: User, newPassword: String? = null) {
        try {
            if (!newPassword.isNullOrEmpty()) {
                auth.currentUser?.updatePassword(newPassword)?.await()
            }
            firestore.collection("users").document(user.uid).set(user, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.e("FirebaseService", "updateUserProfile exception: ${e.localizedMessage}", e)
        }
    }

    fun getAllUsers(): Flow<List<User>> = callbackFlow {
        val subscription = firestore.collection("users")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirebaseService", "getAllUsers snapshot error: ${error.localizedMessage}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        try { doc.toObject(User::class.java)?.copy(uid = doc.id) } catch (e: Exception) { null }
                    }
                    trySend(list)
                }
            }
        awaitClose { subscription.remove() }
    }

    suspend fun uploadImage(imageUri: Uri, folder: String, context: android.content.Context? = null): String {
        return try {
            val imageRef = storage.reference.child("$folder/${UUID.randomUUID()}.jpg")
            if (context != null) {
                val stream = context.contentResolver.openInputStream(imageUri)
                if (stream != null) {
                    imageRef.putStream(stream).await()
                    stream.close()
                    val url = imageRef.downloadUrl.await().toString()
                    if (url.isNotBlank()) return url
                }
            }
            imageRef.putFile(imageUri).await()
            imageRef.downloadUrl.await().toString()
        } catch (e: Exception) {
            Log.e("FirebaseService", "uploadImage exception for uri $imageUri: ${e.localizedMessage}", e)
            if (imageUri.toString().isNotBlank()) {
                imageUri.toString()
            } else ""
        }
    }

    // --- Donations ---
    suspend fun createDonation(donation: Donation, imageUri: Uri?): String {
        var finalDonation = donation
        if (imageUri != null) {
            try {
                val downloadUrl = uploadImage(imageUri, "donations")
                if (downloadUrl.isNotBlank()) {
                    finalDonation = donation.copy(foodImage = downloadUrl)
                }
            } catch (e: Exception) {
                Log.e("FirebaseService", "createDonation image upload exception: ${e.localizedMessage}")
            }
        }
        
        return try {
            val docRef = firestore.collection("donations").document()
            val donationWithId = finalDonation.copy(donationId = docRef.id)
            docRef.set(donationWithId).await()
            docRef.id
        } catch (e: Exception) {
            Log.e("FirebaseService", "createDonation firestore set exception: ${e.localizedMessage}", e)
            UUID.randomUUID().toString()
        }
    }

    fun getAvailableDonations(): Flow<List<Donation>> = callbackFlow {
        val subscription = firestore.collection("donations")
            .whereEqualTo("status", "Created")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirebaseService", "getAvailableDonations error: ${error.localizedMessage}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        try { doc.toObject(Donation::class.java)?.copy(donationId = doc.id) } catch (e: Exception) { null }
                    }
                    trySend(list)
                }
            }
        awaitClose { subscription.remove() }
    }

    fun getDonationsByDonor(donorId: String): Flow<List<Donation>> = callbackFlow {
        val subscription = firestore.collection("donations")
            .whereEqualTo("donorId", donorId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirebaseService", "getDonationsByDonor error: ${error.localizedMessage}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        try { doc.toObject(Donation::class.java)?.copy(donationId = doc.id) } catch (e: Exception) { null }
                    }
                    trySend(list)
                }
            }
        awaitClose { subscription.remove() }
    }

    fun getDonationsForNgo(ngoId: String): Flow<List<Donation>> = callbackFlow {
        val subscription = firestore.collection("donations")
            .whereEqualTo("ngoId", ngoId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirebaseService", "getDonationsForNgo error: ${error.localizedMessage}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        try { doc.toObject(Donation::class.java)?.copy(donationId = doc.id) } catch (e: Exception) { null }
                    }
                    trySend(list)
                }
            }
        awaitClose { subscription.remove() }
    }

    suspend fun updateDonationStatus(donationId: String, status: String, ngoId: String? = null, ngoName: String? = null) {
        try {
            val updates = mutableMapOf<String, Any>("status" to status)
            if (ngoId != null) updates["ngoId"] = ngoId
            if (ngoName != null) updates["ngoName"] = ngoName
            
            firestore.collection("donations").document(donationId).update(updates).await()
        } catch (e: Exception) {
            Log.e("FirebaseService", "updateDonationStatus exception: ${e.localizedMessage}", e)
        }
    }

    suspend fun deleteDonation(donationId: String) {
        try {
            firestore.collection("donations").document(donationId).delete().await()
        } catch (e: Exception) {
            Log.e("FirebaseService", "deleteDonation exception: ${e.localizedMessage}", e)
        }
    }

    // --- Notifications ---
    fun getNotifications(userId: String, role: String): Flow<List<Notification>> = callbackFlow {
        val subscription = firestore.collection("notifications")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirebaseService", "getNotifications snapshot error: ${error.localizedMessage}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    try {
                        val list = snapshot.documents.mapNotNull { doc ->
                            try {
                                val n = doc.toObject(Notification::class.java)?.copy(id = doc.id) ?: return@mapNotNull null
                                val type = n.type.lowercase(Locale.getDefault())

                                if (role == "donor") {
                                    // Exclude system logs, user registrations, admin actions for donors
                                    val isExcluded = type == "user_registered" ||
                                            type == "ngo_registered" ||
                                            type.contains("admin") ||
                                            type.contains("system") ||
                                            type.contains("sync")
                                    if (isExcluded) return@mapNotNull null

                                    // Must target this specific donor
                                    if (n.userId == userId || (n.role == "donor" && (n.userId.isBlank() || n.userId == userId))) {
                                        n
                                    } else null
                                } else if (role == "ngo") {
                                    if (n.userId == userId || n.userId.isEmpty() || n.role == "ngo") {
                                        n
                                    } else null
                                } else {
                                    if (n.userId == userId || n.userId.isEmpty() || n.role == role) {
                                        n
                                    } else null
                                }
                            } catch (e: Exception) { null }
                        }
                        trySend(list)
                    } catch (e: Exception) {
                        Log.e("FirebaseService", "getNotifications parse exception: ${e.localizedMessage}")
                    }
                }
            }
        awaitClose { subscription.remove() }
    }

    suspend fun sendNotification(notification: Notification): String {
        return try {
            // Duplicate check: prevent duplicate notifications for same user & event within 10 seconds
            val now = System.currentTimeMillis()
            val existingSnap = firestore.collection("notifications")
                .whereEqualTo("userId", notification.userId)
                .whereEqualTo("type", notification.type)
                .get()
                .await()

            val isDuplicate = existingSnap.documents.any { doc ->
                val ts = doc.getLong("timestamp") ?: 0L
                val relId = doc.getString("relatedDonationId") ?: ""
                (relId == notification.relatedDonationId) && (now - ts < 10000L)
            }

            if (isDuplicate) {
                Log.d("FirebaseService", "Duplicate notification ignored: ${notification.title}")
                return ""
            }

            val docRef = firestore.collection("notifications").document()
            val notifWithId = notification.copy(id = docRef.id)
            docRef.set(notifWithId).await()
            docRef.id
        } catch (e: Exception) {
            Log.e("FirebaseService", "sendNotification exception: ${e.localizedMessage}", e)
            ""
        }
    }

    suspend fun markNotificationAsRead(notificationId: String) {
        try {
            if (notificationId.isNotBlank()) {
                firestore.collection("notifications").document(notificationId).update("isRead", true).await()
            }
        } catch (e: Exception) {
            Log.e("FirebaseService", "markNotificationAsRead exception: ${e.localizedMessage}", e)
        }
    }

    suspend fun markAllNotificationsAsRead(userId: String) {
        try {
            val snapshot = firestore.collection("notifications")
                .whereEqualTo("isRead", false)
                .get().await()
            val batch = firestore.batch()
            snapshot.documents.forEach { doc ->
                val target = doc.getString("userId") ?: ""
                if (target == userId || target.isEmpty()) {
                    batch.update(doc.reference, "isRead", true)
                }
            }
            batch.commit().await()
        } catch (e: Exception) {
            Log.e("FirebaseService", "markAllNotificationsAsRead exception: ${e.localizedMessage}", e)
        }
    }

    suspend fun deleteNotification(notificationId: String) {
        try {
            if (notificationId.isNotBlank()) {
                firestore.collection("notifications").document(notificationId).delete().await()
            }
        } catch (e: Exception) {
            Log.e("FirebaseService", "deleteNotification exception: ${e.localizedMessage}", e)
        }
    }

    suspend fun clearAllNotifications(userId: String, role: String) {
        try {
            val snapshot = firestore.collection("notifications").get().await()
            val batch = firestore.batch()
            snapshot.documents.forEach { doc ->
                val n = doc.toObject(Notification::class.java)
                if (n != null && (n.userId == userId || (n.userId.isEmpty() && n.role.equals(role, ignoreCase = true)) || role.lowercase() == "admin")) {
                    batch.delete(doc.reference)
                }
            }
            batch.commit().await()
        } catch (e: Exception) {
            Log.e("FirebaseService", "clearAllNotifications exception: ${e.localizedMessage}", e)
        }
    }
}
