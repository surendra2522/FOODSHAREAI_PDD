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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.NotificationEntity
import com.example.ui.theme.*
import com.example.ui.viewmodel.FoodShareViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationScreen(
    viewModel: FoodShareViewModel,
    onDismiss: () -> Unit = {},
    onNavigateToDonate: () -> Unit = {},
    onNavigateToImpact: () -> Unit = {},
    onNavigateToClaim: () -> Unit = {}
) {
    val context = LocalContext.current
    val notificationsList by viewModel.notifications.collectAsState()
    val unreadCount by viewModel.unreadNotificationsCount.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var selectedNotificationForDetails by remember { mutableStateOf<NotificationEntity?>(null) }
    var itemToDeleteConfirm by remember { mutableStateOf<NotificationEntity?>(null) }
    var notificationSearchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }

    var showMenu by remember { mutableStateOf(false) }
    var showClearAllConfirmDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val categories = listOf("All", "Donations", "Pickups", "Deliveries", "AI Alerts", "System")

    // Filter, deduplicate, and sort newest first
    val categoryFilteredList = remember(notificationsList, selectedCategory, notificationSearchQuery) {
        var list = notificationsList
            .distinctBy { it.id.ifBlank { "${it.title}_${it.timestamp}" } }
            .sortedByDescending { it.timestamp }

        if (selectedCategory != "All") {
            list = list.filter { notif ->
                val type = notif.type.lowercase(Locale.getDefault())
                val title = notif.title.lowercase(Locale.getDefault())
                when (selectedCategory) {
                    "Donations" -> type.contains("donation") || type.contains("accept") || type.contains("cancel") || type.contains("expire") || type.contains("detail")
                    "Pickups" -> type.contains("pickup") || type.contains("assign") || type.contains("collect") || type.contains("ready")
                    "Deliveries" -> type.contains("deliver") || type.contains("transit") || type.contains("complete")
                    "AI Alerts" -> type.contains("ai") || title.contains("ai") || type.contains("warning") || type.contains("alert")
                    "System" -> type.contains("user") || type.contains("system") || type.contains("general") || type.contains("report")
                    else -> true
                }
            }
        }
        if (notificationSearchQuery.isNotBlank()) {
            list = list.filter {
                it.title.contains(notificationSearchQuery, ignoreCase = true) ||
                it.message.contains(notificationSearchQuery, ignoreCase = true) ||
                it.type.contains(notificationSearchQuery, ignoreCase = true)
            }
        }
        list
    }

    // Group Notifications by Today, Yesterday, Earlier
    val groupedNotifications = remember(categoryFilteredList) {
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val yesterday = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val todayList = mutableListOf<NotificationEntity>()
        val yesterdayList = mutableListOf<NotificationEntity>()
        val earlierList = mutableListOf<NotificationEntity>()

        categoryFilteredList.forEach { notif ->
            when {
                notif.timestamp >= today -> todayList.add(notif)
                notif.timestamp >= yesterday -> yesterdayList.add(notif)
                else -> earlierList.add(notif)
            }
        }

        listOf(
            "Today" to todayList,
            "Yesterday" to yesterdayList,
            "Earlier" to earlierList
        ).filter { it.second.isNotEmpty() }
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ==========================================
            // HEADER (Back, Title, Badge Count, Three-Dot Menu)
            // ==========================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFFF8FAFC), CircleShape)
                            .border(1.dp, Color(0xFFE5E7EB), CircleShape)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = PrimaryText, modifier = Modifier.size(18.dp))
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = "Notifications",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryText
                    )

                    if (unreadCount > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "($unreadCount)",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2563EB)
                        )
                    }
                }

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFFF8FAFC), CircleShape)
                            .border(1.dp, Color(0xFFE5E7EB), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Menu Options",
                            tint = PrimaryText,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier
                            .background(PureWhite)
                            .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(12.dp))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Clear All Notifications", color = RubyRed, fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = RubyRed, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showMenu = false
                                showClearAllConfirmDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Mark All as Read", color = PrimaryText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Default.DoneAll, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showMenu = false
                                viewModel.markAllNotificationsAsRead()
                                Toast.makeText(context, "All notifications marked as read", Toast.LENGTH_SHORT).show()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Notification Settings", color = PrimaryText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null, tint = SecondaryText, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showMenu = false
                                showSettingsDialog = true
                            }
                        )
                    }
                }
            }

            HorizontalDivider(color = Color(0xFFE5E7EB))

            // CATEGORY FILTER CHIPS
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                items(categories) { category ->
                    val isSelected = selectedCategory == category
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategory = category },
                        label = { Text(category, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldGreen,
                            selectedLabelColor = PureWhite,
                            containerColor = Color(0xFFF1F5F9),
                            labelColor = PrimaryText
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.heightIn(min = 32.dp)
                    )
                }
            }

            // SEARCH INPUT
            OutlinedTextField(
                value = notificationSearchQuery,
                onValueChange = { notificationSearchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                placeholder = { Text("Search notifications...", fontSize = 12.sp, color = SecondaryText) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = EmeraldGreen, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (notificationSearchQuery.isNotEmpty()) {
                        IconButton(onClick = { notificationSearchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = SecondaryText, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = customOutlinedTextFieldColors()
            )

            // ==========================================
            // NOTIFICATIONS GROUPED LIST OR EMPTY STATE
            // ==========================================
            if (categoryFilteredList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 220.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = PureWhite,
                        border = BorderStroke(1.dp, Color(0xFFE5E7EB))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp, horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(LightGreenBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsNone,
                                    contentDescription = null,
                                    tint = EmeraldGreen,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "🔔 No Notifications",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryText
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "You're all caught up.\nNew donation updates and rescue alerts will appear here.",
                                fontSize = 12.5.sp,
                                color = SecondaryText,
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 36.dp)
                ) {
                    groupedNotifications.forEach { (groupTitle, list) ->
                        item {
                            Text(
                                text = groupTitle.uppercase(),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = SecondaryText,
                                letterSpacing = 0.8.sp,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }

                        items(list, key = { it.id }) { notification ->
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { dismissValue ->
                                    if (dismissValue == SwipeToDismissBoxValue.EndToStart || dismissValue == SwipeToDismissBoxValue.StartToEnd) {
                                        viewModel.deleteNotification(notification.id)
                                        Toast.makeText(context, "Notification removed", Toast.LENGTH_SHORT).show()
                                        true
                                    } else false
                                }
                            )

                            SwipeToDismissBox(
                                state = dismissState,
                                backgroundContent = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(RubyRed.copy(alpha = 0.85f))
                                            .padding(horizontal = 20.dp),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = PureWhite,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            ) {
                                NotificationItemCard(
                                    notification = notification,
                                    onClick = {
                                        viewModel.markNotificationAsRead(notification.id)
                                        val t = notification.type.lowercase(Locale.getDefault())
                                        when {
                                            t.contains("created") || t.contains("claim") -> onNavigateToClaim()
                                            t.contains("accept") || t.contains("assign") || t.contains("pickup") || t.contains("transit") -> onNavigateToDonate()
                                            t.contains("deliver") || t.contains("impact") || t.contains("complete") -> onNavigateToImpact()
                                            else -> selectedNotificationForDetails = notification
                                        }
                                    },
                                    onDeleteRequest = {
                                        itemToDeleteConfirm = notification
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // CONFIRM SINGLE DELETE DIALOG
        itemToDeleteConfirm?.let { notification ->
            AlertDialog(
                onDismissRequest = { itemToDeleteConfirm = null },
                containerColor = PureWhite,
                shape = RoundedCornerShape(18.dp),
                title = {
                    Text("Delete notification?", fontWeight = FontWeight.Bold, color = PrimaryText, fontSize = 17.sp)
                },
                text = {
                    Text("Are you sure you want to delete '${notification.title}'?", fontSize = 13.sp, color = SecondaryText)
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteNotification(notification.id)
                            itemToDeleteConfirm = null
                            Toast.makeText(context, "Notification deleted", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RubyRed),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(42.dp)
                    ) {
                        Text("Delete", color = PureWhite, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { itemToDeleteConfirm = null },
                        modifier = Modifier.height(42.dp)
                    ) {
                        Text("Cancel", color = SecondaryText, fontWeight = FontWeight.Medium)
                    }
                }
            )
        }

        // CLEAR ALL CONFIRMATION DIALOG
        if (showClearAllConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showClearAllConfirmDialog = false },
                containerColor = PureWhite,
                shape = RoundedCornerShape(18.dp),
                title = {
                    Text("Clear all notifications?", fontWeight = FontWeight.Bold, color = PrimaryText, fontSize = 17.sp)
                },
                text = {
                    Text("This will permanently remove all notifications from this device.", fontSize = 13.sp, color = SecondaryText, lineHeight = 18.sp)
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.clearAllNotifications()
                            showClearAllConfirmDialog = false
                            Toast.makeText(context, "Notifications cleared", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RubyRed),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(42.dp)
                    ) {
                        Text("Clear", color = PureWhite, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showClearAllConfirmDialog = false },
                        modifier = Modifier.height(42.dp)
                    ) {
                        Text("Cancel", color = SecondaryText, fontWeight = FontWeight.Medium)
                    }
                }
            )
        }

        // NOTIFICATION SETTINGS DIALOG
        if (showSettingsDialog) {
            var pushEnabled by remember { mutableStateOf(true) }
            var donationUpdates by remember { mutableStateOf(true) }
            var rescueAlerts by remember { mutableStateOf(true) }
            var aiWarnings by remember { mutableStateOf(true) }

            AlertDialog(
                onDismissRequest = { showSettingsDialog = false },
                containerColor = PureWhite,
                shape = RoundedCornerShape(18.dp),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Settings, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Notification Settings", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = PrimaryText)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Push Notifications", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
                                Text("Receive real-time mobile push alerts", fontSize = 11.5.sp, color = SecondaryText)
                            }
                            Switch(checked = pushEnabled, onCheckedChange = { pushEnabled = it }, colors = SwitchDefaults.colors(checkedThumbColor = PureWhite, checkedTrackColor = EmeraldGreen))
                        }
                        HorizontalDivider(color = Color(0xFFE5E7EB))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Donation Updates", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
                                Text("Status updates when claimed or collected", fontSize = 11.5.sp, color = SecondaryText)
                            }
                            Switch(checked = donationUpdates, onCheckedChange = { donationUpdates = it }, colors = SwitchDefaults.colors(checkedThumbColor = PureWhite, checkedTrackColor = EmeraldGreen))
                        }
                        HorizontalDivider(color = Color(0xFFE5E7EB))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Rescue Mission Alerts", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
                                Text("Instant notifications for nearby donations", fontSize = 11.5.sp, color = SecondaryText)
                            }
                            Switch(checked = rescueAlerts, onCheckedChange = { rescueAlerts = it }, colors = SwitchDefaults.colors(checkedThumbColor = PureWhite, checkedTrackColor = EmeraldGreen))
                        }
                        HorizontalDivider(color = Color(0xFFE5E7EB))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("AI Quality Warnings", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
                                Text("Alerts when AI detects potential food decay", fontSize = 11.5.sp, color = SecondaryText)
                            }
                            Switch(checked = aiWarnings, onCheckedChange = { aiWarnings = it }, colors = SwitchDefaults.colors(checkedThumbColor = PureWhite, checkedTrackColor = EmeraldGreen))
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showSettingsDialog = false
                            Toast.makeText(context, "Notification preferences saved", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(42.dp)
                    ) {
                        Text("Save Preferences", color = PureWhite, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        // Details Modal
        selectedNotificationForDetails?.let { notification ->
            val dateStr = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()).format(Date(notification.timestamp))
            AlertDialog(
                onDismissRequest = { selectedNotificationForDetails = null },
                containerColor = PureWhite,
                shape = RoundedCornerShape(16.dp),
                title = { Text(notification.title, fontWeight = FontWeight.Bold, color = PrimaryText, fontSize = 16.5.sp) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(notification.message, fontSize = 13.sp, color = PrimaryText, lineHeight = 18.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Timestamp: $dateStr", fontSize = 11.sp, color = SecondaryText)
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { selectedNotificationForDetails = null },
                        modifier = Modifier.height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Close", color = PureWhite, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PureWhite.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = EmeraldGreen)
            }
        }
    }
}

@Composable
fun NotificationItemCard(
    notification: NotificationEntity,
    onClick: () -> Unit,
    onDeleteRequest: () -> Unit
) {
    val (icon, iconBg, iconTint) = remember(notification.type, notification.title) {
        val t = notification.type.lowercase(Locale.getDefault())
        val title = notification.title.lowercase(Locale.getDefault())
        when {
            t.contains("created") || t.contains("donation") -> Triple(Icons.Default.Restaurant, Color(0xFFF0FDF4), Color(0xFF16A34A)) // 🍱 Donation
            t.contains("pickup") || t.contains("transit") || t.contains("assign") -> Triple(Icons.Default.LocalShipping, Color(0xFFEFF6FF), Color(0xFF2563EB)) // 🚚 Pickup
            t.contains("complete") || t.contains("deliver") || t.contains("accept") -> Triple(Icons.Default.CheckCircle, Color(0xFFF0FDF4), Color(0xFF16A34A)) // ✅ Completed
            t.contains("warning") || t.contains("alert") || t.contains("ai") || title.contains("ai") || title.contains("warning") -> Triple(Icons.Default.Warning, Color(0xFFFEF3C7), Color(0xFFD97706)) // ⚠️ Warning
            t.contains("cancel") || t.contains("expire") || t.contains("error") -> Triple(Icons.Default.Error, Color(0xFFFEF2F2), Color(0xFFDC2626)) // ❌ Error
            else -> Triple(Icons.Default.Notifications, Color(0xFFF1F5F9), Color(0xFF475569)) // 🔔 General
        }
    }

    val formattedTime = remember(notification.timestamp) {
        val diffMins = (System.currentTimeMillis() - notification.timestamp) / (1000 * 60)
        when {
            diffMins < 1 -> "Just now"
            diffMins < 60 -> "$diffMins min ago"
            diffMins < 1440 -> "${diffMins / 60} hr ago"
            else -> SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()).format(Date(notification.timestamp))
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = PureWhite,
        border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
        shadowElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            // Thin blue left accent bar for unread notifications
            if (!notification.isRead) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(4.dp)
                        .background(Color(0xFF2563EB))
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Colored circular icon on left
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Title, Description, Time & Delete
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = notification.title.ifBlank { "Notification" },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        Text(
                            text = formattedTime,
                            fontSize = 11.sp,
                            color = SecondaryText,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = notification.message,
                            fontSize = 14.sp,
                            color = PrimaryText.copy(alpha = 0.8f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 19.sp,
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = onDeleteRequest,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = "Delete",
                                tint = SecondaryText.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
