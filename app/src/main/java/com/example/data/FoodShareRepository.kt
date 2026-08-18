package com.example.data

import android.net.Uri
import android.util.Log
import com.example.data.firebase.FirebaseService
import com.example.data.local.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.util.UUID

class FoodShareRepository() {
    private val firebaseService = FirebaseService()
    private val firestore = FirebaseFirestore.getInstance()

    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser

    suspend fun checkSession(): Boolean {
        val uid = firebaseService.getCurrentUserUid() ?: run {
            Log.d("AuthNavigation", "checkSession: No active Firebase Auth session.")
            return false
        }
        return try {
            val user = firebaseService.getUserProfile(uid)
            if (user != null) {
                val entity = user.toEntity()
                Log.d("AuthNavigation", "Loaded session role: '${entity.role}' for user: '${entity.email}' (UID: $uid)")
                _currentUser.value = entity
                true
            } else {
                Log.w("AuthNavigation", "checkSession: User profile not found in Firestore for UID $uid")
                false
            }
        } catch (e: Exception) {
            Log.e("AuthNavigation", "checkSession exception: ${e.localizedMessage}")
            false
        }
    }

    suspend fun login(email: String, passwordHash: String, selectedRole: String): Boolean {
        Log.d("AuthNavigation", "Initiating login for email: $email, selected role: $selectedRole")
        val uid = firebaseService.login(email, passwordHash) ?: run {
            Log.e("AuthNavigation", "Login failed: Firebase auth failed for $email")
            return false
        }
        val user = firebaseService.getUserProfile(uid) ?: run {
            Log.e("AuthNavigation", "Login failed: Could not fetch user profile for UID $uid")
            return false
        }
        
        val normalizedRole = user.role.lowercase().trim()
        val normalizedSelectedRole = selectedRole.lowercase().trim()

        Log.d("AuthNavigation", "Login response: User UID: $uid, Firestore Raw Role: '${user.role}', Normalized Role: '$normalizedRole', Selected Role: '$normalizedSelectedRole'")

        val entity = user.toEntity()
        
        if (normalizedRole == normalizedSelectedRole || normalizedRole == "admin") {
            Log.d("AuthNavigation", "Saved role: '${entity.role}'. Login successful! Setting _currentUser.")
            _currentUser.value = entity
            return true
        } else {
            Log.w("AuthNavigation", "Role mismatch! DB Role: '$normalizedRole' vs Selected Role: '$normalizedSelectedRole'")
            return false
        }
    }

    suspend fun register(email: String, name: String, passwordHash: String, role: String): Boolean {
        val normalizedRole = role.lowercase().trim()
        Log.d("AuthNavigation", "Registering new user with email: $email, role: $normalizedRole")
        val uid = firebaseService.register(email, passwordHash, name, normalizedRole) ?: run {
            Log.e("AuthNavigation", "Registration failed in FirebaseService")
            return false
        }
        val entity = UserEntity(
            id = uid,
            email = email,
            name = name,
            role = normalizedRole
        )
        Log.d("AuthNavigation", "Saved role: '${entity.role}' for new user UID: $uid")
        _currentUser.value = entity

        try {
            val adminNotif = com.example.data.firebase.models.Notification(
                userId = "",
                role = "admin",
                title = if (normalizedRole == "ngo") "🏢 New NGO Registered" else "👤 New Donor Registered",
                message = "$name ($email) joined FoodShareAI as ${normalizedRole.uppercase()}.",
                type = "user_registered",
                timestamp = System.currentTimeMillis()
            )
            firebaseService.sendNotification(adminNotif)
        } catch (e: Exception) {
            Log.e("FoodShareRepository", "Error sending register admin notification: ${e.localizedMessage}")
        }

        return true
    }

