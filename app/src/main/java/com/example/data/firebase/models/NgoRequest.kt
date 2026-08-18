package com.example.data.firebase.models

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class NgoRequest(
    val requestId: String = "",
    val ngoId: String = "",
    val donationId: String = "",
    val status: String = "Pending", // "Pending", "Accepted", "Completed", "Rejected"
    @ServerTimestamp val acceptedAt: Date? = null,
    @ServerTimestamp val completedAt: Date? = null
)
