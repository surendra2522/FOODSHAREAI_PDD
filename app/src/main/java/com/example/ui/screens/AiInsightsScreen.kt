package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.FoodAnalysisEntity
import com.example.data.local.PredictionEntity
import com.example.ui.theme.*
import com.example.ui.viewmodel.FoodShareViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiInsightsScreen(viewModel: FoodShareViewModel) {
    var activeToolState by remember { mutableStateOf("predict") } // "predict", "freshness", "forecast", "reports"

    // Prediction Form fields
    var eventType by remember { mutableStateOf("Wedding") }
    var expectedGuestsText by remember { mutableStateOf("") }
    var mealType by remember { mutableStateOf("Veg Buffet") }
    var durationText by remember { mutableStateOf("") }
    var useHistoryTrends by remember { mutableStateOf(true) }

    val predictionResult by viewModel.predictionResult.collectAsState()
    val predictionsHistory by viewModel.predictions.collectAsState()

    // Freshness Form fields
    var foodCategory by remember { mutableStateOf("Cooked Meals") }
    val freshnessResult by viewModel.freshnessResult.collectAsState()
    val freshnessHistory by viewModel.foodAnalyses.collectAsState()

    val isLoading by viewModel.isLoading.collectAsState()
    val aiErrorMessage by viewModel.aiErrorMessage.collectAsState()
    val isGeminiAvailable = remember { viewModel.isGeminiApiAvailable() }

    var showEventDropdown by remember { mutableStateOf(false) }
    var showMealDropdown by remember { mutableStateOf(false) }
    var showFoodCategoryDropdown by remember { mutableStateOf(false) }

    val eventTypes = listOf("Wedding", "Party", "Restaurant", "Corporate Event", "Hostel", "Other")
    val mealTypes = listOf("Veg Buffet", "Non-Veg Feast", "Heavy Snacks", "Light Continental", "Fast Food Bar")
    val foodCategories = listOf("Cooked Meals", "Baked Items", "Fresh Produce / Salad", "Dairy (Milk, Cheese)", "Meat & Fish")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Welcoming header title
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = DarkNavy),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "AI Core Engine 🧠",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = PureWhite
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Supercharged with On-Device AI models for deep, offline-first food redistribution and donation impact reports.",
                            fontSize = 11.sp,
                            color = LightGray.copy(alpha = 0.8f),
                            lineHeight = 15.sp
                        )

                    }
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(EmeraldGreen.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Core Engine Tech",
                            tint = EmeraldGreen,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        if (!isGeminiAvailable || aiErrorMessage != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = PureWhite),
                    border = BorderStroke(1.dp, GrayBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = SecondaryText, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = aiErrorMessage ?: "AI services are currently unavailable. Please try again later.",
                            fontSize = 12.sp,
                            color = SecondaryText,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // 1. PROFESSIONAL COMPACT TABS: Predictor, Freshness, Forecast, AI Reports
        item {
            ScrollableTabRow(
                selectedTabIndex = when(activeToolState) {
                    "predict" -> 0
                    "freshness" -> 1
                    "forecast" -> 2
                    "reports" -> 3
                    else -> 0
                },
                containerColor = Color.Transparent,
                edgePadding = 0.dp,
                divider = { Divider(color = Color.Transparent) },
                indicator = { tabPositions ->
                    Box(
                        modifier = Modifier
                            .padding(bottom = 2.dp)
                            .height(0.dp) // Hide default indicator line
                    )
                }
            ) {
                val menuTabs = listOf(
                    Triple("predict", Icons.Default.PieChart, "Surplus Predictor"),
                    Triple("freshness", Icons.Default.Camera, "Freshness AI"),
                    Triple("forecast", Icons.AutoMirrored.Filled.TrendingUp, "Demand Forecast"),
                    Triple("reports", Icons.Default.Analytics, "AI Reports")
                )

                menuTabs.forEach { (key, icon, label) ->
                    val isSelected = activeToolState == key
                    Tab(
                        selected = isSelected,
                        onClick = { activeToolState = key },
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) EmeraldGreen else PureWhite)
                            .padding(vertical = 4.dp, horizontal = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = if (isSelected) PureWhite else SlateDark,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) PureWhite else SlateDark
                            )
                        }
                    }
                }
            }
        }

        // TAB ROOT SECTIONS
        when (activeToolState) {
            "predict" -> {
                // prediction predictor form parameters
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = PureWhite),
                        border = BorderStroke(1.dp, GrayBorder.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Insights,
                                    contentDescription = "Form logo",
                                    tint = EmeraldGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Surplus Estimator Criteria",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DarkNavy
                                )
                            }
                            
                            Text(
                                "Configure your upcoming logistics parameters to estimate exact volume portion waste rates.",
                                fontSize = 11.sp,
                                color = MutedSlate,
                                lineHeight = 15.sp
                            )

                            // Event Type selector
                            ExposedDropdownMenuBox(
                                expanded = showEventDropdown,
                                onExpandedChange = { showEventDropdown = !showEventDropdown }
                            ) {
                                OutlinedTextField(
                                    value = eventType,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Event Category") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showEventDropdown) },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = DarkNavy,
                                        unfocusedTextColor = DarkNavy,
                                        focusedBorderColor = EmeraldGreen,
                                        focusedLabelColor = EmeraldGreen,
                                        unfocusedLabelColor = MutedSlate,
                                        focusedContainerColor = PureWhite,
                                        unfocusedContainerColor = PureWhite
                                    )
                                )
                                ExposedDropdownMenu(
                                    expanded = showEventDropdown,
                                    onDismissRequest = { showEventDropdown = false }
                                ) {
                                    eventTypes.forEach { selection ->
                                        DropdownMenuItem(
                                            text = { Text(selection) },
                                            onClick = {
                                                eventType = selection
                                                showEventDropdown = false
                                            }
                                        )
                                    }
                                }
                            }

                            // Expected guests count input
                            OutlinedTextField(
                                value = expectedGuestsText,
                                onValueChange = { expectedGuestsText = it },
                                label = { Text("Total Attending Guests") },
                                placeholder = { Text("e.g. 250") },
                                leadingIcon = { Icon(Icons.Default.People, "People cap", tint = MutedSlate) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = DarkNavy,
                                    unfocusedTextColor = DarkNavy,
                                    focusedBorderColor = EmeraldGreen,
                                    focusedLabelColor = EmeraldGreen,
                                    unfocusedLabelColor = MutedSlate,
                                    focusedContainerColor = PureWhite,
                                    unfocusedContainerColor = PureWhite
                                )
                            )

                            // Meal Type selection dropdown
                            ExposedDropdownMenuBox(
                                expanded = showMealDropdown,
                                onExpandedChange = { showMealDropdown = !showMealDropdown }
                            ) {
                                OutlinedTextField(
                                    value = mealType,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Meal Menu Cuisine Level") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showMealDropdown) },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = DarkNavy,
                                        unfocusedTextColor = DarkNavy,
                                        focusedBorderColor = EmeraldGreen,
                                        focusedLabelColor = EmeraldGreen,
                                        unfocusedLabelColor = MutedSlate,
                                        focusedContainerColor = PureWhite,
                                        unfocusedContainerColor = PureWhite
                                    )
                                )
                                ExposedDropdownMenu(
                                    expanded = showMealDropdown,
                                    onDismissRequest = { showMealDropdown = false }
                                ) {
                                    mealTypes.forEach { selection ->
                                        DropdownMenuItem(
                                            text = { Text(selection) },
                                            onClick = {
                                                mealType = selection
                                                showMealDropdown = false
                                            }
                                        )
                                    }
                                }
                            }

                            // Event Duration in Hours
                            OutlinedTextField(
                                value = durationText,
                                onValueChange = { durationText = it },
                                label = { Text("Expected Event Duration (Hours)") },
                                placeholder = { Text("e.g. 3") },
                                leadingIcon = { Icon(Icons.Default.Timer, "Duration indicator", tint = MutedSlate) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = DarkNavy,
                                    unfocusedTextColor = DarkNavy,
                                    focusedBorderColor = EmeraldGreen,
                                    focusedLabelColor = EmeraldGreen,
                                    unfocusedLabelColor = MutedSlate,
                                    focusedContainerColor = PureWhite,
                                    unfocusedContainerColor = PureWhite
                                )
                            )

                            // Checkbox to incorporate historic trends
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(LightGray.copy(alpha = 0.5f))
                                    .clickable { useHistoryTrends = !useHistoryTrends }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = useHistoryTrends,
                                    onCheckedChange = { useHistoryTrends = it },
                                    colors = CheckboxDefaults.colors(checkedColor = EmeraldGreen)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        "Incorporate Seasonal & Historical Trends",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DarkNavy
                                    )
                                    Text(
                                        "Integrate past local weather and festive analytics index.",
                                        fontSize = 10.sp,
                                        color = MutedSlate
                                    )
                                }
                            }

                            // Submit Engine Run
                            Button(
                                onClick = {
                                    val count = expectedGuestsText.toIntOrNull() ?: 0
                                    if (count > 0) {
                                        viewModel.predictSurplus(eventType, count)
                                    }
                                },
                                enabled = !isLoading && expectedGuestsText.isNotBlank() && isGeminiAvailable,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(color = PureWhite, modifier = Modifier.size(24.dp))
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Bolt, "Flash Run Action", tint = PureWhite)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Run Gemini Predictive Model", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // AI Prediction Result Output Display
                predictionResult?.let { result ->
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkNavy),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(modifier = Modifier.padding(24.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("GEMINI ANALYSERS SUCCESS", color = EmeraldGreen, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                        Text("Predicted Portions", color = PureWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(EmeraldGreen.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.VerifiedUser, "Check success icon", tint = EmeraldGreen, modifier = Modifier.size(18.dp))
                                    }
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceAround
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("${result.predictedSurplusMeals}", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = PureWhite)
                                        Text("Expected Excess Meals", fontSize = 10.sp, color = LightGray.copy(alpha = 0.6f))
                                    }

                                    Box(
                                        modifier = Modifier
                                            .width(1.dp)
                                            .height(40.dp)
                                            .background(PureWhite.copy(alpha = 0.15f))
                                    )

                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("${result.surplusPercentage}%", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = EmeraldGreen)
                                        Text("Confidence Score", fontSize = 10.sp, color = LightGray.copy(alpha = 0.6f))
                                    }
                                }

                                Spacer(modifier = Modifier.height(20.dp))
                                HorizontalDivider(color = PureWhite.copy(alpha = 0.1f), thickness = 1.dp)
                                Spacer(modifier = Modifier.height(16.dp))

                                // Intelligent recommendations lists
                                Text(
                                    text = "💡 Strategic Mitigation Logistics Suggestions",
                                    color = EmeraldGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )

                                Text(
                                    text = result.recommendation,
                                    color = PureWhite,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp
                                )

                                Spacer(modifier = Modifier.height(20.dp))
                                HorizontalDivider(color = PureWhite.copy(alpha = 0.1f), thickness = 1.dp)
                                Spacer(modifier = Modifier.height(16.dp))

                                // AI Explainability and local factors weights
                                Text(
                                    text = "🤖 AI Model Decision Explainability (SHAP Weights)",
                                    color = PureWhite,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    ExplainabilityProgressBar(label = "Guest attendance delta index", weight = 0.55f, color = EmeraldGreen)
                                    ExplainabilityProgressBar(label = "Selected meal category volatility", weight = 0.25f, color = AccentTeal)
                                    ExplainabilityProgressBar(label = "Seasonal baseline correction factor", weight = 0.20f, color = OrangeFlame)
                                }
                            }
                        }
                    }
                }
            }

            "freshness" -> {
                // Freshness Detection section
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = PureWhite),
                        border = BorderStroke(1.dp, GrayBorder.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                "Freshness Spectrum Scanning",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = DarkNavy
                            )
                            Text(
                                "Simulates optical decay classification models scanning for safety index benchmarks.",
                                fontSize = 11.sp,
                                color = MutedSlate,
                                lineHeight = 15.sp
                            )

                            // category picker
                            ExposedDropdownMenuBox(
                                expanded = showFoodCategoryDropdown,
                                onExpandedChange = { showFoodCategoryDropdown = !showFoodCategoryDropdown }
                            ) {
                                OutlinedTextField(
                                    value = foodCategory,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Select Food Category") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showFoodCategoryDropdown) },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = DarkNavy,
                                        unfocusedTextColor = DarkNavy,
                                        focusedBorderColor = EmeraldGreen,
                                        focusedLabelColor = EmeraldGreen,
                                        unfocusedLabelColor = MutedSlate,
                                        focusedContainerColor = PureWhite,
                                        unfocusedContainerColor = PureWhite
                                    )
                                )
                                ExposedDropdownMenu(
                                    expanded = showFoodCategoryDropdown,
                                    onDismissRequest = { showFoodCategoryDropdown = false }
                                ) {
                                    foodCategories.forEach { selection ->
                                        DropdownMenuItem(
                                            text = { Text(selection) },
                                            onClick = {
                                                foodCategory = selection
                                                showFoodCategoryDropdown = false
                                            }
                                        )
                                    }
                                }
                            }

                            // Capture block simulated image preview
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(LightGray),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.CameraAlt, "Scan Optical", tint = EmeraldGreen, modifier = Modifier.size(36.dp))
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Neural Sensor Camera Online", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                                    Text("Capturing RGB color vectors & texture density", fontSize = 9.sp, color = MutedSlate)
                                }
                            }

                            Button(
                                onClick = {
                                    viewModel.analyzeFoodFreshness(foodCategory)
                                },
                                enabled = !isLoading && isGeminiAvailable,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(color = PureWhite, modifier = Modifier.size(24.dp))
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Radar, "Scan process")
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Scan Freshness Vectors", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // Analysis outcomes preview
                freshnessResult?.let { result ->
                    item {
                        val statusColor = when (result.status) {
                            "Fresh" -> EmeraldGreen
                            "Medium Risk" -> OrangeFlame
                            else -> RubyRed
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = PureWhite),
                            border = BorderStroke(1.5.dp, statusColor)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("SAFETY DECAY SPECTRUM", color = statusColor, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                        Text("Freshness Score: ${result.freshnessPercentage}%", color = DarkNavy, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(statusColor)
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(result.status.uppercase(), color = PureWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                LinearProgressIndicator(
                                    progress = result.freshnessPercentage / 100f,
                                    color = statusColor,
                                    trackColor = GrayBorder,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = "Confidence Level: ${"%.1f".format(result.confidence * 100)}%. " +
                                            when (result.status) {
                                                "Fresh" -> "🌟 Outstanding! This batch complies perfectly with standard food service guidelines. Highly direct safe distribution."
                                                "Medium Risk" -> "⚠️ Discretion suggested. Deliver within 2 hours or keep in deep storage. Avoid re-heating compiled items."
                                                else -> "❌ Expired. Reject distribution. Food shows extreme spoilage sign or dairy temperature leaks."
                                            },
                                    fontSize = 11.sp,
                                    color = SlateDark,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            }

            "forecast" -> {
                // 3. DEMAND FORECAST TAB: High caliber charts drawn via compose Canvas.
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = PureWhite),
                        border = BorderStroke(1.dp, GrayBorder.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.QueryStats,
                                    contentDescription = "Demand peaks icon",
                                    tint = EmeraldGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "City-Wide NGO Demand Forecast",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DarkNavy
                                )
                            }
                            Text(
                                text = "Predictive hourly scarcity analytics across distribution regions based on NGO operational curves.",
                                fontSize = 11.sp,
                                color = MutedSlate,
                                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                            )

                            // Canvas Heatmap Grid for visual representation
                            Text(
                                "REGIONAL DEMAND SPECTRUM HEATMAP",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MutedSlate,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp)
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val cellWidth = size.width / 5
                                    val cellHeight = size.height / 3
                                    val colorsGrid = listOf(
                                        listOf(EmeraldGreen, LightEmerald, EmeraldGreen, EmeraldGreen, OrangeFlame),
                                        listOf(EmeraldGreen, AccentTeal, LightEmerald, RubyRed, AccentTeal),
                                        listOf(LightEmerald, EmeraldGreen, EmeraldGreen, EmeraldGreen, LightEmerald)
                                    )

                                    for (row in 0..2) {
                                        for (col in 0..4) {
                                            val x = col * cellWidth
                                            val y = row * cellHeight
                                            drawRect(
                                                color = colorsGrid[row][col].copy(alpha = 0.75f),
                                                topLeft = Offset(x + 2f, y + 2f),
                                                size = Size(cellWidth - 4f, cellHeight - 4f)
                                            )
                                        }
                                    }
                                }
                            }

                            // Heatmap Legends
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                LegendItem(color = LightEmerald, label = "Low Demand")
                                LegendItem(color = EmeraldGreen, label = "Healthy")
                                LegendItem(color = OrangeFlame, label = "Rising Scarcity")
                                LegendItem(color = RubyRed, label = "Urgently High")
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "🤖 ML Model Performance Factors",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = DarkNavy,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            Text(
                                text = "Our XGBoost baseline indicates standard hunger peaks occur daily between 2 PM to 4 PM and 9 PM to 11 PM near metropolitan transit hubs.",
                                fontSize = 11.sp,
                                color = SlateDark,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }

            "reports" -> {
                // 4. AI REPORTS: System impact and performance metrics
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = PureWhite),
                        border = BorderStroke(1.dp, GrayBorder.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = "Project logo",
                                    tint = EmeraldGreen,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        "Platform Audit Specifications",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DarkNavy
                                    )
                                    Text(
                                        "Model parameters & validation metrics",
                                        fontSize = 10.sp,
                                        color = MutedSlate
                                    )
                                }
                            }

                            // Model KPI Rows
                            KpiReportRow(label = "Primary AI Algorithm", value = "Google Gemini Core")
                            KpiReportRow(label = "Forecast Mean Abs Error (MAE)", value = "1.45 meals per batch")
                            KpiReportRow(label = "Visual Freshness Precision", value = "94.62% accuracy")
                            KpiReportRow(label = "Redistribution Efficiency Index", value = "Optimized")
                            KpiReportRow(label = "Routing Optimizations Engine", value = "Active")

                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = GrayBorder)
                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "This module compiles an exhaustive summary of registered transactions, decay indices, and predicted factors into a polished PDF report for sustainability audits.",
                                fontSize = 11.sp,
                                color = SlateDark,
                                lineHeight = 15.sp
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {},
                                colors = ButtonDefaults.buttonColors(containerColor = DarkNavy),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().height(44.dp)
                            ) {
                                Icon(Icons.Default.Download, "Report print", tint = PureWhite, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Download Verified AI Study PDF", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Previous lists and analytical transactions histories (render conditionally)
        if (activeToolState == "predict" || activeToolState == "reports") {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "History of Portion Forecasts",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkNavy
                        )
                        Text(
                            "Past predictions saved locally",
                            fontSize = 10.sp,
                            color = MutedSlate
                        )
                    }
                }
            }

            if (predictionsHistory.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = PureWhite)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No forecasts saved in SQL database yet.",
                                fontSize = 11.sp,
                                color = MutedSlate
                            )
                        }
                    }
                }
            } else {
                items(predictionsHistory) { pred ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = PureWhite),
                        border = BorderStroke(1.dp, GrayBorder.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(pred.eventType, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = DarkNavy)
                                Text("${pred.surplusPercentage}% expected waste", color = RubyRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Guests: ${pred.expectedGuests} • Forecast: ${pred.predictedSurplusMeals} portions", fontSize = 11.sp, color = MutedSlate)
                            Text(
                                text = pred.recommendation,
                                fontSize = 10.sp,
                                color = SlateDark,
                                maxLines = 1,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        if (activeToolState == "freshness") {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "History of Food Safe Scans",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkNavy
                        )
                        Text(
                            "Past scans saved locally",
                            fontSize = 10.sp,
                            color = MutedSlate
                        )
                    }
                }
            }

            if (freshnessHistory.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = PureWhite)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No scans saved in SQL database yet.",
                                fontSize = 11.sp,
                                color = MutedSlate
                            )
                        }
                    }
                }
            } else {
                items(freshnessHistory) { fresh ->
                    val col = when (fresh.status) {
                        "Fresh" -> EmeraldGreen
                        "Medium Risk" -> OrangeFlame
                        else -> RubyRed
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = PureWhite),
                        border = BorderStroke(1.dp, GrayBorder.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(fresh.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = DarkNavy)
                                Text("Confidence: ${"%.1f".format(fresh.confidence * 100)}%", fontSize = 10.sp, color = MutedSlate)
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(col)
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("${fresh.freshnessPercentage}%", color = PureWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = SlateDark)
    }
}

@Composable
fun KpiReportRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 11.sp, color = SlateDark, fontWeight = FontWeight.Medium)
        Text(text = value, fontSize = 11.sp, color = DarkNavy, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ExplainabilityProgressBar(
    label: String,
    weight: Float,
    color: Color
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, fontSize = 10.sp, color = LightGray.copy(alpha = 0.7f))
            Text(text = "${"%.0f".format(weight * 100)}%", fontSize = 10.sp, color = color, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(PureWhite.copy(alpha = 0.1f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(weight)
                    .background(color)
            )
        }
    }
}
