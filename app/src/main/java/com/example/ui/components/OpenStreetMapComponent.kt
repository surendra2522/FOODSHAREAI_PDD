package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.data.local.DonationEntity
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.ScaleBarOverlay
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider
import java.util.Locale

data class MapMarkerItem(
    val id: String,
    val title: String,
    val category: String,
    val freshnessScore: Int,
    val quantityMeals: Int,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val status: String, // "Available", "Reserved", "Urgent", "Completed", "NGO"
    val photoUri: String? = null,
    val distanceKm: Double = 1.8,
    val pickupTime: String = "10:00 AM - 4:00 PM",
    val aiConfidence: String = "High Confidence",
    val rawDonation: DonationEntity? = null

)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InteractiveOpenStreetMap(
    modifier: Modifier = Modifier,
    initialLat: Double = 12.9716,
    initialLng: Double = 77.5946,
    markers: List<MapMarkerItem> = emptyList(),
    selectedRouteDestination: MapMarkerItem? = null,
    onMarkerClick: (MapMarkerItem) -> Unit = {},
    onMapTap: (Double, Double, String) -> Unit = { _, _, _ -> },
    showSearchHeader: Boolean = true,
    showControls: Boolean = true,
    showOfflineBanner: Boolean = false,
    enableDraggablePin: Boolean = false,
    selectedDraggableLat: Double = initialLat,
    selectedDraggableLng: Double = initialLng,
    onDraggablePinMoved: (Double, Double, String) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var searchSuggestions by remember { mutableStateOf<List<Pair<String, GeoPoint>>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var travelMode by remember { mutableStateOf("Driving") } // Driving vs Walking
    var isOfflineMode by remember { mutableStateOf(false) }

    val currentCenter = remember(initialLat, initialLng) { GeoPoint(initialLat, initialLng) }

    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
        Configuration.getInstance().osmdroidTileCache = context.cacheDir
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(15.0)
                    controller.setCenter(currentCenter)

                    // Add Scale bar overlay
                    val scaleBarOverlay = ScaleBarOverlay(this).apply {
                        setAlignBottom(true)
                        setLineWidth(2f)
                        setTextSize(24f)
                    }
                    overlays.add(scaleBarOverlay)

                    // Add Compass overlay
                    val compassOverlay = CompassOverlay(ctx, InternalCompassOrientationProvider(ctx), this)
                    compassOverlay.enableCompass()
                    overlays.add(compassOverlay)

                    // Map events overlay for click/drag
                    val mapEventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                            coroutineScope.launch(Dispatchers.IO) {
                                val address = reverseGeocode(context, p.latitude, p.longitude)
                                withContext(Dispatchers.Main) {
                                    if (enableDraggablePin) {
                                        onDraggablePinMoved(p.latitude, p.longitude, address)
                                    }
                                    onMapTap(p.latitude, p.longitude, address)
                                }
                            }
                            return true
                        }

                        override fun longPressHelper(p: GeoPoint): Boolean {
                            coroutineScope.launch(Dispatchers.IO) {
                                val address = reverseGeocode(context, p.latitude, p.longitude)
                                withContext(Dispatchers.Main) {
                                    if (enableDraggablePin) {
                                        onDraggablePinMoved(p.latitude, p.longitude, address)
                                    }
                                    onMapTap(p.latitude, p.longitude, address)
                                }
                            }
                            return true
                        }
                    })
                    overlays.add(0, mapEventsOverlay)

                    mapViewRef = this
                }
            },
            update = { mapView ->
                // Clear existing markers and route overlays
                val toRemove = mapView.overlays.filter { it is Marker || it is Polyline }
                mapView.overlays.removeAll(toRemove)

                // Add NGO Base Marker
                val ngoPoint = GeoPoint(initialLat, initialLng)
                val ngoMarker = Marker(mapView).apply {
                    position = ngoPoint
                    title = "NGO Distribution Center"
                    snippet = "FoodShare Hub"
                    icon = getMarkerDrawable(context, "NGO")
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    setOnMarkerClickListener { _, _ ->
                        onMarkerClick(
                            MapMarkerItem(
                                id = "-1",
                                title = "NGO Operations Center",
                                category = "Distribution Hub",
                                freshnessScore = 100,
                                quantityMeals = 500,
                                address = "Central Logistics Hub",
                                latitude = initialLat,
                                longitude = initialLng,
                                status = "NGO",
                                distanceKm = 0.0
                            )
                        )
                        true
                    }
                }
                mapView.overlays.add(ngoMarker)

                // Add Donation Item Markers
                markers.forEach { item ->
                    val marker = Marker(mapView).apply {
                        position = GeoPoint(item.latitude, item.longitude)
                        title = item.title
                        snippet = "${item.quantityMeals} meals • ${item.address}"
                        icon = getMarkerDrawable(context, item.status)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        setOnMarkerClickListener { _, _ ->
                            onMarkerClick(item)
                            true
                        }
                    }
                    mapView.overlays.add(marker)
                }

                // Draggable Single Location Marker if enabled
                if (enableDraggablePin) {
                    val dragPoint = GeoPoint(selectedDraggableLat, selectedDraggableLng)
                    val dragMarker = Marker(mapView).apply {
                        position = dragPoint
                        title = "Selected Pickup Spot"
                        snippet = "Drag or tap map to re-position"
                        icon = getMarkerDrawable(context, "Available")
                        isDraggable = true
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                            override fun onMarkerDrag(m: Marker?) {}
                            override fun onMarkerDragEnd(m: Marker?) {
                                m?.position?.let { p ->
                                    coroutineScope.launch(Dispatchers.IO) {
                                        val addr = reverseGeocode(context, p.latitude, p.longitude)
                                        withContext(Dispatchers.Main) {
                                            onDraggablePinMoved(p.latitude, p.longitude, addr)
                                        }
                                    }
                                }
                            }
                            override fun onMarkerDragStart(m: Marker?) {}
                        })
                    }
                    mapView.overlays.add(dragMarker)
                }

                // Draw Route Polyline if destination selected
                selectedRouteDestination?.let { dest ->
                    val destPoint = GeoPoint(dest.latitude, dest.longitude)
                    val routePolyline = Polyline(mapView).apply {
                        addPoint(ngoPoint)
                        // Add intermediate curve waypoints for realistic route feel
                        val midLat = (initialLat + dest.latitude) / 2 + 0.001
                        val midLng = (initialLng + dest.longitude) / 2 - 0.001
                        addPoint(GeoPoint(midLat, midLng))
                        addPoint(destPoint)

                        outlinePaint.color = if (travelMode == "Walking") {
                            android.graphics.Color.parseColor("#3B82F6") // Blue for walking
                        } else {
                            android.graphics.Color.parseColor("#10B981") // Green for driving
                        }
                        outlinePaint.strokeWidth = 12f
                    }
                    mapView.overlays.add(routePolyline)
                }

                mapView.invalidate()
            },
            modifier = Modifier.fillMaxSize()
        )

        // Offline Status Top Banner
        if (showOfflineBanner) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(DarkNavy.copy(alpha = 0.9f))
                    .border(1.dp, EmeraldGreen.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(EmeraldGreen)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "OpenStreetMap • Cached Offline Ready",
                        color = PureWhite,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Location Search Section Overlay
        if (showSearchHeader) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp, start = 12.dp, end = 12.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = PureWhite),
                    border = BorderStroke(1.dp, GrayBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { query ->
                                    searchQuery = query
                                    if (query.length >= 3) {
                                        isSearching = true
                                        coroutineScope.launch(Dispatchers.IO) {
                                            val results = searchNominatim(context, query)
                                            withContext(Dispatchers.Main) {
                                                searchSuggestions = results
                                                isSearching = false
                                            }
                                        }
                                    } else {
                                        searchSuggestions = emptyList()
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("map_search_field"),
                                placeholder = {
                                    Text(
                                        "Search Street, NGO, Donor or Category...",
                                        color = Color(0xFF9CA3AF),
                                        fontSize = 12.sp
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Search, "Search", tint = EmeraldGreen)
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = {
                                            searchQuery = ""
                                            searchSuggestions = emptyList()
                                        }) {
                                            Icon(Icons.Default.Close, "Clear search", tint = SecondaryText)
                                        }
                                    }
                                },
                                singleLine = true,
                                colors = customOutlinedTextFieldColors()
                            )
                        }

                        // Search suggestions list popup
                        if (searchSuggestions.isNotEmpty()) {
                            HorizontalDivider(color = Color(0xFF334155), thickness = 1.dp)
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 180.dp)
                                    .padding(vertical = 4.dp)
                            ) {
                                items(searchSuggestions.size) { idx ->
                                    val (name, point) = searchSuggestions[idx]
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                searchQuery = name
                                                searchSuggestions = emptyList()
                                                mapViewRef?.controller?.animateTo(point)
                                                mapViewRef?.controller?.setZoom(16.0)
                                                onMapTap(point.latitude, point.longitude, name)
                                            }
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Place,
                                            null,
                                            tint = OrangeFlame,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = name,
                                            color = PureWhite,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Map Control Floating Buttons (Zoom +, Zoom -, My Location, Travel Mode)
        if (showControls) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 24.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Driving / Walking Route Switcher button
                if (selectedRouteDestination != null) {
                    FloatingActionButton(
                        onClick = {
                            travelMode = if (travelMode == "Driving") "Walking" else "Driving"
                        },
                        containerColor = SlateDark,
                        contentColor = AccentTeal,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = if (travelMode == "Driving") Icons.Default.DirectionsCar else Icons.AutoMirrored.Filled.DirectionsWalk,
                            contentDescription = "Toggle Travel Mode"
                        )
                    }
                }

                // My Location FAB
                FloatingActionButton(
                    onClick = {
                        val userLoc = GeoPoint(initialLat, initialLng)
                        mapViewRef?.controller?.animateTo(userLoc)
                        mapViewRef?.controller?.setZoom(17.0)
                        Toast.makeText(context, "Centered to Hub Location", Toast.LENGTH_SHORT).show()
                    },
                    containerColor = SlateDark,
                    contentColor = EmeraldGreen,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = "My Location")
                }

                // Zoom In FAB
                FloatingActionButton(
                    onClick = {
                        mapViewRef?.controller?.zoomIn()
                    },
                    containerColor = SlateDark,
                    contentColor = PureWhite,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Zoom In")
                }

                // Zoom Out FAB
                FloatingActionButton(
                    onClick = {
                        mapViewRef?.controller?.zoomOut()
                    },
                    containerColor = SlateDark,
                    contentColor = PureWhite,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Zoom Out")
                }
            }
        }
    }
}