    suspend fun resetPassword(email: String, newPasswordHash: String): Boolean {
        return try {
            firebaseService.sendPasswordResetEmail(email)
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun updateProfile(name: String, email: String, passwordHash: String, imageUri: Uri? = null): Boolean {
        val cur = _currentUser.value ?: return false
        var imageUrl = cur.profileImage
        
        if (imageUri != null) {
            imageUrl = firebaseService.uploadImage(imageUri, "profiles")
        }

        val updatedEntity = cur.copy(name = name, email = email, profileImage = imageUrl)
        val firebaseUser = updatedEntity.toFirebaseUser()
        firebaseService.updateUserProfile(firebaseUser, if (passwordHash.isNotEmpty()) passwordHash else null)
        updateProfileDoc(updatedEntity)
        _currentUser.value = updatedEntity
        return true
    }

    suspend fun updateFullProfile(
        name: String,
        contactPerson: String,
        phone: String,
        email: String,
        address: String,
        city: String,
        state: String,
        pincode: String,
        description: String,
        registrationId: String = "",
        missionStatement: String = ""
    ): Boolean {
        val cur = _currentUser.value ?: return false
        val updatedEntity = cur.copy(
            name = name,
            contactPerson = contactPerson,
            phone = phone,
            email = email,
            address = address,
            city = city,
            state = state,
            pincode = pincode,
            description = description,
            registrationId = if (registrationId.isNotBlank()) registrationId else cur.registrationId,
            missionStatement = if (missionStatement.isNotBlank()) missionStatement else cur.missionStatement
        )
        val firebaseUser = updatedEntity.toFirebaseUser()
        firebaseService.updateUserProfile(firebaseUser)
        updateProfileDoc(updatedEntity)
        _currentUser.value = updatedEntity

        if (cur.role == "donor") {
            try {
                firebaseService.sendNotification(
                    com.example.data.firebase.models.Notification(
                        userId = cur.id,
                        role = "donor",
                        title = "👤 Profile Updated",
                        message = "Your profile information has been updated successfully.",
                        type = "profile_updated",
                        timestamp = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                Log.e("FoodShareRepository", "Error sending profile updated notification: ${e.localizedMessage}")
            }
        }
        return true
    }

    suspend fun uploadProfileImage(imageUri: Uri, context: android.content.Context? = null): String {
        val cur = _currentUser.value ?: return ""
        val imageUrl = firebaseService.uploadImage(imageUri, "profiles", context)
        if (imageUrl.isNotBlank()) {
            val updatedEntity = cur.copy(profileImage = imageUrl)
            firebaseService.updateUserProfile(updatedEntity.toFirebaseUser())
            updateProfileDoc(updatedEntity)
            _currentUser.value = updatedEntity
        }
        return imageUrl
    }

    suspend fun removeProfileImage(): Boolean {
        val cur = _currentUser.value ?: return false
        val updatedEntity = cur.copy(profileImage = "")
        firebaseService.updateUserProfile(updatedEntity.toFirebaseUser())
        updateProfileDoc(updatedEntity)
        _currentUser.value = updatedEntity
        return true
    }

    suspend fun updateNgoProfile(
        name: String = "",
        phone: String = "",
        missionStatement: String? = null,
        licenseNumber: String? = null,
        registrationId: String? = null,
        address: String? = null,
        operatingCities: String? = null,
        vehicleFleetCount: Int? = null,
        volunteerCount: Int? = null,
        foodCategories: String? = null,
        operatingHours: String? = null,
        emergencyContact: String? = null,
        serviceArea: String? = null,
        imageUri: Uri? = null
    ): Boolean {
        val cur = _currentUser.value ?: return false
        var imageUrl = cur.profileImage
        if (imageUri != null) {
            imageUrl = firebaseService.uploadImage(imageUri, "profiles")
        }
        val updatedEntity = cur.copy(
            name = if (name.isNotBlank()) name else cur.name,
            phone = if (phone.isNotBlank()) phone else cur.phone,
            profileImage = imageUrl,
            missionStatement = missionStatement ?: cur.missionStatement,
            licenseNumber = licenseNumber ?: cur.licenseNumber,
            registrationId = registrationId ?: cur.registrationId,
            address = address ?: cur.address,
            operatingCities = operatingCities ?: cur.operatingCities,
            vehicleFleetCount = vehicleFleetCount ?: cur.vehicleFleetCount,
            volunteerCount = volunteerCount ?: cur.volunteerCount,
            foodCategories = foodCategories ?: cur.foodCategories,
            operatingHours = operatingHours ?: cur.operatingHours,
            emergencyContact = emergencyContact ?: cur.emergencyContact,
            serviceArea = serviceArea ?: cur.serviceArea
        )

        val firebaseUser = com.example.data.firebase.models.User(
            uid = updatedEntity.id,
            name = updatedEntity.name,
            email = updatedEntity.email,
            phone = updatedEntity.phone,
            role = updatedEntity.role,
            profileImage = updatedEntity.profileImage,
            impactScore = updatedEntity.impactScore,
            mealsSaved = updatedEntity.mealsSaved,
            co2OffsetKg = updatedEntity.co2OffsetKg,
            totalDonations = updatedEntity.totalDonations,
            createdAt = updatedEntity.createdAt,
            missionStatement = updatedEntity.missionStatement,
            licenseNumber = updatedEntity.licenseNumber,
            registrationId = updatedEntity.registrationId,
            address = updatedEntity.address,
            operatingCities = updatedEntity.operatingCities,
            vehicleFleetCount = updatedEntity.vehicleFleetCount,
            volunteerCount = updatedEntity.volunteerCount,
            foodCategories = updatedEntity.foodCategories,
            operatingHours = updatedEntity.operatingHours,
            emergencyContact = updatedEntity.emergencyContact,
            serviceArea = updatedEntity.serviceArea
        )
        firebaseService.updateUserProfile(firebaseUser)
        updateProfileDoc(updatedEntity)
        _currentUser.value = updatedEntity
        return true
    }

    fun logout() {
        Log.d("AuthNavigation", "User logged out. Clearing session.")
        firebaseService.logout()
        _currentUser.value = null
    }

    // --- Donations & Data Flows with Safe Document Parsing ---
    private fun parseDonationEntity(doc: com.google.firebase.firestore.DocumentSnapshot): DonationEntity? {
        return try {
            val d = doc.toObject(com.example.data.firebase.models.Donation::class.java)
            if (d != null) {
                DonationEntity(
                    id = if (d.donationId.isNotBlank()) d.donationId else doc.id,
                    donorId = d.donorId,
                    donorName = d.donorName,
                    title = d.foodName.ifBlank { "Surplus Food Donation" },
                    foodType = d.foodType,
                    quantity = d.quantity,
                    pickupTime = d.pickupAddress,
                    expiryTime = d.expiryTime,
                    location = d.pickupAddress,
                    description = d.description,
                    imageUrl = d.foodImage,
                    status = d.status,
                    ngoId = d.ngoId,
                    ngoName = d.ngoName,
                    latitude = d.latitude,
                    longitude = d.longitude,
                    timestamp = d.timestamp,
                    acceptedAt = d.acceptedAt,
                    assignedAt = d.assignedAt,
                    pickupStartedAt = d.pickupStartedAt,
                    nearPickupAt = d.nearPickupAt,
                    collectedAt = d.collectedAt,
                    deliveryStartedAt = d.deliveryStartedAt,
                    deliveredAt = d.deliveredAt,
                    completedAt = d.completedAt,
                    volunteerName = d.volunteerName,
                    volunteerPhone = d.volunteerPhone,
                    volunteerVehicle = d.volunteerVehicle,
                    currentLatitude = d.currentLatitude,
                    currentLongitude = d.currentLongitude
                )
            } else {
                doc.toObject(DonationEntity::class.java)?.copy(id = doc.id)
            }
        } catch (e: Exception) {
            try {
                doc.toObject(DonationEntity::class.java)?.copy(id = doc.id)
            } catch (e2: Exception) {
                Log.e("FoodShareRepository", "Error parsing Donation doc ${doc.id}: ${e2.localizedMessage}")
                null
            }
        }
    }

    private fun parseUserEntity(doc: com.google.firebase.firestore.DocumentSnapshot): UserEntity? {
        return try {
            val userModel = doc.toObject(com.example.data.firebase.models.User::class.java)
            if (userModel != null) {
                UserEntity(
                    id = if (userModel.uid.isNotBlank()) userModel.uid else doc.id,
                    email = userModel.email,
                    name = userModel.name,
                    phone = userModel.phone,
                    role = userModel.role.lowercase().trim(),
                    profileImage = userModel.profileImage,
                    impactScore = userModel.impactScore,
                    mealsSaved = userModel.mealsSaved,
                    co2OffsetKg = userModel.co2OffsetKg,
                    totalDonations = userModel.totalDonations,
                    createdAt = userModel.createdAt,
                    missionStatement = userModel.missionStatement,
                    licenseNumber = userModel.licenseNumber,
                    registrationId = userModel.registrationId,
                    address = userModel.address,
                    operatingCities = userModel.operatingCities,
                    vehicleFleetCount = userModel.vehicleFleetCount,
                    volunteerCount = userModel.volunteerCount,
                    foodCategories = userModel.foodCategories,
                    operatingHours = userModel.operatingHours,
                    emergencyContact = userModel.emergencyContact,
                    serviceArea = userModel.serviceArea
                )
            } else {
                doc.toObject(UserEntity::class.java)?.copy(id = doc.id)
            }
        } catch (e: Exception) {
            try {
                doc.toObject(UserEntity::class.java)?.copy(id = doc.id)
            } catch (e2: Exception) {
                Log.e("FoodShareRepository", "Error parsing User doc ${doc.id}: ${e2.localizedMessage}")
                null
            }
        }
    }

    fun getAllDonations(): Flow<List<DonationEntity>> = callbackFlow {
        val subscription = firestore.collection("donations")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FoodShareRepository", "getAllDonations snapshot error: ${error.localizedMessage}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { parseDonationEntity(it) }
                    trySend(list)
                }
            }
        awaitClose { subscription.remove() }
    }

    fun getAvailableDonations(): Flow<List<DonationEntity>> = callbackFlow {
        val subscription = firestore.collection("donations")
            .whereEqualTo("status", "Created")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FoodShareRepository", "getAvailableDonations snapshot error: ${error.localizedMessage}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { parseDonationEntity(it) }
                    trySend(list)
                }
            }
        awaitClose { subscription.remove() }
    }

