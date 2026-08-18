package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import com.example.ui.components.InteractiveOpenStreetMap
import com.example.ui.theme.*
import com.example.ui.viewmodel.FoodShareViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DonationTrackingScreen(
    donation: DonationEntity,
    viewModel: FoodShareViewModel,
    onBackClick: () -> Unit,
    onNavigateToChat: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val currentUser by viewModel.currentUser.collectAsState()
    val isNgoRole = currentUser?.role == "ngo"

    // Live State Flow from Firebase/ViewModel
    val allDonations by viewModel.allDonations.collectAsState()
    val liveDonation = remember(allDonations, donation.id) {
        allDonations.find { it.id == donation.id } ?: donation
    }

    var showCancelDialog by remember { mutableStateOf(false) }
    var showChatDialog by remember { mutableStateOf(false) }
    var showAssignVolunteerModal by remember { mutableStateOf(false) }
    var pendingNextStageUpdate by remember { mutableStateOf<Triple<String, String, () -> Unit>?>(null) }
    var isUpdatingStage by remember { mutableStateOf(false) }

    // Volunteer Input Form States
    var volunteerNameInput by remember { mutableStateOf("Team Alpha") }
    var volunteerPhoneInput by remember { mutableStateOf("+91 91234 56789") }
    var volunteerVehicleInput by remember { mutableStateOf("TN-02-AZ-4821") }

    val donationIdFormatted = remember(liveDonation.id) {
        if (liveDonation.id.isNotBlank()) "#DON-${liveDonation.id.takeLast(6).uppercase()}"
        else "#DON-849201"
    }

    // 9 Stage index mapping (0 to 8)
    val stageIndex = remember(liveDonation.status) {
        when (liveDonation.status.trim().lowercase()) {
            "created", "posted" -> 0
            "accepted" -> 1
            "volunteer assigned", "assigned" -> 2
            "volunteer on the way", "in transit", "en route" -> 3
            "volunteer near pickup", "near pickup" -> 4
            "food collected", "collected", "picked up" -> 5
            "delivery started" -> 6
            "delivered" -> 7
            "completed" -> 8
            else -> 0
        }
    }

    val isCompleted = stageIndex >= 8

    fun formatStageTimestamp(ts: Long): String {
        return if (ts > 0L) {
            SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(ts))
        } else "Pending"
    }

    // Status Pill Config with Distinct Stage Colors
    val (statusLabel, statusBg, statusText) = remember(liveDonation.status) {
        when (liveDonation.status.trim().lowercase()) {
            "created", "posted" -> Triple("CREATED", Color(0xFFFEF3C7), Color(0xFFD97706))
            "accepted" -> Triple("ACCEPTED", Color(0xFFD1FAE5), EmeraldGreen)
            "volunteer assigned", "assigned" -> Triple("VOLUNTEER ASSIGNED", Color(0xFFDBEAFE), Color(0xFF2563EB))
            "volunteer on the way", "in transit", "en route" -> Triple("VOLUNTEER ON THE WAY", Color(0xFFE0E7FF), Color(0xFF4F46E5))
            "volunteer near pickup", "near pickup" -> Triple("NEAR PICKUP (300m)", Color(0xFFFFEDD5), Color(0xFFEA580C))
            "food collected", "collected", "picked up" -> Triple("FOOD COLLECTED", Color(0xFFF3E8FF), Color(0xFF9333EA))
            "delivery started" -> Triple("DELIVERY STARTED", Color(0xFFCFFAFE), Color(0xFF0891B2))
            "delivered" -> Triple("DELIVERED", Color(0xFFCCFBF1), Color(0xFF0D9488))
            "completed" -> Triple("MISSION COMPLETED", LightGreenBg, DarkGreen)
            else -> Triple(liveDonation.status.uppercase(), LightGreenBg, EmeraldGreen)
        }
    }

    // Confirm Stage Dialog
    pendingNextStageUpdate?.let { (stageName, description, onConfirm) ->
        AlertDialog(
            onDismissRequest = { pendingNextStageUpdate = null },
            containerColor = PureWhite,
            shape = RoundedCornerShape(24.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VolunteerActivism, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Confirm Update to $stageName", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
                }
            },
            text = { Text(description, fontSize = 12.5.sp, color = SecondaryText, lineHeight = 17.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        onConfirm()
                        pendingNextStageUpdate = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Confirm Update", color = PureWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingNextStageUpdate = null }) {
                    Text("Cancel", color = SecondaryText)
                }
            }
        )
    }

    // Modal to Assign Volunteer (Stage 2)
    if (showAssignVolunteerModal) {
        AlertDialog(
            onDismissRequest = { showAssignVolunteerModal = false },
            containerColor = PureWhite,
            shape = RoundedCornerShape(24.dp),
            title = { Text("Assign Rescue Volunteer", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = PrimaryText) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Enter volunteer dispatch details to notify donor:", fontSize = 12.sp, color = SecondaryText)

                    OutlinedTextField(
                        value = volunteerNameInput,
                        onValueChange = { volunteerNameInput = it },
                        label = { Text("Volunteer Team Name", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        colors = customOutlinedTextFieldColors()
                    )

                    OutlinedTextField(
                        value = volunteerPhoneInput,
                        onValueChange = { volunteerPhoneInput = it },
                        label = { Text("Volunteer Contact Phone", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        colors = customOutlinedTextFieldColors()
                    )

                    OutlinedTextField(
                        value = volunteerVehicleInput,
                        onValueChange = { volunteerVehicleInput = it },
                        label = { Text("Vehicle Reg Number", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        colors = customOutlinedTextFieldColors()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAssignVolunteerModal = false
                        viewModel.updateDonationStage(
                            donationId = liveDonation.id,
                            newStatus = "Volunteer Assigned",
                            volunteerName = volunteerNameInput,
                            volunteerPhone = volunteerPhoneInput,
                            volunteerVehicle = volunteerVehicleInput
                        ) {
                            Toast.makeText(context, "Volunteer Assigned & Donor Notified!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Assign & Dispatch", color = PureWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAssignVolunteerModal = false }) {
                    Text("Cancel", color = SecondaryText)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = PureWhite,
                shadowElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { onBackClick() },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = PrimaryText
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = if (isNgoRole) "NGO Rescue Dispatch" else "Donation Tracking",
                                color = PrimaryText,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = donationIdFormatted,
                                color = EmeraldGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = statusBg,
                        border = BorderStroke(1.dp, statusText.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(statusText, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = statusLabel,
                                color = statusText,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = PureWhite,
                shadowElevation = 12.dp,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isNgoRole) {
                        // NGO ACTION BUTTONS BASED ON ACTIVE STAGE
                        when (stageIndex) {
                            0 -> { // Created -> Accept Donation
                                Button(
                                    onClick = {
                                        pendingNextStageUpdate = Triple(
                                            "Accept Donation",
                                            "Claim this surplus donation for your NGO food rescue operation.",
                                            { viewModel.updateDonationStage(liveDonation.id, "Accepted") }
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                                ) {
                                    Icon(Icons.Default.VolunteerActivism, null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Accept Donation", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = PureWhite)
                                }
                            }

                            1 -> { // Accepted -> Assign Volunteer
                                Button(
                                    onClick = { showAssignVolunteerModal = true },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                                ) {
                                    Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Assign Volunteer", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = PureWhite)
                                }
                            }

                            2 -> { // Volunteer Assigned -> Start Pickup
                                Button(
                                    onClick = {
                                        pendingNextStageUpdate = Triple(
                                            "Start Pickup",
                                            "Mark volunteer on the way to pickup location. Live GPS tracking will begin.",
                                            { viewModel.updateDonationStage(liveDonation.id, "Volunteer On The Way") }
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.DirectionsRun, null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Start Pickup", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = PureWhite)
                                }
                            }

                            3 -> { // Volunteer On The Way -> Mark Near Pickup
                                Button(
                                    onClick = {
                                        pendingNextStageUpdate = Triple(
                                            "Mark Near Pickup",
                                            "Notify donor that volunteer team has arrived within 300m of pickup address.",
                                            { viewModel.updateDonationStage(liveDonation.id, "Volunteer Near Pickup") }
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))
                                ) {
                                    Icon(Icons.Default.NearMe, null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Mark Near Pickup (300m)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = PureWhite)
                                }
                            }

                            4 -> { // Near Pickup -> Food Collected
                                Button(
                                    onClick = {
                                        pendingNextStageUpdate = Triple(
                                            "Food Collected",
                                            "Confirm food has been inspected, loaded, and verified for distribution.",
                                            { viewModel.updateDonationStage(liveDonation.id, "Food Collected") }
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                                ) {
                                    Icon(Icons.Default.TakeoutDining, null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Food Collected", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = PureWhite)
                                }
                            }

                            5 -> { // Food Collected -> Delivery Started
                                Button(
                                    onClick = {
                                        pendingNextStageUpdate = Triple(
                                            "Delivery Started",
                                            "Mark delivery started to shelter distribution center.",
                                            { viewModel.updateDonationStage(liveDonation.id, "Delivery Started") }
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                                ) {
                                    Icon(Icons.Default.LocalShipping, null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Delivery Started", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = PureWhite)
                                }
                            }

                            6 -> { // Delivery Started -> Mark Delivered
                                Button(
                                    onClick = {
                                        pendingNextStageUpdate = Triple(
                                            "Mark Delivered",
                                            "Confirm food has safely arrived at NGO shelter.",
                                            { viewModel.updateDonationStage(liveDonation.id, "Delivered") }
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = DarkGreen)
                                ) {
                                    Icon(Icons.Default.TaskAlt, null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Mark Delivered", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = PureWhite)
                                }
                            }

                            7 -> { // Delivered -> Complete Mission
                                Button(
                                    onClick = {
                                        pendingNextStageUpdate = Triple(
                                            "Complete Mission",
                                            "Automatically update NGO & Donor analytics, meals saved, and CO2 offset metrics.",
                                            { viewModel.updateDonationStage(liveDonation.id, "Completed") }
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = DarkGreen)
                                ) {
                                    Icon(Icons.Default.Celebration, null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Complete Mission", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = PureWhite)
                                }
                            }

                            else -> { // Completed Stage
                                Surface(
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape = RoundedCornerShape(18.dp),
                                    color = LightGreenBg,
                                    border = BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.4f))
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.CheckCircle, null, tint = DarkGreen, modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Mission Completed & Analytics Updated", fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = DarkGreen)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // DONOR READ-ONLY READOUT
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:+919876543210"))
                                context.startActivity(intent)
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, EmeraldGreen)
                        ) {
                            Icon(Icons.Default.Phone, null, tint = EmeraldGreen, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Call NGO", color = EmeraldGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        Button(
                            onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "I just donated ${liveDonation.quantity} meals on FoodShareAI! Saved CO2 and fed local families.")
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Impact"))
                            },
                            modifier = Modifier.weight(1.2f).height(48.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen, contentColor = PureWhite)
                        ) {
                            Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share Impact", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        },
        containerColor = PureWhite
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ====================================================
            // 1. DONATION SUMMARY CARD
            // ====================================================
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = PureWhite,
                border = BorderStroke(1.dp, GrayBorder.copy(alpha = 0.6f)),
                shadowElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "DONATION SUMMARY",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = SecondaryText
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = LightGreenBg
                        ) {
                            Text(
                                text = liveDonation.foodType.ifBlank { "Prepared Meals" },
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = DarkGreen,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(SurfaceLight),
                            contentAlignment = Alignment.Center
                        ) {
                            if (liveDonation.imageUrl.isNotBlank()) {
                                AsyncImage(
                                    model = liveDonation.imageUrl,
                                    contentDescription = "Food Image",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(Icons.Default.Restaurant, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(28.dp))
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = liveDonation.title.ifBlank { "Surplus Food Donation" },
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Donor: ${liveDonation.donorName.ifBlank { "Verified Partner" }}",
                                fontSize = 12.sp,
                                color = SecondaryText
                            )
                            Text(
                                text = "Portions: ${liveDonation.quantity} Meals",
                                fontSize = 12.sp,
                                color = DarkGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    HorizontalDivider(color = GrayBorder.copy(alpha = 0.6f))

                    Text(
                        text = "Pickup Location: ${liveDonation.location.ifBlank { "Metropolitan Sector 4" }}",
                        fontSize = 11.5.sp,
                        color = PrimaryText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 15.sp
                    )
                }
            }

            // ====================================================
            // 2. LIVE REALTIME STAGE TIMELINE (9 STAGES)
            // ====================================================
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = PureWhite,
                border = BorderStroke(1.dp, GrayBorder.copy(alpha = 0.6f)),
                shadowElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Timeline, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("LIVE RESCUE TIMELINE", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
                        }
                        Surface(shape = RoundedCornerShape(6.dp), color = LightGreenBg) {
                            Text("🟢 Realtime Sync", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = DarkGreen, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }

                    HorizontalDivider(color = GrayBorder.copy(alpha = 0.6f))

                    val timeline9Stages = listOf(
                        TimelineStageData("Donation Created", "Form published & listed on rescue network", Icons.Default.AddCircle, Color(0xFFF59E0B), formatStageTimestamp(liveDonation.timestamp)),
                        TimelineStageData("NGO Accepted", "${liveDonation.ngoName.ifBlank { "Hope Shelter Trust" }} accepted mission", Icons.Default.CheckCircle, Color(0xFF10B981), formatStageTimestamp(liveDonation.acceptedAt)),
                        TimelineStageData("Volunteer Assigned", "Volunteer '${liveDonation.volunteerName.ifBlank { "Team Alpha" }}' assigned", Icons.Default.PersonSearch, Color(0xFF3B82F6), formatStageTimestamp(liveDonation.assignedAt)),
                        TimelineStageData("Volunteer On The Way", "Thermal van en route to pickup address", Icons.AutoMirrored.Filled.DirectionsRun, Color(0xFF6366F1), formatStageTimestamp(liveDonation.pickupStartedAt)),
                        TimelineStageData("Volunteer Near Pickup", "Volunteer within 300m of pickup location", Icons.Default.NearMe, Color(0xFFF97316), formatStageTimestamp(liveDonation.nearPickupAt)),
                        TimelineStageData("Food Collected", "Portions loaded & thermal grade verified", Icons.Default.Inventory2, Color(0xFF8B5CF6), formatStageTimestamp(liveDonation.collectedAt)),
                        TimelineStageData("Delivery Started", "En route to shelter distribution center", Icons.Default.LocalShipping, Color(0xFF06B6D4), formatStageTimestamp(liveDonation.deliveryStartedAt)),
                        TimelineStageData("Delivered", "Food safely arrived at destination", Icons.Default.TaskAlt, Color(0xFF0D9488), formatStageTimestamp(liveDonation.deliveredAt)),
                        TimelineStageData("Completed", "Beneficiaries served & stats updated", Icons.Default.AutoAwesome, Color(0xFF059669), formatStageTimestamp(liveDonation.completedAt))
                    )

                    timeline9Stages.forEachIndexed { index, stage ->
                        val isCompletedNode = index < stageIndex
                        val isCurrentNode = index == stageIndex
                        val isUpcomingNode = index > stageIndex

                        TimelineNodeRow(
                            index = index,
                            title = stage.title,
                            description = stage.description,
                            icon = stage.icon,
                            stageColor = stage.color,
                            isCompleted = isCompletedNode,
                            isCurrent = isCurrentNode,
                            isUpcoming = isUpcomingNode,
                            isLast = index == timeline9Stages.lastIndex,
                            timestampStr = stage.timestampStr
                        )
                    }
                }
            }

            // ====================================================
            // 3. LIVE GPS ROUTE MAP
            // ====================================================
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = PureWhite,
                border = BorderStroke(1.dp, GrayBorder.copy(alpha = 0.6f)),
                shadowElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Map, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("LIVE GPS TRACKING", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
                        }
                        Surface(shape = RoundedCornerShape(8.dp), color = LightGreenBg) {
                            Text("ETA: 12 mins • 1.8 km", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DarkGreen, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(16.dp))
                    ) {
                        InteractiveOpenStreetMap(
                            initialLat = if (liveDonation.latitude != 0.0) liveDonation.latitude else 13.0480,
                            initialLng = if (liveDonation.longitude != 0.0) liveDonation.longitude else 80.0934,
                            showSearchHeader = false,
                            showControls = true
                        )
                    }
                }
            }

            // ====================================================
            // 4. VOLUNTEER DISPATCH DETAILS CARD
            // ====================================================
            if (stageIndex >= 2) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = PureWhite,
                    border = BorderStroke(1.dp, GrayBorder.copy(alpha = 0.6f)),
                    shadowElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("DISPATCHED VOLUNTEER", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SecondaryText)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .background(LightGreenBg, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = DarkGreen, modifier = Modifier.size(24.dp))
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(liveDonation.volunteerName.ifBlank { "Rajesh Kumar (Team Alpha)" }, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
                                Text("Vehicle: ${liveDonation.volunteerVehicle.ifBlank { "TN-02-AZ-4821" }}", fontSize = 12.sp, color = SecondaryText)
                                Text("Contact: ${liveDonation.volunteerPhone.ifBlank { "+91 91234 56789" }}", fontSize = 11.5.sp, color = DarkGreen, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ====================================================
// TIMELINE ROW COMPONENT WITH DISTINCT STAGE COLORS & PULSE
// ====================================================
@Composable
fun TimelineNodeRow(
    index: Int,
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    stageColor: Color,
    isCompleted: Boolean,
    isCurrent: Boolean,
    isUpcoming: Boolean,
    isLast: Boolean,
    timestampStr: String
) {
    val transition = rememberInfiniteTransition(label = "pulseAnim")
    val pulseScale by transition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(animation = tween(1000, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "scale"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        // Node Column
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isCurrent) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .scale(pulseScale)
                            .background(stageColor.copy(alpha = 0.25f), CircleShape)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(
                            color = when {
                                isCompleted -> EmeraldGreen
                                isCurrent -> stageColor
                                else -> SurfaceLight
                            },
                            shape = CircleShape
                        )
                        .border(
                            width = 1.dp,
                            color = when {
                                isCompleted -> EmeraldGreen
                                isCurrent -> stageColor
                                else -> GrayBorder
                            },
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isCompleted) Icons.Default.Check else icon,
                        contentDescription = null,
                        tint = if (isCompleted || isCurrent) PureWhite else SecondaryText,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(38.dp)
                        .background(if (isCompleted) EmeraldGreen else GrayBorder.copy(alpha = 0.5f))
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Content Column
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (!isLast) 12.dp else 0.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = if (isCurrent || isCompleted) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isUpcoming) SecondaryText else PrimaryText
                )
                Text(
                    text = timestampStr,
                    fontSize = 10.sp,
                    color = SecondaryText,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 11.sp,
                color = if (isCurrent) stageColor else SecondaryText,
                lineHeight = 15.sp
            )
        }
    }
}

private data class TimelineStageData(
    val title: String,
    val description: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: Color,
    val timestampStr: String
)
