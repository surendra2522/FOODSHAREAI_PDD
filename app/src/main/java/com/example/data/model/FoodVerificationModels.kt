package com.example.data.model

import android.graphics.Bitmap

/**
 * Explicit verification states for AI Food Verification Pipeline
 */
enum class VerificationStatus {
    FOOD,
    NON_FOOD,
    UNCERTAIN,
    IMAGE_QUALITY_FAILED,
    ANALYSIS_FAILED
}

/**
 * Calibrated confidence levels without fabricated percentages
 */
enum class ConfidenceLevel {
    HIGH,
    MEDIUM,
    LOW;

    companion object {
        fun fromString(value: String?): ConfidenceLevel {
            return when (value?.uppercase()?.trim()) {
                "HIGH" -> HIGH
                "MEDIUM" -> MEDIUM
                "LOW" -> LOW
                else -> LOW
            }
        }
    }
}

/**
 * Allowed visual condition values for detected food
 */
enum class VisualCondition(val displayText: String) {
    FRESH("Fresh"),
    ACCEPTABLE("Acceptable"),
    QUESTIONABLE("Questionable"),
    SPOILED("Spoiled"),
    UNABLE_TO_ASSESS("Unable to Assess");

    companion object {
        fun fromString(value: String?): VisualCondition {
            val normalized = value?.lowercase()?.trim() ?: ""
            return when {
                normalized.contains("spoil") || normalized.contains("rotten") || normalized.contains("decay") -> SPOILED
                normalized.contains("fresh") -> FRESH
                normalized.contains("acceptable") || normalized.contains("good") -> ACCEPTABLE
                normalized.contains("question") || normalized.contains("risk") -> QUESTIONABLE
                else -> UNABLE_TO_ASSESS
            }
        }
    }
}

/**
 * Centralized Configuration Values
 */
object FoodVerificationConfig {
    const val FOOD_DETECTION_THRESHOLD = 0.80
    const val MIN_IMAGE_DIMENSION = 120
    const val MIN_LUMINANCE = 20.0
    const val MAX_LUMINANCE = 248.0
    const val MIN_SHARPNESS_GRADIENT = 9.5
}

/**
 * Strict Food Verification Result Schema
 */
data class FoodVerificationResult(
    val verificationStatus: VerificationStatus,
    val isFood: Boolean = false,
    val foodName: String = "",
    val foodCategory: String = "",
    val foodClassificationConfidence: ConfidenceLevel = ConfidenceLevel.LOW,
    val visualCondition: VisualCondition = VisualCondition.UNABLE_TO_ASSESS,
    val visualConditionConfidence: ConfidenceLevel = ConfidenceLevel.LOW,
    val visibleSpoilage: Boolean = false,
    val safeForDonation: Boolean = false,
    val reason: String = "",
    val canPublish: Boolean = false,
    val imageQualityReason: String? = null,
    val freshProbability: Float = 0f,
    val spoiledProbability: Float = 0f,
    val confidence: Float = 0f
)

