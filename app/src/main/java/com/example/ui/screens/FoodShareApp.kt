package com.example.ui.screens

import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.DonationEntity
import com.example.ui.theme.*
import com.example.ui.viewmodel.FoodShareViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodShareApp(viewModel: FoodShareViewModel = viewModel()) {
    val context = LocalContext.current
    var showSplash by remember { mutableStateOf(true) }
    val currentUser by viewModel.currentUser.collectAsState()
    var authScreenState by remember { mutableStateOf("login") }

    var currentMainTab by remember { mutableStateOf("Home") }
    var showNotificationsPanel by remember { mutableStateOf(false) }
    var selectedDonationForTracking by remember { mutableStateOf<DonationEntity?>(null) }

    val notificationsList by viewModel.notifications.collectAsState()
    val rawUnreadCount = notificationsList.count { !it.isRead }

    val currentRole = remember(currentUser?.role) {
        (currentUser?.role ?: "donor").lowercase().trim()
    }

    val handleLogout: () -> Unit = {
        viewModel.logout { isSuccess ->
            showNotificationsPanel = false
            selectedDonationForTracking = null
            currentMainTab = "Home"
            authScreenState = "login"
            if (isSuccess) {
                Toast.makeText(context, "Logged out successfully.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Logged out. Server will sync later.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Intercept system Back press
    BackHandler(enabled = selectedDonationForTracking != null) {
        selectedDonationForTracking = null
    }

    BackHandler(enabled = showNotificationsPanel && selectedDonationForTracking == null) {
        showNotificationsPanel = false
    }

    if (showSplash) {
        SplashScreen(onTimeout = { showSplash = false })
        return
    }

    // ROOT UNAUTHENTICATED ROUTE
    if (currentUser == null) {
        when (authScreenState) {
            "login" -> LoginScreen(viewModel, { authScreenState = "register" }, { authScreenState = "admin_login" }, { authScreenState = "reset_password" }, { viewModel.clearMessages() })
            "register" -> RegisterScreen(viewModel, { authScreenState = "login" }, { viewModel.clearMessages() })
            "admin_login" -> AdminLoginScreen(viewModel, { authScreenState = "login" }, { viewModel.clearMessages() })
            "reset_password" -> ResetPasswordScreen(viewModel, { authScreenState = "login" }, {})
        }
        return
    }

    if (selectedDonationForTracking != null) {
        DonationTrackingScreen(
            donation = selectedDonationForTracking!!,
            viewModel = viewModel,
            onBackClick = { selectedDonationForTracking = null }
        )
        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = PureWhite,
        topBar = {
            if (currentRole != "ngo") {
                TopAppBar(
                    modifier = Modifier.padding(horizontal = 4.dp),
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start,
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(EmeraldGreen.copy(alpha = 0.12f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Restaurant,
                                    contentDescription = "FoodShareAI Logo",
                                    tint = EmeraldGreen,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "FoodShareAI",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryText
                                )
                                Text(
                                    text = when (currentRole) {
                                        "admin" -> "Admin Portal"
                                        else -> "Donor Portal"
                                    },
                                    fontSize = 10.sp,
                                    color = EmeraldGreen,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showNotificationsPanel = !showNotificationsPanel },
                            modifier = Modifier
                                .padding(end = 2.dp)
                                .size(40.dp)
                        ) {
                            BadgedBox(
                                badge = {
                                    if (rawUnreadCount > 0) {
                                        Badge(
                                            containerColor = RubyRed,
                                            contentColor = PureWhite,
                                            modifier = Modifier.scale(0.75f)
                                        ) {
                                            Text(
                                                text = if (rawUnreadCount > 99) "99+" else "$rawUnreadCount",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 9.sp
                                            )
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Notifications,
                                    contentDescription = "Notifications",
                                    tint = PrimaryText,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                showNotificationsPanel = false
                                currentMainTab = "Profile"
                            },
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(40.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(EmeraldGreen.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "Profile Avatar",
                                    tint = EmeraldGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = PureWhite,
                        scrolledContainerColor = PureWhite
                    )
                )
            }
        },
        bottomBar = {
            val tabs = when (currentRole) {
                "ngo" -> listOf(
                    Triple("Home", Icons.Default.Home, "home"),
                    Triple("Claim Food", Icons.Default.Restaurant, "claim_food"),
                    Triple("Impact", Icons.Default.AutoAwesome, "impact"),
                    Triple("Profile", Icons.Default.Person, "profile")
                )
                "admin" -> listOf(
                    Triple("Home", Icons.Default.AdminPanelSettings, "home"),
                    Triple("Users", Icons.Default.People, "users"),
                    Triple("Impact", Icons.Default.AutoAwesome, "impact"),
                    Triple("Profile", Icons.Default.Person, "profile")
                )
                else -> listOf( // "donor"
                    Triple("Home", Icons.Default.Home, "home"),
                    Triple("Donate", Icons.Default.AddCircle, "donate"),
                    Triple("Impact", Icons.Default.AutoAwesome, "impact"),
                    Triple("Profile", Icons.Default.Person, "profile")
                )
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, top = 0.dp, end = 12.dp, bottom = 12.dp)
                    .navigationBarsPadding()
                    .height(70.dp)
                    .shadow(10.dp, RoundedCornerShape(20.dp), clip = false),
                shape = RoundedCornerShape(20.dp),
                color = PureWhite,
                border = BorderStroke(1.dp, Color(0xFFF1F5F9))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEach { (label, icon, route) ->
                        val isSelected = currentMainTab == label
                        val animPillBg by animateColorAsState(
                            targetValue = if (isSelected) Color(0xFFDFF7EE) else Color.Transparent,
                            animationSpec = tween(200),
                            label = "pillBgAnim"
                        )
                        val animIconTint by animateColorAsState(
                            targetValue = if (isSelected) EmeraldGreen else SecondaryText,
                            animationSpec = tween(200),
                            label = "iconTintAnim"
                        )
                        val animLabelColor by animateColorAsState(
                            targetValue = if (isSelected) EmeraldGreen else SecondaryText,
                            animationSpec = tween(200),
                            label = "labelColorAnim"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    showNotificationsPanel = false
                                    currentMainTab = label
                                    Log.d("AuthNavigation", "User selected tab '$label' for role '$currentRole'")
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(54.dp)
                                        .height(38.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(animPillBg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = label,
                                        tint = animIconTint,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = animLabelColor,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Crossfade(
                targetState = currentMainTab,
                label = "MainTabTransition"
            ) { tab ->
                when (tab) {
                    "Home" -> {
                        when (currentRole) {
                            "ngo" -> NgoDashboard(viewModel, { showNotificationsPanel = false; currentMainTab = "Claim Food" }, { showNotificationsPanel = true })
                            "admin" -> AdminDashboard(viewModel)
                            else -> DonorDashboard(viewModel, { showNotificationsPanel = false; currentMainTab = "Donate" }, { showNotificationsPanel = false; currentMainTab = "Impact" }, { selectedDonationForTracking = it })
                        }
                    }
                    "Donate" -> {
                        if (currentRole == "donor") {
                            CreateDonationScreen(viewModel, { showNotificationsPanel = false; currentMainTab = "Impact" })
                        } else if (currentRole == "ngo") {
                            AvailableFoodScreen(viewModel)
                        } else {
                            AdminDashboard(viewModel)
                        }
                    }
                    "Claim Food", "Available Food" -> {
                        if (currentRole == "ngo") {
                            AvailableFoodScreen(viewModel)
                        } else {
                            DonorDashboard(viewModel, { currentMainTab = "Donate" }, { currentMainTab = "Impact" }, { selectedDonationForTracking = it })
                        }
                    }
                    "Users" -> {
                        if (currentRole == "admin") {
                            AdminDashboard(viewModel)
                        } else {
                            ProfileScreen(
                                viewModel = viewModel,
                                onLogoutClick = handleLogout,
                                onHistoryClick = { currentMainTab = "Impact" },
                                onNavigateToDonate = { currentMainTab = "Donate" },
                                onNavigateToNotifications = { showNotificationsPanel = true },
                                onDonationClick = { selectedDonationForTracking = it }
                            )
                        }
                    }
                    "Impact" -> {
                        if (currentRole == "ngo") {
                            HistoryScreen(viewModel, { showNotificationsPanel = false; currentMainTab = "Claim Food" })
                        } else if (currentRole == "admin") {
                            AdminDashboard(viewModel)
                        } else {
                            HistoryScreen(viewModel, { showNotificationsPanel = false; currentMainTab = "Donate" })
                        }
                    }
                    "Profile" -> ProfileScreen(
                        viewModel = viewModel,
                        onLogoutClick = handleLogout,
                        onHistoryClick = { showNotificationsPanel = false; currentMainTab = "Impact" },
                        onNavigateToDonate = { showNotificationsPanel = false; currentMainTab = "Donate" },
                        onNavigateToNotifications = { showNotificationsPanel = true },
                        onDonationClick = { selectedDonationForTracking = it }
                    )
                    else -> {
                        when (currentRole) {
                            "ngo" -> NgoDashboard(viewModel, { showNotificationsPanel = false; currentMainTab = "Claim Food" })
                            "admin" -> AdminDashboard(viewModel)
                            else -> DonorDashboard(viewModel, { showNotificationsPanel = false; currentMainTab = "Donate" }, { showNotificationsPanel = false; currentMainTab = "Impact" }, { selectedDonationForTracking = it })
                        }
                    }
                }
            }

            if (showNotificationsPanel) {
                ModalBottomSheet(
                    onDismissRequest = { showNotificationsPanel = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = PureWhite,
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                ) {
                    NotificationScreen(
                        viewModel = viewModel,
                        onDismiss = { showNotificationsPanel = false },
                        onNavigateToDonate = { currentMainTab = if (currentRole == "ngo") "Claim Food" else "Donate" },
                        onNavigateToImpact = { currentMainTab = "Impact" }
                    )
                }
            }
        }
    }
}
