package sky.universidad.UnApp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sky.universidad.UnApp.core.common.DateFormatters
import sky.universidad.UnApp.core.common.HtmlCleaner
import sky.universidad.UnApp.core.common.ScheduleTimeBlock
import sky.universidad.UnApp.core.common.TaskVisualStatus
import sky.universidad.UnApp.presentation.components.projectMomentum
import sky.universidad.UnApp.presentation.components.rubberBandOffset

class DateFormattersAndPhysicsTest {

    @Test
    fun parseAkdemicDateFormatsAndUrgency() {
        val epoch = DateFormatters.parseToEpochMillis("20/11/2026 23:59")
        assertNotNull(epoch)
        val now = epoch!! - 2L * 3_600_000L
        assertEquals("En 2 h", DateFormatters.formatDueUrgency(epoch, isCompleted = false, nowMillis = now))
        assertEquals("Completada", DateFormatters.formatDueUrgency(epoch, isCompleted = true, nowMillis = now))
        assertEquals("Vencida", DateFormatters.formatDueUrgency(epoch, isCompleted = false, nowMillis = epoch + 10_000L))
    }

    @Test
    fun htmlCleanerStripsTagsAndPreservesLineBreaks() {
        val cleaned = HtmlCleaner.toPlainText("<p>Primer párrafo</p><p>Segundo <b>párrafo</b></p>")
        assertTrue(cleaned.contains("Primer párrafo"))
        assertTrue(cleaned.contains("Segundo párrafo"))
        assertTrue(!cleaned.contains("<"))
    }

    @Test
    fun appleRubberBandIsStrictlyBoundedAndSublinear() {
        val small = rubberBandOffset(overshootPx = 20f, dimensionPx = 400f)
        val large = rubberBandOffset(overshootPx = 400f, dimensionPx = 400f)
        assertTrue(small > 0f && small < 20f)
        assertTrue(large > small && large < 220f)
    }

    @Test
    fun appleProjectMomentumProjectsForwardInVelocityDirection() {
        val projected = projectMomentum(velocityPxPerSec = 1000f, decelerationRate = 0.998f)
        assertTrue(projected > 490f && projected < 510f)
    }

    @Test
    fun globalTaskVisibilityKeepsActiveAndExpiredTodayAndHidesYesterday() {
        val nowTodayAfternoon = DateFormatters.parseToEpochMillis("15/11/2026 16:00")!!
        val dueFuture = DateFormatters.parseToEpochMillis("18/11/2026 23:59")!!
        val expiredEarlierToday = DateFormatters.parseToEpochMillis("15/11/2026 11:00")!!
        val expiredYesterday = DateFormatters.parseToEpochMillis("14/11/2026 23:59")!!

        assertTrue(DateFormatters.isTaskVisibleInGlobalTab(dueFuture, isEnded = false, nowMillis = nowTodayAfternoon))
        assertTrue(DateFormatters.isTaskVisibleInGlobalTab(expiredEarlierToday, isEnded = true, nowMillis = nowTodayAfternoon))
        assertFalse(DateFormatters.isTaskVisibleInGlobalTab(expiredYesterday, isEnded = true, nowMillis = nowTodayAfternoon))

        assertEquals(
            TaskVisualStatus.EXPIRED_TODAY,
            DateFormatters.resolveTaskVisualStatus(expiredEarlierToday, isSubmitted = false, isEnded = true, nowMillis = nowTodayAfternoon)
        )
        assertEquals(
            TaskVisualStatus.EXPIRED_PAST,
            DateFormatters.resolveTaskVisualStatus(expiredYesterday, isSubmitted = false, isEnded = true, nowMillis = nowTodayAfternoon)
        )
        assertEquals(
            TaskVisualStatus.DELIVERED,
            DateFormatters.resolveTaskVisualStatus(expiredYesterday, isSubmitted = true, isEnded = true, nowMillis = nowTodayAfternoon)
        )
    }

    @Test
    fun resolveTimeBlockCategorizesMorningAfternoonAndNight() {
        assertEquals(ScheduleTimeBlock.MORNING, DateFormatters.resolveTimeBlock("07:30"))
        assertEquals(ScheduleTimeBlock.MORNING, DateFormatters.resolveTimeBlock("10:00 AM"))
        assertEquals(ScheduleTimeBlock.AFTERNOON, DateFormatters.resolveTimeBlock("14:15"))
        assertEquals(ScheduleTimeBlock.AFTERNOON, DateFormatters.resolveTimeBlock("2:30 PM"))
        assertEquals(ScheduleTimeBlock.NIGHT, DateFormatters.resolveTimeBlock("19:00"))
        assertEquals(ScheduleTimeBlock.NIGHT, DateFormatters.resolveTimeBlock("7:00 PM"))
    }

    @Test
    fun extractGivenNameReturnsOnlyGivenNamesInTitleCase() {
        assertEquals("Maria Elena", DateFormatters.extractGivenName("QUISPE MAMANI MARIA ELENA"))
        assertEquals("Juan Carlos", DateFormatters.extractGivenName("LOPEZ, JUAN CARLOS"))
        assertEquals("Perez", DateFormatters.extractGivenName("ANA PEREZ"))
        assertEquals("Estudiante", DateFormatters.extractGivenName(""))
        assertEquals("Estudiante", DateFormatters.extractGivenName(null))
    }

