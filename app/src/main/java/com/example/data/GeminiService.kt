package com.example.data

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class PredictionResult(
    val prediction: String,
    val confidence: Double,
    val explanation: String,
    val suggestedAction: String,
    val predictedSurplusMeals: Int,
    val surplusPercentage: Int,
    val recommendation: String
)

data class FreshnessResult(
    val status: String, // "Fresh", "Medium Risk", "Expired"
    val confidence: Double,
    val explanation: String,
    val suggestedAction: String,
    val freshnessPercentage: Int
)

class GeminiService {

    companion object {
        const val SERVICE_UNAVAILABLE_MSG = "AI services are currently unavailable."
        private const val TAG = "GEMINI_SERVICE"
        private const val MODEL_NAME = "gemini-2.0-flash"
    }

    private val apiKey: String? = try {
        BuildConfig.GEMINI_API_KEY.takeIf {
            it.isNotBlank() && it != "MY_GEMINI_API_KEY" && !it.startsWith("YOUR_")
        } ?: System.getenv("GEMINI_API_KEY")?.takeIf { it.isNotBlank() && !it.startsWith("YOUR_") }
          ?: BuildConfig.GEMINI_API_KEY.takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        System.getenv("GEMINI_API_KEY")?.takeIf { it.isNotBlank() && !it.startsWith("YOUR_") }
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    fun isKeyValid(): Boolean {
        return !apiKey.isNullOrBlank() && apiKey != "MY_GEMINI_API_KEY" && !apiKey.startsWith("YOUR_")
    }

    private suspend fun <T> executeWithRetryAndTimeout(
        timeoutMs: Long = 15000L,
        maxRetries: Int = 2,
        block: suspend () -> T
    ): T {
        var attempt = 0
        var lastError: Exception? = null
        while (attempt <= maxRetries) {
            try {
                return withTimeout(timeoutMs) {
                    block()
                }
            } catch (e: Exception) {
                lastError = e
                attempt++
                if (attempt <= maxRetries) {
                    delay(800L * attempt)
                }
            }
        }
        throw lastError ?: Exception(SERVICE_UNAVAILABLE_MSG)
    }

    suspend fun getSurplusPrediction(eventType: String, expectedGuests: Int): PredictionResult = withContext(Dispatchers.IO) {
        if (!isKeyValid()) {
            throw Exception(SERVICE_UNAVAILABLE_MSG)
        }

        val prompt = """
            You are an expert food waste reduction algorithm. Predict surplus food for:
            Event Type: $eventType
            Expected Guests: $expectedGuests
            
            Respond STRICTLY with a raw JSON object containing these keys:
            - "prediction" (string, summary e.g. "Over-preparation of staple foods by 15-20 portions expected")
            - "confidence" (double, e.g. 0.92 representing 92% confidence level)
            - "explanation" (string, detail on attendance curves and prep metrics)
            - "suggestedAction" (string, specific waste mitigation tactic)
            - "predictedSurplusMeals" (integer, expected count of surplus meals)
            - "surplusPercentage" (integer, expected percent leftover of total prepped food)
            Do not include any markdown formatting or comments.
        """.trimIndent()

        try {
            val responseText = executeWithRetryAndTimeout { makeApiCall(prompt) }
            val cleanJson = extractJsonObjectText(responseText)
            val json = JSONObject(cleanJson)
            val pred = json.optString("prediction", "${(expectedGuests * 0.15).toInt()} meals leftover")
            val conf = json.optDouble("confidence", 0.90)
            val expl = json.optString("explanation", "Based on normal turnout curves for $eventType.")
            val action = json.optString("suggestedAction", "Portion buffet items into smaller plates to curb plate-waste.")
            val surplusVal = json.optInt("predictedSurplusMeals", (expectedGuests * 0.15).toInt())
            val percentVal = json.optInt("surplusPercentage", (conf * 100).toInt())
            
            PredictionResult(
                prediction = pred,
                confidence = conf,
                explanation = expl,
                suggestedAction = action,
                predictedSurplusMeals = surplusVal,
                surplusPercentage = percentVal,
                recommendation = "$pred | Explanation: $expl | Suggested Action: $action"
            )
        } catch (e: Exception) {
            throw Exception(SERVICE_UNAVAILABLE_MSG)
        }
    }

    suspend fun getFreshnessAnalysis(foodType: String): FreshnessResult = withContext(Dispatchers.IO) {
        if (!isKeyValid()) {
            throw Exception(SERVICE_UNAVAILABLE_MSG)
        }

        val prompt = """
            Analyze food decay risk / freshness parameters for:
            Food Category: $foodType
            
            Respond STRICTLY with a raw JSON object containing:
            - "status" (string, choose exactly one: "Fresh", "Medium Risk", "Expired")
            - "confidence" (double, e.g. 0.95 representing 95% confidence level)
            - "explanation" (string, detailed biochemical safety audit context)
            - "suggestedAction" (string, standard refrigeration or container lock rules)
            - "freshnessPercentage" (integer, between 0 and 100)
            Do not include any markdown formatting or comments.
        """.trimIndent()

        try {
            val responseText = executeWithRetryAndTimeout { makeApiCall(prompt) }
            val cleanJson = extractJsonObjectText(responseText)
            val json = JSONObject(cleanJson)
            FreshnessResult(
                status = json.optString("status", "Fresh"),
                confidence = json.optDouble("confidence", 0.95),
                explanation = json.optString("explanation", "The item has strong preservation integrity when refrigerated."),
                suggestedAction = json.optString("suggestedAction", "Store at < 5°C and transport via coolbox."),
                freshnessPercentage = json.optInt("freshnessPercentage", 90)
            )
        } catch (e: Exception) {
            throw Exception(SERVICE_UNAVAILABLE_MSG)
        }
    }

    suspend fun getDonationSummary(
        eventName: String,
        foodCategory: String,
        surplusMeals: Int,
        freshness: String,
        location: String
    ): String = withContext(Dispatchers.IO) {
        if (!isKeyValid()) {
            throw Exception(SERVICE_UNAVAILABLE_MSG)
        }

        val prompt = """
            Create a professional, highly polished 2-sentence summary of the following donation listing:
            Event/Donation Name: $eventName
            Food Category: $foodCategory
            Expected Surplus: $surplusMeals portions
            Certified Freshness: $freshness
            Location: $location
            
            Keep the tone warm, respectful, and focused on redistribution impact.
        """.trimIndent()

        try {
            executeWithRetryAndTimeout { makeApiCall(prompt) }
        } catch (e: Exception) {
            throw Exception(SERVICE_UNAVAILABLE_MSG)
        }
    }

    suspend fun getAiRecommendations(
        eventType: String,
        foodCategory: String,
        expectedGuests: Int
    ): String = withContext(Dispatchers.IO) {
        if (!isKeyValid()) {
            throw Exception(SERVICE_UNAVAILABLE_MSG)
        }

        val prompt = """
            Give 3 custom expert recommendation bullet points for managing food waste or donation packaging, specifically tailored for:
            Event Type: $eventType
            Food Category: $foodCategory
            Guests Attending: $expectedGuests
            
            Keep points concise, practical, and action-oriented.
        """.trimIndent()

        try {
            executeWithRetryAndTimeout { makeApiCall(prompt) }
        } catch (e: Exception) {
            throw Exception(SERVICE_UNAVAILABLE_MSG)
        }
    }

    suspend fun getChatbotResponse(history: List<Pair<String, Boolean>>, message: String): String = withContext(Dispatchers.IO) {
        if (!isKeyValid()) {
            throw Exception(SERVICE_UNAVAILABLE_MSG)
        }

        try {
            executeWithRetryAndTimeout {
                makeApiCall(message)
            }
        } catch (e: Exception) {
            throw Exception(SERVICE_UNAVAILABLE_MSG)
        }
    }

    private fun extractJsonObjectText(rawText: String): String {
        val startIdx = rawText.indexOf('{')
        val endIdx = rawText.lastIndexOf('}')
        if (startIdx >= 0 && endIdx > startIdx) {
            return rawText.substring(startIdx, endIdx + 1).trim()
        }
        return rawText.replace("```json", "").replace("```", "").trim()
    }

    private suspend fun makeApiCall(prompt: String): String {
        val key = apiKey ?: throw Exception(SERVICE_UNAVAILABLE_MSG)

        val requestJson = JSONObject().apply {
            val contentsArray = JSONArray().apply {
                val contentObj = JSONObject().apply {
                    val partsArray = JSONArray().apply {
                        put(JSONObject().put("text", prompt))
                    }
                    put("parts", partsArray)
                }
                put(contentObj)
            }
            put("contents", contentsArray)
        }

        val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent?key=$key"
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception(SERVICE_UNAVAILABLE_MSG)
            }
            val bodyText = response.body?.string() ?: throw Exception(SERVICE_UNAVAILABLE_MSG)
            val jsonRes = JSONObject(bodyText)
            val candidates = jsonRes.getJSONArray("candidates")
            val content = candidates.getJSONObject(0).getJSONObject("content")
            val parts = content.getJSONArray("parts")
            return parts.getJSONObject(0).getString("text")
        }
    }
}
