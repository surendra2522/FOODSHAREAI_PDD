package com.example.data.firebase

import android.net.Uri
import android.util.Log
import com.example.data.firebase.models.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.*

class FirebaseService {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    // --- Authentication ---
    fun getCurrentUserUid(): String? = auth.currentUser?.uid

    suspend fun login(email: String, password: String): String? {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val uid = result.user?.uid
            if (uid != null) saveFcmToken(uid)
            uid
        } catch (e: Exception) {
            Log.e("FirebaseService", "login exception: ${e.localizedMessage}", e)
            null
        }
    }

    suspend fun register(email: String, password: String, name: String, role: String): String? {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val uid = result.user?.uid ?: return null
            
            val user = User(
                uid = uid,
                name = name,
                email = email,
                role = role
            )
            firestore.collection("users").document(uid).set(user).await()
            saveFcmToken(uid)
            uid
        } catch (e: Exception) {
            Log.e("FirebaseService", "register exception: ${e.localizedMessage}", e)
            null
        }
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
    suspend fun getUserProfile(uid: String): User? {
        return try {
            firestore.collection("users").document(uid).get().await().toObject(User::class.java)
        } catch (e: Exception) {
            Log.e("FirebaseService", "getUserProfile exception: ${e.localizedMessage}")
            null
        }
    }

    suspend fun updateUserProfile(user: User, newPassword: String? = null) {
        try {
            if (!newPassword.isNullOrEmpty()) {
                auth.currentUser?.updatePassword(newPassword)?.await()
            }
            firestore.collection("users").document(user.uid).set(user).await()
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
