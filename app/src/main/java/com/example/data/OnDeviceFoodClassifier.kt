package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.example.data.model.*
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.Locale

class OnDeviceFoodClassifier(private val context: Context? = null) {

    companion object {
        private const val TAG = "ON_DEVICE_AI"
        const val FOOD_MODEL_FILE = "models/food_classifier.tflite"
        const val FOOD_LABELS_FILE = "models/food_labels.txt"
        const val FRESHNESS_MODEL_FILE = "models/food_freshness.tflite"
        const val FRESHNESS_LABELS_FILE = "models/freshness_labels.txt"

        const val INPUT_IMAGE_WIDTH = 224
        const val INPUT_IMAGE_HEIGHT = 224
        const val NUM_CHANNELS = 3
    }

    private var foodInterpreter: Interpreter? = null
    private var freshnessInterpreter: Interpreter? = null
    private var foodLabels: List<String> = emptyList()
    private var freshnessLabels: List<String> = emptyList()

    var isFoodModelAvailable: Boolean = false
        private set
    var isFreshnessModelAvailable: Boolean = false
        private set

    init {
        val hasContext = context != null
        Log.i(TAG, "AI_MODEL_CONTEXT_AVAILABLE=$hasContext")

        context?.let { ctx ->
            Log.i(TAG, "AI_MODEL: modelLoadStarted=true")
            Log.i(TAG, "AI_MODEL: assetPath=$FOOD_MODEL_FILE")
            try {
                val fd = ctx.assets.openFd(FOOD_MODEL_FILE)
                val assetSize = fd.declaredLength
                fd.close()
                Log.i(TAG, "AI_MODEL: assetExists=true")
                Log.i(TAG, "AI_MODEL_ASSET_SIZE_BYTES=$assetSize")
            } catch (e: Exception) {
                Log.i(TAG, "AI_MODEL: assetExists=false")
                Log.e(TAG, "AI_MODEL_ERROR: ${e.javaClass.simpleName} - ${e.message}")
            }

            try {
                val foodModelBuffer = loadModelFile(ctx, FOOD_MODEL_FILE)
                if (foodModelBuffer != null) {
                    foodInterpreter = Interpreter(foodModelBuffer)
                    foodLabels = loadLabelsFile(ctx, FOOD_LABELS_FILE)
                    isFoodModelAvailable = true
                    Log.i(TAG, "AI_MODEL: modelLoadSuccessful=true")
                    Log.i(TAG, "AI_MODEL_INTERPRETER_AVAILABLE=true")
                    Log.i(TAG, "AI_MODEL_LABELS_COUNT=${foodLabels.size}")
                } else {
                    Log.w(TAG, "AI_MODEL: modelLoadSuccessful=false")
                    Log.i(TAG, "AI_MODEL_INTERPRETER_AVAILABLE=false")
                }
            } catch (e: Exception) {
                Log.w(TAG, "AI_MODEL: modelLoadSuccessful=false")
                Log.e(TAG, "AI_MODEL_ERROR: ${e.javaClass.simpleName} - ${e.message}")
            }

            // Freshness Model Initialization: 2-Class Verification
            try {
                val freshnessModelBuffer = loadModelFile(ctx, FRESHNESS_MODEL_FILE)
                if (freshnessModelBuffer != null) {
                    val interp = Interpreter(freshnessModelBuffer)
                    val inShape = interp.getInputTensor(0).shape()
                    val outShape = interp.getOutputTensor(0).shape()
                    val numClasses = if (outShape.size > 1) outShape[1] else outShape[0]

                    if (numClasses == 2) {
                        freshnessInterpreter = interp
                        freshnessLabels = loadLabelsFile(ctx, FRESHNESS_LABELS_FILE)
                        isFreshnessModelAvailable = true
                        Log.i(TAG, "AI_FRESHNESS_MODEL: modelLoadSuccessful=true")
                        runFreshnessControlTest(ctx)
                        Log.i(TAG, "AI_FRESHNESS_MODEL: inputShape=${inShape.contentToString()}")
                        Log.i(TAG, "AI_FRESHNESS_MODEL: outputShape=${outShape.contentToString()}")
                    } else {
                        interp.close()
                        freshnessInterpreter = null
                        isFreshnessModelAvailable = false
                        Log.w(TAG, "CURRENT FRESHNESS MODEL IS NOT A 2-CLASS FRESHNESS MODEL (output classes: $numClasses, expected: 2). Disabling freshness model.")
                        Log.i(TAG, "AI_FRESHNESS_MODEL:\nmodelLoadSuccessful=false\nreason=NO_VALIDATED_FRESHNESS_MODEL")
                        Log.i(TAG, "AI_FRESHNESS_MODEL: modelLoadSuccessful=false")
                        Log.i(TAG, "AI_FRESHNESS_MODEL: reason=NO_VALIDATED_FRESHNESS_MODEL")
                    }
                } else {
                    freshnessInterpreter = null
                    isFreshnessModelAvailable = false
                    Log.i(TAG, "AI_FRESHNESS_MODEL:\nmodelLoadSuccessful=false\nreason=NO_VALIDATED_FRESHNESS_MODEL")
                    Log.i(TAG, "AI_FRESHNESS_MODEL: modelLoadSuccessful=false")
                    Log.i(TAG, "AI_FRESHNESS_MODEL: reason=NO_VALIDATED_FRESHNESS_MODEL")
                }
            } catch (e: Exception) {
                freshnessInterpreter = null
                isFreshnessModelAvailable = false
                Log.i(TAG, "AI_FRESHNESS_MODEL:\nmodelLoadSuccessful=false\nreason=NO_VALIDATED_FRESHNESS_MODEL")
                Log.i(TAG, "AI_FRESHNESS_MODEL: modelLoadSuccessful=false")
                Log.i(TAG, "AI_FRESHNESS_MODEL: reason=NO_VALIDATED_FRESHNESS_MODEL")
                Log.e(TAG, "AI_FRESHNESS_MODEL_ERROR: ${e.javaClass.simpleName} - ${e.message}")
            }
        }
    }

