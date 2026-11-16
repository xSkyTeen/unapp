package sky.universidad.UnApp.core.common

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

enum class TaskVisualStatus {
    DELIVERED,
    ACTIVE,
    DUE_TODAY,
    EXPIRED_TODAY,
    EXPIRED_PAST
}

enum class ScheduleTimeBlock(val label: String, val rangeLabel: String) {
    MORNING("Bloque Mañana", "6:00 AM – 12:00 PM"),
    AFTERNOON("Bloque Tarde", "12:00 PM – 6:00 PM"),
    NIGHT("Bloque Noche", "6:00 PM – 11:00 PM")
}

object DateFormatters {
    private val limaZone: TimeZone = TimeZone.getTimeZone("America/Lima")
    private val esPeLocale: Locale = Locale.forLanguageTag("es-PE")

    private val inputPatterns = listOf(
        "dd/MM/yyyy HH:mm:ss",
        "dd/MM/yyyy HH:mm",
        "dd/MM/yyyy hh:mm a",
        "dd 'de' MMMM 'del' yyyy hh:mm a",
        "dd 'de' MMMM 'del' yyyy HH:mm",
        "yyyy-MM-dd'T'HH:mm:ss.SSS",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd HH:mm",
        "dd-MM-yyyy HH:mm",
        "dd/MM/yyyy",
        "yyyy-MM-dd"
    )

    fun parseToEpochMillis(raw: String?): Long? {
        val cleaned = raw?.trim().orEmpty()
        if (cleaned.isEmpty()) return null
        for (pattern in inputPatterns) {
            runCatching {
                val sdf = SimpleDateFormat(pattern, esPeLocale).apply {
                    timeZone = limaZone
                    isLenient = false
                }
                sdf.parse(cleaned)?.time
            }.getOrNull()?.let { return it }
        }
        return null
    }

    fun formatShortDateTime(epochMillis: Long?): String {
        if (epochMillis == null || epochMillis <= 0L) return "Sin actualización previa"
        val sdf = SimpleDateFormat("dd MMM, h:mm a", esPeLocale).apply {
            timeZone = limaZone
        }
        return normalizeAmPm(sdf.format(Date(epochMillis)))
    }

    fun formatRelativeAcademicDate(
        rawDate: String?,
        epochMillis: Long? = parseToEpochMillis(rawDate),
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        val resolved = epochMillis ?: return rawDate?.trim()?.takeIf { it.isNotEmpty() } ?: "Sin fecha"
        val dayDiff = localCalendarDayDiff(resolved, nowMillis)
        val hasTimePart = rawDate.isNullOrBlank() || rawDate.contains(":")
        val timeText = if (hasTimePart) {
            val sdfTime = SimpleDateFormat("h:mm a", esPeLocale).apply {
                timeZone = limaZone
            }
            ", ${normalizeAmPm(sdfTime.format(Date(resolved)))}"
        } else {
            ""
        }
        return when (dayDiff) {
            0 -> "Hoy$timeText"
            1 -> "Mañana$timeText"
            -1 -> "Ayer$timeText"
            else -> formatShortDateTime(resolved)
        }
    }

    private fun normalizeAmPm(raw: String): String = raw
        .replace(Regex("""a\.[ \t\u00A0\u202F]*m\.""", RegexOption.IGNORE_CASE), "AM")
        .replace(Regex("""p\.[ \t\u00A0\u202F]*m\.""", RegexOption.IGNORE_CASE), "PM")

