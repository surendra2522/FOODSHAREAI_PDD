package com.example.ui.utils

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.data.FoodVerificationAuthority
import com.example.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class FoodVisionAnalysisResult(
    val verificationResult: FoodVerificationResult = FoodVerificationResult(VerificationStatus.ANALYSIS_FAILED),
    val foodDetected: Boolean = false,
    val foodDetectionStatus: String = "No Food Detected",
    val isValidFoodImage: Boolean = false,
    val detectedFood: String = "",
    val detectedCategory: String = "",
    val freshnessRating: String = "",
    val confidenceLevelText: String = "LOW",
    val confidenceScore: Int = 0,
    val freshProbability: Float = 0f,
    val spoiledProbability: Float = 0f,
    val safetyDecision: String = "❌ Rejected",
    val reasonExplanation: String = "",
    val imageQualityText: String = "Good",
    val recommendationText: String = "",
    val rejectionReason: String? = null,
    val isApiFailure: Boolean = false
)

object FoodVisionAnalyzer {

    private const val TAG_VERIFY = "AI_VERIFY"

    suspend fun analyzeMultipleFoodImages(bitmaps: List<Bitmap>, context: Context? = null): FoodVisionAnalysisResult = withContext(Dispatchers.IO) {
        if (bitmaps.isEmpty()) {
            val emptyRes = FoodVerificationResult(
                verificationStatus = VerificationStatus.IMAGE_QUALITY_FAILED,
                isFood = false,
                reason = "Please upload a clear photo of the food you want to donate.",
                canPublish = false
            )
            return@withContext mapToLegacyResult(emptyRes)
        }

        Log.i(TAG_VERIFY, "FOOD_VERIFICATION_MULTI_IMAGE_START count=${bitmaps.size}")

        val authority = FoodVerificationAuthority(context)
        val resultsList = mutableListOf<FoodVerificationResult>()

        for (i in bitmaps.indices.take(5)) {
            val bitmap = bitmaps[i]
            val res = authority.verifyImage(bitmap)
            resultsList.add(res)
            Log.i(
                TAG_VERIFY,
                "IMAGE_INDEX=$i status=${res.verificationStatus} freshProb=${res.freshProbability} spoiledProb=${res.spoiledProbability} prediction=${res.visualCondition} confidence=${res.confidence}"
            )
        }

        // Multi-image consensus rule: ALL uploaded images must pass freshness
        val hasSpoiled = resultsList.any { it.visualCondition == VisualCondition.SPOILED || it.visibleSpoilage }
        val hasNonFoodOrQualityFailed = resultsList.any { it.verificationStatus == VerificationStatus.NON_FOOD || it.verificationStatus == VerificationStatus.IMAGE_QUALITY_FAILED }
        val allFresh = resultsList.all { it.canPublish && it.verificationStatus == VerificationStatus.FOOD && it.visualCondition == VisualCondition.FRESH }

        val primaryResult = resultsList.first()

        val finalResult = when {
            allFresh -> {
                primaryResult.copy(
                    verificationStatus = VerificationStatus.FOOD,
                    isFood = true,
                    visualCondition = VisualCondition.FRESH,
                    safeForDonation = true,
                    canPublish = true,
                    reason = "Food appears fresh and is eligible for donation."
                )
            }
            hasSpoiled -> {
                val spoiledItem = resultsList.first { it.visualCondition == VisualCondition.SPOILED || it.visibleSpoilage }
                primaryResult.copy(
                    verificationStatus = VerificationStatus.FOOD,
                    isFood = true,
                    visualCondition = VisualCondition.SPOILED,
                    visibleSpoilage = true,
                    safeForDonation = false,
                    canPublish = false,
                    freshProbability = spoiledItem.freshProbability,
                    spoiledProbability = spoiledItem.spoiledProbability,
                    confidence = spoiledItem.confidence,
                    reason = "Food appears spoiled and cannot be published."
                )
            }
            hasNonFoodOrQualityFailed -> {
                val invalidItem = resultsList.first { it.verificationStatus == VerificationStatus.NON_FOOD || it.verificationStatus == VerificationStatus.IMAGE_QUALITY_FAILED }
                primaryResult.copy(
                    verificationStatus = invalidItem.verificationStatus,
                    isFood = false,
                    visualCondition = VisualCondition.UNABLE_TO_ASSESS,
                    safeForDonation = false,
                    canPublish = false,
                    reason = invalidItem.reason.ifBlank { "Please upload a clear photo of the food you want to donate." }
                )
            }
            else -> primaryResult
        }

        return@withContext mapToLegacyResult(finalResult)
    }

