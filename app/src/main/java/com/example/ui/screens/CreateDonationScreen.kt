package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.*
import com.example.ui.theme.*

import com.example.ui.utils.FoodVisionAnalysisResult
import com.example.ui.utils.FoodVisionAnalyzer
import com.example.ui.utils.ReportExporter
import com.example.ui.viewmodel.FoodShareViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CreateDonationScreen(
    viewModel: FoodShareViewModel,
    onDonationCreated: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val usersList by viewModel.usersList.collectAsState()

    // Current Active Step (1 to 5)
    var currentStep by remember { mutableStateOf(1) }

    // Validation state triggered on Next/Publish press
    var step1Submitted by remember { mutableStateOf(false) }
    var step2Submitted by remember { mutableStateOf(false) }
    var step3Submitted by remember { mutableStateOf(false) }
    var step4Submitted by remember { mutableStateOf(false) }

    // NGO Users from backend
    val ngoUsers = remember(usersList) {
        usersList.filter { it.role == "ngo" }
    }

    // ====================================================
    // FORM STATE — STEP 1: EVENT DETAILS
    // ====================================================
    var donationTitle by remember { mutableStateOf("") }
    var eventCategory by remember { mutableStateOf("Wedding") }
    var eventDate by remember { mutableStateOf("Today, ${SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())}") }
    var eventEndTime by remember { mutableStateOf("09:00 PM") }
    var expectedGuestsText by remember { mutableStateOf("") }
    var mealsPreparedText by remember { mutableStateOf("") }

    val eventCategories = listOf("Wedding", "Corporate Event", "Birthday Party", "Restaurant Surplus", "Buffet", "Community Feast")

    // Dynamic calculated surplus
    val calculatedSurplusMeals = remember(expectedGuestsText, mealsPreparedText) {
        val prepared = mealsPreparedText.toIntOrNull() ?: 0
        val guests = expectedGuestsText.toIntOrNull() ?: 0
        if (prepared > 0 && guests > 0) {
            (prepared - (guests * 0.75).toInt()).coerceAtLeast((prepared * 0.25).toInt())
        } else if (prepared > 0) {
            (prepared * 0.3).toInt().coerceAtLeast(10)
        } else 0
    }

    // ====================================================
    // FORM STATE — STEP 2: FOOD DETAILS
    // ====================================================
    var foodCategory by remember { mutableStateOf("Prepared Meals") }
    var packagingType by remember { mutableStateOf("Sealed Bins") }
    var freshnessWindowHrs by remember { mutableStateOf("4 Hours") }
    var specialInstructions by remember { mutableStateOf("") }

    val foodCategories = listOf("Prepared Meals", "Bakery", "Vegetarian", "Non-Vegetarian", "Snacks", "Drinks")
    val packagingOptions = listOf("Sealed Bins", "Thermal Boxes", "Eco Containers", "Wrapped Trays")
    val freshnessOptions = listOf("2 Hours", "4 Hours", "6 Hours", "8 Hours", "12 Hours")

    // ====================================================
    // FORM STATE — STEP 3: PICKUP DETAILS
    // ====================================================
    var locationAddress by remember { mutableStateOf("") }
    var contactPersonName by remember { mutableStateOf("") }
    var contactPhone by remember { mutableStateOf("") }
    var pickupTime by remember { mutableStateOf("09:30 PM") }

    var latitudeVal by remember { mutableStateOf(13.0480) }
    var longitudeVal by remember { mutableStateOf(80.0934) }
    var isFetchingLocation by remember { mutableStateOf(false) }
    var hasUserSelectedLocation by remember { mutableStateOf(false) }
    var isMapExpanded by remember { mutableStateOf(false) }

    val locationPermissionState = rememberPermissionState(permission = Manifest.permission.ACCESS_FINE_LOCATION)
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    fun fetchLiveGpsLocation() {
        if (locationPermissionState.status.isGranted) {
            isFetchingLocation = true
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                    if (loc != null) {
                        latitudeVal = loc.latitude
                        longitudeVal = loc.longitude
                        locationAddress = getAddressFromCoords(context, loc.latitude, loc.longitude)
                        hasUserSelectedLocation = true
                    }
                    isFetchingLocation = false
                }.addOnFailureListener {
                    isFetchingLocation = false
                }
            } catch (e: SecurityException) {
                isFetchingLocation = false
            }
        } else {
            locationPermissionState.launchPermissionRequest()
        }
    }

    val recommendedNgoName = remember(ngoUsers) {
        if (ngoUsers.isNotEmpty()) ngoUsers.first().name.ifBlank { "Hope Shelter Trust" }
        else "Hope Shelter Trust"
    }

    // ====================================================
    // FORM STATE — STEP 4: AI VERIFICATION
    // ====================================================
    val uploadedBitmaps = remember { mutableStateListOf<Bitmap>() }
    var foodVisionResult by remember { mutableStateOf<FoodVisionAnalysisResult?>(null) }
    var imageRejectionError by remember { mutableStateOf<String?>(null) }
    var isAnalyzingImage by remember { mutableStateOf(false) }
    val cameraPermissionState = rememberPermissionState(permission = Manifest.permission.CAMERA)

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        if (!cameraGranted) {
            imageRejectionError = "Camera permission required."
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 5)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            coroutineScope.launch(Dispatchers.IO) {
                isAnalyzingImage = true
                imageRejectionError = null
                foodVisionResult = null
                val newBitmaps = mutableListOf<Bitmap>()
                for (u in uris.take(5)) {
                    val compressed = compressImageUri(context, u)
                    if (compressed != null) {
                        newBitmaps.add(compressed)
                    }
                }

                if (newBitmaps.isNotEmpty()) {
                    val visionResult = FoodVisionAnalyzer.analyzeMultipleFoodImages(newBitmaps, context)
                    coroutineScope.launch(Dispatchers.Main) {
                        isAnalyzingImage = false
                        foodVisionResult = visionResult
                        uploadedBitmaps.clear()
                        uploadedBitmaps.addAll(newBitmaps)
                        if (!visionResult.isValidFoodImage) {
                            imageRejectionError = visionResult.rejectionReason ?: "Food verification failed. Please upload a clear photo of food."
                        } else {
                            imageRejectionError = null
                        }
                    }
                } else {
                    coroutineScope.launch(Dispatchers.Main) {
                        isAnalyzingImage = false
                        imageRejectionError = "Failed to process image file. Please try another photo."
                    }
                }
            }
        }
    }

    // ====================================================
    // STEP VALIDATIONS
    // ====================================================
    val isStep1Valid = remember(donationTitle, expectedGuestsText, mealsPreparedText) {
        donationTitle.isNotBlank() &&
        (expectedGuestsText.toIntOrNull() ?: 0) > 0 &&
        (mealsPreparedText.toIntOrNull() ?: 0) > 0
    }

    val isStep2Valid = remember(foodCategory, packagingType, freshnessWindowHrs) {
        foodCategory.isNotBlank() && packagingType.isNotBlank() && freshnessWindowHrs.isNotBlank()
    }

    val isStep3Valid = remember(locationAddress, contactPersonName, contactPhone) {
        locationAddress.isNotBlank() && contactPersonName.isNotBlank() && contactPhone.isNotBlank()
    }

    val isStep4Valid = remember(uploadedBitmaps.size, foodVisionResult) {
        uploadedBitmaps.isNotEmpty() &&
        foodVisionResult != null &&
        foodVisionResult?.verificationResult?.verificationStatus == VerificationStatus.FOOD &&
        foodVisionResult?.verificationResult?.isFood == true &&
        foodVisionResult?.verificationResult?.canPublish == true &&
        foodVisionResult?.isValidFoodImage == true
    }


    var isSubmitting by remember { mutableStateOf(false) }
    var showSuccessModal by remember { mutableStateOf(false) }
    var publishedDonationId by remember { mutableStateOf("#DON-${(100000..999999).random()}") }

    // Navigation logic with strict validation
    fun attemptNavigateNext() {
        when (currentStep) {
            1 -> {
                step1Submitted = true
                if (isStep1Valid) currentStep = 2
                else Toast.makeText(context, "Please complete all required event fields.", Toast.LENGTH_SHORT).show()
            }
            2 -> {
                step2Submitted = true
                if (isStep2Valid) currentStep = 3
                else Toast.makeText(context, "Please complete food details.", Toast.LENGTH_SHORT).show()
            }
            3 -> {
                step3Submitted = true
                if (isStep3Valid) currentStep = 4
                else Toast.makeText(context, "Please enter pickup address and contact details.", Toast.LENGTH_SHORT).show()
            }
            4 -> {
                step4Submitted = true
                if (isStep4Valid) currentStep = 5
                else Toast.makeText(context, "Please upload a verified food photo.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Auto-navigate to first incomplete step if publish clicked prematurely
    fun getFirstIncompleteStep(): Int {
        if (!isStep1Valid) return 1
        if (!isStep2Valid) return 2
        if (!isStep3Valid) return 3
        if (!isStep4Valid) return 4
        return 5
    }

    // ====================================================
    // FULL-SCREEN EXPANDED MAP DIALOG
    // ====================================================
    if (isMapExpanded) {
        Dialog(
            onDismissRequest = { isMapExpanded = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = PureWhite
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    OsmLogisticsMapView(
                        latitude = latitudeVal,
                        longitude = longitudeVal,
                        ngoName = recommendedNgoName,
                        hasSelectedLocation = hasUserSelectedLocation,
                        onLocationSelected = { lat, lng, addr ->
                            latitudeVal = lat
                            longitudeVal = lng
                            locationAddress = addr
                            hasUserSelectedLocation = true
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(16.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = PureWhite,
                        shadowElevation = 6.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Pin Precise Pickup Location", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = PrimaryText)
                                Text("Tap anywhere on the map to place pickup pin", fontSize = 12.sp, color = SecondaryText)
                            }
                            IconButton(onClick = { isMapExpanded = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = PrimaryText)
                            }
                        }
                    }

                    Button(
                        onClick = { isMapExpanded = false },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(24.dp)
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen, contentColor = PureWhite)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Confirm Pickup Location", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
    }

    // ====================================================
    // PROFESSIONAL SUCCESS SCREEN DIALOG
    // ====================================================
    if (showSuccessModal) {
        val totalMeals = calculatedSurplusMeals.coerceAtLeast(20)
        val beneficiaries = (totalMeals * 0.9).toInt()
        val co2Saved = String.format(Locale.US, "%.1f kg", totalMeals * 0.45)

        AlertDialog(
            onDismissRequest = {
                showSuccessModal = false
                onDonationCreated()
            },
            containerColor = PureWhite,
            shape = RoundedCornerShape(24.dp),
            icon = {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(LightGreenBg, CircleShape)
                        .border(2.dp, EmeraldGreen.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Success",
                        tint = EmeraldGreen,
                        modifier = Modifier.size(40.dp)
                    )
                }
            },
            title = {
                Text(
                    text = "Donation Published Successfully!",
                    color = PrimaryText,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Your donation has been published to the FoodShareAI network and matched with $recommendedNgoName for immediate pickup.",
                        color = SecondaryText,
                        fontSize = 12.5.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 17.sp
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = LightGreenBg,
                        border = BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Donation ID", fontSize = 11.sp, color = SecondaryText)
                                Text(publishedDonationId, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = EmeraldGreen)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Meals Donated", fontSize = 11.sp, color = SecondaryText)
                                Text("$totalMeals Portions", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkGreen)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Estimated Beneficiaries", fontSize = 11.sp, color = SecondaryText)
                                Text("$beneficiaries People Fed", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("CO₂ Emissions Saved", fontSize = 11.sp, color = SecondaryText)
                                Text(co2Saved, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkGreen)
                            }
                        }
                    }

                    // 4 ACTION BUTTONS
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                showSuccessModal = false
                                onDonationCreated()
                            },
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen, contentColor = PureWhite)
                        ) {
                            Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Track Donation", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "FoodShareAI Surplus Food Donation")
                                        putExtra(Intent.EXTRA_TEXT, "I just donated $totalMeals surplus meals on FoodShareAI! Donation ID: $publishedDonationId")
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Donation"))
                                },
                                modifier = Modifier.weight(1f).height(42.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, EmeraldGreen)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Share", color = EmeraldGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    ReportExporter.exportPdfReport(
                                        context = context,
                                        donorName = donationTitle,
                                        donations = emptyList(),
                                        totalMeals = totalMeals,
                                        co2SavedKg = totalMeals * 0.45,
                                        peopleFed = beneficiaries
                                    )
                                },
                                modifier = Modifier.weight(1.3f).height(42.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, DarkGreen)
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = DarkGreen, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Receipt PDF", color = DarkGreen, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        TextButton(
                            onClick = {
                                showSuccessModal = false
                                onDonationCreated()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Go Home", color = SecondaryText, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    // ====================================================
    // MAIN SCREEN WIZARD LAYOUT
    // ====================================================
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureWhite)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // STEP PROGRESS HEADER & CHIPS
            val stepTitles = listOf("Event Details", "Food Details", "Pickup Details", "AI Verification", "Review & Publish")

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Step $currentStep of 5",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldGreen
                        )
                        Text(
                            text = stepTitles[currentStep - 1],
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryText
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = LightGreenBg,
                        border = BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = "${(currentStep * 20)}% Complete",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkGreen,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                LinearProgressIndicator(
                    progress = { currentStep / 5f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = EmeraldGreen,
                    trackColor = GrayBorder.copy(alpha = 0.5f)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Step Chips Navigation
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(5) { index ->
                        val stepNum = index + 1
                        val isCurrent = currentStep == stepNum
                        val isPassed = stepNum < currentStep

                        FilterChip(
                            selected = isCurrent,
                            onClick = {
                                if (stepNum < currentStep) {
                                    currentStep = stepNum
                                } else {
                                    val firstIncomplete = getFirstIncompleteStep()
                                    if (stepNum <= firstIncomplete) {
                                        currentStep = stepNum
                                    } else {
                                        Toast.makeText(context, "Please complete step $firstIncomplete first.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            label = {
                                Text(
                                    text = "$stepNum. ${stepTitles[index]}",
                                    fontSize = 11.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingIcon = {
                                if (isPassed) {
                                    Icon(Icons.Default.Check, null, modifier = Modifier.size(12.dp), tint = EmeraldGreen)
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldGreen,
                                selectedLabelColor = PureWhite,
                                containerColor = SurfaceLight,
                                labelColor = SecondaryText
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isCurrent,
                                borderColor = GrayBorder,
                                selectedBorderColor = EmeraldGreen
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = GrayBorder.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(14.dp))

            // WIZARD BODY CONTENT
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {
                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = {
                        if (targetState > initialState) {
                            slideInHorizontally { width -> width } + fadeIn() togetherWith
                                slideOutHorizontally { width -> -width } + fadeOut()
                        } else {
                            slideInHorizontally { width -> -width } + fadeIn() togetherWith
                                slideOutHorizontally { width -> width } + fadeOut()
                        }
                    },
                    label = "WizardStepTransition"
                ) { step ->
                    when (step) {
                        1 -> Step1EventDetails(
                            donationTitle = donationTitle,
                            onDonationTitleChange = { donationTitle = it },
                            eventCategory = eventCategory,
                            onEventCategoryChange = { eventCategory = it },
                            eventCategories = eventCategories,
                            eventDate = eventDate,
                            onEventDateChange = { eventDate = it },
                            eventEndTime = eventEndTime,
                            onEventEndTimeChange = { eventEndTime = it },
                            expectedGuestsText = expectedGuestsText,
                            onExpectedGuestsChange = { expectedGuestsText = it },
                            mealsPreparedText = mealsPreparedText,
                            onMealsPreparedChange = { mealsPreparedText = it },
                            calculatedSurplusMeals = calculatedSurplusMeals,
                            showErrors = step1Submitted
                        )
                        2 -> Step2FoodDetails(
                            foodCategory = foodCategory,
                            onFoodCategoryChange = { foodCategory = it },
                            foodCategories = foodCategories,
                            packagingType = packagingType,
                            onPackagingTypeChange = { packagingType = it },
                            packagingOptions = packagingOptions,
                            freshnessWindowHrs = freshnessWindowHrs,
                            onFreshnessWindowChange = { freshnessWindowHrs = it },
                            freshnessOptions = freshnessOptions,
                            specialInstructions = specialInstructions,
                            onSpecialInstructionsChange = { specialInstructions = it }
                        )
                        3 -> Step3PickupDetails(
                            locationAddress = locationAddress,
                            onLocationAddressChange = {
                                locationAddress = it
                                hasUserSelectedLocation = it.isNotBlank()
                            },
                            contactPersonName = contactPersonName,
                            onContactPersonNameChange = { contactPersonName = it },
                            contactPhone = contactPhone,
                            onContactPhoneChange = { contactPhone = it },
                            pickupTime = pickupTime,
                            onPickupTimeChange = { pickupTime = it },
                            latitudeVal = latitudeVal,
                            longitudeVal = longitudeVal,
                            hasUserSelectedLocation = hasUserSelectedLocation,
                            recommendedNgoName = recommendedNgoName,
                            onFetchGps = { fetchLiveGpsLocation() },
                            onExpandMap = { isMapExpanded = true },
                            onLocationSelected = { lat, lng, addr ->
                                latitudeVal = lat
                                longitudeVal = lng
                                locationAddress = addr
                                hasUserSelectedLocation = true
                            },
                            showErrors = step3Submitted
                        )
                        4 -> Step4AiVerification(
                            uploadedBitmaps = uploadedBitmaps,
                            foodVisionResult = foodVisionResult,
                            imageRejectionError = imageRejectionError,
                            isAnalyzingImage = isAnalyzingImage,
                            cameraPermissionState = cameraPermissionState,
                            permissionLauncher = permissionLauncher,
                            galleryLauncher = galleryLauncher,
                            onRemovePhoto = {
                                uploadedBitmaps.clear()
                                foodVisionResult = null
                                imageRejectionError = null
                            },
                            showErrors = step4Submitted
                        )
                        5 -> Step5ReviewPublish(
                            donationTitle = donationTitle,
                            eventCategory = eventCategory,
                            eventDate = eventDate,
                            eventEndTime = eventEndTime,
                            expectedGuests = expectedGuestsText,
                            mealsPrepared = mealsPreparedText,
                            calculatedSurplus = calculatedSurplusMeals,
                            foodCategory = foodCategory,
                            packagingType = packagingType,
                            freshnessWindow = freshnessWindowHrs,
                            specialInstructions = specialInstructions,
                            locationAddress = locationAddress,
                            contactPerson = contactPersonName,
                            contactPhone = contactPhone,
                            pickupTime = pickupTime,
                            ngoName = recommendedNgoName,
                            visionResult = foodVisionResult
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // BOTTOM WIZARD NAVIGATION BAR
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = PureWhite,
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (currentStep > 1) {
                        OutlinedButton(
                            onClick = { currentStep -= 1 },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, GrayBorder)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Back", color = PrimaryText, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (currentStep < 5) {
                        val isCurrentStepValid = when (currentStep) {
                            1 -> isStep1Valid
                            2 -> isStep2Valid
                            3 -> isStep3Valid
                            4 -> isStep4Valid
                            else -> true
                        }
                        Button(
                            onClick = { attemptNavigateNext() },
                            enabled = isCurrentStepValid,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldGreen,
                                contentColor = PureWhite,
                                disabledContainerColor = EmeraldGreen.copy(alpha = 0.35f),
                                disabledContentColor = PureWhite.copy(alpha = 0.6f)
                            )
                        ) {
                            Text("Next Step", fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    } else {
                        // STEP 5: FINAL PUBLISH BUTTON
                        Button(
                            onClick = {
                                val firstIncomplete = getFirstIncompleteStep()
                                if (firstIncomplete < 5) {
                                    currentStep = firstIncomplete
                                    Toast.makeText(context, "Please complete missing information in step $firstIncomplete.", Toast.LENGTH_LONG).show()
                                    return@Button
                                }

                                coroutineScope.launch {
                                    isSubmitting = true
                                    try {
                                        val firstBitmapUri = uploadedBitmaps.firstOrNull()?.let { bitmap ->
                                            getUriFromBitmap(context, bitmap)
                                        }

                                        viewModel.createDonation(
                                            title = donationTitle.trim(),
                                            foodType = foodCategory,
                                            eventType = eventCategory,
                                            quantity = calculatedSurplusMeals.coerceAtLeast(10),
                                            expectedGuests = expectedGuestsText.toIntOrNull() ?: 100,
                                            pickupTime = pickupTime,
                                            expiryTime = freshnessWindowHrs,
                                            location = locationAddress.trim(),
                                            description = "Packaging: $packagingType. Contact: $contactPersonName ($contactPhone). Note: $specialInstructions",
                                            latitude = latitudeVal,
                                            longitude = longitudeVal,
                                            imageUri = firstBitmapUri,
                                            onSuccess = {
                                                isSubmitting = false
                                                showSuccessModal = true
                                            },
                                            onError = { err ->
                                                isSubmitting = false
                                                Toast.makeText(context, err ?: "Failed to publish donation.", Toast.LENGTH_LONG).show()
                                            }
                                        )
                                    } catch (e: Exception) {
                                        isSubmitting = false
                                        Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            enabled = !isSubmitting,
                            modifier = Modifier
                                .weight(1.5f)
                                .height(48.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldGreen,
                                contentColor = PureWhite,
                                disabledContainerColor = GrayBorder
                            )
                        ) {
                            if (isSubmitting) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = PureWhite, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Publishing...", fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Publish Donation", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ====================================================
// WIZARD STEP 1: EVENT DETAILS
// ====================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Step1EventDetails(
    donationTitle: String,
    onDonationTitleChange: (String) -> Unit,
    eventCategory: String,
    onEventCategoryChange: (String) -> Unit,
    eventCategories: List<String>,
    eventDate: String,
    onEventDateChange: (String) -> Unit,
    eventEndTime: String,
    onEventEndTimeChange: (String) -> Unit,
    expectedGuestsText: String,
    onExpectedGuestsChange: (String) -> Unit,
    mealsPreparedText: String,
    onMealsPreparedChange: (String) -> Unit,
    calculatedSurplusMeals: Int,
    showErrors: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Provide event details to compute surplus food estimation.", fontSize = 13.sp, color = SecondaryText)

        // Donation Title
        OutlinedTextField(
            value = donationTitle,
            onValueChange = onDonationTitleChange,
            label = { Text("Donation Title", fontSize = 13.sp) },
            placeholder = { Text("e.g. Grand Wedding Surplus Meals", fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Assignment, contentDescription = null, tint = SecondaryText, modifier = Modifier.size(18.dp)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            isError = showErrors && donationTitle.isBlank(),
            supportingText = if (showErrors && donationTitle.isBlank()) { { Text("Please enter Donation Title.", color = RubyRed, fontSize = 11.sp) } } else null,
            singleLine = true,
            colors = customOutlinedTextFieldColors()
        )

        // Event Category Chips
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Event Category", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                eventCategories.forEach { cat ->
                    val isSelected = eventCategory == cat
                    FilterChip(
                        selected = isSelected,
                        onClick = { onEventCategoryChange(cat) },
                        label = { Text(cat, fontSize = 12.sp) },
                        leadingIcon = {
                            if (isSelected) Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp))
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldGreen,
                            selectedLabelColor = PureWhite,
                            containerColor = SurfaceLight,
                            labelColor = PrimaryText
                        )
                    )
                }
            }
        }

        // Date & End Time Row
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = eventDate,
                onValueChange = onEventDateChange,
                label = { Text("Event Date", fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Outlined.CalendarToday, contentDescription = null, tint = SecondaryText, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                colors = customOutlinedTextFieldColors()
            )

            OutlinedTextField(
                value = eventEndTime,
                onValueChange = onEventEndTimeChange,
                label = { Text("Event End Time", fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Outlined.Schedule, contentDescription = null, tint = SecondaryText, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                colors = customOutlinedTextFieldColors()
            )
        }

        // Expected Guests & Meals Prepared Row
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = expectedGuestsText,
                onValueChange = { onExpectedGuestsChange(it.filter { c -> c.isDigit() }) },
                label = { Text("Expected Guests", fontSize = 12.sp) },
                placeholder = { Text("e.g. 200", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Outlined.People, contentDescription = null, tint = SecondaryText, modifier = Modifier.size(18.dp)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                isError = showErrors && (expectedGuestsText.toIntOrNull() ?: 0) <= 0,
                supportingText = if (showErrors && (expectedGuestsText.toIntOrNull() ?: 0) <= 0) { { Text("Please enter Expected Guests.", color = RubyRed, fontSize = 10.5.sp) } } else null,
                singleLine = true,
                colors = customOutlinedTextFieldColors()
            )

            OutlinedTextField(
                value = mealsPreparedText,
                onValueChange = { onMealsPreparedChange(it.filter { c -> c.isDigit() }) },
                label = { Text("Meals Prepared", fontSize = 12.sp) },
                placeholder = { Text("e.g. 250", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Outlined.Restaurant, contentDescription = null, tint = SecondaryText, modifier = Modifier.size(18.dp)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                isError = showErrors && (mealsPreparedText.toIntOrNull() ?: 0) <= 0,
                supportingText = if (showErrors && (mealsPreparedText.toIntOrNull() ?: 0) <= 0) { { Text("Please enter Meals Prepared.", color = RubyRed, fontSize = 10.5.sp) } } else null,
                singleLine = true,
                colors = customOutlinedTextFieldColors()
            )
        }

        // Live Calculated Estimated Surplus Card
        if (calculatedSurplusMeals > 0) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = LightGreenBg,
                border = BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Estimated Surplus Food", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = DarkGreen)
                    }
                    Text(
                        text = "$calculatedSurplusMeals Portions",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = EmeraldGreen
                    )
                }
            }
        }
    }
}

// ====================================================
// WIZARD STEP 2: FOOD DETAILS
// ====================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Step2FoodDetails(
    foodCategory: String,
    onFoodCategoryChange: (String) -> Unit,
    foodCategories: List<String>,
    packagingType: String,
    onPackagingTypeChange: (String) -> Unit,
    packagingOptions: List<String>,
    freshnessWindowHrs: String,
    onFreshnessWindowChange: (String) -> Unit,
    freshnessOptions: List<String>,
    specialInstructions: String,
    onSpecialInstructionsChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Specify food category, packaging type, and handling instructions.", fontSize = 13.sp, color = SecondaryText)

        // Food Category (ONLY 6 STRICT CATEGORIES)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Food Category", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                foodCategories.forEach { cat ->
                    val isSelected = foodCategory == cat
                    FilterChip(
                        selected = isSelected,
                        onClick = { onFoodCategoryChange(cat) },
                        label = { Text(cat, fontSize = 12.sp) },
                        shape = RoundedCornerShape(12.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldGreen,
                            selectedLabelColor = PureWhite,
                            containerColor = SurfaceLight
                        )
                    )
                }
            }
        }

        // Packaging Type (RADIO BUTTON SELECTION ROW)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Packaging Type", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceLight, RoundedCornerShape(14.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                packagingOptions.forEach { option ->
                    val isSelected = packagingType == option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onPackagingTypeChange(option) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onPackagingTypeChange(option) },
                            colors = RadioButtonDefaults.colors(selectedColor = EmeraldGreen)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(option, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = PrimaryText)
                    }
                }
            }
        }

        // Freshness Window Dropdown Menu
        var isDropdownExpanded by remember { mutableStateOf(false) }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Freshness Window", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
            Box {
                OutlinedTextField(
                    value = freshnessWindowHrs,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Safe Window") },
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, Modifier.clickable { isDropdownExpanded = true }) },
                    modifier = Modifier.fillMaxWidth().clickable { isDropdownExpanded = true },
                    shape = RoundedCornerShape(16.dp),
                    colors = customOutlinedTextFieldColors()
                )
                DropdownMenu(
                    expanded = isDropdownExpanded,
                    onDismissRequest = { isDropdownExpanded = false },
                    modifier = Modifier.background(PureWhite)
                ) {
                    freshnessOptions.forEach { opt ->
                        DropdownMenuItem(
                            text = { Text(opt, fontWeight = if (opt == freshnessWindowHrs) FontWeight.Bold else FontWeight.Normal) },
                            onClick = {
                                onFreshnessWindowChange(opt)
                                isDropdownExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // Special Instructions
        OutlinedTextField(
            value = specialInstructions,
            onValueChange = onSpecialInstructionsChange,
            label = { Text("Special Instructions", fontSize = 13.sp) },
            placeholder = { Text("e.g. Keep in hot insulated container until courier arrives", fontSize = 12.5.sp) },
            leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null, tint = SecondaryText, modifier = Modifier.size(18.dp)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            maxLines = 2,
            colors = customOutlinedTextFieldColors()
        )
    }
}

// ====================================================
// WIZARD STEP 3: PICKUP DETAILS
// ====================================================
@Composable
fun Step3PickupDetails(
    locationAddress: String,
    onLocationAddressChange: (String) -> Unit,
    contactPersonName: String,
    onContactPersonNameChange: (String) -> Unit,
    contactPhone: String,
    onContactPhoneChange: (String) -> Unit,
    pickupTime: String,
    onPickupTimeChange: (String) -> Unit,
    latitudeVal: Double,
    longitudeVal: Double,
    hasUserSelectedLocation: Boolean,
    recommendedNgoName: String,
    onFetchGps: () -> Unit,
    onExpandMap: () -> Unit,
    onLocationSelected: (Double, Double, String) -> Unit,
    showErrors: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Enter venue pickup location and contact details for NGO dispatch.", fontSize = 13.sp, color = SecondaryText)

        // Pickup Address
        OutlinedTextField(
            value = locationAddress,
            onValueChange = onLocationAddressChange,
            label = { Text("Pickup Address", fontSize = 13.sp) },
            placeholder = { Text("Enter venue street, landmark, or city", fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Outlined.Place, contentDescription = null, tint = SecondaryText, modifier = Modifier.size(18.dp)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            maxLines = 2,
            isError = showErrors && locationAddress.isBlank(),
            supportingText = if (showErrors && locationAddress.isBlank()) { { Text("Please enter Pickup Address.", color = RubyRed, fontSize = 11.sp) } } else null,
            colors = customOutlinedTextFieldColors()
        )

        // Map buttons: Detect My Location & Open Full Map
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onFetchGps,
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, EmeraldGreen)
            ) {
                Icon(Icons.Default.NearMe, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Detect My Location", color = EmeraldGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onExpandMap,
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, GrayBorder)
            ) {
                Icon(Icons.Default.Fullscreen, contentDescription = null, tint = PrimaryText, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Open Full Map", color = PrimaryText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Map Box
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, GrayBorder),
            shadowElevation = 2.dp
        ) {
            OsmLogisticsMapView(
                latitude = latitudeVal,
                longitude = longitudeVal,
                ngoName = recommendedNgoName,
                hasSelectedLocation = hasUserSelectedLocation,
                onLocationSelected = onLocationSelected,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Contact Person & Phone Number
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = contactPersonName,
                onValueChange = onContactPersonNameChange,
                label = { Text("Contact Person", fontSize = 12.sp) },
                placeholder = { Text("e.g. Rajesh Kumar", fontSize = 12.5.sp) },
                leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null, tint = SecondaryText, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                isError = showErrors && contactPersonName.isBlank(),
                singleLine = true,
                colors = customOutlinedTextFieldColors()
            )

            OutlinedTextField(
                value = contactPhone,
                onValueChange = { onContactPhoneChange(it.filter { c -> c.isDigit() || c == '+' || c == ' ' }) },
                label = { Text("Mobile Number", fontSize = 12.sp) },
                placeholder = { Text("e.g. +91 98765 43210", fontSize = 12.5.sp) },
                leadingIcon = { Icon(Icons.Outlined.Phone, contentDescription = null, tint = SecondaryText, modifier = Modifier.size(18.dp)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                isError = showErrors && contactPhone.isBlank(),
                singleLine = true,
                colors = customOutlinedTextFieldColors()
            )
        }

        // Pickup Time
        OutlinedTextField(
            value = pickupTime,
            onValueChange = onPickupTimeChange,
            label = { Text("Pickup Time", fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Outlined.Schedule, contentDescription = null, tint = SecondaryText, modifier = Modifier.size(18.dp)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            singleLine = true,
            colors = customOutlinedTextFieldColors()
        )
    }
}

// ====================================================
// WIZARD STEP 4: AI VERIFICATION
// ====================================================
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun Step4AiVerification(
    uploadedBitmaps: List<Bitmap>,
    foodVisionResult: FoodVisionAnalysisResult?,
    imageRejectionError: String?,
    isAnalyzingImage: Boolean,
    cameraPermissionState: com.google.accompanist.permissions.PermissionState,
    permissionLauncher: androidx.activity.compose.ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>,
    galleryLauncher: androidx.activity.compose.ManagedActivityResultLauncher<PickVisualMediaRequest, List<Uri>>,
    onRemovePhoto: () -> Unit,
    showErrors: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Upload up to 5 clear food photos for Food Vision AI freshness inspection.", fontSize = 13.sp, color = SecondaryText)

        if (isAnalyzingImage) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = LightGreenBg,
                border = BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = EmeraldGreen, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Checking image... Analyzing food condition...", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = EmeraldGreen)
                }
            }
        }

        // PHOTO THUMBNAILS CAROUSEL (IF ANY UPLOADED)
        if (uploadedBitmaps.isNotEmpty() && !isAnalyzingImage) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                items(uploadedBitmaps.size) { index ->
                    Box(
                        modifier = Modifier
                            .width(140.dp)
                            .height(130.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(SurfaceLight)
                    ) {
                        Image(
                            bitmap = uploadedBitmaps[index].asImageBitmap(),
                            contentDescription = "Food Photo ${index + 1}",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Surface(
                            modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                            shape = RoundedCornerShape(6.dp),
                            color = DarkNavy.copy(alpha = 0.75f)
                        ) {
                            Text("Photo ${index + 1}", color = PureWhite, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
            }
        }

        // VERIFICATION STATE SPECIFIC CARDS
        if (!isAnalyzingImage && foodVisionResult != null) {
            val status = foodVisionResult.verificationResult.verificationStatus

            when (status) {
                VerificationStatus.NON_FOOD -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFFFEF2F2),
                        border = BorderStroke(1.dp, Color(0xFFFCA5A5))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Cancel, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("❌ Food Not Detected", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF991B1B))
                            }
                            Text("This image does not appear to contain food.", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryText)
                            Text("Please upload a clear photo of the food you want to share.", fontSize = 12.sp, color = SecondaryText)
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = onRemovePhoto,
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color(0xFFDC2626))
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Retake Image", color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen, contentColor = PureWhite)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Upload Again", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                VerificationStatus.UNCERTAIN -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFFFFFBEB),
                        border = BorderStroke(1.dp, Color(0xFFFCD34D))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("⚠ Unable to Confirm Food", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF92400E))
                            }
                            Text("We couldn't confidently identify food in this image.", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryText)
                            Text("Please upload a clearer photo showing the food.", fontSize = 12.sp, color = SecondaryText)
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = onRemovePhoto,
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color(0xFFD97706))
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Retake Image", color = Color(0xFFD97706), fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen, contentColor = PureWhite)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Upload Again", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                VerificationStatus.IMAGE_QUALITY_FAILED -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFFFFFBEB),
                        border = BorderStroke(1.dp, Color(0xFFFCD34D))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("⚠ Image Quality Too Low", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF92400E))
                            }
                            Text("Please upload a clear photo of the food.", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryText)
                            if (!foodVisionResult.imageQualityText.isNullOrBlank()) {
                                Text(foodVisionResult.imageQualityText, fontSize = 11.5.sp, color = SecondaryText)
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = onRemovePhoto,
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color(0xFFD97706))
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Retake Image", color = Color(0xFFD97706), fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen, contentColor = PureWhite)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Upload Again", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                VerificationStatus.ANALYSIS_FAILED -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFFFEF2F2),
                        border = BorderStroke(1.dp, Color(0xFFFCA5A5))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Error, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("On-Device AI Model Required", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF991B1B))
                            }
                            val failureDetail = foodVisionResult.rejectionReason?.takeIf { it.isNotBlank() } ?: "On-device food model missing (food_classifier.tflite required in assets/models/)."
                            Text(failureDetail, fontSize = 13.sp, color = SecondaryText)

                        }
                    }


                    Button(
                        onClick = onRemovePhoto,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen, contentColor = PureWhite)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Try Again", fontWeight = FontWeight.Bold)
                    }
                }

                VerificationStatus.FOOD, VerificationStatus.NON_FOOD, VerificationStatus.UNCERTAIN, VerificationStatus.IMAGE_QUALITY_FAILED, VerificationStatus.ANALYSIS_FAILED -> {
                    val uiStateName = if (foodVisionResult.isValidFoodImage) "Food Quality Verified" else if (foodVisionResult.verificationResult.verificationStatus == VerificationStatus.NON_FOOD || foodVisionResult.verificationResult.verificationStatus == VerificationStatus.UNCERTAIN) "Food Not Detected" else "Food Quality Rejected"
                    Log.i(
                        "FINAL_UI_DECISION",
                        "status=${foodVisionResult.verificationResult.verificationStatus}\nisFood=${foodVisionResult.foodDetected}\nvisualCondition=${foodVisionResult.verificationResult.visualCondition}\nfreshProbability=${foodVisionResult.freshProbability}\nspoiledProbability=${foodVisionResult.spoiledProbability}\ncanPublish=${foodVisionResult.verificationResult.canPublish}\nisValidFoodImage=${foodVisionResult.isValidFoodImage}\nuiState=$uiStateName"
                    )

                    if (foodVisionResult.isValidFoodImage) {
                        // Green Success Card (Fresh Food Verified)
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = PureWhite,
                            border = BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.4f)),
                            shadowElevation = 2.dp
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Food Quality Verified", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = DarkGreen)
                                    }
                                    Surface(shape = RoundedCornerShape(8.dp), color = EmeraldGreen) {
                                        Text("Verified Fresh", color = PureWhite, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                                    }
                                }

                                HorizontalDivider(color = GrayBorder.copy(alpha = 0.4f))

                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Food Detected:", fontSize = 12.sp, color = SecondaryText, fontWeight = FontWeight.Medium)
                                        Text("Yes", fontSize = 12.5.sp, color = DarkGreen, fontWeight = FontWeight.Bold)
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Detected Dish:", fontSize = 12.sp, color = SecondaryText, fontWeight = FontWeight.Medium)
                                        Text(foodVisionResult.detectedFood.ifBlank { "Prepared Meal" }, fontSize = 12.5.sp, color = PrimaryText, fontWeight = FontWeight.Bold)
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Visual Freshness:", fontSize = 12.sp, color = SecondaryText, fontWeight = FontWeight.Medium)
                                        Text("Fresh", fontSize = 12.5.sp, color = EmeraldGreen, fontWeight = FontWeight.Bold)
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Freshness Confidence:", fontSize = 12.sp, color = SecondaryText, fontWeight = FontWeight.Medium)
                                        val confPct = if (foodVisionResult.freshProbability > 0f) "${(foodVisionResult.freshProbability * 100).toInt()}%" else foodVisionResult.confidenceLevelText
                                        Text(confPct, fontSize = 12.5.sp, color = DarkGreen, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = LightGreenBg
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("Status:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DarkGreen)
                                        Text("Food appears fresh and is suitable for donation based on visual freshness screening.", fontSize = 11.5.sp, color = PrimaryText, lineHeight = 16.sp)
                                    }
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = onRemovePhoto,
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, EmeraldGreen)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Retake Image / Upload Again", color = EmeraldGreen, fontWeight = FontWeight.Bold)
                        }
                    } else if (foodVisionResult.verificationResult.verificationStatus == VerificationStatus.NON_FOOD || foodVisionResult.verificationResult.verificationStatus == VerificationStatus.IMAGE_QUALITY_FAILED || foodVisionResult.verificationResult.verificationStatus == VerificationStatus.UNCERTAIN) {
                        // Food Not Detected Card (Non-food or poor image quality)
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = Color(0xFFFFFBEB),
                            border = BorderStroke(1.dp, Color(0xFFFCD34D))
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(22.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Food Not Detected", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                                }
                                Text(
                                    "Please upload a clear photo of the food you want to donate.",
                                    fontSize = 13.sp,
                                    color = PrimaryText,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text("Reason: ${foodVisionResult.reasonExplanation.ifBlank { "No food was detected in the uploaded image." }}", fontSize = 12.sp, color = SecondaryText)
                            }
                        }

                        Button(
                            onClick = onRemovePhoto,
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen, contentColor = PureWhite)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Retake Image / Upload Again", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        // Spoiled Food Rejection Card
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = Color(0xFFFEF2F2),
                            border = BorderStroke(1.dp, Color(0xFFFCA5A5))
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Error, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(22.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Food Quality Rejected", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF991B1B))
                                }
                                Text(
                                    "Food appears spoiled or unsuitable for donation based on visual freshness screening.",
                                    fontSize = 13.sp,
                                    color = PrimaryText,
                                    fontWeight = FontWeight.SemiBold
                                )
                                val spoiledPct = if (foodVisionResult.spoiledProbability > 0f) "${(foodVisionResult.spoiledProbability * 100).toInt()}%" else "High"
                                Text("Spoiled confidence: $spoiledPct", fontSize = 12.sp, color = Color(0xFF991B1B), fontWeight = FontWeight.Bold)
                                Text("Reason: ${foodVisionResult.reasonExplanation}", fontSize = 12.sp, color = SecondaryText)
                            }
                        }

                        Button(
                            onClick = onRemovePhoto,
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen, contentColor = PureWhite)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Retake Image / Upload Again", fontWeight = FontWeight.Bold)
                        }
                    }
                }

            }
        } else if (!isAnalyzingImage && uploadedBitmaps.isEmpty()) {
            // Upload Area Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clickable {
                        galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                shape = RoundedCornerShape(18.dp),
                color = SurfaceLight,
                border = BorderStroke(1.dp, GrayBorder)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Upload food photos for AI inspection (Up to 5)", color = PrimaryText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Supported: JPG, PNG, HEIC (Max 10MB)", color = SecondaryText, fontSize = 11.sp)
                    }
                }
            }

            if (showErrors && uploadedBitmaps.isEmpty()) {
                Text("Please upload a food image.", color = RubyRed, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        if (cameraPermissionState.status.isGranted) {
                            galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        } else {
                            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
                        }
                    },
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, EmeraldGreen)
                ) {
                    Icon(Icons.Default.PhotoCamera, null, tint = EmeraldGreen, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Camera", color = EmeraldGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Button(
                    onClick = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen, contentColor = PureWhite)
                ) {
                    Icon(Icons.Default.PhotoLibrary, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Gallery (Max 5)", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                }
            }
        }
    }
}


