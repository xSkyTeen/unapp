package sky.universidad.UnApp.presentation.bus

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import sky.universidad.UnApp.BuildConfig
import sky.universidad.UnApp.domain.model.BusRoute
import sky.universidad.UnApp.presentation.MainUiState
import sky.universidad.UnApp.presentation.components.AppleBottomSheet
import sky.universidad.UnApp.presentation.components.AppleCard
import sky.universidad.UnApp.presentation.components.ApplePillBadge
import sky.universidad.UnApp.presentation.components.AppleSegmentedControl
import sky.universidad.UnApp.presentation.components.AppleStickyCurvedHeader
import sky.universidad.UnApp.presentation.components.EmptyStateView
import sky.universidad.UnApp.presentation.components.UnAppIcons
import sky.universidad.UnApp.presentation.components.applePressable

@Composable
fun BusScreen(
    state: MainUiState,
    bottomContentPadding: PaddingValues,
    onBack: () -> Unit,
    onToggleFavorite: (routeId: String, favorite: Boolean) -> Unit
) {
    val context = LocalContext.current
    var viewModeIndex by remember { mutableIntStateOf(0) } // 0 = Mapa en vivo, 1 = Lista
    var selectedFilterRouteId by remember { mutableStateOf<String?>(null) }
    var selectedRouteSheet by remember { mutableStateOf<BusRoute?>(null) }
    var selectedMapStop by remember { mutableStateOf<Pair<BusRouteGeoSpec, BusStopGeoPoint>?>(null) }

    var hasLocationPermission by remember {
        mutableStateOf(checkLocationPermission(context))
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasLocationPermission = grants.values.any { it }
        if (!hasLocationPermission) {
            Toast.makeText(
                context,
                "Activa el permiso de ubicación para ver tu posición en tiempo real.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppleStickyCurvedHeader(
            title = "Bus universitario UNAP",
            subtitle = "Mapa de Puno · Paraderos y horarios",
            onBack = onBack,
            bottomContent = {
                AppleSegmentedControl(
                    options = listOf("Mapa en vivo", "Rutas y horarios"),
                    selectedIndex = viewModeIndex,
                    onSelect = { viewModeIndex = it }
                )
            }
        )

        if (viewModeIndex == 0) {
            BusLiveMapSection(
                routes = state.busRoutes,
                selectedFilterRouteId = selectedFilterRouteId,
                onSelectFilterRouteId = { id ->
                    selectedFilterRouteId = id
                    selectedMapStop = null
                },
                hasLocationPermission = hasLocationPermission,
                onRequestLocationPermission = {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                },
                selectedStop = selectedMapStop,
                onSelectStop = { spec, stop -> selectedMapStop = spec to stop },
                onClearSelectedStop = { selectedMapStop = null },
                bottomContentPadding = bottomContentPadding,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 14.dp,
                    bottom = bottomContentPadding.calculateBottomPadding() + 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (state.busRoutes.isEmpty()) {
                    item {
                        EmptyStateView(
                            icon = UnAppIcons.Bus,
                            title = "Sin rutas cargadas",
                            subtitle = "Las rutas del transporte universitario estarán disponibles sin conexión."
                        )
                    }
                } else {
                    items(state.busRoutes, key = { it.id }) { route ->
                        val spec = PunoBusGeoData.specFor(route.id)
                        AppleCard(
                            modifier = Modifier.fillMaxWidth(),
                            accentColor = spec?.color,
                            onClick = { selectedRouteSheet = route }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ApplePillBadge(text = route.shift)
                                    if (route.isFavorite) {
                                        ApplePillBadge(
                                            text = "Frecuente",
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { onToggleFavorite(route.id, !route.isFavorite) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = UnAppIcons.Pin,
                                        contentDescription = "Marcar como ruta frecuente",
                                        tint = if (route.isFavorite) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = route.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = route.corridor,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = route.scheduleSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${route.stops.size} paraderos · Toca para ver horarios",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Icon(
                                    imageVector = UnAppIcons.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    selectedRouteSheet?.let { route ->
        val spec = PunoBusGeoData.specFor(route.id)
        AppleBottomSheet(onDismiss = { selectedRouteSheet = null }) {
            ApplePillBadge(text = route.shift)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = route.name,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = route.scheduleSummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Paraderos y horarios referenciales",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(10.dp))

            Column {
                val displayStops = spec?.stops ?: route.stops.map {
                    BusStopGeoPoint(it, 0.0, 0.0, route.scheduleSummary)
                }
                displayStops.forEachIndexed { index, stop ->
                    val isLast = index == displayStops.lastIndex
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(24.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (index == 0 || isLast) {
                                            spec?.color ?: MaterialTheme.colorScheme.primary
                                        } else {
                                            (spec?.color ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.45f)
                                        }
                                    )
                            )
                            if (!isLast) {
                                Box(
                                    modifier = Modifier
                                        .width(2.dp)
                                        .height(34.dp)
                                        .background(MaterialTheme.colorScheme.outlineVariant)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.padding(bottom = if (isLast) 0.dp else 10.dp)) {
                            Text(
                                text = stop.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stop.scheduleText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            if (route.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(12.dp)
                ) {
                    Text(
                        text = route.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = {
                    selectedFilterRouteId = route.id
                    selectedMapStop = null
                    selectedRouteSheet = null
                    viewModeIndex = 0
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("Ver ruta en el mapa de Puno", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun BusLiveMapSection(
    routes: List<BusRoute>,
    selectedFilterRouteId: String?,
    onSelectFilterRouteId: (String?) -> Unit,
    hasLocationPermission: Boolean,
    onRequestLocationPermission: () -> Unit,
    selectedStop: Pair<BusRouteGeoSpec, BusStopGeoPoint>?,
    onSelectStop: (BusRouteGeoSpec, BusStopGeoPoint) -> Unit,
    onClearSelectedStop: () -> Unit,
    bottomContentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val allSpecs = remember { PunoBusGeoData.allSpecs() }
    val visibleSpecs = remember(selectedFilterRouteId) {
        if (selectedFilterRouteId == null) allSpecs
        else allSpecs.filter { it.routeId == selectedFilterRouteId }
    }

    val mapViewRef = remember { mutableStateOf<MapView?>(null) }
    val myLocationOverlayRef = remember { mutableStateOf<MyLocationNewOverlay?>(null) }

    // Configure osmdroid user agent & cache once
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("osmdroid_prefs", Context.MODE_PRIVATE)
        Configuration.getInstance().load(context, prefs)
        Configuration.getInstance().userAgentValue = "${BuildConfig.APPLICATION_ID}/${BuildConfig.VERSION_NAME}"
    }

    DisposableEffect(Unit) {
        onDispose {
            myLocationOverlayRef.value?.disableMyLocation()
            mapViewRef.value?.onPause()
            mapViewRef.value?.onDetach()
        }
    }

    // Re-sync GPS overlay when permission changes
    LaunchedEffect(hasLocationPermission, mapViewRef.value) {
        val map = mapViewRef.value ?: return@LaunchedEffect
        if (hasLocationPermission) {
            val overlay = myLocationOverlayRef.value ?: MyLocationNewOverlay(GpsMyLocationProvider(context), map).also {
                myLocationOverlayRef.value = it
                map.overlays.add(it)
            }
            overlay.enableMyLocation()
            map.invalidate()
        }
    }

    // Re-render routes and stops cleanly whenever filter changes
    LaunchedEffect(visibleSpecs, mapViewRef.value) {
        val map = mapViewRef.value ?: return@LaunchedEffect
        val locationOverlay = myLocationOverlayRef.value
        map.overlays.clear()

        visibleSpecs.forEach { spec ->
            val points = spec.stops.map { it.toGeoPoint() }
            if (points.size >= 2) {
                val line = Polyline().apply {
                    setPoints(points)
                    outlinePaint.color = spec.androidColor
                    outlinePaint.strokeWidth = 10f
                    outlinePaint.strokeCap = Paint.Cap.ROUND
                    outlinePaint.strokeJoin = Paint.Join.ROUND
                    outlinePaint.isAntiAlias = true
                }
                map.overlays.add(line)
            }

            spec.stops.forEachIndexed { idx, stop ->
                val isEndpoint = idx == 0 || idx == spec.stops.lastIndex
                val marker = Marker(map).apply {
                    position = stop.toGeoPoint()
                    title = stop.name
                    snippet = stop.scheduleText
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = createStopMarkerDrawable(
                        context = context,
                        fillColor = spec.androidColor,
                        sizeDp = if (isEndpoint) 18 else 14
                    )
                    setOnMarkerClickListener { _, _ ->
                        onSelectStop(spec, stop)
                        map.controller.animateTo(stop.toGeoPoint())
                        true
                    }
                }
                map.overlays.add(marker)
            }
        }

        if (locationOverlay != null) {
            map.overlays.add(locationOverlay)
        }

        val targetFocus = if (visibleSpecs.size == 1 && visibleSpecs.first().stops.isNotEmpty()) {
            visibleSpecs.first().stops[visibleSpecs.first().stops.size / 2].toGeoPoint()
        } else {
            PunoBusGeoData.PUNO_MAP_CENTER
        }
        val targetZoom = if (visibleSpecs.size == 1) 14.3 else 13.7
        map.controller.setZoom(targetZoom)
        map.controller.animateTo(targetFocus)
        map.invalidate()
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(13.8)
                    controller.setCenter(PunoBusGeoData.PUNO_MAP_CENTER)
                    onResume()
                    if (hasLocationPermission) {
                        val locOverlay = MyLocationNewOverlay(GpsMyLocationProvider(ctx), this).apply {
                            enableMyLocation()
                        }
                        myLocationOverlayRef.value = locOverlay
                        overlays.add(locOverlay)
                    }
                    mapViewRef.value = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Top route filter chips over the map
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MapFilterChip(
                label = "Todas",
                dotColor = null,
                selected = selectedFilterRouteId == null,
                onClick = { onSelectFilterRouteId(null) }
            )
            allSpecs.forEach { spec ->
                MapFilterChip(
                    label = spec.shortLabel,
                    dotColor = spec.color,
                    selected = selectedFilterRouteId == spec.routeId,
                    onClick = { onSelectFilterRouteId(spec.routeId) }
                )
            }
        }

        // Right-side map quick controls (My Location & UNAP Campus)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                shadowElevation = 2.dp,
                modifier = Modifier.applePressable {
                    if (!hasLocationPermission) {
                        onRequestLocationPermission()
                        return@applePressable
                    }
                    val overlay = myLocationOverlayRef.value
                    val fix = overlay?.myLocation
                    if (fix != null) {
                        mapViewRef.value?.controller?.animateTo(fix, 16.0, 600L)
                    } else {
                        overlay?.enableFollowLocation()
                        Toast.makeText(
                            context,
                            "Buscando señal GPS en Puno...",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            ) {
                Text(
                    text = if (hasLocationPermission) "Mi ubicación" else "Activar GPS",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                )
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                shadowElevation = 2.dp,
                modifier = Modifier.applePressable {
                    mapViewRef.value?.controller?.animateTo(
                        PunoBusGeoData.UNAP_CAMPUS_CENTER,
                        15.6,
                        600L
                    )
                }
            ) {
                Text(
                    text = "Campus UNAP",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                )
            }
        }

        // Bottom info & schedule overlay card
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = bottomContentPadding.calculateBottomPadding() + 12.dp
                )
        ) {
            val activeStop = selectedStop
            if (activeStop != null) {
                val (spec, stop) = activeStop
                val matchingRoute = routes.firstOrNull { it.id == spec.routeId }
                AppleCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    accentColor = spec.color
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = matchingRoute?.name ?: spec.shortLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = spec.color
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stop.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Horario: ${stop.scheduleText}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = onClearSelectedStop,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = UnAppIcons.Close,
                                contentDescription = "Cerrar paradero",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            } else {
                val filteredRoute = selectedFilterRouteId?.let { id -> routes.firstOrNull { it.id == id } }
                val filteredSpec = selectedFilterRouteId?.let { PunoBusGeoData.specFor(it) }
                AppleCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    accentColor = filteredSpec?.color
                ) {
                    Text(
                        text = filteredRoute?.name ?: "Rutas UNAP en Puno",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = filteredRoute?.scheduleSummary
                            ?: "Toca cualquier paradero en el mapa para ver sus horarios y ubicación.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun MapFilterChip(
    label: String,
    dotColor: Color?,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
    }
    val fg = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(
                width = 0.8.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(50)
            )
            .applePressable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (dotColor != null) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (selected) Color.White else dotColor)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = fg
        )
    }
}

private fun checkLocationPermission(context: Context): Boolean {
    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    return fine || coarse
}

private fun createStopMarkerDrawable(
    context: Context,
    fillColor: Int,
    sizeDp: Int
): GradientDrawable {
    val px = (sizeDp * context.resources.displayMetrics.density).toInt().coerceAtLeast(18)
    val strokePx = (2.5f * context.resources.displayMetrics.density).toInt().coerceAtLeast(3)
    return GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(fillColor)
        setStroke(strokePx, android.graphics.Color.WHITE)
        setSize(px, px)
    }
}
