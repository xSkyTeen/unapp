package sky.universidad.UnApp.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "students")
data class StudentEntity(
    @PrimaryKey val id: String,
    val code: String,
    val dni: String,
    val fullName: String,
    val career: String,
    val faculty: String,
    val semester: String,
    val lastUpdated: Long
)

@Entity(
    tableName = "courses",
    indices = [Index(value = ["remoteId"], unique = true)]
)
data class CourseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val remoteId: String,
    val sectionId: String,
    val code: String,
    val name: String,
    val teacher: String,
    val credits: Int,
    val semester: String,
    val lastUpdated: Long,
    val colorHex: String = "#1D4ED8",
    val sourcePortal: String = "aulavirtual",
    val sectionGroup: String = ""
)

@Entity(
    tableName = "tasks",
    indices = [
        Index(value = ["remoteId"], unique = true),
        Index(value = ["courseId"])
    ]
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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
    val isNew: Boolean,
    val lastUpdated: Long
)

@Entity(
    tableName = "forums",
    indices = [
        Index(value = ["remoteId"], unique = true),
        Index(value = ["courseId"])
    ]
)
data class ForumEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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
    val isNew: Boolean = false,
    val lastUpdated: Long
)

@Entity(
    tableName = "announcements",
    indices = [
        Index(value = ["remoteId"], unique = true),
        Index(value = ["courseId"])
    ]
)
data class AnnouncementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val remoteId: String,
    val courseId: String,
    val courseName: String,
    val title: String,
    val content: String,
    val publishedAtRaw: String?,
    val publishedTimestamp: Long?,
    val sourcePortal: String,
    val lastUpdated: Long
)

@Entity(
    tableName = "schedules",
    indices = [
        Index(value = ["remoteId"], unique = true),
        Index(value = ["dayOfWeek"])
    ]
)
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val remoteId: String,
    val courseId: String,
    val courseName: String,
    val dayOfWeek: Int,
    val startTime: String,
    val endTime: String,
    val classroom: String,
    val teacher: String,
    val slotType: String,
    val isManual: Boolean,
    val lastUpdated: Long
)

@Entity(
    tableName = "exams",
    indices = [Index(value = ["remoteId"], unique = true)]
)
data class ExamEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val remoteId: String,
    val courseId: String,
    val courseName: String,
    val title: String,
    val examType: String,
    val dateRaw: String?,
    val timestamp: Long?,
    val classroom: String,
    val grade: String?,
    val lastUpdated: Long
)

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val courseId: String?,
    val courseName: String?,
    val isPinned: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "bus_routes")
data class BusRouteEntity(
    @PrimaryKey val id: String,
    val name: String,
    val corridor: String,
    val shift: String,
    val scheduleSummary: String,
    val stopsDelimited: String,
    val isFavorite: Boolean,
    val notes: String,
    val lastUpdated: Long
)

@Entity(tableName = "sync_status")
data class SyncEntity(
    @PrimaryKey val module: String,
    val lastSuccessAt: Long,
    val status: String,
    val message: String
)