// ====================================================
// WIZARD STEP 5: REVIEW & PUBLISH SUMMARY CARDS
// ====================================================
@Composable
fun Step5ReviewPublish(
    donationTitle: String,
    eventCategory: String,
    eventDate: String,
    eventEndTime: String,
    expectedGuests: String,
    mealsPrepared: String,
    calculatedSurplus: Int,
    foodCategory: String,
    packagingType: String,
    freshnessWindow: String,
    specialInstructions: String,
    locationAddress: String,
    contactPerson: String,
    contactPhone: String,
    pickupTime: String,
    ngoName: String,
    visionResult: FoodVisionAnalysisResult?
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Review final parameters before publishing to live rescue network.", fontSize = 13.sp, color = SecondaryText)

        // 1. DONATION SUMMARY CARD
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = PureWhite,
            border = BorderStroke(1.dp, GrayBorder),
            shadowElevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("DONATION SUMMARY", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = EmeraldGreen)
                HorizontalDivider(color = GrayBorder.copy(alpha = 0.4f))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Donation Title", fontSize = 11.sp, color = SecondaryText)
                        Text(donationTitle.ifBlank { "Surplus Food Donation" }, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Calculated Surplus", fontSize = 11.sp, color = SecondaryText)
                        Text("$calculatedSurplus Meals", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = EmeraldGreen)
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Event Category", fontSize = 11.sp, color = SecondaryText)
                        Text(eventCategory, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = PrimaryText)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Event Date & Time", fontSize = 11.sp, color = SecondaryText)
                        Text("$eventDate ($eventEndTime)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = PrimaryText)
                    }
                }
            }
        }

        // 2. FOOD SUMMARY CARD
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = PureWhite,
            border = BorderStroke(1.dp, GrayBorder),
            shadowElevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("FOOD SUMMARY", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = EmeraldGreen)
                HorizontalDivider(color = GrayBorder.copy(alpha = 0.4f))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Category", fontSize = 11.sp, color = SecondaryText)
                        Text(foodCategory, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryText)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Packaging Type", fontSize = 11.sp, color = SecondaryText)
                        Text(packagingType, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryText)
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Freshness Window", fontSize = 11.sp, color = SecondaryText)
                        Text(freshnessWindow, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DarkGreen)
                    }
                    if (specialInstructions.isNotBlank()) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Note", fontSize = 11.sp, color = SecondaryText)
                            Text(specialInstructions, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = PrimaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        // 3. PICKUP SUMMARY CARD
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = PureWhite,
            border = BorderStroke(1.dp, GrayBorder),
            shadowElevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("PICKUP SUMMARY", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = EmeraldGreen)
                HorizontalDivider(color = GrayBorder.copy(alpha = 0.4f))

                Column {
                    Text("Pickup Address", fontSize = 11.sp, color = SecondaryText)
                    Text(locationAddress.ifBlank { "Specified Location" }, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = PrimaryText)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Contact Person", fontSize = 11.sp, color = SecondaryText)
                        Text("$contactPerson ($contactPhone)", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = PrimaryText)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Auto-Matched NGO", fontSize = 11.sp, color = SecondaryText)
                        Text(ngoName, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = DarkGreen)
                    }
                }
            }
        }

        // 4. AI SUMMARY CARD
        visionResult?.let { v ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = LightGreenBg,
                border = BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, null, tint = EmeraldGreen, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("${v.foodDetectionStatus} • ${v.detectedFood}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DarkGreen)
                    }
                    Text("Visual Condition: ${v.freshnessRating} • Confidence Level: ${v.confidenceLevelText}", fontSize = 12.sp, color = PrimaryText, fontWeight = FontWeight.Bold)

                    HorizontalDivider(color = EmeraldGreen.copy(alpha = 0.2f))
                    Text(v.reasonExplanation, fontSize = 11.5.sp, color = PrimaryText, lineHeight = 16.sp)
                }
            }
        }
    }
}