    private fun loadModelFile(context: Context, assetPath: String): MappedByteBuffer? {
        return try {
            val fileDescriptor = context.assets.openFd(assetPath)
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        } catch (e: IOException) {
            null
        }
    }

    private fun loadLabelsFile(context: Context, assetPath: String): List<String> {
        return try {
            context.assets.open(assetPath).bufferedReader().useLines { it.toList() }
        } catch (e: IOException) {
            emptyList()
        }
    }

    /**
     * Preprocesses bitmap image into normalized Float32 ByteBuffer (224x224x3) for Stage 1 MobileNet [-1.0, 1.0].
     */
    fun preprocessBitmap(bitmap: Bitmap): ByteBuffer {
        val resized = Bitmap.createScaledBitmap(bitmap, INPUT_IMAGE_WIDTH, INPUT_IMAGE_HEIGHT, true)
        val byteBuffer = ByteBuffer.allocateDirect(4 * INPUT_IMAGE_WIDTH * INPUT_IMAGE_HEIGHT * NUM_CHANNELS)
        byteBuffer.order(ByteOrder.nativeOrder())

        val intValues = IntArray(INPUT_IMAGE_WIDTH * INPUT_IMAGE_HEIGHT)
        resized.getPixels(intValues, 0, resized.width, 0, 0, resized.width, resized.height)

        var pixelIdx = 0
        for (i in 0 until INPUT_IMAGE_HEIGHT) {
            for (j in 0 until INPUT_IMAGE_WIDTH) {
                val pixelVal = intValues[pixelIdx++]
                // Normalize RGB channels to [-1.0, 1.0] for Stage 1 MobileNet Float32 model
                byteBuffer.putFloat(((pixelVal shr 16 and 0xFF) / 127.5f) - 1.0f)
                byteBuffer.putFloat(((pixelVal shr 8 and 0xFF) / 127.5f) - 1.0f)
                byteBuffer.putFloat(((pixelVal and 0xFF) / 127.5f) - 1.0f)
            }
        }
        byteBuffer.rewind()
        return byteBuffer
    }

