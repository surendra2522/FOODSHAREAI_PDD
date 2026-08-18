package com.example.data.local

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class PredictionEntity(
    val id: String = "",
    val userId: String = "",
    val eventType: String = "",
    val expectedGuests: Int = 0,
    val predictedSurplusMeals: Int = 0,
    val surplusPercentage: Int = 0,
    val recommendation: String = "",
    val type: String = "prediction",
    val timestamp: Long = System.currentTimeMillis()
)