// ==========================================
// OPENSTREETMAP COMPOSABLE COMPONENT
// ==========================================
@Composable
fun OsmLogisticsMapView(
    latitude: Double,
    longitude: Double,
    ngoName: String,
    hasSelectedLocation: Boolean,
    onLocationSelected: (Double, Double, String) -> Unit,
    onMapViewReady: ((MapView) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
    }

    val donorPoint = remember(latitude, longitude) { GeoPoint(latitude, longitude) }

    AndroidView(
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(16.5)
                controller.setCenter(donorPoint)

                onMapViewReady?.invoke(this)

                if (hasSelectedLocation) {
                    val donorMarker = Marker(this).apply {
                        position = donorPoint
                        title = "Pickup Location"
                        snippet = "Donor Location"
                        icon = createTintedMarkerDrawable(ctx, android.graphics.Color.parseColor("#10B981"))
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        isDraggable = true
                        setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                            override fun onMarkerDrag(m: Marker) {}
                            override fun onMarkerDragEnd(m: Marker) {
                                val addr = getAddressFromCoords(ctx, m.position.latitude, m.position.longitude)
                                onLocationSelected(m.position.latitude, m.position.longitude, addr)
                            }
                            override fun onMarkerDragStart(m: Marker) {}
                        })
                    }
                    overlays.add(donorMarker)
                }

                val mapEventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                        val addr = getAddressFromCoords(ctx, p.latitude, p.longitude)
                        onLocationSelected(p.latitude, p.longitude, addr)
                        return true
                    }

                    override fun longPressHelper(p: GeoPoint): Boolean {
                        val addr = getAddressFromCoords(ctx, p.latitude, p.longitude)
                        onLocationSelected(p.latitude, p.longitude, addr)
                        return true
                    }
                })
                overlays.add(0, mapEventsOverlay)
            }
        },
        update = { mapView ->
            mapView.controller.animateTo(donorPoint)
            val existingMarker = mapView.overlays.filterIsInstance<Marker>().firstOrNull { it.title == "Pickup Location" }
            if (hasSelectedLocation) {
                if (existingMarker != null) {
                    existingMarker.position = donorPoint
                } else {
                    val newMarker = Marker(mapView).apply {
                        position = donorPoint
                        title = "Pickup Location"
                        snippet = "Donor Location"
                        icon = createTintedMarkerDrawable(mapView.context, android.graphics.Color.parseColor("#10B981"))
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    }
                    mapView.overlays.add(newMarker)
                }
                mapView.invalidate()
            }
        },
        modifier = modifier
    )
}

