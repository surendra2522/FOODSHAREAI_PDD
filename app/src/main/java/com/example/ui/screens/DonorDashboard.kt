package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.DonationEntity
import com.example.ui.theme.*
import com.example.ui.viewmodel.FoodShareViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Custom theme colors for Donor Dashboard
private val PageBgPureWhite = Color(0xFFFFFFFF)
private val CardPureWhite = Color(0xFFFFFFFF)
private val LightMintGrad = Color(0xFFF0FDF4)
private val TextCharcoal = Color(0xFF111827)
private val TextSlateMuted = Color(0xFF6B7280)
private val BorderDividerGrey = Color(0xFFE5E7EB)
private val SuccessForestGreen = Color(0xFF16A34A)
private val StatusAcceptedBlue = Color(0xFF3B82F6)

@Composable
fun DonorDashboard(
    viewModel: FoodShareViewModel,
    onCreateDonationClick: () -> Unit,
    onMyDonationsClick: () -> Unit,
    onDonationClick: (DonationEntity) -> Unit = {}
) {
    val currentUser by viewModel.currentUser.collectAsState()
    val allDonations by viewModel.allDonations.collectAsState()

    val personalDonations = remember(allDonations, currentUser) {
        allDonations.filter { it.donorId == currentUser?.id }
    }
    val recentDonations = personalDonations.take(4)

    // Greeting logic based on system time
    val currentHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val timeGreeting = remember(currentHour) {
        when (currentHour) {
            in 5..11 -> "Good Morning"
            in 12..16 -> "Good Afternoon"
            else -> "Good Evening"
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBgPureWhite)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)
    ) {
        // ==========================================
        // 1. WELCOME CARD (Height reduced by 20%, 24dp radius, light green gradient)
        // ==========================================
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = CardPureWhite,
                border = BorderStroke(1.dp, BorderDividerGrey),
                shadowElevation = 2.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(LightMintGrad, CardPureWhite)
                            )
                        )
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                            Text(
                                text = "👋 $timeGreeting",
                                color = EmeraldGreen,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = currentUser?.name ?: "Food Share Org",
                                color = TextCharcoal,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Let's reduce food waste together.",
                                color = TextSlateMuted,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(EmeraldGreen.copy(alpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolunteerActivism,
                                contentDescription = null,
                                tint = EmeraldGreen,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }

        // ==========================================
        // 2. STATISTICS CARDS (20dp radius, 16dp spacing, equal height 124dp)
        // ==========================================
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val mealsSavedValue = currentUser?.mealsSaved ?: 125
                val co2Value = "%.1f".format(Locale.US, currentUser?.co2OffsetKg ?: 52.0)

                ImpactCard(
                    icon = Icons.Default.Restaurant,
                    value = "$mealsSavedValue",
                    title = "Meals Saved",
                    trendText = "+5%",
                    modifier = Modifier.weight(1f)
                )

                ImpactCard(
                    icon = Icons.Default.Eco,
                    value = "$co2Value kg",
                    title = "CO₂ Offset",
                    trendText = "+3.2 kg",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // ==========================================
        // 3. DONATE CARD (Reduced height, "+ Donate Food" button, subtle arrow)
        // ==========================================
        item {
            val surplusInteractionSource = remember { MutableInteractionSource() }
            val isSurplusPressed by surplusInteractionSource.collectIsPressedAsState()
            val surplusScale by animateFloatAsState(
                targetValue = if (isSurplusPressed) 0.98f else 1f,
                animationSpec = tween(150),
                label = "surplusScale"
            )

            Surface(
                onClick = { onCreateDonationClick() },
                interactionSource = surplusInteractionSource,
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(surplusScale),
                shape = RoundedCornerShape(22.dp),
                shadowElevation = 4.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(EmeraldGreen, DarkGreen)
                            )
                        )
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Share Surplus Food",
                                color = PureWhite,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "List your excess food now to help local communities",
                                color = PureWhite.copy(alpha = 0.88f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // "+ Donate Food" button pill
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = PureWhite
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "+ Donate Food",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldGreen
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = EmeraldGreen,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // 4. RECENT DONATIONS HEADER & LIST ("View All →", Status Badges)
        // ==========================================
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Donations",
                    color = TextCharcoal,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                TextButton(
                    onClick = onMyDonationsClick,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "View All →",
                        color = EmeraldGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Empty state or list of donations
        if (recentDonations.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = CardPureWhite,
                    border = BorderStroke(1.dp, BorderDividerGrey),
                    shadowElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp, horizontal = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .background(EmeraldGreen.copy(alpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.VolunteerActivism,
                                contentDescription = null,
                                tint = EmeraldGreen,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Your first donation will appear here.",
                            color = TextCharcoal,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Start sharing surplus food to help your local community.",
                            color = TextSlateMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = onCreateDonationClick,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldGreen,
                                contentColor = PureWhite
                            ),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 9.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Create First Donation",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        } else {
            items(recentDonations) { donation ->
                DonationListItem(donation) {
                    onDonationClick(donation)
                }
            }
        }
    }
}

@Composable
fun ImpactCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    title: String,
    trendText: String? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(124.dp),
        shape = RoundedCornerShape(20.dp),
        color = CardPureWhite,
        border = BorderStroke(1.dp, BorderDividerGrey),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(EmeraldGreen.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = EmeraldGreen,
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (trendText != null) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = LightMintGrad,
                        border = BorderStroke(0.5.dp, EmeraldGreen.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                contentDescription = null,
                                tint = EmeraldGreen,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = trendText,
                                color = EmeraldGreen,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            Column {
                AnimatedContent(
                    targetState = value,
                    transitionSpec = {
                        slideInVertically { height -> height } + fadeIn() togetherWith
                            slideOutVertically { height -> -height } + fadeOut()
                    },
                    label = "statValueAnim"
                ) { targetVal ->
                    Text(
                        text = targetVal,
                        color = TextCharcoal,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = title,
                    color = TextSlateMuted,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun DonationListItem(
    donation: DonationEntity,
    onClick: () -> Unit = {}
) {
    val dateText = remember(donation.timestamp) {
        try {
            SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(donation.timestamp))
        } catch (e: Exception) {
            "Today"
        }
    }

    val statusBgColor = when (donation.status.lowercase()) {
        "completed" -> SuccessForestGreen
        "delivered" -> EmeraldGreen
        "accepted" -> StatusAcceptedBlue
        else -> Color(0xFFF59E0B)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        color = CardPureWhite,
        border = BorderStroke(1.dp, BorderDividerGrey),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(EmeraldGreen.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Fastfood,
                    contentDescription = null,
                    tint = EmeraldGreen,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = donation.title,
                    color = TextCharcoal,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${donation.quantity} Meals • $dateText",
                        color = TextSlateMuted,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = statusBgColor.copy(alpha = 0.12f),
                        border = BorderStroke(0.5.dp, statusBgColor.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = donation.status.ifBlank { "Claimed" },
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusBgColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Details",
                tint = TextSlateMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
