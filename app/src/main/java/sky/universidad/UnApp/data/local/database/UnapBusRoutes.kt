package sky.universidad.UnApp.data.local.database

import sky.universidad.UnApp.data.local.entities.BusRouteEntity

object UnapBusRoutes {

    fun defaultBusRoutes(now: Long = System.currentTimeMillis()): List<BusRouteEntity> = listOf(
        BusRouteEntity(
            id = "bus_floral_centro",
            name = "Ruta Centro - Ciudad Universitaria",
            corridor = "Plaza de Armas / Av. El Sol / Av. Floral",
            shift = "Mañana y Tarde",
            scheduleSummary = "Salidas cada 15 min entre 06:30 y 19:30",
            stopsDelimited = listOf(
                "Parque Pino",
                "Av. El Sol / Jr. Puno",
                "Av. La Torre (Terminal Terrestre)",
                "Óvalo Huáscar",
                "Av. Floral (Grifo UNAP)",
                "Puerta Principal Ciudad Universitaria"
            ).joinToString("||"),
            isFavorite = true,
            notes = "Referencia habitual hacia Ciudad Universitaria por Av. Floral. Verificar comunicados de DBU ante desvíos.",
            lastUpdated = now
        ),
        BusRouteEntity(
            id = "bus_norte_alto",
            name = "Ruta Norte - Alto Puno",
            corridor = "Alto Puno / Av. Sesquicentenario",
            shift = "Mañana",
            scheduleSummary = "Salidas 06:40, 07:30, 13:15 y 17:30",
            stopsDelimited = listOf(
                "Plaza Alto Puno",
                "Estadio Alto Puno",
                "Av. Sesquicentenario",
                "Ingreso Norte UNAP",
                "Pabellón Central Ciudad Universitaria"
            ).joinToString("||"),
            isFavorite = false,
            notes = "Conexión directa desde la zona norte hacia campus principal.",
            lastUpdated = now
        ),
        BusRouteEntity(
            id = "bus_sur_salcedo",
            name = "Ruta Sur - Salcedo / Costanera",
            corridor = "Salcedo / Av. Costanera / Av. Floral",
            shift = "Mañana y Noche",
            scheduleSummary = "Salidas 06:30, 07:20, 14:00 y 18:15",
            stopsDelimited = listOf(
                "Centro Poblado Salcedo",
                "Av. Costanera Sur",
                "Puerto Muelle / Av. Titicaca",
                "Av. Floral (Grifo UNAP)",
                "Ciudad Universitaria"
            ).joinToString("||"),
            isFavorite = false,
            notes = "Cubre la franja sur y costanera hasta el campus UNAP.",
            lastUpdated = now
        ),
        BusRouteEntity(
            id = "bus_interprovincial",
            name = "Rutas Especiales DBU (Provincias)",
            corridor = "Pooles provinciales autorizados",
            shift = "Turnos programados",
            scheduleSummary = "Consultar programación semanal de Bienestar Universitario",
            stopsDelimited = listOf(
                "Corredor Norte (Azángaro / Melgar / Huancané)",
                "Corredor Sur (Chucuito / Yunguyo)",
                "Ciudad Universitaria UNAP Puno"
            ).joinToString("||"),
            isFavorite = false,
            notes = "Sujeto a padrón y horarios publicados por la Dirección de Bienestar Universitario (DBU).",
            lastUpdated = now
        )
    )
}