    /**
     * Preprocesses bitmap image into raw Float32 ByteBuffer (224x224x3) in [0.0, 255.0] range
     * for the freshness model's internal Rescaling(1/255.0) layer.
     */
    fun preprocessBitmapForFreshness(bitmap: Bitmap): ByteBuffer {
        val resized = Bitmap.createScaledBitmap(bitmap, INPUT_IMAGE_WIDTH, INPUT_IMAGE_HEIGHT, true)
        val byteBuffer = ByteBuffer.allocateDirect(4 * INPUT_IMAGE_WIDTH * INPUT_IMAGE_HEIGHT * NUM_CHANNELS)
        byteBuffer.order(ByteOrder.nativeOrder())

        val intValues = IntArray(INPUT_IMAGE_WIDTH * INPUT_IMAGE_HEIGHT)
        resized.getPixels(intValues, 0, resized.width, 0, 0, resized.width, resized.height)

        var inputMin = Float.MAX_VALUE
        var inputMax = -Float.MAX_VALUE
        var sumVal = 0.0
        val count = INPUT_IMAGE_WIDTH * INPUT_IMAGE_HEIGHT * NUM_CHANNELS

        var pixelIdx = 0
        for (i in 0 until INPUT_IMAGE_HEIGHT) {
            for (j in 0 until INPUT_IMAGE_WIDTH) {
                val pixelVal = intValues[pixelIdx++]
                val r = (pixelVal shr 16 and 0xFF).toFloat()
                val g = (pixelVal shr 8 and 0xFF).toFloat()
                val b = (pixelVal and 0xFF).toFloat()

                byteBuffer.putFloat(r)
                byteBuffer.putFloat(g)
                byteBuffer.putFloat(b)

                if (r < inputMin) inputMin = r
                if (r > inputMax) inputMax = r
                if (g < inputMin) inputMin = g
                if (g > inputMax) inputMax = g
                if (b < inputMin) inputMin = b
                if (b > inputMax) inputMax = b
                sumVal += (r + g + b)
            }
        }
        byteBuffer.rewind()

        val inputMean = sumVal / count
        val firstPixelR = byteBuffer.getFloat(0)
        val firstPixelG = byteBuffer.getFloat(4)
        val firstPixelB = byteBuffer.getFloat(8)

        Log.i(
            TAG,
            "AI_REAL_FOOD_TEST_START:\noriginalWidth=${bitmap.width}\noriginalHeight=${bitmap.height}\nbitmapConfig=${bitmap.config}\nresizedWidth=$INPUT_IMAGE_WIDTH\nresizedHeight=$INPUT_IMAGE_HEIGHT\nresizeMethod=BILINEAR\nfilter=true\ninputMin=$inputMin\ninputMax=$inputMax\ninputMean=$inputMean\nfirstPixelR=$firstPixelR\nfirstPixelG=$firstPixelG\nfirstPixelB=$firstPixelB"
        )
        return byteBuffer
    }

    fun cleanFoodLabel(rawLabel: String): String {
        if (rawLabel.isBlank() || rawLabel.startsWith("Unknown", ignoreCase = true)) {
            return "Prepared Meal"
        }

        val firstPart = rawLabel.split(",")[0].split(";")[0].trim().lowercase()

        val cleanMap = mapOf(
            "pizza pizza pie" to "Pizza",
            "hotdog hot dog red hot" to "Hot Dog",
            "bagel beigel" to "Bagel",
            "head cabbage" to "Cabbage",
            "french loaf" to "French Bread",
            "spaghetti squash" to "Spaghetti Squash",
            "acorn squash" to "Acorn Squash",
            "butternut squash" to "Butternut Squash",
            "cucumber cuke" to "Cucumber",
            "artichoke globe artichoke" to "Artichoke",
            "pineapple ananas" to "Pineapple",
            "jackfruit jak jack" to "Jackfruit",
            "chocolate sauce chocolate syrup" to "Chocolate Syrup",
            "meat loaf meatloaf" to "Meatloaf",
            "potpie" to "Pot Pie",
            "ice cream icecream" to "Ice Cream",
            "ice lolly lolly lollipop popsicle" to "Popsicle",
            "cheeseburger" to "Cheeseburger",
            "burrito" to "Burrito",
            "guacamole" to "Guacamole",
            "consomme" to "Consomme",
            "hot pot hotpot" to "Hot Pot",
            "trifle" to "Trifle",
            "mashed potato" to "Mashed Potatoes",
            "broccoli" to "Broccoli",
            "cauliflower" to "Cauliflower",
            "strawberry" to "Strawberry",
            "orange" to "Orange",
            "lemon" to "Lemon",
            "fig" to "Fig",
            "banana" to "Banana",
            "pomegranate" to "Pomegranate",
            "carbonara" to "Carbonara",
            "dough" to "Dough",
            "red wine" to "Red Wine",
            "espresso" to "Espresso",
            "eggnog" to "Eggnog",
            "corn" to "Corn",
            "pretzel" to "Pretzel",
            "french fries chips" to "French Fries"
        )

        cleanMap[firstPart]?.let { return it }

        val words = firstPart.replace("_", " ").split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (words.isEmpty()) return "Prepared Meal"

        val selectedWords = if (words.size > 2 && !firstPart.contains("sauce") && !firstPart.contains("soup") && !firstPart.contains("salad") && !firstPart.contains("bread") && !firstPart.contains("cake") && !firstPart.contains("pie")) {
            words.take(2)
        } else {
            words
        }

        return selectedWords.joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
    }