    @Test
    fun formatTimeAmPmFormats24HourAndRangeStrings() {
        assertEquals("7:30 AM", DateFormatters.formatTimeAmPm("07:30"))
        assertEquals("12:15 AM", DateFormatters.formatTimeAmPm("00:15"))
        assertEquals("12:00 PM", DateFormatters.formatTimeAmPm("12:00"))
        assertEquals("2:15 PM", DateFormatters.formatTimeAmPm("14:15"))
        assertEquals("11:45 PM", DateFormatters.formatTimeAmPm("23:45"))
        assertEquals("2:30 PM", DateFormatters.formatTimeAmPm("2:30 PM"))
        assertEquals("8:00 AM – 10:00 AM", DateFormatters.formatTimeRangeAmPm("08:00", "10:00"))
    }

    @Test
    fun formatRelativeAcademicDateFormatsHoyMananaAyer() {
        val nowNov15 = DateFormatters.parseToEpochMillis("15/11/2026 15:00")!!
        assertEquals("Hoy, 11:59 PM", DateFormatters.formatRelativeAcademicDate("15/11/2026 23:59", nowMillis = nowNov15))
        assertEquals("Mañana, 8:30 AM", DateFormatters.formatRelativeAcademicDate("16/11/2026 08:30", nowMillis = nowNov15))
        assertEquals("Ayer, 6:00 PM", DateFormatters.formatRelativeAcademicDate("14/11/2026 18:00", nowMillis = nowNov15))
        val farText = DateFormatters.formatRelativeAcademicDate("22/11/2026 10:00", nowMillis = nowNov15)
        assertTrue(farText.contains("22") && farText.contains("10:00 AM"))
    }

    @Test
    fun isTaskDueTodayOrTomorrowMatchesCalendarDays() {
        val nowNov15 = DateFormatters.parseToEpochMillis("15/11/2026 10:00")!!
        val todayEvening = DateFormatters.parseToEpochMillis("15/11/2026 23:59")!!
        val tomorrowMorning = DateFormatters.parseToEpochMillis("16/11/2026 09:00")!!
        val yesterday = DateFormatters.parseToEpochMillis("14/11/2026 23:59")!!
        val nextWeek = DateFormatters.parseToEpochMillis("22/11/2026 23:59")!!

        assertTrue(DateFormatters.isTaskDueTodayOrTomorrow(todayEvening, nowNov15))
        assertTrue(DateFormatters.isTaskDueTodayOrTomorrow(tomorrowMorning, nowNov15))
        assertFalse(DateFormatters.isTaskDueTodayOrTomorrow(yesterday, nowNov15))
        assertFalse(DateFormatters.isTaskDueTodayOrTomorrow(nextWeek, nowNov15))
        assertFalse(DateFormatters.isTaskDueTodayOrTomorrow(null, nowNov15))
    }

    @Test
    fun parseMinutesFromMidnightHandles24hAndAmPm() {
        assertEquals(450, DateFormatters.parseMinutesFromMidnight("07:30"))
        assertEquals(900, DateFormatters.parseMinutesFromMidnight("15:00"))
        assertEquals(900, DateFormatters.parseMinutesFromMidnight("3:00 PM"))
        assertEquals(1020, DateFormatters.parseMinutesFromMidnight("5:00 PM"))
        assertEquals(0, DateFormatters.parseMinutesFromMidnight("12:00 AM"))
        assertEquals(720, DateFormatters.parseMinutesFromMidnight("12:00 PM"))
    }

    @Test
    fun isForumActiveNowAndPriorityScoreArchiveOldSeptemberForums() {
        val nowNov15 = DateFormatters.parseToEpochMillis("15/11/2026 14:00")!!
        val septClose = DateFormatters.parseToEpochMillis("20/09/2026 23:59")!!
        val nov18Close = DateFormatters.parseToEpochMillis("18/11/2026 23:59")!!
        val nov25Close = DateFormatters.parseToEpochMillis("25/11/2026 23:59")!!

        // Old September forum even if close date or only start date exists
        assertFalse(DateFormatters.isForumActiveNow(septClose, "10/09/2026 08:00", isEnded = false, nowMillis = nowNov15))
        assertFalse(DateFormatters.isForumActiveNow(null, "10/09/2026 08:00", isEnded = false, nowMillis = nowNov15))

        // Active forums in November
        assertTrue(DateFormatters.isForumActiveNow(nov18Close, "10/11/2026 08:00", isEnded = false, nowMillis = nowNov15))

        val scoreNov18 = DateFormatters.forumPriorityScore(nov18Close, null, false, nowNov15)
        val scoreNov25 = DateFormatters.forumPriorityScore(nov25Close, null, false, nowNov15)
        val scoreSept = DateFormatters.forumPriorityScore(septClose, "10/09/2026 08:00", false, nowNov15)

        assertTrue(scoreNov18 < scoreNov25)
        assertTrue(scoreNov25 < scoreSept)
    }
}
