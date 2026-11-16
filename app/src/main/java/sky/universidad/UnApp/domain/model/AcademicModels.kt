package sky.universidad.UnApp.domain.model

data class StudentProfile(
    val id: String,
    val code: String,
    val dni: String,
    val fullName: String,
    val career: String,
    val faculty: String,
    val semester: String
)

data class Course(
    val remoteId: String,
    val sectionId: String,
    val code: String,
    val name: String,
    val teacher: String,
    val credits: Int,
    val semester: String,
    val pendingTasksCount: Int = 0,
    val colorHex: String = "#1D4ED8",
    val sourcePortal: String = "aulavirtual",
    val sectionGroup: String = ""
)

data class AcademicTask(
    val remoteId: String,
    val homeworkId: String = "",
    val courseId: String,
    val courseName: String,
    val title: String,
    val description: String,
    val unit: String,
    val topicName: String = "",
    val startDateRaw: String? = null,
    val dueDateRaw: String?,
    val dueTimestamp: Long?,
    val attempts: Int = 1,
    val fileFilter: String = "",
    val isEnded: Boolean = false,
    val isCompletedLocal: Boolean,
    val isSubmittedRemote: Boolean,
    val isNew: Boolean
)

data class ForumTopic(
    val remoteId: String,
    val rawForumId: String = "",
    val courseId: String,
    val courseName: String,
    val title: String,
    val unitName: String,
    val description: String,
    val startDateRaw: String? = null,
    val closeDateRaw: String?,
    val closeTimestamp: Long?,
    val author: String,
    val answersCount: Int = 0,
    val isEnded: Boolean = false,
    val isNew: Boolean = false
)

data class Announcement(
    val remoteId: String,
    val courseId: String,
    val courseName: String,
    val title: String,
    val content: String,
    val publishedAtRaw: String?,
    val publishedTimestamp: Long?,
    val sourcePortal: String
)

data class ScheduleSlot(
    val id: Long = 0L,
    val remoteId: String,
    val courseId: String,
    val courseName: String,
    val dayOfWeek: Int,
    val startTime: String,
    val endTime: String,
    val classroom: String,
    val teacher: String,
    val slotType: String,
    val isManual: Boolean
)

data class Exam(
    val remoteId: String,
    val courseId: String,
    val courseName: String,
    val title: String,
    val examType: String,
    val dateRaw: String?,
    val timestamp: Long?,
    val classroom: String,
    val grade: String?
)

data class PersonalNote(
    val id: Long = 0L,
    val title: String,
    val content: String,
    val courseId: String?,
    val courseName: String?,
    val isPinned: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

data class BusRoute(
    val id: String,
    val name: String,
    val corridor: String,
    val shift: String,
    val scheduleSummary: String,
    val stops: List<String>,
    val isFavorite: Boolean,
    val notes: String
)

data class SyncStatusInfo(
    val module: String,
    val lastSuccessAt: Long,
    val status: String,
    val message: String
)
