package com.example.data.firebase.models

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class Notification(
    val id: String = "",
    val userId: String = "",
    val role: String = "donor",
    val title: String = "",
    val message: String = "",
    val type: String = "general",
    val relatedDonationId: String = "",
    val isRead: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
