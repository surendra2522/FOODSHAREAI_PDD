package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.example.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FoodVerificationAuthority(
    private val context: Context? = null
) {
    private val onDeviceClassifier = OnDeviceFoodClassifier(context)

    companion object {
        private const val TAG_VERIFY = "AI_VERIFY"
        private const val TAG_ERROR = "AI_VERIFY_ERROR"
    }

    suspend fun verifyImage(
        bitmap: Bitmap,
        quantity: Int = 10,
        title: String = "Donation"
    ): FoodVerificationResult = withContext(Dispatchers.IO) {
        Log.i(TAG_VERIFY, "image URI available / bitmap received")

        // 1. LOCAL IMAGE QUALITY AUDIT
        val qualityResult = performImageQualityCheck(bitmap)
        if (qualityResult.verificationStatus == VerificationStatus.IMAGE_QUALITY_FAILED) {
            Log.w(TAG_VERIFY, "image quality check failed: ${qualityResult.imageQualityReason}")
            return@withContext qualityResult
        }

        // 2. ON-DEVICE TFLITE / LITERT INFERENCE (STAGE 1: FOOD DETECTION & STAGE 2: FRESHNESS ANALYSIS)
        try {
            val result = onDeviceClassifier.classifyFoodImage(bitmap)

            // 3. BACKEND AUTHORITY BUSINESS RULE ENFORCEMENT
            val finalResult = enforceAuthorityRules(result, quantity, title)
            Log.i(TAG_VERIFY, "verificationStatus = ${finalResult.verificationStatus}, canPublish = ${finalResult.canPublish}")
            return@withContext finalResult
        } catch (e: Exception) {
            Log.e(TAG_ERROR, "Verification failed in authority: ${e.localizedMessage}", e)
            return@withContext FoodVerificationResult(
                verificationStatus = VerificationStatus.ANALYSIS_FAILED,
                isFood = false,
                foodName = "",
                foodCategory = "",
                foodClassificationConfidence = ConfidenceLevel.LOW,
                visualCondition = VisualCondition.UNABLE_TO_ASSESS,
                visualConditionConfidence = ConfidenceLevel.LOW,
                visibleSpoilage = false,
                safeForDonation = false,
                reason = "On-device AI analysis failed: ${e.localizedMessage ?: "Unknown error"}",
                canPublish = false
            )
        }
    }

    /**
     * Performs image quality validation (resolution, brightness, sharpness/blur).
     */
    fun performImageQualityCheck(bitmap: Bitmap): FoodVerificationResult {
        val width = bitmap.width
        val height = bitmap.height

        Log.d(TAG_VERIFY, "image width = $width, height = $height, byte size = ${bitmap.byteCount}")

        // 1. Resolution Check
        if (width < FoodVerificationConfig.MIN_IMAGE_DIMENSION || height < FoodVerificationConfig.MIN_IMAGE_DIMENSION) {
            return FoodVerificationResult(
                verificationStatus = VerificationStatus.IMAGE_QUALITY_FAILED,
                isFood = false,
                reason = "Please upload a clearer image.",
                imageQualityReason = "Image dimensions too small (minimum 120x120 required).",
                canPublish = false
            )
        }

        val stepX = (width / 20).coerceAtLeast(1)
        val stepY = (height / 20).coerceAtLeast(1)

        var totalLum = 0.0
        var totalDiff = 0.0
        var prevLum = -1.0
        var pixelCount = 0

        for (x in 0 until width step stepX) {
            for (y in 0 until height step stepY) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                val lum = 0.299 * r + 0.587 * g + 0.114 * b
                totalLum += lum
                pixelCount++

                if (prevLum >= 0) {
                    totalDiff += Math.abs(lum - prevLum)
                }
                prevLum = lum
            }
        }

        val avgLum = totalLum / pixelCount.coerceAtLeast(1)
        val avgGradient = totalDiff / pixelCount.coerceAtLeast(1)

        // 2. Dark Photo Check (< 20.0)
        if (avgLum < FoodVerificationConfig.MIN_LUMINANCE) {
            return FoodVerificationResult(
                verificationStatus = VerificationStatus.IMAGE_QUALITY_FAILED,
                isFood = false,
                reason = "Please upload a clearer image.",
                imageQualityReason = "Photo is too dark.",
                canPublish = false
            )
        }

        // 3. Overexposed / Blank Check (> 248.0)
        if (avgLum > FoodVerificationConfig.MAX_LUMINANCE) {
            return FoodVerificationResult(
                verificationStatus = VerificationStatus.IMAGE_QUALITY_FAILED,
                isFood = false,
                reason = "Please upload a clearer image.",
                imageQualityReason = "Photo is overexposed or blank.",
                canPublish = false
            )
        }

        // 4. Blur Check (Gradient < 9.5)
        if (avgGradient < FoodVerificationConfig.MIN_SHARPNESS_GRADIENT) {
            return FoodVerificationResult(
                verificationStatus = VerificationStatus.IMAGE_QUALITY_FAILED,
                isFood = false,
                reason = "Please upload a clearer image.",
                imageQualityReason = "Photo is too blurry.",
                canPublish = false
            )
        }

        return FoodVerificationResult(
            verificationStatus = VerificationStatus.FOOD,
            isFood = true
        )
    }

    /**
     * Enforces backend authority rules for Stage 1 (Food Detection) and Stage 2 (Freshness Analysis).
     */
    fun enforceAuthorityRules(
        rawResult: FoodVerificationResult,
        quantity: Int,
        title: String
    ): FoodVerificationResult {
        // Stage 1 Enforcement: NON_FOOD
        if (rawResult.verificationStatus == VerificationStatus.NON_FOOD) {
            return rawResult.copy(
                isFood = false,
                canPublish = false,
                foodName = "",
                foodCategory = "",
                visualCondition = VisualCondition.UNABLE_TO_ASSESS,
                visibleSpoilage = false,
                safeForDonation = false,
                reason = rawResult.reason.ifBlank { "No food was detected in the uploaded image." }
            )
        }

        // Stage 1 Enforcement: UNCERTAIN
        if (rawResult.verificationStatus == VerificationStatus.UNCERTAIN) {
            return rawResult.copy(
                isFood = false,
                canPublish = false,
                foodName = "",
                foodCategory = "",
                visualCondition = VisualCondition.UNABLE_TO_ASSESS,
                visibleSpoilage = false,
                safeForDonation = false,
                reason = "We couldn't confidently identify food in this image. Please upload a clearer photo."
            )
        }

        // Stage 1 Enforcement: IMAGE_QUALITY_FAILED
        if (rawResult.verificationStatus == VerificationStatus.IMAGE_QUALITY_FAILED) {
            return rawResult.copy(
                isFood = false,
                canPublish = false,
                foodName = "",
                foodCategory = "",
                visualCondition = VisualCondition.UNABLE_TO_ASSESS,
                visibleSpoilage = false,
                safeForDonation = false,
                reason = "Please upload a clearer image."
            )
        }

        // Stage 1 Enforcement: ANALYSIS_FAILED
        if (rawResult.verificationStatus == VerificationStatus.ANALYSIS_FAILED) {
            return rawResult.copy(
                isFood = false,
                canPublish = false,
                foodName = "",
                foodCategory = "",
                visualCondition = VisualCondition.UNABLE_TO_ASSESS,
                visibleSpoilage = false,
                safeForDonation = false,
                reason = if (rawResult.reason.isNotBlank()) rawResult.reason else "Unable to analyze image. On-device model required."
            )
        }

        // Stage 2 Enforcement: SPOILED or QUESTIONABLE food condition
        if (rawResult.visibleSpoilage || rawResult.visualCondition == VisualCondition.SPOILED || rawResult.visualCondition == VisualCondition.QUESTIONABLE) {
            return rawResult.copy(
                canPublish = false,
                safeForDonation = false,
                reason = "Food appears spoiled and cannot be published."
            )
        }

        // Stage 2 Enforcement: UNABLE_TO_ASSESS food condition or unverified freshness
        if (rawResult.visualCondition == VisualCondition.UNABLE_TO_ASSESS) {
            return rawResult.copy(
                canPublish = false,
                safeForDonation = false,
                reason = if (rawResult.reason.isNotBlank()) rawResult.reason else "Freshness could not be verified on-device."
            )
        }

        // Stage 2 Enforcement: FRESH or ACCEPTABLE food condition
        val isPublishable = rawResult.isFood && !rawResult.visibleSpoilage && (rawResult.visualCondition == VisualCondition.FRESH || rawResult.visualCondition == VisualCondition.ACCEPTABLE)

        return rawResult.copy(
            safeForDonation = isPublishable,
            canPublish = isPublishable,
            reason = if (isPublishable) {
                "Food appears fresh and is suitable for donation based on visual freshness screening."
            } else {
                rawResult.reason.ifBlank { "Freshness could not be verified on-device." }
            }
        )
    }
}
