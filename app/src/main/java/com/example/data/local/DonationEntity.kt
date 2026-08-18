package com.example.data.local

import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName

@IgnoreExtraProperties
data class DonationEntity(
    @get:PropertyName("donationId") @set:PropertyName("donationId") var id: String = "",
    val donorId: String = "",
    val donorName: String = "",
    @get:PropertyName("foodName") @set:PropertyName("foodName") var title: String = "", 
    val foodType: String = "",
    val eventType: String = "",
    val quantity: Int = 0,
    // New fields for AI analysis status and confidence
    val analysisCompleted: Boolean = false,
    val aiConfidence: String? = null,
    val expectedGuests: Int = 0,
    @get:PropertyName("pickupAddress") @set:PropertyName("pickupAddress") var pickupTime: String = "", 
    val expiryTime: String = "",
    val location: String = "",
    val description: String = "",
    @get:PropertyName("foodImage") @set:PropertyName("foodImage") var imageUrl: String = "",
    val status: String = "Created",
    val ngoId: String = "",
    val ngoName: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis(),
    val acceptedAt: Long = 0L,
    val assignedAt: Long = 0L,
    val pickupStartedAt: Long = 0L,
    val nearPickupAt: Long = 0L,
    val collectedAt: Long = 0L,
    val deliveryStartedAt: Long = 0L,
    val deliveredAt: Long = 0L,
    val completedAt: Long = 0L,
    val volunteerName: String = "",
    val volunteerPhone: String = "",
    val volunteerVehicle: String = "",
    val currentLatitude: Double = 0.0,
    val currentLongitude: Double = 0.0
)
