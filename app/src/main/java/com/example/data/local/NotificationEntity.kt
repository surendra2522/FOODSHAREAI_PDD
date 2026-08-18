package com.example.data.local

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class NotificationEntity(
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
