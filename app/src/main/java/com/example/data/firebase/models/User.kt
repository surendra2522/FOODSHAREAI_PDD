package com.example.data.firebase.models

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class User(
    val uid: String = "",
    val name: String = "",
    val displayName: String = "",
    val fullName: String = "",
    val email: String = "",
    val phone: String = "",
    val role: String = "",
    val profileImage: String = "",
    val impactScore: Int = 10,
    val mealsSaved: Int = 0,
    val co2OffsetKg: Double = 0.0,
    val totalDonations: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val fcmToken: String = "",
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
