package sky.universidad.UnApp.data.local.database

import sky.universidad.UnApp.data.local.entities.AnnouncementEntity
import sky.universidad.UnApp.data.local.entities.BusRouteEntity
import sky.universidad.UnApp.data.local.entities.CourseEntity
import sky.universidad.UnApp.data.local.entities.ExamEntity
import sky.universidad.UnApp.data.local.entities.ForumEntity
import sky.universidad.UnApp.data.local.entities.NoteEntity
import sky.universidad.UnApp.data.local.entities.ScheduleEntity
import sky.universidad.UnApp.data.local.entities.StudentEntity
import sky.universidad.UnApp.data.local.entities.SyncEntity
import sky.universidad.UnApp.data.local.entities.TaskEntity
import sky.universidad.UnApp.domain.model.AcademicTask
import sky.universidad.UnApp.domain.model.Announcement
import sky.universidad.UnApp.domain.model.BusRoute
import sky.universidad.UnApp.domain.model.Course
import sky.universidad.UnApp.domain.model.Exam
import sky.universidad.UnApp.domain.model.ForumTopic
import sky.universidad.UnApp.domain.model.PersonalNote
import sky.universidad.UnApp.domain.model.ScheduleSlot
import sky.universidad.UnApp.domain.model.StudentProfile
import sky.universidad.UnApp.domain.model.SyncStatusInfo

private const val STOP_DELIMITER = "||"

fun StudentEntity.toDomain() = StudentProfile(
    id = id,
    code = code,
    dni = dni,
    fullName = fullName,
    career = career,
    faculty = faculty,
    semester = semester
)

fun CourseEntity.toDomain(pendingTasksCount: Int = 0) = Course(
    remoteId = remoteId,
    sectionId = sectionId,
    code = code,
    name = name,
    teacher = teacher,
    credits = credits,
    semester = semester,
    pendingTasksCount = pendingTasksCount,
    colorHex = colorHex,
    sourcePortal = sourcePortal,
    sectionGroup = sectionGroup
)

fun TaskEntity.toDomain() = AcademicTask(
    remoteId = remoteId,
    homeworkId = homeworkId,
    courseId = courseId,
    courseName = courseName,
    title = title,
    description = description,
    unit = unit,
    topicName = topicName,
    startDateRaw = startDateRaw,
    dueDateRaw = dueDateRaw,
    dueTimestamp = dueTimestamp,
    attempts = attempts,
    fileFilter = fileFilter,
    isEnded = isEnded,
    isCompletedLocal = isCompletedLocal,
    isSubmittedRemote = isSubmittedRemote,
    isNew = isNew
)

fun ForumEntity.toDomain() = ForumTopic(
    remoteId = remoteId,
    rawForumId = rawForumId,
    courseId = courseId,
    courseName = courseName,
    title = title,
    unitName = unitName,
    description = description,
    startDateRaw = startDateRaw,
    closeDateRaw = closeDateRaw,
    closeTimestamp = closeTimestamp,
    author = author,
    answersCount = answersCount,
    isEnded = isEnded,
    isNew = isNew
)

fun AnnouncementEntity.toDomain() = Announcement(
    remoteId = remoteId,
    courseId = courseId,
    courseName = courseName,
    title = title,
    content = content,
    publishedAtRaw = publishedAtRaw,
    publishedTimestamp = publishedTimestamp,
    sourcePortal = sourcePortal
)

fun ScheduleEntity.toDomain() = ScheduleSlot(
    id = id,
    remoteId = remoteId,
    courseId = courseId,
    courseName = courseName,
    dayOfWeek = dayOfWeek,
    startTime = startTime,
    endTime = endTime,
    classroom = classroom,
    teacher = teacher,
    slotType = slotType,
    isManual = isManual
)

fun ExamEntity.toDomain() = Exam(
    remoteId = remoteId,
    courseId = courseId,
    courseName = courseName,
    title = title,
    examType = examType,
    dateRaw = dateRaw,
    timestamp = timestamp,
    classroom = classroom,
    grade = grade
)

fun NoteEntity.toDomain() = PersonalNote(
    id = id,
    title = title,
    content = content,
    courseId = courseId,
    courseName = courseName,
    isPinned = isPinned,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun BusRouteEntity.toDomain() = BusRoute(
    id = id,
    name = name,
    corridor = corridor,
    shift = shift,
    scheduleSummary = scheduleSummary,
    stops = stopsDelimited.split(STOP_DELIMITER).filter { it.isNotBlank() },
    isFavorite = isFavorite,
    notes = notes
)

fun BusRoute.toEntity(lastUpdated: Long) = BusRouteEntity(
    id = id,
    name = name,
    corridor = corridor,
    shift = shift,
    scheduleSummary = scheduleSummary,
    stopsDelimited = stops.joinToString(STOP_DELIMITER),
    isFavorite = isFavorite,
    notes = notes,
    lastUpdated = lastUpdated
)

fun SyncEntity.toDomain() = SyncStatusInfo(
    module = module,
    lastSuccessAt = lastSuccessAt,
    status = status,
    message = message
)
