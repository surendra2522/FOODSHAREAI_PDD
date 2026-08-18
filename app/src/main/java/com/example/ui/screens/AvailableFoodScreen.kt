package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.ui.components.MapMarkerItem
import com.example.ui.theme.*
import com.example.ui.viewmodel.FoodShareViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvailableFoodScreen(
    viewModel: FoodShareViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val currentUser by viewModel.currentUser.collectAsState()
    val availableDonations by viewModel.availableDonations.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var activeQuickFilter by remember { mutableStateOf<String?>(null) }
    var showFilterBottomSheet by remember { mutableStateOf(false) }
    var showMapViewModal by remember { mutableStateOf(false) }
    var selectedDonationForDetails by remember { mutableStateOf<DonationEntity?>(null) }
    var claimingDonationForConfirm by remember { mutableStateOf<DonationEntity?>(null) }
    var isClaimingProcess by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }

    // Bottom Sheet Filter States
    var maxDistanceKm by remember { mutableFloatStateOf(25.0f) }
    var minFreshnessScore by remember { mutableIntStateOf(70) }
    var minQuantityMeals by remember { mutableIntStateOf(0) }
    var sortByField by remember { mutableStateOf("Distance") }

    val categories = listOf(
        "All",
        "Cooked Meals",
        "Veg",
        "Non-Veg",
        "Bakery",
        "Fruits",
        "Beverages"
    )

    val quickFiltersRow1 = listOf("Distance", "Freshness", "Quantity", "Pickup Time")

    // Filter Logic
    val filteredDonations = remember(
        availableDonations,
        searchQuery,
        selectedCategory,
        activeQuickFilter,
        maxDistanceKm,
        minFreshnessScore,
        minQuantityMeals,
        sortByField
    ) {
        var result = availableDonations.filter { donation ->
            val matchesSearch = searchQuery.isBlank() ||
                    donation.title.contains(searchQuery, ignoreCase = true) ||
                    donation.location.contains(searchQuery, ignoreCase = true) ||
                    donation.donorName.contains(searchQuery, ignoreCase = true) ||
                    donation.foodType.contains(searchQuery, ignoreCase = true)

            val matchesCategory = when (selectedCategory) {
                "All" -> true
                "Cooked Meals" -> donation.foodType.contains("Cooked", ignoreCase = true) || donation.foodType.contains("Meal", ignoreCase = true)
                "Veg" -> donation.foodType.contains("Veg", ignoreCase = true) && !donation.foodType.contains("Non", ignoreCase = true)
                "Non-Veg" -> donation.foodType.contains("Non-Veg", ignoreCase = true) || donation.foodType.contains("Chicken", ignoreCase = true) || donation.foodType.contains("Meat", ignoreCase = true)
                "Bakery" -> donation.foodType.contains("Bakery", ignoreCase = true) || donation.foodType.contains("Bread", ignoreCase = true)
                "Fruits" -> donation.foodType.contains("Fruit", ignoreCase = true) || donation.foodType.contains("Produce", ignoreCase = true)
                "Beverages" -> donation.foodType.contains("Beverage", ignoreCase = true) || donation.foodType.contains("Drink", ignoreCase = true)
                else -> true
            }

            val matchesQuickFilter = when (activeQuickFilter) {
                "Distance" -> true
                "Freshness" -> true
                "Quantity" -> donation.quantity >= 30
                "Pickup Time" -> donation.pickupTime.isNotBlank()
                else -> true
            }

            val matchesMinQuantity = donation.quantity >= minQuantityMeals

            matchesSearch && matchesCategory && matchesQuickFilter && matchesMinQuantity
        }

        result = when (sortByField) {
            "Highest Quantity" -> result.sortedByDescending { it.quantity }
            "Freshest" -> result.sortedByDescending { it.quantity }
            "Latest" -> result.sortedByDescending { it.timestamp }
            else -> result // Nearest / Distance
        }

        result
    }

    val ngoLat = 12.9716
    val ngoLng = 77.5946

    val mapMarkers = remember(filteredDonations) {
        filteredDonations.mapIndexed { index, donation ->
            val lat = if (donation.latitude != 0.0) donation.latitude else ngoLat + (index % 5 - 2) * 0.008
            val lng = if (donation.longitude != 0.0) donation.longitude else ngoLng + (index % 4 - 1) * 0.007
            MapMarkerItem(
                id = donation.id,
                title = donation.title,
                category = donation.foodType,
                freshnessScore = 96,
                quantityMeals = donation.quantity,
                address = donation.location,
                latitude = lat,
                longitude = lng,
                status = if (donation.quantity >= 40) "Urgent" else "Available",
                photoUri = donation.imageUrl,
                distanceKm = 0.8 + (index * 0.4),
                pickupTime = donation.pickupTime.ifBlank { "Immediate Pickup" },
                aiConfidence = "98% Match",
                rawDonation = donation
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureWhite)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // ==========================================
            // 1. MINIMAL HEADER (Title, Subtitle & 40dp Filter Icon ONLY)
            // ==========================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Claim Food",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryText
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Find nearby verified food donations",
                        fontSize = 12.5.sp,
                        color = SecondaryText
                    )
                }

                // Properly Aligned 40dp Filter Icon Button
                IconButton(
                    onClick = { showFilterBottomSheet = true },
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFFF8FAFC), CircleShape)
                        .border(1.dp, GrayBorder, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Filter Options",
                        tint = EmeraldGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // ==========================================
            // 2. SEARCH BAR (With Clear X Button)
            // ==========================================
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                placeholder = { Text("Search by donor, food type or location", fontSize = 13.sp, color = SecondaryText) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = EmeraldGreen, modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = SecondaryText, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = customOutlinedTextFieldColors()
            )

            // ==========================================
            // 3. HORIZONTALLY SCROLLABLE QUICK FILTERS
            // ==========================================
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                items(quickFiltersRow1) { filter ->
                    val isSelected = activeQuickFilter == filter
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            activeQuickFilter = if (isSelected) null else filter
                        },
                        label = {
                            Text(
                                text = filter,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                softWrap = false
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = when (filter) {
                                    "Distance" -> Icons.Outlined.LocationOn
                                    "Freshness" -> Icons.Default.AutoAwesome
                                    "Quantity" -> Icons.Default.ShoppingBag
                                    "Pickup Time" -> Icons.Outlined.Timer
                                    else -> Icons.Default.FilterList
                                },
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = LightGreenBg,
                            selectedLabelColor = DarkGreen,
                            containerColor = PureWhite,
                            labelColor = SecondaryText
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = GrayBorder,
                            selectedBorderColor = EmeraldGreen
                        ),
                        modifier = Modifier.heightIn(min = 38.dp)
                    )
                }
            }

            // ==========================================
            // 4. HORIZONTALLY SCROLLABLE CATEGORY CHIPS
            // ==========================================
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                items(categories) { category ->
                    val isSelected = category == selectedCategory
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategory = category },
                        label = {
                            Text(
                                text = category,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                softWrap = false
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldGreen,
                            selectedLabelColor = PureWhite,
                            containerColor = PureWhite,
                            labelColor = SecondaryText
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = GrayBorder,
                            selectedBorderColor = EmeraldGreen
                        ),
                        modifier = Modifier.heightIn(min = 38.dp)
                    )
                }
            }

            // ==========================================
            // 5. AVAILABLE COUNT & AI RECOMMENDED BADGE (Only displayed when donations exist)
            // ==========================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Available Donations (${filteredDonations.size})",
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryText
                )

                // AI Recommended badge shown ONLY when at least 1 donation exists
                if (filteredDonations.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = LightGreenBg
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = DarkGreen, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("AI Recommended", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = DarkGreen)
                        }
                    }
                }
            }

            // ==========================================
            // 6. DONATION LIST & COMPACT EMPTY STATE (Height ~ 180-200dp)
            // ==========================================
            if (isRefreshing || isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = EmeraldGreen, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Fetching real-time food donations...", fontSize = 12.5.sp, color = EmeraldGreen, fontWeight = FontWeight.Bold)
                    }
                }
            } else if (filteredDonations.isEmpty()) {
                // COMPACT EMPTY STATE CARD (Height ~ 180-200dp)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 200.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, GrayBorder.copy(alpha = 0.6f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("📦", fontSize = 32.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("No nearby food donations found.", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "We'll notify your NGO automatically when verified donors publish food nearby.",
                            fontSize = 12.sp,
                            color = SecondaryText,
                            textAlign = TextAlign.Center,
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    coroutineScope.launch {
                                        isRefreshing = true
                                        viewModel.refreshData()
                                        delay(500)
                                        isRefreshing = false
                                        Toast.makeText(context, "Refreshed live donations", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.height(48.dp),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, EmeraldGreen),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Refresh", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = EmeraldGreen)
                            }

                            Button(
                                onClick = {
                                    maxDistanceKm += 15.0f
                                    showMapViewModal = true
                                },
                                modifier = Modifier.height(48.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
                            ) {
                                Text("Search Wider", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = PureWhite)
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    items(filteredDonations, key = { it.id }) { donation ->
                        RedesignedDonationMarketplaceCard(
                            donation = donation,
                            onViewDetails = { selectedDonationForDetails = donation },
                            onClaimClick = {
                                coroutineScope.launch {
                                    isClaimingProcess = true
                                    viewModel.acceptDonation(donation.id)
                                    delay(400)
                                    isClaimingProcess = false
                                    Toast.makeText(context, "Donation claimed successfully!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            isAnalyzing = false
                        )
                    }
                }
            }
        }

        // ==========================================
        // 7. FILTER BOTTOM SHEET DIALOG
        // ==========================================
        if (showFilterBottomSheet) {
            AlertDialog(
                onDismissRequest = { showFilterBottomSheet = false },
                containerColor = PureWhite,
                shape = RoundedCornerShape(20.dp),
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Filter Options", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = PrimaryText)
                        }
                        IconButton(onClick = { showFilterBottomSheet = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = SecondaryText)
                        }
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // 1. Distance Slider
                        Column {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Distance", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
                                Text("${maxDistanceKm.toInt()} km", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = EmeraldGreen)
                            }
                            Slider(
                                value = maxDistanceKm,
                                onValueChange = { maxDistanceKm = it },
                                valueRange = 5f..50f,
                                steps = 9,
                                colors = SliderDefaults.colors(thumbColor = EmeraldGreen, activeTrackColor = EmeraldGreen)
                            )
                        }

                        HorizontalDivider(color = GrayBorder.copy(alpha = 0.5f))

                        // 2. Freshness %
                        Column {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Freshness %", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
                                Text("$minFreshnessScore%+", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD97706))
                            }
                            Slider(
                                value = minFreshnessScore.toFloat(),
                                onValueChange = { minFreshnessScore = it.toInt() },
                                valueRange = 50f..95f,
                                steps = 8,
                                colors = SliderDefaults.colors(thumbColor = Color(0xFFD97706), activeTrackColor = Color(0xFFD97706))
                            )
                        }

                        HorizontalDivider(color = GrayBorder.copy(alpha = 0.5f))

                        // 3. Quantity
                        Column {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Quantity", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
                                Text("$minQuantityMeals+ meals", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = DarkGreen)
                            }
                            Slider(
                                value = minQuantityMeals.toFloat(),
                                onValueChange = { minQuantityMeals = it.toInt() },
                                valueRange = 0f..100f,
                                steps = 9,
                                colors = SliderDefaults.colors(thumbColor = DarkGreen, activeTrackColor = DarkGreen)
                            )
                        }

                        HorizontalDivider(color = GrayBorder.copy(alpha = 0.5f))

                        // 4. Sort Options
                        Column {
                            Text("Sort Options", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
                            Spacer(modifier = Modifier.height(6.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(listOf("Nearest", "Highest Quantity", "Freshest", "Latest")) { sortOpt ->
                                    val isSel = sortByField == sortOpt
                                    FilterChip(
                                        selected = isSel,
                                        onClick = { sortByField = sortOpt },
                                        label = { Text(sortOpt, fontSize = 11.5.sp, fontWeight = FontWeight.Bold) },
                                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = EmeraldGreen, selectedLabelColor = PureWhite),
                                        modifier = Modifier.heightIn(min = 36.dp)
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showFilterBottomSheet = false },
                        modifier = Modifier.height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Text("Apply Filters", color = PureWhite, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            maxDistanceKm = 25.0f
                            minFreshnessScore = 70
                            minQuantityMeals = 0
                            selectedCategory = "All"
                            activeQuickFilter = null
                            sortByField = "Distance"
                            showFilterBottomSheet = false
                        },
                        modifier = Modifier.heightIn(min = 44.dp)
                    ) {
                        Text("Reset Filters", color = RubyRed, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        // Fullscreen Map Modal
        if (showMapViewModal) {
            AlertDialog(
                onDismissRequest = { showMapViewModal = false },
                containerColor = PureWhite,
                shape = RoundedCornerShape(20.dp),
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Map, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Full-Screen Rescue Map", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
                        }
                        IconButton(onClick = { showMapViewModal = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = SecondaryText)
                        }
                    }
                },
                text = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, GrayBorder, RoundedCornerShape(14.dp))
                    ) {
                        InteractiveOpenStreetMap(
                            initialLat = ngoLat,
                            initialLng = ngoLng,
                            markers = mapMarkers,
                            onMarkerClick = { marker ->
                                marker.rawDonation?.let { don ->
                                    claimingDonationForConfirm = don
                                }
                            },
                            showSearchHeader = false,
                            showControls = true
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showMapViewModal = false },
                        modifier = Modifier.height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Text("Close Map", color = PureWhite, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        // View Details Modal
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
                        Text("Pickup Time Window: ${donation.pickupTime.ifBlank { "Immediate" }}", fontSize = 12.sp, color = SecondaryText)
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { selectedDonationForDetails = null },
                        modifier = Modifier.height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Text("Close", color = PureWhite, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}

@Composable
fun RedesignedDonationMarketplaceCard(
    donation: DonationEntity,
    onViewDetails: () -> Unit,
    onClaimClick: () -> Unit,
    isAnalyzing: Boolean = false
) {
    val context = LocalContext.current
    var isBookmarked by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onViewDetails() },
        shape = RoundedCornerShape(20.dp),
        color = PureWhite,
        border = BorderStroke(1.dp, GrayBorder.copy(alpha = 0.7f)),
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Row 1: Image, Title, Donor, Bookmark & Freshness Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(LightGreenBg),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!donation.imageUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = donation.imageUrl,
                                contentDescription = donation.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(Icons.Default.Restaurant, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(26.dp))
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = donation.title.ifBlank { "Surplus Food Rescue" },
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryText,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Donor: ${donation.donorName.ifBlank { "Verified Donor" }}",
                            fontSize = 12.sp,
                            color = SecondaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = {
                            isBookmarked = !isBookmarked
                            Toast.makeText(context, if (isBookmarked) "Saved to bookmarks" else "Removed from bookmarks", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = "Bookmark",
                            tint = if (isBookmarked) EmeraldGreen else SecondaryText,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    if (isAnalyzing) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            CircularProgressIndicator(
                                color = DarkGreen,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "Analyzing...",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = DarkGreen
                            )
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = LightGreenBg
                        ) {
                            Text(
                                text = donation.aiConfidence ?: "",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = DarkGreen,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = GrayBorder.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(12.dp))

            // Row 2: Quantity, Distance, Pickup Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ShoppingBag, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("${donation.quantity} Meals", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = DarkGreen)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = SecondaryText, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(donation.location.ifBlank { "0.8 km" }, fontSize = 12.sp, color = SecondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Timer, contentDescription = null, tint = SecondaryText, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(donation.pickupTime.ifBlank { "Immediate" }, fontSize = 12.sp, color = SecondaryText)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Row 3: Action Buttons in equal spaced row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Call Donor
                IconButton(
                    onClick = {
                        val phoneNum = donation.volunteerPhone.ifBlank { "1800-FOOD-AI" }
                        val dialIntent = android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:$phoneNum"))
                        context.startActivity(dialIntent)
                    },
                    modifier = Modifier
                        .size(38.dp)
                        .background(LightGreenBg, CircleShape)
                ) {
                    Icon(Icons.Default.Phone, contentDescription = "Call Donor", tint = EmeraldGreen, modifier = Modifier.size(18.dp))
                }

                // Navigate
                IconButton(
                    onClick = {
                        val lat = if (donation.latitude != 0.0) donation.latitude else 12.9716
                        val lng = if (donation.longitude != 0.0) donation.longitude else 77.5946
                        val mapIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("geo:$lat,$lng?q=$lat,$lng(${android.net.Uri.encode(donation.title)})"))
                        mapIntent.setPackage("com.google.android.apps.maps")
                        try {
                            context.startActivity(mapIntent)
                        } catch (e: Exception) {
                            val webMapIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://maps.google.com/?q=$lat,$lng"))
                            context.startActivity(webMapIntent)
                        }
                    },
                    modifier = Modifier
                        .size(38.dp)
                        .background(Color(0xFFDBEAFE), CircleShape)
                ) {
                    Icon(Icons.Default.Directions, contentDescription = "Navigate", tint = Color(0xFF2563EB), modifier = Modifier.size(18.dp))
                }

                // Share
                IconButton(
                    onClick = {
                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_SUBJECT, "Surplus Food Rescue: ${donation.title}")
                            putExtra(android.content.Intent.EXTRA_TEXT, "Surplus Food Rescue Opportunity!\nTitle: ${donation.title}\nPortions: ${donation.quantity} Meals\nLocation: ${donation.location}\nShared via FoodShareAI App.")
                        }
                        context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Food Donation"))
                    },
                    modifier = Modifier
                        .size(38.dp)
                        .background(Color(0xFFF3E8FF), CircleShape)
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share", tint = Color(0xFF9333EA), modifier = Modifier.size(18.dp))
                }

                // Details Button
                OutlinedButton(
                    onClick = onViewDetails,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, GrayBorder),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Text("Details", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
                }

                // Claim Now Button
                Button(
                    onClick = onClaimClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Text("Claim Now", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PureWhite)
                }
            }
        }
    }
}
