package sky.universidad.UnApp.presentation.bus

import androidx.compose.ui.graphics.Color
import org.osmdroid.util.GeoPoint

data class BusStopGeoPoint(
    val name: String,
    val lat: Double,
    val lon: Double,
    val scheduleText: String
) {
    fun toGeoPoint(): GeoPoint = GeoPoint(lat, lon)
}

data class BusRouteGeoSpec(
    val routeId: String,
    val shortLabel: String,
    val color: Color,
    val androidColor: Int,
    val stops: List<BusStopGeoPoint>
)

object PunoBusGeoData {

    val UNAP_CAMPUS_CENTER = GeoPoint(-15.8256, -70.0170)
    val PUNO_MAP_CENTER = GeoPoint(-15.8330, -70.0188)

    private val specsByRouteId: Map<String, BusRouteGeoSpec> = listOf(
        BusRouteGeoSpec(
            routeId = "bus_floral_centro",
            shortLabel = "Centro",
            color = Color(0xFF2563EB),
            androidColor = android.graphics.Color.parseColor("#2563EB"),
            stops = listOf(
                BusStopGeoPoint("Parque Pino", -15.8382, -70.0279, "06:30 – 19:30 · cada 15 min"),
                BusStopGeoPoint("Av. El Sol / Jr. Puno", -15.8388, -70.0238, "06:34 – 19:34 · cada 15 min"),
                BusStopGeoPoint("Av. La Torre (Terminal)", -15.8404, -70.0190, "06:38 – 19:38 · cada 15 min"),
                BusStopGeoPoint("Óvalo Huáscar", -15.8342, -70.0185, "06:42 – 19:42 · cada 15 min"),
                BusStopGeoPoint("Av. Floral (Grifo UNAP)", -15.8292, -70.0176, "06:46 – 19:46 · cada 15 min"),
                BusStopGeoPoint("Puerta Principal UNAP", -15.8256, -70.0170, "06:50 – 19:50 · Llegada")
            )
        ),
        BusRouteGeoSpec(
            routeId = "bus_norte_alto",
            shortLabel = "Alto Puno",
            color = Color(0xFF0891B2),
            androidColor = android.graphics.Color.parseColor("#0891B2"),
            stops = listOf(
                BusStopGeoPoint("Plaza Alto Puno", -15.8068, -70.0235, "06:40 · 07:30 · 13:15 · 17:30"),
                BusStopGeoPoint("Estadio Alto Puno", -15.8105, -70.0212, "06:44 · 07:34 · 13:19 · 17:34"),
                BusStopGeoPoint("Av. Sesquicentenario", -15.8156, -70.0182, "06:49 · 07:39 · 13:24 · 17:39"),
                BusStopGeoPoint("Ingreso Norte UNAP", -15.8205, -70.0155, "06:54 · 07:44 · 13:29 · 17:44"),
                BusStopGeoPoint("Pabellón Central UNAP", -15.8240, -70.0160, "06:58 · 07:48 · 13:33 · 17:48")
            )
        ),
        BusRouteGeoSpec(
            routeId = "bus_sur_salcedo",
            shortLabel = "Salcedo",
            color = Color(0xFFD97706),
            androidColor = android.graphics.Color.parseColor("#D97706"),
            stops = listOf(
                BusStopGeoPoint("Centro Poblado Salcedo", -15.8645, -70.0085, "06:30 · 07:20 · 14:00 · 18:15"),
                BusStopGeoPoint("Av. Costanera Sur", -15.8530, -70.0125, "06:37 · 07:27 · 14:07 · 18:22"),
                BusStopGeoPoint("Puerto Muelle / Av. Titicaca", -15.8365, -70.0155, "06:44 · 07:34 · 14:14 · 18:29"),
                BusStopGeoPoint("Av. Floral (Grifo UNAP)", -15.8292, -70.0176, "06:49 · 07:39 · 14:19 · 18:34"),
                BusStopGeoPoint("Ciudad Universitaria UNAP", -15.8256, -70.0170, "06:54 · 07:44 · 14:24 · 18:39")
            )
        ),
        BusRouteGeoSpec(
            routeId = "bus_interprovincial",
            shortLabel = "Provincias",
            color = Color(0xFF4F46E5),
            androidColor = android.graphics.Color.parseColor("#4F46E5"),
            stops = listOf(
                BusStopGeoPoint("Corredor Norte (Azángaro / Melgar)", -15.8125, -70.0195, "Turnos DBU programados"),
                BusStopGeoPoint("Corredor Sur (Chucuito / Yunguyo)", -15.8510, -70.0130, "Turnos DBU programados"),
                BusStopGeoPoint("Ciudad Universitaria UNAP", -15.8256, -70.0170, "Llegada al campus")
            )
        )
    ).associateBy { it.routeId }

    fun specFor(routeId: String): BusRouteGeoSpec? = specsByRouteId[routeId]

    fun allSpecs(): List<BusRouteGeoSpec> = specsByRouteId.values.toList()
}