    /**
     * Executes Stage 1 (Food Detection) and Stage 2 (Freshness Analysis) using on-device TFLite models.
     */
    fun classifyFoodImage(bitmap: Bitmap): FoodVerificationResult {
        Log.i(TAG, "FOOD_GATE_START")

        if (!isFoodModelAvailable || foodInterpreter == null) {
            Log.e(TAG, "ON_DEVICE_AI_ERROR: $FOOD_MODEL_FILE missing from assets/models/")
            return FoodVerificationResult(
                verificationStatus = VerificationStatus.ANALYSIS_FAILED,
                isFood = false,
                reason = "Unable to run on-device food classification ($FOOD_MODEL_FILE missing).",
                canPublish = false
            )
        }

        // 1. Preprocess Bitmap for Stage 1 Food Classifier
        val inputBuffer = preprocessBitmap(bitmap)

        // 2. Run Stage 1 Food Classifier Model
        val numClasses = if (foodLabels.isNotEmpty()) foodLabels.size else 1000
        val outputScores = Array(1) { FloatArray(numClasses) }

        try {
            inputBuffer.rewind()
            foodInterpreter?.run(inputBuffer, outputScores)
        } catch (e: Exception) {
            Log.e(TAG, "AI_MODEL_INFERENCE_ERROR: ${e.javaClass.name} - ${e.message}", e)
            return FoodVerificationResult(
                verificationStatus = VerificationStatus.ANALYSIS_FAILED,
                isFood = false,
                reason = "Unable to run on-device food classification: ${e.localizedMessage}",
                canPublish = false
            )
        }

        // 3. Evaluate Stage 1 Output (Food/Non-Food Gate)
        val scores = outputScores[0]
        var maxScore = -Float.MAX_VALUE
        var maxClassIdx = -1

        for (i in scores.indices) {
            val s = scores[i]
            if (s > maxScore) {
                maxScore = s
                maxClassIdx = i
            }
        }

        // Compute Softmax probability for top class logit
        var sumExp = 0.0
        for (i in scores.indices) {
            sumExp += Math.exp((scores[i] - maxScore).toDouble())
        }
        val softmaxProb = if (sumExp > 0.0) (1.0 / sumExp).toFloat() else 0f

        val rawLabel = if (maxClassIdx in foodLabels.indices) foodLabels[maxClassIdx] else "Unknown Object"
        val lowerLabel = rawLabel.lowercase()

        // Comprehensive Non-Food keywords list (documents, paper, electronics, footwear, clothing, vehicles, animals, furniture, wall, etc.)
        val nonFoodKeywords = setOf(
            "handwritten", "paper", "document", "envelope", "letter", "book", "comic", "notebook", "binder", "folder",
            "web", "site", "screen", "monitor", "cellular telephone", "mobile phone", "cellular phone", "phone",
            "laptop", "computer", "keyboard", "mouse", "remote control", "typewriter", "paper towel", "toilet tissue",
            "shoe", "sneaker", "boot", "sandal", "running shoe", "sock", "pant", "jean", "skirt", "coat", "jacket",
            "shirt", "t-shirt", "jersey", "sweater", "vest", "tie", "glove", "hat", "cap", "helmet", "belt", "wallet",
            "car", "truck", "pickup", "bus", "van", "ambulance", "taxi", "tractor", "bicycle", "motorcycle", "moped",
            "dog", "retriever", "terrier", "poodle", "cat", "persian cat", "bird", "parrot", "pigeon", "owl", "horse",
            "cow", "bull", "pig", "sheep", "goat", "deer", "bear", "lion", "tiger", "wolf", "fox", "elephant", "monkey",
            "wall", "carpet", "rug", "door", "window", "curtain", "ceiling", "floor", "brick", "concrete", "tile",
            "screwdriver", "hammer", "wrench", "pliers", "saw", "drill", "flashlight", "shovel", "rake", "broom"
        )

        // Known document/paper ImageNet class indices
        val documentIndices = setOf(446, 453, 454, 549, 623, 637, 681, 700, 815, 893, 916, 917, 921, 922, 999)

        val top5Indices = scores.indices.sortedByDescending { scores[it] }.take(5)
        val top5Labels = top5Indices.map { idx -> if (idx in foodLabels.indices) foodLabels[idx].lowercase() else "" }

        val foodKeywords = setOf(
            "guacamole", "consomme", "hot pot", "hotpot", "trifle", "ice cream", "icecream", "bagel", "pretzel",
            "cheeseburger", "hotdog", "mashed potato", "cabbage", "broccoli", "cauliflower", "zucchini", "squash",
            "cucumber", "artichoke", "bell pepper", "mushroom", "strawberry", "orange", "lemon", "fig", "pineapple",
            "banana", "jackfruit", "pomegranate", "carbonara", "chocolate", "dough", "meat loaf", "pizza", "potpie",
            "burrito", "eggnog", "corn", "apple", "bread", "sandwich", "soup bowl", "wok", "frying pan", "crock pot",
            "plate", "confectionery", "food", "dish", "meal", "soup", "rice", "meat", "curry", "stew", "bowl", "tray",
            "pot", "pan", "fruit", "vegetable", "sauce", "dessert", "cake", "pie", "salad", "noodle", "pasta"
        )
        val foodIndices = setOf(467, 505, 509, 521, 567, 582, 659, 684, 809, 849, 909, 987)

        val isTopNonFoodKeyword = nonFoodKeywords.any { lowerLabel.contains(it) } || (maxClassIdx in documentIndices)
        val hasFoodInTop5 = top5Indices.any { i ->
            (i in 923..969) || (i in foodIndices) || foodKeywords.any { k -> top5Labels[top5Indices.indexOf(i)].contains(k) }
        }

        // Clearly non-food decision
        val isClearlyNonFood = isTopNonFoodKeyword && !hasFoodInTop5

        if (isClearlyNonFood) {
            Log.i(TAG, "FOOD_GATE_RESULT:\nisFood=false\nverificationStatus=NON_FOOD\ncanPublish=false\nreason=\"No food was detected in the uploaded image.\"\nfreshnessModelExecuted=false")
            Log.i(TAG, "FINAL_VERIFICATION_RESULT:\nverificationStatus=NON_FOOD\nisFood=false\nvisualCondition=UNABLE_TO_ASSESS\ncanPublish=false\nisValidFoodImage=false\nreason=\"No food was detected in the uploaded image.\"")
            return FoodVerificationResult(
                verificationStatus = VerificationStatus.NON_FOOD,
                isFood = false,
                foodName = "",
                foodCategory = "",
                foodClassificationConfidence = ConfidenceLevel.LOW,
                visualCondition = VisualCondition.UNABLE_TO_ASSESS,
                safeForDonation = false,
                reason = "No food was detected in the uploaded image.",
                canPublish = false
            )
        }

        // 4. Image is Food or Food-Like / Prepared Dish -> Proceed to Stage 2 Freshness Model
        Log.i(TAG, "FOOD_GATE_RESULT:\nisFood=true\nverificationStatus=FOOD\ncanPublish=pending\nreason=\"Food detected or food-like candidate\"\nfreshnessModelExecuted=true")
        Log.i(TAG, "FRESHNESS_MODEL_STARTED=true")

        val cleanedName = cleanFoodLabel(rawLabel)
        val foodName = if (softmaxProb >= 0.25f && cleanedName.isNotBlank() && !cleanedName.equals("Unknown Object", ignoreCase = true)) {
            cleanedName
        } else {
            "Prepared Meal"
        }

        val foodConfidence = when {
            softmaxProb >= 0.50f -> ConfidenceLevel.HIGH
            softmaxProb >= 0.25f -> ConfidenceLevel.MEDIUM
            else -> ConfidenceLevel.LOW
        }

        if (!isFreshnessModelAvailable || freshnessInterpreter == null) {
            Log.w(TAG, "AI_FRESHNESS_MODEL: modelLoadSuccessful=false")
            Log.i(TAG, "FINAL_VERIFICATION_RESULT:\nverificationStatus=FOOD\nisFood=true\nvisualCondition=UNABLE_TO_ASSESS\ncanPublish=false\nisValidFoodImage=false\nreason=\"Freshness could not be verified on-device.\"")
            return FoodVerificationResult(
                verificationStatus = VerificationStatus.FOOD,
                isFood = true,
                foodName = foodName,
                foodCategory = "Edible Food",
                foodClassificationConfidence = foodConfidence,
                visualCondition = VisualCondition.UNABLE_TO_ASSESS,
                visualConditionConfidence = ConfidenceLevel.LOW,
                visibleSpoilage = false,
                safeForDonation = false,
                reason = "Freshness could not be verified on-device.",
                canPublish = false
            )
        }

        val freshnessInputBuffer = preprocessBitmapForFreshness(bitmap)
        val freshnessOutputScores = Array(1) { FloatArray(2) }

        try {
            freshnessInterpreter?.run(freshnessInputBuffer, freshnessOutputScores)
        } catch (e: Exception) {
            Log.e(TAG, "AI_FRESHNESS_INFERENCE_ERROR: ${e.javaClass.name} - ${e.message}", e)
            return FoodVerificationResult(
                verificationStatus = VerificationStatus.FOOD,
                isFood = true,
                foodName = foodName,
                foodCategory = "Edible Food",
                foodClassificationConfidence = foodConfidence,
                visualCondition = VisualCondition.UNABLE_TO_ASSESS,
                visualConditionConfidence = ConfidenceLevel.LOW,
                visibleSpoilage = false,
                safeForDonation = false,
                reason = "Freshness could not be verified on-device.",
                canPublish = false
            )
        }

        val scoresArr = freshnessOutputScores[0]
        val freshProbability = scoresArr[0]
        val spoiledProbability = scoresArr[1]

        val predictedClass = if (spoiledProbability > freshProbability) 1 else 0
        val confidence = scoresArr[predictedClass]
        val predictedLabel = if (predictedClass == 0) "Fresh" else "Spoiled"

        Log.i(
            TAG,
            "FRESHNESS_RESULT:\npredictedClass=$predictedClass\npredictedLabel=$predictedLabel\nfreshProbability=$freshProbability\nspoiledProbability=$spoiledProbability\nconfidence=$confidence"
        )

        val isFresh = (predictedClass == 0)
        val visualCond = if (isFresh) VisualCondition.FRESH else VisualCondition.SPOILED
        val isSpoiled = !isFresh

        val finalVerificationStatus = VerificationStatus.FOOD
        val finalCanPublish = isFresh
        val finalReason = if (isFresh) {
            "Food appears fresh and is suitable for donation based on visual freshness screening."
        } else {
            "Food appears spoiled and cannot be published."
        }

        Log.i(
            TAG,
            "FINAL_VERIFICATION_RESULT:\nverificationStatus=FOOD\nisFood=true\nvisualCondition=${if (isFresh) "FRESH" else "SPOILED"}\ncanPublish=$finalCanPublish\nisValidFoodImage=$isFresh\nreason=\"$finalReason\""
        )

        return FoodVerificationResult(
            verificationStatus = finalVerificationStatus,
            isFood = true,
            foodName = foodName,
            foodCategory = "Edible Food",
            foodClassificationConfidence = foodConfidence,
            visualCondition = visualCond,
            visualConditionConfidence = if (confidence > 0.50f) ConfidenceLevel.HIGH else ConfidenceLevel.MEDIUM,
            visibleSpoilage = isSpoiled,
            safeForDonation = isFresh,
            reason = finalReason,
            canPublish = finalCanPublish,
            freshProbability = freshProbability,
            spoiledProbability = spoiledProbability,
            confidence = confidence
        )
    }