    private fun localDayStartMillis(epochMillis: Long): Long {
        return Calendar.getInstance(limaZone).apply {
            timeInMillis = epochMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun localCalendarDayDiff(targetMillis: Long, referenceMillis: Long = System.currentTimeMillis()): Int {
        val targetStart = localDayStartMillis(targetMillis)
        val refStart = localDayStartMillis(referenceMillis)
        return Math.round((targetStart - refStart) / 86_400_000.0).toInt()
    }

    fun isTaskDueTodayOrTomorrow(
        dueTimestamp: Long?,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (dueTimestamp == null) return false
        return localCalendarDayDiff(dueTimestamp, nowMillis) in 0..1
    }

    fun formatTimeAmPm(rawTime: String?): String {
        val cleaned = rawTime?.trim().orEmpty()
        if (cleaned.isEmpty()) return ""
        val match = Regex("""^(\d{1,2}):(\d{2})(?:\s*(AM|PM|a\.?\s*m\.?|p\.?\s*m\.?))?$""", RegexOption.IGNORE_CASE)
            .find(cleaned) ?: return cleaned
        var hour = match.groupValues[1].toIntOrNull() ?: return cleaned
        val minute = match.groupValues[2]
        val existingPeriod = match.groupValues[3].uppercase()
        if (existingPeriod.contains("P") && hour < 12) hour += 12
        if (existingPeriod.contains("A") && hour == 12) hour = 0
        val period = if (hour >= 12) "PM" else "AM"
        val h12 = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        return "$h12:$minute $period"
    }

    fun formatTimeRangeAmPm(start: String?, end: String?): String {
        val s = formatTimeAmPm(start)
        val e = formatTimeAmPm(end)
        return when {
            s.isNotBlank() && e.isNotBlank() -> "$s – $e"
            s.isNotBlank() -> s
            else -> e
        }
    }

    /**
     * In UNAP / Akdemic student profiles, full names come as "APELLIDO_PATERNO APELLIDO_MATERNO NOMBRES".
     * Returns only the student's given names in Title Case (never surnames when 3+ tokens exist).
     */
    fun extractGivenName(fullName: String?): String {
        val cleaned = fullName?.trim().orEmpty()
        if (cleaned.isEmpty()) return "Estudiante"
        if (cleaned.contains(",")) {
            val afterComma = cleaned.substringAfter(",").trim()
            if (afterComma.isNotEmpty()) return formatTitleCase(afterComma)
        }
        val words = cleaned.split(Regex("\\s+")).filter { it.isNotBlank() }
        val givenWords = when {
            words.size >= 3 -> words.drop(2)
            words.size == 2 -> listOf(words[1])
            else -> words
        }
        return formatTitleCase(givenWords.joinToString(" "))
    }

    fun formatTitleCase(raw: String?): String {
        val cleaned = raw?.trim().orEmpty()
        if (cleaned.isEmpty()) return ""
        return cleaned.split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                val lower = word.lowercase(esPeLocale)
                lower.replaceFirstChar { ch ->
                    if (ch.isLowerCase()) ch.titlecase(esPeLocale) else ch.toString()
                }
            }
    }

    fun formatSyncTime(epochMillis: Long?): String {
        if (epochMillis == null || epochMillis <= 0L) return "Pendiente"
        val diff = System.currentTimeMillis() - epochMillis
        return when {
            diff < TimeUnit.MINUTES.toMillis(1) -> "Hace unos segundos"
            diff < TimeUnit.HOURS.toMillis(1) -> {
                val mins = TimeUnit.MILLISECONDS.toMinutes(diff)
                "Hace $mins min"
            }
            diff < TimeUnit.DAYS.toMillis(1) -> {
                val hours = TimeUnit.MILLISECONDS.toHours(diff)
                "Hace $hours h"
            }
            else -> formatShortDateTime(epochMillis)
        }
    }

    fun formatDueUrgency(dueMillis: Long?, isCompleted: Boolean, nowMillis: Long = System.currentTimeMillis()): String {
        if (isCompleted) return "Completada"
        if (dueMillis == null) return "Sin fecha límite"
        val diff = dueMillis - nowMillis
        return when {
            diff < 0 -> "Vencida"
            diff < TimeUnit.HOURS.toMillis(1) -> "Menos de 1 h"
            diff < TimeUnit.HOURS.toMillis(24) -> {
                val hours = TimeUnit.MILLISECONDS.toHours(diff)
                "En $hours h"
            }
            else -> {
                val days = TimeUnit.MILLISECONDS.toDays(diff)
                if (days == 1L) "Mañana" else "En $days días"
            }
        }
    }

    fun dayName(dayOfWeek: Int): String = when (dayOfWeek) {
        1 -> "Lunes"
        2 -> "Martes"
        3 -> "Miércoles"
        4 -> "Jueves"
        5 -> "Viernes"
        6 -> "Sábado"
        7 -> "Domingo"
        else -> "Día"
    }

    fun dayShort(dayOfWeek: Int): String = when (dayOfWeek) {
        1 -> "Lun"
        2 -> "Mar"
        3 -> "Mié"
        4 -> "Jue"
        5 -> "Vie"
        6 -> "Sáb"
        7 -> "Dom"
        else -> "-"
    }

    fun isSameLocalDay(millisA: Long, millisB: Long): Boolean {
        val calA = Calendar.getInstance(limaZone).apply { timeInMillis = millisA }
        val calB = Calendar.getInstance(limaZone).apply { timeInMillis = millisB }
        return calA.get(Calendar.YEAR) == calB.get(Calendar.YEAR) &&
            calA.get(Calendar.DAY_OF_YEAR) == calB.get(Calendar.DAY_OF_YEAR)
    }

    /**
     * Global task tab logic requested by UNAP user:
     * - If task is still active (`dueTimestamp == null` or `dueTimestamp >= now` and `!isEnded`), show it.
     * - If task expired TODAY (same calendar day in America/Lima), still show it.
     * - If task expired YESTERDAY or earlier, hide it from global list (only visible inside course detail).
     */
    fun isTaskVisibleInGlobalTab(
        dueTimestamp: Long?,
        isEnded: Boolean,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (dueTimestamp != null) {
            if (dueTimestamp >= nowMillis && !isEnded) return true
            return isSameLocalDay(dueTimestamp, nowMillis)
        }
        return !isEnded
    }

    fun resolveTaskVisualStatus(
        dueTimestamp: Long?,
        isSubmitted: Boolean,
        isEnded: Boolean,
        nowMillis: Long = System.currentTimeMillis()
    ): TaskVisualStatus {
        if (isSubmitted) return TaskVisualStatus.DELIVERED
        if (dueTimestamp == null) {
            return if (isEnded) TaskVisualStatus.EXPIRED_PAST else TaskVisualStatus.ACTIVE
        }
        val expiredNow = dueTimestamp < nowMillis || isEnded
        val sameDay = isSameLocalDay(dueTimestamp, nowMillis)
        return when {
            expiredNow && sameDay -> TaskVisualStatus.EXPIRED_TODAY
            expiredNow -> TaskVisualStatus.EXPIRED_PAST
            sameDay -> TaskVisualStatus.DUE_TODAY
            else -> TaskVisualStatus.ACTIVE
        }
    }

    fun statusLabel(status: TaskVisualStatus): String = when (status) {
        TaskVisualStatus.DELIVERED -> "Entregada"
        TaskVisualStatus.ACTIVE -> "Activa"
        TaskVisualStatus.DUE_TODAY -> "Vence hoy"
        TaskVisualStatus.EXPIRED_TODAY -> "Vencida hoy"
        TaskVisualStatus.EXPIRED_PAST -> "Vencida"
    }

    fun parseHour24(timeRaw: String?): Int? {
        val cleaned = timeRaw?.trim().orEmpty()
        val match = Regex("""(\d{1,2}):(\d{2})(?:\s*(AM|PM|a\.?\s*m\.?|p\.?\s*m\.?))?""", RegexOption.IGNORE_CASE)
            .find(cleaned)
        var hour = match?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("""(\d{1,2})""").find(cleaned)?.groupValues?.get(1)?.toIntOrNull()
            ?: return null
        val suffix = match?.groupValues?.get(3)?.uppercase().orEmpty()
        if (suffix.contains("P") && hour < 12) hour += 12
        if (suffix.contains("A") && hour == 12) hour = 0
        return hour.coerceIn(0, 23)
    }

    fun parseMinutesFromMidnight(timeRaw: String?): Int? {
        val cleaned = timeRaw?.trim().orEmpty()
        if (cleaned.isEmpty()) return null
        val match = Regex("""(\d{1,2})(?::(\d{2}))?(?:\s*(AM|PM|a\.?\s*m\.?|p\.?\s*m\.?))?""", RegexOption.IGNORE_CASE)
            .find(cleaned) ?: return null
        var hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: 0
        val suffix = match.groupValues[3].uppercase()
        if (suffix.contains("P") && hour < 12) hour += 12
        if (suffix.contains("A") && hour == 12) hour = 0
        return hour.coerceIn(0, 23) * 60 + minute.coerceIn(0, 59)
    }

    /**
     * Evaluates whether an academic forum is genuinely active today instead of an old closed/past-month forum
     * missing `isEnded = true` from the portal API.
     */
    fun isForumActiveNow(
        closeTimestamp: Long?,
        startDateRaw: String?,
        isEnded: Boolean,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (isEnded) return false
        if (closeTimestamp != null) {
            return closeTimestamp >= localDayStartMillis(nowMillis)
        }
        val startTs = parseToEpochMillis(startDateRaw)
        if (startTs != null && (nowMillis - startTs) > TimeUnit.DAYS.toMillis(14)) {
            return false
        }
        return true
    }

    /**
     * Priority key for sorting forums by date importance (smaller = shown higher):
     * 1. Active upcoming forums ordered by closest deadline first
     * 2. Active forums closed earlier today
     * 3. Active undated/recent forums
     * 4. Ended/old forums (e.g. from previous months) at the bottom ordered most-recent-first
     */
    fun forumPriorityScore(
        closeTimestamp: Long?,
        startDateRaw: String?,
        isEnded: Boolean,
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        val active = isForumActiveNow(closeTimestamp, startDateRaw, isEnded, nowMillis)
        val startTs = parseToEpochMillis(startDateRaw)
        if (!active) {
            val refTs = closeTimestamp ?: startTs ?: 0L
            return 10_000_000_000_000L + (nowMillis - refTs).coerceAtLeast(0L)
        }
        if (closeTimestamp != null) {
            return if (closeTimestamp >= nowMillis) {
                (closeTimestamp - nowMillis).coerceAtLeast(0L)
            } else {
                1_000_000_000_000L + (nowMillis - closeTimestamp).coerceAtLeast(0L)
            }
        }
        return 2_000_000_000_000L + (nowMillis - (startTs ?: nowMillis)).coerceAtLeast(0L)
    }

    fun resolveTimeBlock(startTime: String?): ScheduleTimeBlock {
        val hour = parseHour24(startTime) ?: return ScheduleTimeBlock.MORNING
        return when {
            hour < 12 -> ScheduleTimeBlock.MORNING
            hour < 18 -> ScheduleTimeBlock.AFTERNOON
            else -> ScheduleTimeBlock.NIGHT
        }
    }
}
