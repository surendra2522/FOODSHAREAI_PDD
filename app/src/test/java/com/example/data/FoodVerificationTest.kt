package com.example.data

import android.graphics.Bitmap
import android.graphics.Color
import com.example.data.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FoodVerificationTest {

    private lateinit var authority: FoodVerificationAuthority

    @Before
    fun setUp() {
        authority = FoodVerificationAuthority()
    }

    // ====================================================
    // QUALITY CHECK TESTS
    // ====================================================

    @Test
    fun `quality check fails for micro dimension image`() {
        val bitmap = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
        val result = authority.performImageQualityCheck(bitmap)

        assertEquals(VerificationStatus.IMAGE_QUALITY_FAILED, result.verificationStatus)
        assertFalse(result.isFood)
        assertFalse(result.canPublish)
        assertTrue(result.reason.contains("clearer image"))
    }

    @Test
    fun `quality check fails for dark image`() {
        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(Color.BLACK) // Lum ~ 0

        val result = authority.performImageQualityCheck(bitmap)

        assertEquals(VerificationStatus.IMAGE_QUALITY_FAILED, result.verificationStatus)
        assertFalse(result.isFood)
        assertFalse(result.canPublish)
        assertTrue(result.reason.contains("clearer image"))
    }

    @Test
    fun `quality check fails for overexposed blank white image`() {
        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)

        val result = authority.performImageQualityCheck(bitmap)

        assertEquals(VerificationStatus.IMAGE_QUALITY_FAILED, result.verificationStatus)
        assertFalse(result.isFood)
        assertFalse(result.canPublish)
        assertTrue(result.reason.contains("clearer image"))
    }



    // ====================================================
    // NON-FOOD & UNCERTAIN STATE ENFORCEMENT TESTS
    // ====================================================

    @Test
    fun `NON_FOOD status strictly forces canPublish false and clears food fields`() {
        val rawResult = FoodVerificationResult(
            verificationStatus = VerificationStatus.NON_FOOD,
            isFood = false,
            foodName = "Fake Meal",
            foodCategory = "Fake Category",
            visualCondition = VisualCondition.FRESH,
            safeForDonation = true,
            canPublish = true
        )

        val verified = authority.enforceAuthorityRules(rawResult, quantity = 50, title = "Phone Image")

        assertEquals(VerificationStatus.NON_FOOD, verified.verificationStatus)
        assertFalse(verified.isFood)
        assertFalse(verified.canPublish)
        assertFalse(verified.safeForDonation)
        assertEquals("", verified.foodName)
        assertEquals("", verified.foodCategory)
        assertEquals(VisualCondition.UNABLE_TO_ASSESS, verified.visualCondition)
    }

    @Test
    fun `UNCERTAIN status strictly forces canPublish false and clears food fields`() {
        val rawResult = FoodVerificationResult(
            verificationStatus = VerificationStatus.UNCERTAIN,
            isFood = false,
            foodName = "Mixed Meal",
            foodCategory = "Cooked Meals",
            visualCondition = VisualCondition.ACCEPTABLE,
            canPublish = true
        )

        val verified = authority.enforceAuthorityRules(rawResult, quantity = 20, title = "Uncertain Image")

        assertEquals(VerificationStatus.UNCERTAIN, verified.verificationStatus)
        assertFalse(verified.isFood)
        assertFalse(verified.canPublish)
        assertFalse(verified.safeForDonation)
        assertEquals("", verified.foodName)
        assertEquals("", verified.foodCategory)
    }

    @Test
    fun `ANALYSIS_FAILED status strictly forces canPublish false and error explanation`() {
        val rawResult = FoodVerificationResult(
            verificationStatus = VerificationStatus.ANALYSIS_FAILED,
            isFood = false,
            canPublish = true
        )

        val verified = authority.enforceAuthorityRules(rawResult, quantity = 10, title = "Failed Analysis")

        assertEquals(VerificationStatus.ANALYSIS_FAILED, verified.verificationStatus)
        assertFalse(verified.isFood)
        assertFalse(verified.canPublish)
        assertFalse(verified.safeForDonation)
        assertTrue(verified.reason.contains("Unable to analyze image"))
    }


    // ====================================================
    // FOOD STATE & SPOILAGE VALIDATION TESTS
    // ====================================================

    @Test
    fun `FOOD state with visible spoilage is rejected from publication`() {
        val rawResult = FoodVerificationResult(
            verificationStatus = VerificationStatus.FOOD,
            isFood = true,
            foodName = "Steamed Rice",
            foodCategory = "Prepared Meals",
            foodClassificationConfidence = ConfidenceLevel.HIGH,
            visualCondition = VisualCondition.SPOILED,
            visibleSpoilage = true,
            canPublish = true
        )

        val verified = authority.enforceAuthorityRules(rawResult, quantity = 20, title = "Spoiled Rice")

        assertEquals(VerificationStatus.FOOD, verified.verificationStatus)
        assertTrue(verified.isFood)
        assertFalse(verified.canPublish)
        assertFalse(verified.safeForDonation)
        assertTrue(verified.reason.contains("spoiled"))
    }

    @Test
    fun `FOOD state with fresh condition and valid business inputs allows publication`() {
        val rawResult = FoodVerificationResult(
            verificationStatus = VerificationStatus.FOOD,
            isFood = true,
            foodName = "Vegetable Biryani",
            foodCategory = "Prepared Meals",
            foodClassificationConfidence = ConfidenceLevel.HIGH,
            visualCondition = VisualCondition.FRESH,
            visualConditionConfidence = ConfidenceLevel.HIGH,
            visibleSpoilage = false,
            canPublish = true
        )

        val verified = authority.enforceAuthorityRules(rawResult, quantity = 50, title = "Fresh Biryani")

        assertEquals(VerificationStatus.FOOD, verified.verificationStatus)
        assertTrue(verified.isFood)
        assertTrue(verified.canPublish)
        assertTrue(verified.safeForDonation)
        assertEquals("Vegetable Biryani", verified.foodName)
    }


    // ====================================================
    // CRITICAL REGRESSION TESTS (REQUIREMENT 29)
    // ====================================================

    @Test
    fun `CRITICAL REGRESSION - Non food items like phone or laptop never get verified as Mixed Meal`() {
        val phoneResult = authority.enforceAuthorityRules(
            FoodVerificationResult(
                verificationStatus = VerificationStatus.NON_FOOD,
                isFood = false,
                foodName = "",
                foodCategory = "",
                foodClassificationConfidence = ConfidenceLevel.LOW,
                visualCondition = VisualCondition.UNABLE_TO_ASSESS,
                visibleSpoilage = false,
                reason = "No edible food items were detected.",
                canPublish = false
            ),
            quantity = 10,
            title = "Phone Image"
        )

        assertNotEquals("Mixed Meal", phoneResult.foodName)
        assertNotEquals("Fresh", phoneResult.visualCondition.displayText)
        assertFalse(phoneResult.canPublish)
        assertFalse(phoneResult.isFood)
    }

    @Test
    fun `ANALYSIS_FAILED status preserves detailed error cause reason`() {
        val rawResult = FoodVerificationResult(
            verificationStatus = VerificationStatus.ANALYSIS_FAILED,
            isFood = false,
            reason = "On-device food model missing (food_classifier.tflite required in assets/models/).",
            canPublish = false
        )

        val verified = authority.enforceAuthorityRules(rawResult, quantity = 5, title = "Test Listing")

        assertEquals(VerificationStatus.ANALYSIS_FAILED, verified.verificationStatus)
        assertEquals("On-device food model missing (food_classifier.tflite required in assets/models/).", verified.reason)
        assertFalse(verified.canPublish)
    }

    @Test
    fun `FOOD status with UNABLE_TO_ASSESS freshness enforces canPublish false and safeForDonation false`() {
        val rawResult = FoodVerificationResult(
            verificationStatus = VerificationStatus.FOOD,
            isFood = true,
            foodName = "Mashed Potato",
            foodCategory = "Edible Food",
            visualCondition = VisualCondition.UNABLE_TO_ASSESS,
            reason = "Freshness could not be verified on-device.",
            canPublish = false,
            safeForDonation = false
        )

        val verified = authority.enforceAuthorityRules(rawResult, quantity = 10, title = "Mashed Potato")

        assertEquals(VerificationStatus.FOOD, verified.verificationStatus)
        assertTrue(verified.isFood)
        assertFalse(verified.canPublish)
        assertFalse(verified.safeForDonation)
        assertEquals(VisualCondition.UNABLE_TO_ASSESS, verified.visualCondition)
        assertEquals("Freshness could not be verified on-device.", verified.reason)
    }

}