    fun getAllFoodAnalyses(): Flow<List<FoodAnalysisEntity>> = callbackFlow {
        val subscription = firestore.collection("food_analyses")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FoodShareRepository", "getAllFoodAnalyses error: ${error.localizedMessage}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        try { doc.toObject(FoodAnalysisEntity::class.java)?.copy(id = doc.id) } catch (e: Exception) { null }
                    }
                    trySend(list)
                }
            }
        awaitClose { subscription.remove() }
    }

    fun getAllPredictions(): Flow<List<PredictionEntity>> = callbackFlow {
        val subscription = firestore.collection("predictions")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FoodShareRepository", "getAllPredictions error: ${error.localizedMessage}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        try { doc.toObject(PredictionEntity::class.java)?.copy(id = doc.id) } catch (e: Exception) { null }
                    }
                    trySend(list)
                }
            }
        awaitClose { subscription.remove() }
    }

    fun getNotifications(userId: String = "", role: String = "donor"): Flow<List<NotificationEntity>> = callbackFlow {
        val subscription = firestore.collection("notifications")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FoodShareRepository", "getNotifications error: ${error.localizedMessage}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        try {
                            val n = doc.toObject(NotificationEntity::class.java)?.copy(id = doc.id)
                            if (n != null && (userId.isEmpty() || n.userId == userId || n.userId.isEmpty() || (role == "ngo" && n.role == "ngo"))) {
                                n
                            } else null
                        } catch (e: Exception) { null }
                    }
                    trySend(list)
                }
            }
        awaitClose { subscription.remove() }
    }

    suspend fun markNotificationAsRead(id: String) {
        firebaseService.markNotificationAsRead(id)
    }

    suspend fun markAllNotificationsAsRead(userId: String) {
        firebaseService.markAllNotificationsAsRead(userId)
    }

    suspend fun deleteNotification(id: String) {
        firebaseService.deleteNotification(id)
    }

    suspend fun clearAllNotifications(userId: String, role: String) {
        firebaseService.clearAllNotifications(userId, role)
    }

    suspend fun sendAiAlertNotification(title: String, message: String, type: String, donationId: String = "") {
        try {
            val notif = com.example.data.firebase.models.Notification(
                userId = "",
                role = "admin",
                title = title,
                message = message,
                type = type,
                relatedDonationId = donationId,
                timestamp = System.currentTimeMillis()
            )
            firebaseService.sendNotification(notif)

            val ngoNotif = notif.copy(role = "ngo")
            firebaseService.sendNotification(ngoNotif)
        } catch (e: Exception) {
            Log.e("FoodShareRepository", "Failed to send AI alert notification: ${e.localizedMessage}")
        }
    }

    fun getAllUsers(): Flow<List<UserEntity>> = callbackFlow {
        val subscription = firestore.collection("users")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FoodShareRepository", "getAllUsers error: ${error.localizedMessage}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { parseUserEntity(it) }
                    trySend(list)
                }
            }
        awaitClose { subscription.remove() }
    }

    suspend fun createDonation(
        title: String,
        foodType: String,
        eventType: String,
        quantity: Int,
        expectedGuests: Int,
        pickupTime: String,
        expiryTime: String,
        location: String,
        description: String,
        latitude: Double = 0.0,
        longitude: Double = 0.0,
        imageUri: Uri? = null
    ): Boolean {
        return try {
            val donor = _currentUser.value ?: run {
                Log.w("FoodShareRepository", "currentUser is null during createDonation, attempting auth fallback.")
                val authUid = firebaseService.getCurrentUserUid()
                if (authUid != null) {
                    val user = firebaseService.getUserProfile(authUid)
                    if (user != null) {
                        UserEntity(
                            id = user.uid,
                            email = user.email,
                            name = user.name.ifBlank { "Verified Donor" },
                            phone = user.phone,
                            role = user.role.ifBlank { "donor" }
                        ).also { _currentUser.value = it }
                    } else {
                        UserEntity(id = authUid, name = "Verified Donor", role = "donor").also { _currentUser.value = it }
                    }
                } else {
                    UserEntity(id = "donor_${UUID.randomUUID().toString().take(8)}", name = "Verified Donor", role = "donor").also { _currentUser.value = it }
                }
            }
            
            val donation = com.example.data.firebase.models.Donation(
                donorId = donor.id,
                donorName = donor.name.ifBlank { "Verified Donor" },
                foodName = title.ifBlank { "Surplus Food Donation" },
                foodType = foodType.ifBlank { "Prepared Meals" },
                quantity = quantity.coerceAtLeast(1),
                expiryTime = expiryTime,
                pickupAddress = location.ifBlank { "Pickup Location" },
                description = description,
                status = "Created",
                latitude = latitude,
                longitude = longitude,
                timestamp = System.currentTimeMillis()
            )
            
            val donationId = firebaseService.createDonation(donation, imageUri)
            Log.d("FoodShareRepository", "Successfully created donation with ID: $donationId")

            if (donationId.isNotBlank() && donor.id.isNotBlank()) {
                try {
                    firebaseService.sendNotification(
                        com.example.data.firebase.models.Notification(
                            userId = donor.id,
                            role = "donor",
                            title = "🍱 Donation Published",
                            message = "Your donation \"${donation.foodName}\" is now visible to nearby NGOs.",
                            type = "donation_published",
                            relatedDonationId = donationId,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                } catch (e: Exception) {
                    Log.e("FoodShareRepository", "Error sending published notification: ${e.localizedMessage}")
                }
            }

            // Update user stats in Firestore safely
            try {
                val updatedUser = donor.copy(
                    totalDonations = donor.totalDonations + 1,
                    mealsSaved = donor.mealsSaved + quantity,
                    co2OffsetKg = donor.co2OffsetKg + (quantity * 2.5),
                    impactScore = donor.impactScore + 15
                )
                updateProfileDoc(updatedUser)
                _currentUser.value = updatedUser
            } catch (e: Exception) {
                Log.e("FoodShareRepository", "Failed to update profile doc stats: ${e.localizedMessage}")
            }

            // Send structured notifications for Donor & NGO
            try {
                // 1. Donor Notification
                val donorNotif = com.example.data.firebase.models.Notification(
                    userId = donor.id,
                    role = "donor",
                    title = "✅ Donation Published Successfully",
                    message = "Your donation '$title' has been published successfully.",
                    type = "donation_created",
                    relatedDonationId = donationId,
                    timestamp = System.currentTimeMillis()
                )
                firebaseService.sendNotification(donorNotif)

                // 2. Nearby NGO Notification
                val ngoNotif = com.example.data.firebase.models.Notification(
                    userId = "", // Broadcast to all NGOs
                    role = "ngo",
                    title = "🍱 New Food Donation Available",
                    message = "📍 $title\n$quantity Meals\n$location\nClaim before expiry.",
                    type = "donation_created",
                    relatedDonationId = donationId,
                    timestamp = System.currentTimeMillis()
                )
                firebaseService.sendNotification(ngoNotif)

                // 3. Admin Notification
                val adminNotif = com.example.data.firebase.models.Notification(
                    userId = "",
                    role = "admin",
                    title = "➕ New Donation Created",
                    message = "Donation '$title' ($quantity meals) was published by ${donor.name}.",
                    type = "donation_created",
                    relatedDonationId = donationId,
                    timestamp = System.currentTimeMillis()
                )
                firebaseService.sendNotification(adminNotif)
            } catch (e: Exception) {
                Log.e("FoodShareRepository", "Failed to send creation notifications: ${e.localizedMessage}")
            }

            true
        } catch (e: Exception) {
            Log.e("FoodShareRepository", "createDonation failed: ${e.localizedMessage}", e)
            false
        }
    }

    suspend fun updateDonationStatus(donationId: String, newStatus: String, ngoId: String? = null, ngoName: String? = null): Boolean {
        if (newStatus == "Created") {
            // Cancellation
            firestore.collection("donations").document(donationId).update(
                mapOf(
                    "status" to "Created",
                    "ngoId" to "",
                    "ngoName" to ""
                )
            ).await()
            return true
        }

        firebaseService.updateDonationStatus(donationId, newStatus, ngoId, ngoName)
        
        // Add to requests collection if accepted
        if (newStatus == "Accepted" && ngoId != null) {
            val request = hashMapOf(
                "requestId" to UUID.randomUUID().toString(),
                "ngoId" to ngoId,
                "donationId" to donationId,
                "status" to "Accepted",
                "acceptedAt" to com.google.firebase.Timestamp.now()
            )
            firestore.collection("requests").document(request["requestId"] as String).set(request).await()
        }

        // If Completed, update NGO stats
        if (newStatus == "Completed" && ngoId != null) {
            val ngoDoc = firestore.collection("users").document(ngoId).get().await()
            val ngo = ngoDoc.toObject(com.example.data.firebase.models.User::class.java)
            if (ngo != null) {
                val donationDoc = firestore.collection("donations").document(donationId).get().await()
                val donation = donationDoc.toObject(DonationEntity::class.java)
                if (donation != null) {
                    val updatedNgo = ngo.copy(
                        mealsSaved = ngo.mealsSaved + donation.quantity,
                        co2OffsetKg = ngo.co2OffsetKg + (donation.quantity * 2.5),
                        totalDonations = ngo.totalDonations + 1,
                        impactScore = ngo.impactScore + 25
                    )
                    firestore.collection("users").document(ngoId).set(updatedNgo).await()
                }
            }
        }
        return true
    }

    suspend fun updateDonationStage(
        donationId: String,
        newStatus: String,
        ngoId: String? = null,
        ngoName: String? = null,
        volunteerName: String? = null,
        volunteerPhone: String? = null,
        volunteerVehicle: String? = null
    ): Boolean {
        val docRef = firestore.collection("donations").document(donationId)
        val now = System.currentTimeMillis()
        val updates = mutableMapOf<String, Any>(
            "status" to newStatus
        )

        if (!ngoId.isNullOrBlank()) updates["ngoId"] = ngoId
        if (!ngoName.isNullOrBlank()) updates["ngoName"] = ngoName

        when (newStatus) {
            "Accepted" -> {
                updates["acceptedAt"] = now
            }
            "Volunteer Assigned" -> {
                updates["assignedAt"] = now
                if (!volunteerName.isNullOrBlank()) updates["volunteerName"] = volunteerName
                if (!volunteerPhone.isNullOrBlank()) updates["volunteerPhone"] = volunteerPhone
                if (!volunteerVehicle.isNullOrBlank()) updates["volunteerVehicle"] = volunteerVehicle
            }
            "Volunteer On The Way" -> {
                updates["pickupStartedAt"] = now
            }
            "Volunteer Near Pickup" -> {
                updates["nearPickupAt"] = now
            }
            "Food Collected" -> {
                updates["collectedAt"] = now
            }
            "Delivery Started" -> {
                updates["deliveryStartedAt"] = now
            }
            "Delivered" -> {
                updates["deliveredAt"] = now
            }
            "Completed" -> {
                updates["completedAt"] = now
            }
        }

        docRef.update(updates).await()

        try {
            val donationSnap = docRef.get().await()
            val donation = parseDonationEntity(donationSnap)
            if (donation != null) {
                // 1. Structured Notifications for Donor
                val (donorTitle, donorMessage, donorType) = when (newStatus) {
                    "Accepted" -> Triple("🤝 Donation Accepted", "${donation.ngoName.ifBlank { "Hope NGO" }} accepted your donation and will arrange pickup.", "donation_accepted")
                    "Volunteer Assigned" -> Triple("🚚 Volunteer Assigned", "Volunteer ${donation.volunteerName.ifBlank { "Ravi" }} has been assigned for pickup.", "volunteer_assigned")
                    "Volunteer On The Way" -> Triple("📍 Pickup Started", "Volunteer ${donation.volunteerName.ifBlank { "Ravi" }} is on the way to your location.", "pickup_started")
                    "Food Collected" -> Triple("📦 Food Picked Up", "Your donation has been collected successfully.", "food_collected")
                    "Delivered" -> Triple("❤️ Donation Delivered", "Your donated food has been delivered successfully.", "food_delivered")
                    "Completed" -> Triple("🎉 Donation Completed", "Thank you for helping reduce food waste.", "donation_completed")
                    "Cancelled" -> Triple("❌ Donation Cancelled", "Your donation has been cancelled.", "donation_cancelled")
                    "Expired" -> Triple("⏰ Donation Expired", "No NGO claimed your donation before expiry.", "donation_expired")
                    else -> Triple("Donation Update", "Status updated for '${donation.title}' to $newStatus.", "donation_updated")
                }

                if (donation.donorId.isNotBlank()) {
                    firebaseService.sendNotification(
                        com.example.data.firebase.models.Notification(
                            userId = donation.donorId,
                            role = "donor",
                            title = donorTitle,
                            message = donorMessage,
                            type = donorType,
                            relatedDonationId = donation.id,
                            timestamp = now
                        )
                    )
                }

                if ((newStatus == "Delivered" || newStatus == "Completed") && donation.donorId.isNotBlank()) {
                    val fedCount = (donation.quantity * 0.9).toInt().coerceAtLeast(1)
                    firebaseService.sendNotification(
                        com.example.data.firebase.models.Notification(
                            userId = donation.donorId,
                            role = "donor",
                            title = "❤️ Impact Summary",
                            message = "Your donation helped feed $fedCount people.",
                            type = "impact",
                            relatedDonationId = donation.id,
                            timestamp = now + 1
                        )
                    )
                }

                // 2. Structured Notifications for NGO
                val targetNgoId = if (!ngoId.isNullOrBlank()) ngoId else donation.ngoId
                val (ngoTitle, ngoMessage, ngoType) = when (newStatus) {
                    "Accepted" -> Triple("✅ Claim Accepted", "You accepted donation '${donation.title}'.", "accepted")
                    "Volunteer Assigned" -> Triple("👤 Volunteer Assigned", "Volunteer Team '${donation.volunteerName.ifBlank { "Alpha" }}' assigned.", "assigned")
                    "Volunteer On The Way" -> Triple("🚚 Volunteer En Route", "Volunteer started journey for '${donation.title}'.", "pickup_started")
                    "Volunteer Near Pickup" -> Triple("📍 Volunteer Arrived", "Volunteer reached donor location for '${donation.title}'.", "near_pickup")
                    "Food Collected" -> Triple("📦 Food Picked Up", "Food successfully collected for '${donation.title}'.", "collected")
                    "Delivery Started" -> Triple("🚚 Food In Transit", "Volunteer is transporting food for '${donation.title}'.", "in_transit")
                    "Delivered", "Completed" -> Triple("🏠 Delivery Completed", "Food delivered successfully for '${donation.title}'.", "delivered")
                    "Cancelled" -> Triple("❌ Donation Cancelled", "The donor cancelled donation '${donation.title}'.", "cancelled")
                    else -> Triple("Rescue Update", "Status updated for '${donation.title}' to $newStatus.", "general")
                }

                if (targetNgoId.isNotBlank()) {
                    firebaseService.sendNotification(
                        com.example.data.firebase.models.Notification(
                            userId = targetNgoId,
                            role = "ngo",
                            title = ngoTitle,
                            message = ngoMessage,
                            type = ngoType,
                            relatedDonationId = donation.id,
                            timestamp = now
                        )
                    )
                }

                // 3. Structured Notifications for Volunteer
                val (volTitle, volMessage, volType) = when (newStatus) {
                    "Volunteer Assigned" -> Triple("📋 Pickup Task Assigned", "Pickup assigned for '${donation.title}' at ${donation.location}.", "pickup_assigned")
                    "Volunteer On The Way" -> Triple("🚚 Pickup Started", "En route to collect food for '${donation.title}'.", "pickup_started")
                    "Food Collected" -> Triple("📦 Food Picked Up", "Successfully collected '${donation.title}'.", "collected")
                    "Delivery Started" -> Triple("🚚 Delivery Started", "Transporting '${donation.title}' to distribution center.", "in_transit")
                    "Delivered", "Completed" -> Triple("🎉 Task Completed", "Successfully delivered '${donation.title}'.", "delivered")
                    "Cancelled" -> Triple("❌ Task Cancelled", "Pickup task for '${donation.title}' was cancelled.", "cancelled")
                    else -> Triple("Task Update", "Task status updated for '${donation.title}'.", "general")
                }

                firebaseService.sendNotification(
                    com.example.data.firebase.models.Notification(
                        userId = "",
                        role = "volunteer",
                        title = volTitle,
                        message = volMessage,
                        type = volType,
                        relatedDonationId = donation.id,
                        timestamp = now
                    )
                )

                // 4. Structured Notifications for Admin
                if (newStatus == "Completed" || newStatus == "Cancelled") {
                    val (adminTitle, adminMessage, adminType) = if (newStatus == "Completed") {
                        Triple("🎉 Rescue Completed", "Donation '${donation.title}' was successfully rescued and delivered.", "completed")
                    } else {
                        Triple("❌ Rescue Cancelled", "Donation '${donation.title}' was cancelled.", "cancelled")
                    }
                    firebaseService.sendNotification(
                        com.example.data.firebase.models.Notification(
                            userId = "",
                            role = "admin",
                            title = adminTitle,
                            message = adminMessage,
                            type = adminType,
                            relatedDonationId = donation.id,
                            timestamp = now
                        )
                    )
                }

                if (newStatus == "Completed") {
                    if (donation.ngoId.isNotBlank()) {
                        try {
                            val ngoDoc = firestore.collection("users").document(donation.ngoId).get().await()
                            val ngo = ngoDoc.toObject(com.example.data.firebase.models.User::class.java)
                            if (ngo != null) {
                                val updatedNgo = ngo.copy(
                                    mealsSaved = ngo.mealsSaved + donation.quantity,
                                    co2OffsetKg = ngo.co2OffsetKg + (donation.quantity * 0.45),
                                    totalDonations = ngo.totalDonations + 1,
                                    impactScore = ngo.impactScore + 25
                                )
                                firestore.collection("users").document(donation.ngoId).set(updatedNgo).await()
                            }
                        } catch (e: Exception) {
                            Log.e("FoodShareRepository", "Error updating NGO stats: ${e.localizedMessage}")
                        }
                    }
                    if (donation.donorId.isNotBlank()) {
                        try {
                            val donorDoc = firestore.collection("users").document(donation.donorId).get().await()
                            val donor = donorDoc.toObject(com.example.data.firebase.models.User::class.java)
                            if (donor != null) {
                                val updatedDonor = donor.copy(
                                    mealsSaved = donor.mealsSaved + donation.quantity,
                                    co2OffsetKg = donor.co2OffsetKg + (donation.quantity * 0.45),
                                    totalDonations = donor.totalDonations + 1,
                                    impactScore = donor.impactScore + 25
                                )
                                firestore.collection("users").document(donation.donorId).set(updatedDonor).await()
                            }
                        } catch (e: Exception) {
                            Log.e("FoodShareRepository", "Error updating Donor stats: ${e.localizedMessage}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("FoodShareRepository", "Error sending stage update notification/stats: ${e.localizedMessage}")
        }

        return true
    }

    private suspend fun updateProfileDoc(entity: UserEntity) {
        val user = entity.toFirebaseUser()
        firestore.collection("users").document(entity.id).set(user).await()
    }


    suspend fun addFoodAnalysis(title: String, status: String, confidence: Double, freshnessPercentage: Int) {
        val entity = FoodAnalysisEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            status = status,
            confidence = confidence,
            freshnessPercentage = freshnessPercentage
        )
        firestore.collection("food_analyses").document(entity.id).set(entity).await()
    }

    suspend fun addPrediction(eventType: String, expectedGuests: Int, predictedMeals: Int, surplusPercentage: Int, recommendation: String) {
        val entity = PredictionEntity(
            id = UUID.randomUUID().toString(),
            eventType = eventType,
            expectedGuests = expectedGuests,
            predictedSurplusMeals = predictedMeals,
            surplusPercentage = surplusPercentage,
            recommendation = recommendation
        )
        firestore.collection("predictions").document(entity.id).set(entity).await()
    }

    suspend fun deleteUser(user: UserEntity) {
        firestore.collection("users").document(user.id).delete().await()
    }

    suspend fun deleteDonation(donationId: String) {
        firestore.collection("donations").document(donationId).delete().await()
    }

    suspend fun updateDonation(donationId: String, title: String, quantity: Int) {
        firestore.collection("donations").document(donationId).update(
            mapOf("foodName" to title, "quantity" to quantity)
        ).await()

        try {
            val ngoNotif = com.example.data.firebase.models.Notification(
                userId = "",
                role = "ngo",
                title = "📝 Donation Details Updated",
                message = "Donor updated details for '$title' ($quantity Meals).",
                type = "details_updated",
                relatedDonationId = donationId,
                timestamp = System.currentTimeMillis()
            )
            firebaseService.sendNotification(ngoNotif)
        } catch (e: Exception) {
            Log.e("FoodShareRepository", "Error sending update notification: ${e.localizedMessage}")
        }
    }
}