// ==========================================
// MATERIAL 3 BOTTOM SHEET FOR MARKER DETAILS
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DonationMarkerDetailBottomSheet(
    markerItem: MapMarkerItem?,
    onDismiss: () -> Unit,
    onAcceptDonation: (String) -> Unit
) {
    if (markerItem == null) return

    val context = LocalContext.current
    var travelMode by remember { mutableStateOf("Driving") }

    val etaMinutes = if (travelMode == "Driving") {
        (markerItem.distanceKm * 3.5 + 2).toInt()
    } else {
        (markerItem.distanceKm * 12 + 5).toInt()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SlateDark,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(48.dp)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF475569))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Title & Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = markerItem.title,
                        color = PureWhite,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${markerItem.category} • ${markerItem.quantityMeals} Servings Available",
                        color = EmeraldGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            when (markerItem.status) {
                                "Available" -> EmeraldGreen
                                "Reserved" -> OrangeFlame
                                "Urgent" -> RubyRed
                                "Completed" -> Color.Gray
                                else -> AccentTeal
                            }
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = markerItem.status.uppercase(),
                        color = PureWhite,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            // Food Image Banner if available
            if (!markerItem.photoUri.isNullOrBlank()) {
                AsyncImage(
                    model = markerItem.photoUri,
                    contentDescription = "Food Image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkNavy)
                )
            }

            // Metrics Cards Row (Freshness, Distance, AI Confidence)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = DarkNavy),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Freshness", color = Color(0xFF64748B), fontSize = 10.sp)
                        Text("${markerItem.freshnessScore}% Pristine", color = EmeraldGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = DarkNavy),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Distance", color = Color(0xFF64748B), fontSize = 10.sp)
                        Text("${markerItem.distanceKm} km away", color = AccentTeal, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = DarkNavy),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("AI Confidence", color = Color(0xFF64748B), fontSize = 10.sp)
                        Text(markerItem.aiConfidence, color = OrangeFlame, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Pickup Address & Window Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkNavy),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, null, tint = OrangeFlame, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pickup Address", color = PureWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = markerItem.address,
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 22.dp)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, null, tint = EmeraldGreen, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pickup Time Window", color = PureWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = "${markerItem.pickupTime} (ETA: ~$etaMinutes mins)",
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 22.dp)
                    )
                }
            }

            // Mode Selector & External Navigation Action Button Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val gmmIntentUri = Uri.parse("geo:${markerItem.latitude},${markerItem.longitude}?q=${Uri.encode(markerItem.address)}")
                        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                        try {
                            context.startActivity(mapIntent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Opening OpenStreetMap / Navigation...", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, AccentTeal)
                ) {
                    Icon(Icons.Default.Navigation, null, tint = AccentTeal, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("External Maps", color = AccentTeal, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                if (markerItem.status == "Available" || markerItem.status == "Urgent") {
                    Button(
                        onClick = {
                            if (markerItem.id != "-1") {
                                onAcceptDonation(markerItem.id)
                                Toast.makeText(context, "Donation accepted for pickup rescue!", Toast.LENGTH_SHORT).show()
                            }
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(1.2f)
                            .height(48.dp)
                            .testTag("accept_donation_marker_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, null, tint = PureWhite, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Claim Donation", color = PureWhite, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ==========================================
// HELPER DRAWABLE GENERATOR FOR MAP MARKERS
// ==========================================
private fun getMarkerDrawable(context: Context, status: String): BitmapDrawable {
    val (colorHex, letter) = when (status) {
        "NGO" -> Pair("#10B981", "🟢")        // Green (Current NGO Location)
        "Available" -> Pair("#3B82F6", "🔵")  // Blue (Donor Location)
        "Claimed", "Reserved" -> Pair("#F97316", "🟧") // Orange (Claimed)
        "Completed" -> Pair("#64748B", "🔘")  // Grey (Completed)
        "Expired", "Urgent" -> Pair("#EF4444", "🔴")   // Red (Expired / Urgent)
        else -> Pair("#3B82F6", "🔵")
    }

    val size = 96
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor(colorHex)
        style = Paint.Style.FILL
    }

    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    // Top Pin Circle
    canvas.drawCircle(size / 2f, size / 2f - 10, 34f, pinPaint)
    canvas.drawCircle(size / 2f, size / 2f - 10, 34f, borderPaint)

    // Bottom Pointer Triangle
    val path = Path().apply {
        moveTo(size / 2f - 14, size / 2f + 14)
        lineTo(size / 2f + 14, size / 2f + 14)
        lineTo(size / 2f, size / 2f + 40)
        close()
    }
    canvas.drawPath(path, pinPaint)
    canvas.drawPath(path, borderPaint)

    // Inner Emoji / Letter Icon
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 30f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    canvas.drawText(letter, size / 2f, size / 2f + 2, textPaint)

    return BitmapDrawable(context.resources, bitmap)
}

// ==========================================
// GEOCODING & NOMINATIM HELPER UTILITIES
// ==========================================
private fun reverseGeocode(context: Context, lat: Double, lng: Double): String {
    return try {
        val geocoder = android.location.Geocoder(context, Locale.getDefault())
        @Suppress("DEPRECATION")
        val addresses = geocoder.getFromLocation(lat, lng, 1)
        if (!addresses.isNullOrEmpty()) {
            val addr = addresses[0]
            val street = addr.thoroughfare ?: addr.subThoroughfare ?: addr.featureName ?: ""
            val locality = addr.locality ?: addr.subLocality ?: ""
            val admin = addr.adminArea ?: ""
            val postalCode = addr.postalCode ?: ""
            val fullList = listOf(street, locality, admin, postalCode).filter { it.isNotBlank() }
            if (fullList.isNotEmpty()) fullList.joinToString(", ") else "Lat: %.4f, Lng: %.4f".format(lat, lng)
        } else {
            "Lat: %.4f, Lng: %.4f".format(lat, lng)
        }
    } catch (e: Exception) {
        "Lat: %.4f, Lng: %.4f (OpenStreetMap Pin)".format(lat, lng)
    }
}

private fun searchNominatim(context: Context, query: String): List<Pair<String, GeoPoint>> {
    val results = mutableListOf<Pair<String, GeoPoint>>()
    try {
        val geocoder = android.location.Geocoder(context, Locale.getDefault())
        @Suppress("DEPRECATION")
        val addresses = geocoder.getFromLocationName(query, 5)
        if (addresses != null) {
            for (addr in addresses) {
                val formattedName = addr.getAddressLine(0) ?: "${addr.locality ?: "Location"}, ${addr.adminArea ?: ""}"
                results.add(Pair(formattedName, GeoPoint(addr.latitude, addr.longitude)))
            }
        }
    } catch (e: Exception) {
        // Fallback default sample coordinates around area for search
        results.add(Pair("$query (City Center)", GeoPoint(12.9716, 77.5946)))
    }
    return results
}