private fun createTintedMarkerDrawable(context: Context, color: Int): BitmapDrawable {
    val size = 48
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }

    canvas.drawCircle(size / 2f, size / 2f, size / 2.2f, paint)
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(size / 2f, size / 2f, size / 5f, paint)

    return BitmapDrawable(context.resources, bitmap)
}

private fun getAddressFromCoords(context: Context, lat: Double, lng: Double): String {
    return try {
        val geocoder = android.location.Geocoder(context, Locale.getDefault())
        @Suppress("DEPRECATION")
        val addresses = geocoder.getFromLocation(lat, lng, 1)
        if (!addresses.isNullOrEmpty()) {
            val addr = addresses[0]
            val street = addr.thoroughfare ?: addr.subThoroughfare ?: addr.featureName ?: ""
            val locality = addr.locality ?: addr.subLocality ?: ""
            val admin = addr.adminArea ?: ""
            val country = addr.countryName ?: ""
            listOf(street, locality, admin, country).filter { it.isNotBlank() }.joinToString(", ")
        } else {
            "Block H, Sector 4, Metro City, India"
        }
    } catch (e: Exception) {
        "Block H, Sector 4, Metro City, India"
    }
}

private fun getUriFromBitmap(context: Context, bitmap: Bitmap): Uri? {
    return try {
        val file = File(context.cacheDir, "upload_photo_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        Uri.fromFile(file)
    } catch (e: Exception) {
        null
    }
}

private fun compressImageUri(context: Context, uri: Uri): Bitmap? {
    return try {
        Log.d("AI_VERIFY", "image selected")
        Log.d("AI_VERIFY", "image URI available")
        val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
        Log.d("AI_VERIFY", "image MIME type = $mimeType")

        val inputStream = context.contentResolver.openInputStream(uri)
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()

        if (bitmap != null) {
            Log.d("AI_VERIFY", "image width = ${bitmap.width}, image height = ${bitmap.height}")
            val maxDimension = 1080
            val width = bitmap.width
            val height = bitmap.height
            val scaledBitmap = if (width > maxDimension || height > maxDimension) {
                val ratio = width.toFloat() / height.toFloat()
                val newWidth = if (ratio > 1) maxDimension else (maxDimension * ratio).toInt()
                val newHeight = if (ratio > 1) (maxDimension / ratio).toInt() else maxDimension
                Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
            } else {
                bitmap
            }

            val compressedFile = File(context.cacheDir, "compressed_photo_${System.currentTimeMillis()}.jpg")
            compressedFile.outputStream().use { out ->
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
            val finalBitmap = BitmapFactory.decodeFile(compressedFile.absolutePath)
            if (finalBitmap != null) {
                Log.d("AI_VERIFY", "image byte size = ${finalBitmap.byteCount}")
                Log.d("AI_VERIFY", "image conversion successful")
            }
            finalBitmap
        } else {
            Log.e("AI_VERIFY_ERROR", "Bitmap decoding from URI returned null")
            null
        }
    } catch (e: Exception) {
        Log.e("AI_VERIFY_ERROR", "Image compression failed: ${e.localizedMessage}")
        null
    }
}

