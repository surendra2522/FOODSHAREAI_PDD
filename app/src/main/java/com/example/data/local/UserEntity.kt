package com.example.data.local

import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName

@IgnoreExtraProperties
data class UserEntity(
    val id: String = "", // UID from Firebase
    val email: String = "",
    val name: String = "",
    val phone: String = "",
    val role: String = "", // "donor", "ngo", "admin"
    @get:PropertyName("profileImage") @set:PropertyName("profileImage") var profileImage: String = "",
    val impactScore: Int = 10,
    val mealsSaved: Int = 0,
    val co2OffsetKg: Double = 0.0,
    val totalDonations: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val missionStatement: String = "Helping redistribute surplus food efficiently across the city.",
    val licenseNumber: String = "",
    val registrationId: String = "",
    val address: String = "",
    val operatingCities: String = "",
    val vehicleFleetCount: Int = 0,
    val volunteerCount: Int = 0,
    val foodCategories: String = "",
    val operatingHours: String = "",
    val emergencyContact: String = "",
    val serviceArea: String = "",
    val contactPerson: String = "",
    val city: String = "",
    val state: String = "",
    val pincode: String = "",
    val description: String = ""
)

fun com.example.data.firebase.models.User.toEntity(): UserEntity {
    return UserEntity(
        id = uid,
        email = email,
        name = name,
        phone = phone,
        role = role.lowercase().trim(),
        profileImage = profileImage,
        impactScore = impactScore,
        mealsSaved = mealsSaved,
        co2OffsetKg = co2OffsetKg,
        totalDonations = totalDonations,
        createdAt = createdAt,
        missionStatement = missionStatement,
        licenseNumber = licenseNumber,
        registrationId = registrationId,
        address = address,
        operatingCities = operatingCities,
        vehicleFleetCount = vehicleFleetCount,
        volunteerCount = volunteerCount,
        foodCategories = foodCategories,
        operatingHours = operatingHours,
        emergencyContact = emergencyContact,
        serviceArea = serviceArea,
        contactPerson = contactPerson,
        city = city,
        state = state,
        pincode = pincode,
        description = description
    )
}

fun UserEntity.toFirebaseUser(fcmToken: String = ""): com.example.data.firebase.models.User {
    return com.example.data.firebase.models.User(
        uid = id,
        name = name,
        email = email,
        phone = phone,
        role = role,
        profileImage = profileImage,
        impactScore = impactScore,
        mealsSaved = mealsSaved,
        co2OffsetKg = co2OffsetKg,
        totalDonations = totalDonations,
        createdAt = createdAt,
        fcmToken = fcmToken,
        missionStatement = missionStatement,
        licenseNumber = licenseNumber,
        registrationId = registrationId,
        address = address,
        operatingCities = operatingCities,
        vehicleFleetCount = vehicleFleetCount,
        volunteerCount = volunteerCount,
        foodCategories = foodCategories,
        operatingHours = operatingHours,
        emergencyContact = emergencyContact,
        serviceArea = serviceArea,
        contactPerson = contactPerson,
        city = city,
        state = state,
        pincode = pincode,
        description = description
    )
}
