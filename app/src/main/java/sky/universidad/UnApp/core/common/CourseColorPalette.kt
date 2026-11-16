package sky.universidad.UnApp.core.common

import androidx.compose.ui.graphics.Color
import sky.universidad.UnApp.data.local.entities.CourseEntity
import sky.universidad.UnApp.domain.model.Course
import kotlin.math.abs

/**
 * Paleta determinista de colores por código de curso (Azul UNAP + acentos iOS armónicos).
 * Garantiza colores diferenciados al iniciar sesión incluso si el estudiante tiene 7 o más cursos.
 */
object CourseColorPalette {

    val PALETTE_HEX: List<String> = listOf(
        "#1D4ED8", // Azul institucional UNAP
        "#0284C7", // Azul cielo profundo
        "#0F766E", // Verde azulado académico
        "#4F46E5", // Índigo universitario
        "#7C3AED", // Violeta suave
        "#16A34A", // Verde esmeralda
        "#D97706", // Ámbar cálido
        "#DC2626", // Rojo coral sutil
        "#DB2777", // Rosa frambuesa
        "#0D9488", // Turquesa profundo
        "#2563EB", // Azul cobalto
        "#EA580C"  // Naranja terracota
    )

    fun normalizeCode(code: String?, fallbackName: String = ""): String {
        val raw = code.orEmpty().trim()
        val isGenericSection = raw.isEmpty() || raw.startsWith("SEC-", ignoreCase = true)
        val source = if (isGenericSection && fallbackName.isNotBlank()) fallbackName else raw
        val cleaned = source.uppercase().replace(Regex("[^A-Z0-9]"), "")
        return cleaned.ifEmpty { raw.uppercase() }
    }

    fun colorHexForIndex(index: Int): String = PALETTE_HEX[index.mod(PALETTE_HEX.size)]

    fun colorHexForKey(key: String): String {
        val norm = key.trim().uppercase()
        if (norm.isEmpty()) return PALETTE_HEX.first()
        return PALETTE_HEX[abs(norm.hashCode()).mod(PALETTE_HEX.size)]
    }

    fun assignColorsByCode(courses: List<CourseEntity>): List<CourseEntity> {
        if (courses.isEmpty()) return emptyList()
        val sortedKeys = courses
            .map { normalizeCode(it.code, it.name) }
            .distinct()
            .sorted()
        val colorByCode = sortedKeys.mapIndexed { idx, key ->
            key to colorHexForIndex(idx)
        }.toMap()

        return courses.map { entity ->
            val key = normalizeCode(entity.code, entity.name)
            val hex = colorByCode[key] ?: colorHexForKey(key)
            entity.copy(colorHex = hex)
        }
    }

    /**
     * Pure Kotlin hex parser (`#RRGGBB` or `#AARRGGBB`) so JVM unit tests run without Robolectric.
     */
    fun parseColor(colorHex: String?): Color {
        val raw = colorHex?.trim()?.removePrefix("#").orEmpty()
        val argbLong = when (raw.length) {
            6 -> ("FF$raw").toLongOrNull(16)
            8 -> raw.toLongOrNull(16)
            else -> null
        } ?: return Color(0xFF1D4ED8)
        return Color(argbLong.toInt())
    }

    fun resolveCourse(
        courseId: String?,
        courseName: String?,
        courses: List<Course>
    ): Course? {
        if (courses.isEmpty()) return null
        if (!courseId.isNullOrBlank()) {
            courses.firstOrNull {
                it.remoteId == courseId ||
                    it.sectionId == courseId ||
                    it.code.equals(courseId, ignoreCase = true)
            }?.let { return it }
        }
        val targetName = courseName.orEmpty().trim()
        if (targetName.isNotBlank()) {
            courses.firstOrNull { it.name.equals(targetName, ignoreCase = true) }?.let { return it }
            val normTarget = normalizeCode(null, targetName)
            courses.firstOrNull { normalizeCode(null, it.name) == normTarget }?.let { return it }
        }
        return null
    }

    fun resolveCourseColor(
        courseId: String?,
        courseName: String?,
        courses: List<Course>
    ): Color {
        val matched = resolveCourse(courseId, courseName, courses)
        return if (matched != null) {
            parseColor(matched.colorHex)
        } else {
            val key = courseName?.takeIf { it.isNotBlank() } ?: courseId.orEmpty()
            parseColor(colorHexForKey(key))
        }
    }

    fun resolveCourseCode(
        courseId: String?,
        courseName: String?,
        courses: List<Course>
    ): String? = resolveCourse(courseId, courseName, courses)?.code
}
