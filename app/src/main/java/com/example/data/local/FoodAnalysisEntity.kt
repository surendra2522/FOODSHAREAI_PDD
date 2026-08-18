package com.example.data.local

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class FoodAnalysisEntity(
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val status: String = "",
    val confidence: Double = 0.0,
    val freshnessPercentage: Int = 0,
    val imageUrl: String = "",
    val type: String = "analysis",
    val timestamp: Long = System.currentTimeMillis()
)
