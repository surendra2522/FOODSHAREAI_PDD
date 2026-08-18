package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.DonationEntity
import com.example.ui.theme.*
import com.example.ui.viewmodel.FoodShareViewModel

@Composable
fun ActivePickupsScreen(
    viewModel: FoodShareViewModel
) {
    val currentUser by viewModel.currentUser.collectAsState()
    val allDonations by viewModel.allDonations.collectAsState()

    // Filter registrations claimed by this NGO that are not finalized and archived (Completed)
    val activePickups = allDonations.filter { 
        it.ngoId == currentUser?.id && it.status != "Completed"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkNavy)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Section
            Column {
                Text(
                    text = "Active Pickups Dispatch 🚚",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = PureWhite
                )
                Text(
                    text = "Track your dispatched couriers and active food rescues.",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8)
                )
            }

            // Rescue Missions Counter Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SlateDark),
                border = BorderStroke(1.dp, Color(0xFF1E293B))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = if (activePickups.isEmpty()) "All Restorations Done" else "${activePickups.size} Active Rescue Missions",
                            color = PureWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Ensure timely collection to maintain food freshness standards.",
                            color = Color(0xFF64748B),
                            fontSize = 10.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (activePickups.isEmpty()) EmeraldGreen.copy(alpha = 0.12f) else OrangeFlame.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (activePickups.isEmpty()) Icons.Default.DoneAll else Icons.Default.LocalShipping,
                            contentDescription = "Status icon",
                            tint = if (activePickups.isEmpty()) EmeraldGreen else OrangeFlame,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // List of Active Pickup Cards
            if (activePickups.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalShipping,
                            contentDescription = "No active",
                            tint = Color(0xFF475569),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No Active Rescue Routes",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = PureWhite
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Navigate to the available list and claim resources to start new missions.",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(activePickups) { donation ->
                        ActivePickupLogisticsCard(
                            donation = donation,
                            onStatusUpdate = { nextStatus ->
                                viewModel.updateDonationWorkflow(donation.id, nextStatus)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ActivePickupLogisticsCard(
    donation: DonationEntity,
    onStatusUpdate: (String) -> Unit
) {
    // We map our database status values smoothly to 1 of 5 standard workflow steps:
    // 1. Claimed ("Accepted" or database default accepted)
    // 2. Accepted ("Approved")
    // 3. Traveling ("Traveling" / "In Transit")
    // 4. Collected ("Collected" / "Picked Up")
    // 5. Delivered ("Delivered" / "Completed")
    val stepsList = listOf("Claimed", "Accepted", "Traveling", "Collected", "Delivered")
    
    // Mapped status indices
    val currentStepIndex = when (donation.status) {
        "Claimed", "Accepted" -> 0
        "Approved" -> 1
        "Traveling", "In Transit" -> 2
        "Collected", "Picked Up" -> 3
        "Delivered" -> 4
        else -> 0
    }

    // Work out next state and action buttons labels
    val (nextStatus, buttonLabel, buttonIcon) = when (donation.status) {
        "Accepted" -> Triple("Approved", "Approve & Accept Mission", Icons.Default.Check)
        "Approved" -> Triple("Traveling", "Start Traveling (Courier Dispatched)", Icons.Default.DirectionsCar)
        "Traveling", "In Transit" -> Triple("Collected", "Cargo Arrived & Collected", Icons.Default.LocalShipping)
        "Collected", "Picked Up" -> Triple("Delivered", "Mark Safety Delivered", Icons.Default.Home)
        "Delivered" -> Triple("Completed", "Archive Completed Operations", Icons.Default.Archive)
        else -> Triple("Traveling", "Start Traveling", Icons.Default.DirectionsCar)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SlateDark),
        border = BorderStroke(1.dp, Color(0xFF1E293B)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = donation.title,
                        color = PureWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "From Donor: ${donation.donorName ?: "Private Partner"}",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }

                // Current db status badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(OrangeFlame.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = donation.status.uppercase(),
                        color = OrangeFlame,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Pickup Info block
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PinDrop, "Destination Address", tint = EmeraldGreen, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = donation.location,
                    color = Color(0xFFCBD5E1),
                    fontSize = 12.sp
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Restaurant, "Meals count", tint = AccentTeal, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${donation.quantity} estimated meals",
                    color = Color(0xFFCBD5E1),
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Rescue Deployment Timeline 📍",
                color = PureWhite,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // ====================================
            // STEP INDICATOR WORKFLOW (5 STAGES)
            // ====================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                stepsList.forEachIndexed { index, stepName ->
                    val isCompleted = index < currentStepIndex
                    val isActive = index == currentStepIndex
                    val isPending = index > currentStepIndex

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        // Node bubble
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isCompleted -> EmeraldGreen
                                        isActive -> OrangeFlame
                                        else -> Color(0xFF1E293B)
                                    }
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isActive) OrangeFlame else Color(0xFF334155),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isCompleted) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Completed step",
                                    tint = PureWhite,
                                    modifier = Modifier.size(14.dp)
                                )
                            } else {
                                Text(
                                    text = "${index + 1}",
                                    color = if (isActive) PureWhite else Color(0xFF64748B),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Label
                        Text(
                            text = stepName,
                            fontSize = 8.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                            color = when {
                                isCompleted -> EmeraldGreen
                                isActive -> OrangeFlame
                                else -> Color(0xFF64748B)
                            },
                            textAlign = TextAlign.Center
                        )
                    }

                    // Draw connecting line between nodes (except for last element)
                    if (index < stepsList.size - 1) {
                        Box(
                            modifier = Modifier
                                .weight(0.5f)
                                .height(2.dp)
                                .background(
                                    if (index < currentStepIndex) EmeraldGreen else Color(0xFF1E293B)
                                )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action deployment button row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Cancel button
                OutlinedButton(
                    onClick = { onStatusUpdate("Created") },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, RubyRed)
                ) {
                    Text("Cancel Rescue", color = RubyRed, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { onStatusUpdate(nextStatus) },
                    modifier = Modifier
                        .weight(2f)
                        .height(48.dp)
                        .testTag("advance_status_${donation.id}"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (donation.status == "Delivered") EmeraldGreen else OrangeFlame
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = buttonIcon,
                            contentDescription = buttonLabel,
                            tint = PureWhite,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = buttonLabel,
                            color = PureWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
