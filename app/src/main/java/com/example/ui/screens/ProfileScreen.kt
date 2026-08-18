package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.DonationEntity
import com.example.ui.theme.*
import com.example.ui.viewmodel.FoodShareViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: FoodShareViewModel,
    onLogoutClick: () -> Unit,
    onHistoryClick: (() -> Unit)? = null,
    onNavigateToDonate: (() -> Unit)? = null,
    onNavigateToNotifications: (() -> Unit)? = null,
    onDonationClick: ((DonationEntity) -> Unit)? = null
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val currentUser by viewModel.currentUser.collectAsState()
    val allDonations by viewModel.allDonations.collectAsState()
    val predictions by viewModel.predictions.collectAsState()
    val aiRecommendations by viewModel.aiRecommendations.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isUploadingProfileImage by viewModel.isUploadingProfileImage.collectAsState()

    val isNgoRole = currentUser?.role == "ngo"

    // Image pickers for Photo Upload
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            try {
                val bytes = java.io.ByteArrayOutputStream()
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, bytes)
                val path = android.provider.MediaStore.Images.Media.insertImage(
                    context.contentResolver,
                    bitmap,
                    "profile_${System.currentTimeMillis()}",
                    null
                )
                if (path != null) {
                    val imageUri = Uri.parse(path)
                    viewModel.uploadProfileImage(imageUri, context) { success ->
                        if (success) {
                            Toast.makeText(context, "Profile picture updated successfully.", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Unable to upload profile picture. Please try again.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Unable to upload profile picture. Please try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.uploadProfileImage(uri, context) { success ->
                if (success) {
                    Toast.makeText(context, "Profile picture updated successfully.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Unable to upload profile picture. Please try again.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ==========================================
    // BACKEND METRICS — DONOR ROLE
    // ==========================================
    val myDonations = remember(allDonations, currentUser) {
        if (currentUser == null) emptyList()
        else allDonations.filter { it.donorId == currentUser?.id }
    }

    val totalDonationsCount = remember(myDonations, currentUser) {
        if (myDonations.isNotEmpty()) myDonations.size
        else currentUser?.totalDonations ?: 0
    }

    val mealsDonatedCount = remember(myDonations, currentUser) {
        if (myDonations.isNotEmpty()) myDonations.sumOf { it.quantity }
        else currentUser?.mealsSaved ?: 0
    }

    val peopleBenefitedCount = remember(mealsDonatedCount) {
        (mealsDonatedCount * 0.9).toInt().coerceAtLeast(0)
    }

    val carbonOffsetKgText = remember(currentUser?.co2OffsetKg, mealsDonatedCount) {
        val co2 = currentUser?.co2OffsetKg ?: (mealsDonatedCount * 0.45)
        if (co2 <= 0.0) "0 kg" else String.format(Locale.US, "%.1f kg", co2)
    }

    // ==========================================
    // BACKEND METRICS — NGO ROLE
    // ==========================================
    val ngoClaimedMissions = remember(allDonations, currentUser) {
        if (currentUser == null) emptyList()
        else allDonations.filter { it.ngoId == currentUser?.id }
    }

    val foodClaimsCount = remember(ngoClaimedMissions) {
        ngoClaimedMissions.size
    }

    val mealsRescuedCount = remember(ngoClaimedMissions) {
        ngoClaimedMissions.filter {
            it.status.equals("Completed", ignoreCase = true) || it.status.equals("Delivered", ignoreCase = true)
        }.sumOf { it.quantity }
    }

    val peopleServedCount = remember(mealsRescuedCount) {
        (mealsRescuedCount * 0.9).toInt().coerceAtLeast(0)
    }

    val co2SavedKgText = remember(mealsRescuedCount) {
        val co2 = mealsRescuedCount * 0.45
        String.format(Locale.US, "%.1f kg", co2)
    }

    val memberSinceText = remember(currentUser?.createdAt) {
        if (currentUser != null && currentUser?.createdAt != 0L) {
            val dateStr = SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(Date(currentUser?.createdAt ?: 0L))
            "Member since $dateStr"
        } else "Member since 2026"
    }

    val userLocation = remember(currentUser) {
        val cityState = listOfNotNull(currentUser?.city.takeIf { !it.isNullOrBlank() }, currentUser?.state.takeIf { !it.isNullOrBlank() }).joinToString(", ")
        val addr = currentUser?.address?.ifBlank { cityState }
        if (!addr.isNullOrBlank()) addr else "Metropolitan Region"
    }

    val ngoRegNumber = remember(currentUser) {
        val reg = currentUser?.registrationId?.ifBlank { currentUser?.licenseNumber }
        if (!reg.isNullOrBlank()) reg else "NGO-REG-${currentUser?.id?.takeLast(6)?.uppercase() ?: "8921"}"
    }

    val userPhone = remember(currentUser) {
        currentUser?.phone?.ifBlank { "+91 98765 43210" } ?: "+91 98765 43210"
    }

    // Dynamic state dialogs
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showPhotoOptionsBottomSheet by remember { mutableStateOf(false) }
    var showLogoutConfirmDialog by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showPrivacyPolicyDialog by remember { mutableStateOf(false) }
    var showHelpSupportDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var selectedDonationForDetails by remember { mutableStateOf<DonationEntity?>(null) }

    // Settings state
    var pushNotificationsEnabled by remember { mutableStateOf(true) }
    var locationSharingEnabled by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureWhite)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            // ====================================
            // 1. PROFILE HEADER CARD
            // ====================================
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = PureWhite,
                border = BorderStroke(1.dp, GrayBorder),
                shadowElevation = 2.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(LightGreenBg, PureWhite)
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { showPhotoOptionsBottomSheet = true }
                            ) {
                                // Profile Photo Avatar / Logo with edit badge
                                Box(
                                    modifier = Modifier.size(68.dp),
                                    contentAlignment = Alignment.BottomEnd
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                            .border(2.5.dp, EmeraldGreen, CircleShape)
                                            .background(PureWhite),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isUploadingProfileImage) {
                                            CircularProgressIndicator(
                                                color = EmeraldGreen,
                                                modifier = Modifier.size(28.dp),
                                                strokeWidth = 2.5.dp
                                            )
                                        } else if (!currentUser?.profileImage.isNullOrBlank()) {
                                            AsyncImage(
                                                model = currentUser?.profileImage,
                                                contentDescription = if (isNgoRole) "NGO Logo" else "Profile Photo",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            val firstLetter = currentUser?.name?.take(1)?.uppercase() ?: (if (isNgoRole) "N" else "D")
                                            Text(
                                                text = firstLetter,
                                                color = EmeraldGreen,
                                                fontSize = 28.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                        }
                                    }

                                    // Camera edit badge overlay
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(EmeraldGreen)
                                            .border(1.5.dp, PureWhite, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CameraAlt,
                                            contentDescription = "Edit Avatar",
                                            tint = PureWhite,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = currentUser?.name?.ifBlank { if (isNgoRole) "Verified NGO Partner" else "Verified Food Donor" } ?: (if (isNgoRole) "Verified NGO Partner" else "Verified Food Donor"),
                                        color = PrimaryText,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    if (currentUser?.contactPerson?.isNotBlank() == true) {
                                        Text(
                                            text = "Contact: ${currentUser?.contactPerson}",
                                            color = SecondaryText,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    } else if (isNgoRole) {
                                        Text(
                                            text = "Reg No: $ngoRegNumber",
                                            color = SecondaryText,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    // Verification Status Badge
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = EmeraldGreen.copy(alpha = 0.12f),
                                        border = BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.3f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Verified,
                                                contentDescription = "Verified Badge",
                                                tint = DarkGreen,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (isNgoRole) "Verified NGO Partner" else "Verified Food Donor",
                                                color = DarkGreen,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            // Edit Profile Button
                            IconButton(
                                onClick = { showEditProfileDialog = true },
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(PureWhite, CircleShape)
                                    .border(1.dp, GrayBorder, CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Profile",
                                    tint = EmeraldGreen,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = GrayBorder.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(10.dp))

                        // Details Grid
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Outlined.Email, contentDescription = null, tint = SecondaryText, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = currentUser?.email?.ifBlank { "partner@foodshareai.org" } ?: "partner@foodshareai.org",
                                        fontSize = 11.5.sp,
                                        color = SecondaryText,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.Phone, contentDescription = null, tint = SecondaryText, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = userPhone,
                                        fontSize = 11.5.sp,
                                        color = SecondaryText,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.CalendarToday, contentDescription = null, tint = SecondaryText, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = memberSinceText,
                                        fontSize = 11.5.sp,
                                        color = SecondaryText,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = SecondaryText, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = userLocation,
                                        fontSize = 11.5.sp,
                                        color = SecondaryText,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ====================================
            // 2. STATISTICS SECTION
            // ====================================
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (isNgoRole) "Rescue Statistics" else "Donor Impact",
                    color = PrimaryText,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )

                if (isNgoRole) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DonorImpactCard("Food Claims", "$foodClaimsCount", "Total Claimed", Icons.Default.VolunteerActivism, EmeraldGreen, Modifier.weight(1f))
                        DonorImpactCard("Meals Rescued", "$mealsRescuedCount", "Portions Saved", Icons.Default.Restaurant, EmeraldGreen, Modifier.weight(1f))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DonorImpactCard("People Served", "$peopleServedCount", "Beneficiaries Fed", Icons.Default.FamilyRestroom, DarkGreen, Modifier.weight(1f))
                        DonorImpactCard("CO₂ Saved", co2SavedKgText, "Emissions Offset", Icons.Default.Co2, DarkGreen, Modifier.weight(1f))
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DonorImpactCard("Total Donations", "$totalDonationsCount", "All Time Posts", Icons.Default.VolunteerActivism, EmeraldGreen, Modifier.weight(1f))
                        DonorImpactCard("Meals Donated", "$mealsDonatedCount", "Portions Provided", Icons.Default.Restaurant, EmeraldGreen, Modifier.weight(1f))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DonorImpactCard("People Benefited", "$peopleBenefitedCount", "Estimated Beneficiaries", Icons.Default.FamilyRestroom, DarkGreen, Modifier.weight(1f))
                        DonorImpactCard("Carbon Offset", carbonOffsetKgText, "Emissions Saved", Icons.Default.Co2, DarkGreen, Modifier.weight(1f))
                    }
                }
            }

            // ====================================
            // 3. AI INSIGHTS
            // ====================================
            val aiText = remember(predictions, aiRecommendations, myDonations, ngoClaimedMissions, isNgoRole) {
                if (isNgoRole) {
                    if (ngoClaimedMissions.isNotEmpty()) {
                        "Dispatching transport within 30 minutes of claim maximizes food freshness score by 98%."
                    } else {
                        "Surplus prepared meals in your operating region peak between 4 PM and 9 PM."
                    }
                } else {
                    val pred = predictions.firstOrNull { !it.recommendation.isNullOrBlank() }?.recommendation
                    if (!aiRecommendations.isNullOrBlank() && !aiRecommendations.orEmpty().contains("unavailable", ignoreCase = true)) {
                        aiRecommendations
                    } else if (!pred.isNullOrBlank()) {
                        pred
                    } else if (myDonations.isNotEmpty()) {
                        "Prepared meals donated between 5 PM–8 PM achieve a 96% fast acceptance rate by local NGOs."
                    } else null
                }
            }

            if (!aiText.isNullOrBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFFF0FDF4),
                    border = BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "AI Rescue Insights",
                                    color = EmeraldGreen,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Surface(shape = RoundedCornerShape(6.dp), color = EmeraldGreen) {
                                Text("Gemini AI", color = PureWhite, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                        Text(
                            text = aiText,
                            color = PrimaryText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 17.sp
                        )
                    }
                }
            }

            // ====================================
            // 4. RECENT DONATIONS SECTION
            // ====================================
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = PureWhite,
                border = BorderStroke(1.dp, GrayBorder),
                shadowElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.History, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isNgoRole) "Recent Food Rescue Missions" else "Recent Donations",
                                color = PrimaryText,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (isNgoRole && ngoClaimedMissions.isNotEmpty() && onHistoryClick != null) {
                            TextButton(onClick = { onHistoryClick() }) {
                                Text("View All →", color = EmeraldGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        } else if (!isNgoRole && myDonations.isNotEmpty() && onHistoryClick != null) {
                            TextButton(onClick = { onHistoryClick() }) {
                                Text("View All →", color = EmeraldGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    HorizontalDivider(color = GrayBorder.copy(alpha = 0.6f))

                    if (isNgoRole) {
                        if (ngoClaimedMissions.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Outlined.Inbox, contentDescription = null, tint = SecondaryText, modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("No rescue missions yet.", fontSize = 14.sp, color = PrimaryText, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("Claim available surplus food donations nearby to start your rescue mission.", fontSize = 12.sp, color = SecondaryText, textAlign = TextAlign.Center)
                                Spacer(modifier = Modifier.height(14.dp))
                                Button(
                                    onClick = {
                                        if (onNavigateToDonate != null) onNavigateToDonate()
                                        else Toast.makeText(context, "Navigating to Available Food...", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp), tint = PureWhite)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Browse Available Donations", color = PureWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        } else {
                            ngoClaimedMissions.take(5).forEach { donation ->
                                DonorDonationCardRow(donation) {
                                    if (onDonationClick != null) onDonationClick(donation)
                                    else selectedDonationForDetails = donation
                                }
                            }
                        }
                    } else {
                        if (myDonations.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Outlined.Inbox, contentDescription = null, tint = SecondaryText, modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("No recent donations.", fontSize = 14.sp, color = PrimaryText, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("Your listed surplus food donations will appear here.", fontSize = 12.sp, color = SecondaryText, textAlign = TextAlign.Center)
                                Spacer(modifier = Modifier.height(14.dp))
                                Button(
                                    onClick = {
                                        if (onNavigateToDonate != null) onNavigateToDonate()
                                        else Toast.makeText(context, "Navigating to Create Donation...", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = PureWhite)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Donate Food", color = PureWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        } else {
                            myDonations.take(5).forEach { donation ->
                                DonorDonationCardRow(donation) {
                                    if (onDonationClick != null) onDonationClick(donation)
                                    else selectedDonationForDetails = donation
                                }
                            }
                        }
                    }
                }
            }

            // ====================================
            // 5. ESSENTIAL SETTINGS LIST
            // ====================================
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = PureWhite,
                border = BorderStroke(1.dp, GrayBorder),
                shadowElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Settings & Preferences",
                        color = PrimaryText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    HorizontalDivider(color = GrayBorder.copy(alpha = 0.6f))

                    // 1. Push Notifications Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Notifications, contentDescription = null, tint = SecondaryText, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Notifications", fontSize = 13.sp, color = PrimaryText, fontWeight = FontWeight.Medium)
                        }
                        Switch(
                            checked = pushNotificationsEnabled,
                            onCheckedChange = {
                                pushNotificationsEnabled = it
                                Toast.makeText(context, if (it) "Notifications Enabled" else "Notifications Muted", Toast.LENGTH_SHORT).show()
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = PureWhite, checkedTrackColor = EmeraldGreen)
                        )
                    }

                    // 2. Location Services Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = SecondaryText, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Location Services", fontSize = 13.sp, color = PrimaryText, fontWeight = FontWeight.Medium)
                        }
                        Switch(
                            checked = locationSharingEnabled,
                            onCheckedChange = {
                                locationSharingEnabled = it
                                Toast.makeText(context, if (it) "Location Services Enabled" else "Location Services Disabled", Toast.LENGTH_SHORT).show()
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = PureWhite, checkedTrackColor = EmeraldGreen)
                        )
                    }

                    // 3. Edit Profile
                    CleanSettingActionRow(if (isNgoRole) "Edit NGO Profile" else "Edit Profile", Icons.Outlined.Person) {
                        showEditProfileDialog = true
                    }

                    // 4. Change Password
                    CleanSettingActionRow("Change Password", Icons.Outlined.Lock) {
                        showChangePasswordDialog = true
                    }

                    // 5. Privacy Policy
                    CleanSettingActionRow("Privacy Policy", Icons.Outlined.Security) {
                        showPrivacyPolicyDialog = true
                    }

                    // 6. Help & Support
                    CleanSettingActionRow("Help & Support", Icons.AutoMirrored.Outlined.HelpOutline) {
                        showHelpSupportDialog = true
                    }

                    // 7. Contact Us
                    CleanSettingActionRow("Contact Us", Icons.Outlined.ContactSupport) {
                        try {
                            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:1800-366-324"))
                            context.startActivity(dialIntent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Support Helpline: 1800-366-324", Toast.LENGTH_LONG).show()
                        }
                    }

                    HorizontalDivider(color = GrayBorder.copy(alpha = 0.6f))

                    // 8. Logout Action
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showLogoutConfirmDialog = true }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = RubyRed, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Logout", fontSize = 13.sp, color = RubyRed, fontWeight = FontWeight.Bold)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = RubyRed, modifier = Modifier.size(16.dp))
                    }

                    // 9. Delete Account Action
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDeleteAccountDialog = true }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null, tint = RubyRed, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Delete Account", fontSize = 13.sp, color = RubyRed, fontWeight = FontWeight.Bold)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = RubyRed, modifier = Modifier.size(16.dp))
                    }
                }
            }

            // Footer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text("Version 1.0.0", fontSize = 12.sp, color = SecondaryText, fontWeight = FontWeight.Medium)
                Text("© FoodShareAI", fontSize = 11.sp, color = SecondaryText)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // ====================================
        // PHOTO OPTIONS BOTTOM SHEET
        // ====================================
        if (showPhotoOptionsBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showPhotoOptionsBottomSheet = false },
                containerColor = PureWhite,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Profile Photo Options",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryText
                    )
                    HorizontalDivider(color = GrayBorder.copy(alpha = 0.5f))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showPhotoOptionsBottomSheet = false
                                cameraLauncher.launch(null)
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(14.dp))
                        Text("Take Photo", fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = PrimaryText)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showPhotoOptionsBottomSheet = false
                                galleryLauncher.launch("image/*")
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(14.dp))
                        Text("Choose from Gallery", fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = PrimaryText)
                    }

                    if (!currentUser?.profileImage.isNullOrBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showPhotoOptionsBottomSheet = false
                                    viewModel.removeProfileImage { success ->
                                        if (success) {
                                            Toast.makeText(context, "Profile photo removed", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = RubyRed, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(14.dp))
                            Text("Remove Photo", fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = RubyRed)
                        }
                    }

                    TextButton(
                        onClick = { showPhotoOptionsBottomSheet = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel", color = SecondaryText, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }

        // ====================================
        // EDIT PROFILE DIALOG
        // ====================================
        if (showEditProfileDialog) {
            var nameInput by remember { mutableStateOf(currentUser?.name ?: "") }
            var contactPersonInput by remember { mutableStateOf(currentUser?.contactPerson ?: "") }
            var phoneInput by remember { mutableStateOf(currentUser?.phone ?: "") }
            var emailInput by remember { mutableStateOf(currentUser?.email ?: "") }
            var addressInput by remember { mutableStateOf(currentUser?.address ?: "") }
            var cityInput by remember { mutableStateOf(currentUser?.city ?: "") }
            var stateInput by remember { mutableStateOf(currentUser?.state ?: "") }
            var pincodeInput by remember { mutableStateOf(currentUser?.pincode ?: "") }
            var descriptionInput by remember { mutableStateOf(currentUser?.description?.ifBlank { currentUser?.missionStatement } ?: "") }
            var regInput by remember { mutableStateOf(currentUser?.registrationId ?: "") }
            var validationError by remember { mutableStateOf<String?>(null) }

            AlertDialog(
                onDismissRequest = { if (!isLoading) showEditProfileDialog = false },
                containerColor = PureWhite,
                shape = RoundedCornerShape(20.dp),
                title = { Text(if (isNgoRole) "Edit NGO Profile" else "Edit Donor Profile", color = PrimaryText, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (validationError != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = RubyRed.copy(alpha = 0.1f),
                                border = BorderStroke(1.dp, RubyRed.copy(alpha = 0.3f))
                            ) {
                                Text(validationError!!, color = RubyRed, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp))
                            }
                        }

                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it; validationError = null },
                            label = { Text(if (isNgoRole) "Organization Name *" else "Donor Name / Title *") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            colors = customOutlinedTextFieldColors()
                        )

                        OutlinedTextField(
                            value = contactPersonInput,
                            onValueChange = { contactPersonInput = it },
                            label = { Text("Contact Person") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            colors = customOutlinedTextFieldColors()
                        )

                        OutlinedTextField(
                            value = phoneInput,
                            onValueChange = { phoneInput = it; validationError = null },
                            label = { Text("Phone Number *") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            colors = customOutlinedTextFieldColors()
                        )

                        OutlinedTextField(
                            value = emailInput,
                            onValueChange = { emailInput = it; validationError = null },
                            label = { Text("Email Address *") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            colors = customOutlinedTextFieldColors()
                        )

                        if (isNgoRole) {
                            OutlinedTextField(
                                value = regInput,
                                onValueChange = { regInput = it; validationError = null },
                                label = { Text("NGO Registration / License No. *") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true,
                                colors = customOutlinedTextFieldColors()
                            )
                        }

                        OutlinedTextField(
                            value = addressInput,
                            onValueChange = { addressInput = it },
                            label = { Text("Street Address") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = customOutlinedTextFieldColors()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = cityInput,
                                onValueChange = { cityInput = it },
                                label = { Text("City") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true,
                                colors = customOutlinedTextFieldColors()
                            )

                            OutlinedTextField(
                                value = stateInput,
                                onValueChange = { stateInput = it },
                                label = { Text("State") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true,
                                colors = customOutlinedTextFieldColors()
                            )
                        }

                        OutlinedTextField(
                            value = pincodeInput,
                            onValueChange = { pincodeInput = it },
                            label = { Text("Pincode") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            colors = customOutlinedTextFieldColors()
                        )

                        OutlinedTextField(
                            value = descriptionInput,
                            onValueChange = { descriptionInput = it },
                            label = { Text("Description / About") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(90.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = customOutlinedTextFieldColors()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            when {
                                nameInput.isBlank() -> validationError = "Name cannot be blank."
                                !emailInput.contains("@") || !emailInput.contains(".") -> validationError = "Enter a valid email address."
                                phoneInput.length < 7 -> validationError = "Enter a valid phone number."
                                else -> {
                                    viewModel.updateFullProfile(
                                        name = nameInput,
                                        contactPerson = contactPersonInput,
                                        phone = phoneInput,
                                        email = emailInput,
                                        address = addressInput,
                                        city = cityInput,
                                        state = stateInput,
                                        pincode = pincodeInput,
                                        description = descriptionInput,
                                        registrationId = regInput,
                                        missionStatement = descriptionInput
                                    ) { success ->
                                        if (success) {
                                            showEditProfileDialog = false
                                            Toast.makeText(context, "Profile updated successfully.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        },
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = PureWhite, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Save Changes", color = PureWhite, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEditProfileDialog = false }, enabled = !isLoading) {
                        Text("Cancel", color = SecondaryText)
                    }
                }
            )
        }

        // Change Password Dialog
        if (showChangePasswordDialog) {
            var currentPass by remember { mutableStateOf("") }
            var newPass by remember { mutableStateOf("") }
            var confirmPass by remember { mutableStateOf("") }

            AlertDialog(
                onDismissRequest = { showChangePasswordDialog = false },
                containerColor = PureWhite,
                shape = RoundedCornerShape(20.dp),
                title = { Text("Change Password", color = PrimaryText, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = currentPass,
                            onValueChange = { currentPass = it },
                            label = { Text("Current Password") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = customOutlinedTextFieldColors()
                        )
                        OutlinedTextField(
                            value = newPass,
                            onValueChange = { newPass = it },
                            label = { Text("New Password") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = customOutlinedTextFieldColors()
                        )
                        OutlinedTextField(
                            value = confirmPass,
                            onValueChange = { confirmPass = it },
                            label = { Text("Confirm New Password") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = customOutlinedTextFieldColors()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newPass.length < 6) {
                                Toast.makeText(context, "New password must be at least 6 characters.", Toast.LENGTH_SHORT).show()
                            } else if (newPass != confirmPass) {
                                Toast.makeText(context, "Passwords do not match.", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.updateProfile(currentUser?.name ?: "", currentUser?.email ?: "", newPass)
                                showChangePasswordDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Update Password", color = PureWhite, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showChangePasswordDialog = false }) {
                        Text("Cancel", color = SecondaryText)
                    }
                }
            )
        }

        // Logout Confirm Dialog
        if (showLogoutConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showLogoutConfirmDialog = false },
                containerColor = PureWhite,
                shape = RoundedCornerShape(20.dp),
                title = { Text("Confirm Logout", color = PrimaryText, fontWeight = FontWeight.Bold) },
                text = { Text("Are you sure you want to log out of FoodShareAI?", color = SecondaryText, fontSize = 13.sp) },
                confirmButton = {
                    Button(
                        onClick = {
                            showLogoutConfirmDialog = false
                            onLogoutClick()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RubyRed),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Logout", color = PureWhite, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLogoutConfirmDialog = false }) {
                        Text("Cancel", color = SecondaryText)
                    }
                }
            )
        }

        // Delete Account Confirm Dialog
        if (showDeleteAccountDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteAccountDialog = false },
                containerColor = PureWhite,
                shape = RoundedCornerShape(20.dp),
                title = { Text("Delete Account", color = RubyRed, fontWeight = FontWeight.Bold) },
                text = { Text("This will permanently delete your FoodShareAI account and all your donation history. This action cannot be undone.", color = SecondaryText, fontSize = 13.sp) },
                confirmButton = {
                    Button(
                        onClick = {
                            showDeleteAccountDialog = false
                            currentUser?.let { viewModel.deleteUser(it) }
                            onLogoutClick()
                            Toast.makeText(context, "Account deleted successfully.", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RubyRed),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Delete Permanently", color = PureWhite, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteAccountDialog = false }) {
                        Text("Cancel", color = SecondaryText)
                    }
                }
            )
        }

        // Details Modal for Donation Item
        selectedDonationForDetails?.let { donation ->
            AlertDialog(
                onDismissRequest = { selectedDonationForDetails = null },
                containerColor = PureWhite,
                shape = RoundedCornerShape(20.dp),
                title = { Text(donation.title, fontWeight = FontWeight.Bold, color = PrimaryText, fontSize = 17.sp) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Donor: ${donation.donorName}", color = EmeraldGreen, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                        Text("Category: ${donation.foodType}", fontSize = 12.5.sp, color = SecondaryText)
                        Text("Quantity: ${donation.quantity} Meals", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = DarkGreen)
                        Text("Pickup Address: ${donation.location}", fontSize = 12.5.sp, color = PrimaryText, lineHeight = 17.sp)
                        Text("Status: ${donation.status}", fontSize = 12.5.sp, color = EmeraldGreen, fontWeight = FontWeight.Bold)
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { selectedDonationForDetails = null },
                        modifier = Modifier.height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Close", color = PureWhite, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}

@Composable
fun DonorImpactCard(
    title: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(112.dp),
        shape = RoundedCornerShape(18.dp),
        color = PureWhite,
        border = BorderStroke(1.dp, GrayBorder),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = SecondaryText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(iconColor.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Column {
                Text(
                    text = value,
                    color = PrimaryText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = subtitle,
                    color = SecondaryText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun DonorDonationCardRow(
    donation: DonationEntity,
    onClick: () -> Unit
) {
    val dateText = remember(donation.timestamp) {
        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(donation.timestamp))
    }

    val ngoNameText = remember(donation.ngoName) {
        if (!donation.ngoName.isNullOrBlank()) donation.ngoName else "Claimed by Partner NGO"
    }

    val statusColor = when (donation.status.lowercase()) {
        "completed", "delivered" -> EmeraldGreen
        "accepted", "in transit" -> Color(0xFF2563EB)
        "cancelled" -> RubyRed
        else -> Color(0xFFD97706)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        color = LightGreenBg.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, GrayBorder.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(statusColor.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Restaurant, contentDescription = null, tint = statusColor, modifier = Modifier.size(18.dp))
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = donation.title.ifBlank { "Surplus Food Donation" },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${donation.quantity} Meals • $ngoNameText • $dateText",
                        fontSize = 11.sp,
                        color = SecondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = statusColor.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.3f))
            ) {
                Text(
                    text = donation.status,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = statusColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
fun CleanSettingActionRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = SecondaryText, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(title, fontSize = 13.sp, color = PrimaryText, fontWeight = FontWeight.Medium)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = SecondaryText, modifier = Modifier.size(16.dp))
    }
}
