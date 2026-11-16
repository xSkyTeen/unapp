package sky.universidad.UnApp.domain.model

enum class CourseResourceType {
    DOCUMENT,
    EXTERNAL_LINK,
    HOMEWORK,
    FORUM,
    EXAM,
    READING,
    VIRTUAL_CLASS
}

data class CourseResourceItem(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val type: CourseResourceType,
    val url: String? = null,
    val dateRaw: String? = null,
    val fileExtension: String = ""
)

data class CourseWeekTopic(
    val id: String,
    val name: String,
    val description: String,
    val resources: List<CourseResourceItem> = emptyList()
)

data class CourseUnitItem(
    val id: String,
    val name: String,
    val topics: List<CourseWeekTopic> = emptyList()
)

data class AttachmentLink(
    val id: String,
    val name: String,
    val url: String?
)

data class StudentDeliveryItem(
    val id: String,
    val dateLabel: String,
    val fileName: String? = null,
    val url: String? = null
)

data class HomeworkDetailInfo(
    val id: String,
    val title: String,
    val description: String,
    val dateBegin: String,
    val dateEnd: String,
    val attempts: Int,
    val fileFilter: String,
    val isGroup: Boolean,
    val teacherFiles: List<AttachmentLink> = emptyList(),
    val studentDeliveries: List<StudentDeliveryItem> = emptyList(),
    val rubricScore: String? = null
)

data class ForumReplyItem(
    val id: String,
    val userName: String,
    val dateView: String,
    val message: String,
    val score: String? = null
)

data class CourseReadingItem(
    val id: String,
    val title: String,
    val unit: String,
    val description: String,
    val type: String,
    val dateRaw: String? = null,
    val fileUrl: String? = null
)

data class CourseQualificationItem(
    val id: String,
    val title: String,
    val category: String,
    val unit: String,
    val score: String,
    val status: String
)

data class CourseParticipant(
    val id: String,
    val name: String,
    val role: String
)

data class CourseGroupItem(
    val id: String,
    val name: String,
    val membersCount: Int = 0,
    val description: String = ""
)

data class CourseConferenceItem(
    val id: String,
    val title: String,
    val dateRaw: String? = null,
    val url: String? = null,
    val status: String = ""
)

data class CourseCalendarEvent(
    val id: String,
    val title: String,
    val startRaw: String? = null,
    val endRaw: String? = null,
    val description: String = ""
)

data class CourseFullDetail(
    val sectionId: String,
    val teachers: List<String> = emptyList(),
    val sectionLabel: String = "",
    val syllabusUrl: String,
    val teacherSyllabusUrl: String,
    val units: List<CourseUnitItem> = emptyList(),
    val readings: List<CourseReadingItem> = emptyList(),
    val qualifications: List<CourseQualificationItem> = emptyList(),
    val participants: List<CourseParticipant> = emptyList(),
    val groups: List<CourseGroupItem> = emptyList(),
    val conferences: List<CourseConferenceItem> = emptyList(),
    val calendarEvents: List<CourseCalendarEvent> = emptyList(),
    val loadedAt: Long = System.currentTimeMillis()
)

data class InAppAcademicAlert(
    val id: Long = System.currentTimeMillis(),
    val title: String,
    val message: String,
    val courseName: String? = null
)

data class CurriculumCourseItem(
    val code: String,
    val name: String,
    val credits: String,
    val hoursSummary: String,
    val prerequisites: String,
    val status: String = ""
)

data class CurriculumCycle(
    val cycleName: String,
    val courses: List<CurriculumCourseItem>
)

data class AcademicHistoryCourse(
    val course: String,
    val academicYear: Int,
    val credits: String,
    val finalGrade: String,
    val attempts: Int,
    val approved: Boolean
)

data class AcademicHistorySemester(
    val periodName: String,
    val courses: List<AcademicHistoryCourse>
)

data class CourseAttendanceItem(
    val sectionId: String,
    val courseName: String,
    val totalClasses: Int,
    val maxAbsences: Int,
    val dictated: Int,
    val assisted: Int,
    val absences: Int
)

data class AdminRecordItem(
    val id: String,
    val category: String,
    val concept: String,
    val dateLabel: String,
    val amountOrStatus: String
)

data class IntranetOverviewData(
    val curriculum: List<CurriculumCycle> = emptyList(),
    val history: List<AcademicHistorySemester> = emptyList(),
    val attendance: List<CourseAttendanceItem> = emptyList(),
    val adminRecords: List<AdminRecordItem> = emptyList(),
    val loadedAt: Long = System.currentTimeMillis()
)