    private fun runFreshnessControlTest(context: Context) {
        val testImages = listOf("test_freshness/fresh.jpg", "test_freshness/spoiled.jpg")
        Log.i(TAG, "--- STARTING FRESHNESS CONTROL TEST ON ANDROID ---")
        for (path in testImages) {
            try {
                context.assets.open(path).use { inputStream ->
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    if (bitmap != null) {
                        Log.i(TAG, "Control Test Image: $path")
                        val inputBuffer = preprocessBitmapForFreshness(bitmap)
                        
                        // Calculate stats
                        var inputMin = Float.MAX_VALUE
                        var inputMax = -Float.MAX_VALUE
                        var sumVal = 0.0
                        val count = INPUT_IMAGE_WIDTH * INPUT_IMAGE_HEIGHT * NUM_CHANNELS
                        for (idx in 0 until count) {
                            val v = inputBuffer.getFloat(idx * 4)
                            if (v < inputMin) inputMin = v
                            if (v > inputMax) inputMax = v
                            sumVal += v
                        }
                        val inputMean = sumVal / count
                        val firstPixelR = inputBuffer.getFloat(0)
                        val firstPixelG = inputBuffer.getFloat(4)
                        val firstPixelB = inputBuffer.getFloat(8)

                        Log.i(TAG, "Control Image: $path (size: ${bitmap.width}x${bitmap.height})")
                        Log.i(TAG, "AI_FRESHNESS_INPUT:")
                        Log.i(TAG, "bitmapWidth=${bitmap.width}")
                        Log.i(TAG, "bitmapHeight=${bitmap.height}")
                        Log.i(TAG, "resizedWidth=$INPUT_IMAGE_WIDTH")
                        Log.i(TAG, "resizedHeight=$INPUT_IMAGE_HEIGHT")
                        Log.i(TAG, "inputMin=$inputMin")
                        Log.i(TAG, "inputMax=$inputMax")
                        Log.i(TAG, "inputMean=$inputMean")
                        Log.i(TAG, "firstPixelR=$firstPixelR")
                        Log.i(TAG, "firstPixelG=$firstPixelG")
                        Log.i(TAG, "firstPixelB=$firstPixelB")
                        Log.i(TAG, "first10_pixels=[")
                        for (idx in 0 until 10) {
                            val r = inputBuffer.getFloat(idx * 3 * 4)
                            val g = inputBuffer.getFloat((idx * 3 + 1) * 4)
                            val b = inputBuffer.getFloat((idx * 3 + 2) * 4)
                            Log.i(TAG, "(${r.toInt()},${g.toInt()},${b.toInt()})${if (idx < 9) "," else ""}")
                        }
                        Log.i(TAG, "]")

                        val outputScores = Array(1) { FloatArray(2) }
                        freshnessInterpreter?.run(inputBuffer, outputScores)

                        val scoresArr = outputScores[0]
                        val freshProb = scoresArr[0]
                        val spoiledProb = scoresArr[1]
                        val predictedClass = if (spoiledProb > freshProb) 1 else 0
                        val predictedLabel = if (predictedClass == 0) "Fresh" else "Spoiled"
                        val confidence = scoresArr[predictedClass]

                        Log.i(TAG, "Control Prediction for $path:")
                        Log.i(TAG, "predictedClass=$predictedClass")
                        Log.i(TAG, "predictedLabel=$predictedLabel")
                        Log.i(TAG, "freshProbability=$freshProb")
                        Log.i(TAG, "spoiledProbability=$spoiledProb")
                        Log.i(TAG, "confidence=$confidence")
                    } else {
                        Log.e(TAG, "Failed to decode control test image bitmap: $path")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error running control test for $path: ${e.message}", e)
            }
        }
        Log.i(TAG, "--- END OF FRESHNESS CONTROL TEST ON ANDROID ---")
    }
}