    suspend fun analyzeFoodImage(bitmap: Bitmap, context: Context? = null): FoodVisionAnalysisResult {
        return analyzeMultipleFoodImages(listOf(bitmap), context)
    }

    /**
     * Maps authority FoodVerificationResult to FoodVisionAnalysisResult for UI binding
     */
    fun mapToLegacyResult(result: FoodVerificationResult): FoodVisionAnalysisResult {
        val statusText = when (result.verificationStatus) {
            VerificationStatus.FOOD -> if (result.visualCondition == VisualCondition.FRESH) "Food Quality Verified" else "Food Detected"
            VerificationStatus.NON_FOOD -> "Food Not Detected"
            VerificationStatus.UNCERTAIN -> "Food Not Detected"
            VerificationStatus.IMAGE_QUALITY_FAILED -> "Image Quality Too Low"
            VerificationStatus.ANALYSIS_FAILED -> "On-Device AI Model Required"
        }

        val isValid = result.verificationStatus == VerificationStatus.FOOD && result.visualCondition == VisualCondition.FRESH

        val decision = when {
            isValid -> "✅ Food Quality Verified"
            result.verificationStatus == VerificationStatus.NON_FOOD || result.verificationStatus == VerificationStatus.UNCERTAIN -> "❌ Food Not Detected"
            else -> "❌ Food Quality Rejected"
        }

        val rejectionMsg = if (!isValid) {
            when (result.verificationStatus) {
                VerificationStatus.NON_FOOD -> if (result.reason.isNotBlank()) result.reason else "Please upload a clear photo of the food you want to donate."
                VerificationStatus.UNCERTAIN -> if (result.reason.isNotBlank()) result.reason else "Please upload a clear photo of the food you want to donate."
                VerificationStatus.IMAGE_QUALITY_FAILED -> if (result.reason.isNotBlank()) result.reason else "Please upload a clear photo of the food."
                VerificationStatus.ANALYSIS_FAILED -> if (result.reason.isNotBlank()) result.reason else "On-device food model required."
                else -> if (result.visualCondition == VisualCondition.SPOILED) "Food appears spoiled or unsuitable for donation based on visual freshness screening." else result.reason.ifBlank { "Please upload a clear photo of the food." }
            }
        } else null

        val isFailure = result.verificationStatus == VerificationStatus.ANALYSIS_FAILED

        return FoodVisionAnalysisResult(
            verificationResult = result,
            foodDetected = result.isFood,
            foodDetectionStatus = statusText,
            isValidFoodImage = isValid,
            detectedFood = if (result.isFood) result.foodName else "",
            detectedCategory = if (result.isFood) result.foodCategory else "",
            freshnessRating = if (result.isFood) result.visualCondition.displayText else "",
            confidenceLevelText = result.foodClassificationConfidence.name,
            confidenceScore = when (result.foodClassificationConfidence) {
                ConfidenceLevel.HIGH -> 90
                ConfidenceLevel.MEDIUM -> 75
                ConfidenceLevel.LOW -> 40
            },
            freshProbability = result.freshProbability,
            spoiledProbability = result.spoiledProbability,
            safetyDecision = decision,
            reasonExplanation = if (result.verificationStatus == VerificationStatus.NON_FOOD && result.reason.isBlank()) "No food was detected in the uploaded image." else result.reason,
            imageQualityText = result.imageQualityReason ?: "Passed Quality Audit",
            recommendationText = if (isValid) "Food appears fresh and is suitable for donation based on visual freshness screening." else "Upload a clear photo of fresh food.",
            rejectionReason = rejectionMsg,
            isApiFailure = isFailure
        )
    }
}

