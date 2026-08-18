package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.DonationEntity
import com.example.ui.theme.*
import com.example.ui.viewmodel.FoodShareViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DonationHistoryScreen(
    viewModel: FoodShareViewModel,
    onBackClick: () -> Unit,
    onDonationClick: (DonationEntity) -> Unit
) {
    val currentUser by viewModel.currentUser.collectAsState()
    val allDonations by viewModel.allDonations.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedStatusFilter by remember { mutableStateOf("All") } // All | Created | Accepted | Collected | Delivered | Completed
    var selectedDateFilter by remember { mutableStateOf("All Time") } // All Time | This Week | This Month
    var isSortNewestFirst by remember { mutableStateOf(true) }

    val donorDonations = remember(allDonations, currentUser) {
        if (currentUser?.role == "donor") {
            allDonations.filter { it.donorId == currentUser?.id }
        } else {
            allDonations
        }
    }

    // Apply Search, Status, Date, and Sorting Filters strictly on real backend data
    val filteredList = remember(donorDonations, searchQuery, selectedStatusFilter, selectedDateFilter, isSortNewestFirst) {
        val now = System.currentTimeMillis()
        var result = donorDonations

        // Search Filter
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            result = result.filter { item ->
                item.title.lowercase().contains(q) ||
                item.foodType.lowercase().contains(q) ||
                item.ngoName.lowercase().contains(q) ||
                item.location.lowercase().contains(q) ||
                item.id.lowercase().contains(q)
            }
        }

        // Status Filter
        if (selectedStatusFilter != "All") {
            result = result.filter { item ->
                when (selectedStatusFilter) {
                    "Created" -> item.status.equals("Created", ignoreCase = true) || item.status.equals("Posted", ignoreCase = true)
                    "Accepted" -> item.status.equals("Accepted", ignoreCase = true)
                    "Collected" -> item.status.equals("Picked Up", ignoreCase = true) || item.status.equals("Collected", ignoreCase = true) || item.status.equals("Volunteer Assigned", ignoreCase = true)
                    "Delivered" -> item.status.equals("Delivered", ignoreCase = true)
                    "Completed" -> item.status.equals("Completed", ignoreCase = true)
                    else -> true
                }
            }
        }

        // Date Filter
        if (selectedDateFilter != "All Time") {
            val weekStart = now - (7L * 24 * 60 * 60 * 1000)
            val monthStart = now - (30L * 24 * 60 * 60 * 1000)
            result = result.filter { item ->
                when (selectedDateFilter) {
                    "This Week" -> item.timestamp >= weekStart
                    "This Month" -> item.timestamp >= monthStart
                    else -> true
                }
            }
        }

        // Sorting
        if (isSortNewestFirst) {
            result.sortedByDescending { it.timestamp }
        } else {
            result.sortedBy { it.timestamp }
        }
    }

    Scaffold(
        containerColor = PureWhite,
        topBar = {
            Surface(
                color = PureWhite,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = PrimaryText)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Donation History", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
                        Text("${filteredList.size} records found", fontSize = 11.sp, color = SecondaryText)
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Search Bar Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                placeholder = { Text("Search by title, NGO, address...", fontSize = 13.sp, color = SecondaryText) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = EmeraldGreen) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = SecondaryText)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EmeraldGreen,
                    unfocusedBorderColor = GrayBorder,
                    focusedContainerColor = PureWhite,
                    unfocusedContainerColor = Color(0xFFF8FAFC)
                )
            )

            // Status Filter Chips
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("All", "Created", "Accepted", "Collected", "Delivered", "Completed")) { status ->
                    val isSelected = status == selectedStatusFilter
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedStatusFilter = status },
                        label = { Text(status, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldGreen,
                            selectedLabelColor = PureWhite,
                            containerColor = PureWhite,
                            labelColor = SecondaryText
                        )
                    )
                }
            }

            // Secondary Filter Controls: Date Range & Sort Order
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Date Filter Pill
                var showDateMenu by remember { mutableStateOf(false) }
                Box {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = LightGreenBg,
                        border = BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.3f)),
                        modifier = Modifier.clickable { showDateMenu = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.CalendarToday, contentDescription = null, tint = DarkGreen, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(selectedDateFilter, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DarkGreen)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = DarkGreen, modifier = Modifier.size(14.dp))
                        }
                    }
                    DropdownMenu(
                        expanded = showDateMenu,
                        onDismissRequest = { showDateMenu = false },
                        modifier = Modifier.background(PureWhite)
                    ) {
                        listOf("All Time", "This Week", "This Month").forEach { filter ->
                            DropdownMenuItem(
                                text = { Text(filter, fontSize = 12.sp, fontWeight = if (filter == selectedDateFilter) FontWeight.Bold else FontWeight.Normal, color = PrimaryText) },
                                onClick = {
                                    selectedDateFilter = filter
                                    showDateMenu = false
                                }
                            )
                        }
                    }
                }

                // Sort Toggle Button
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF1F5F9),
                    border = BorderStroke(1.dp, GrayBorder.copy(alpha = 0.6f)),
                    modifier = Modifier.clickable { isSortNewestFirst = !isSortNewestFirst }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isSortNewestFirst) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                            contentDescription = null,
                            tint = PrimaryText,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isSortNewestFirst) "Newest First" else "Oldest First", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = PrimaryText)
                    }
                }
            }

            // History Records List or Empty State
            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .background(LightGreenBg, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Inbox, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(40.dp))
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (donorDonations.isEmpty()) "No donations yet." else "No matching donations found.",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryText
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (donorDonations.isEmpty()) "Create your first surplus food donation to start building your donation history."
                                   else "Try adjusting your search query or status filter to see other records.",
                            fontSize = 12.sp,
                            color = SecondaryText,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(filteredList) { donation ->
                        HistoryDonationRecordCard(
                            donation = donation,
                            onCardClick = { onDonationClick(donation) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryDonationRecordCard(
    donation: DonationEntity,
    onCardClick: () -> Unit
) {
    val dateText = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(donation.timestamp))
    val ngoText = if (donation.ngoName.isNotBlank()) donation.ngoName else "Awaiting NGO Match"
    val addressText = if (donation.location.isNotBlank()) donation.location else if (donation.pickupTime.isNotBlank()) donation.pickupTime else "Verified Pickup Address"

    val (badgeBg, badgeText) = remember(donation.status) {
        when (donation.status.uppercase()) {
            "POSTED", "CREATED" -> Pair(Color(0xFFFEF3C7), Color(0xFFD97706))
            "ACCEPTED" -> Pair(Color(0xFFD1FAE5), EmeraldGreen)
            "VOLUNTEER_ASSIGNED", "ASSIGNED" -> Pair(Color(0xFFDBEAFE), Color(0xFF2563EB))
            "PICKED_UP", "COLLECTED" -> Pair(Color(0xFFF3E8FF), Color(0xFF9333EA))
            "DELIVERED", "COMPLETED" -> Pair(LightGreenBg, DarkGreen)
            else -> Pair(LightGreenBg, DarkGreen)
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick() },
        shape = RoundedCornerShape(18.dp),
        color = PureWhite,
        border = BorderStroke(1.dp, GrayBorder.copy(alpha = 0.6f)),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row: Title & Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = donation.title.ifBlank { donation.foodType },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = badgeBg
                ) {
                    Text(
                        text = donation.status,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeText,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // Info Row: Meals & NGO
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Restaurant, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${donation.quantity} Meals • NGO: $ngoText",
                    fontSize = 12.sp,
                    color = PrimaryText,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Info Row: Address (Max 2 lines)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Icon(Icons.Outlined.Place, contentDescription = null, tint = SecondaryText, modifier = Modifier.size(14.dp).padding(top = 2.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = addressText,
                    fontSize = 12.sp,
                    color = SecondaryText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )
            }

            // Info Row: Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Schedule, contentDescription = null, tint = SecondaryText, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = dateText,
                    fontSize = 11.sp,
                    color = SecondaryText,
                    fontWeight = FontWeight.Medium
                )
            }

            // View Details Button
            Button(
                onClick = onCardClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen, contentColor = PureWhite)
            ) {
                Text("View Details →", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
