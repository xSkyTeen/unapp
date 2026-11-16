package sky.universidad.UnApp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
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

@Dao
interface StudentDao {
    @Query("SELECT * FROM students LIMIT 1")
    fun observeStudent(): Flow<StudentEntity?>

    @Query("SELECT * FROM students LIMIT 1")
    suspend fun getStudent(): StudentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(student: StudentEntity)

    @Query("DELETE FROM students")
    suspend fun clear()
}

@Dao
interface CourseDao {
    @Query("SELECT * FROM courses ORDER BY name ASC")
    fun observeAll(): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses ORDER BY name ASC")
    suspend fun getAll(): List<CourseEntity>

    @Query("SELECT * FROM courses WHERE remoteId = :remoteId LIMIT 1")
    suspend fun findByRemoteId(remoteId: String): CourseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(courses: List<CourseEntity>)

    @Query("UPDATE courses SET teacher = :teacher, lastUpdated = :updatedAt WHERE remoteId = :remoteId")
    suspend fun updateTeacher(remoteId: String, teacher: String, updatedAt: Long)

    @Query("DELETE FROM courses")
    suspend fun clear()

    @Transaction
    suspend fun replaceAllIfNonEmpty(courses: List<CourseEntity>) {
        if (courses.isEmpty()) return
        clear()
        insertAll(courses)
    }
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY isCompletedLocal ASC, CASE WHEN dueTimestamp IS NULL THEN 1 ELSE 0 END, dueTimestamp ASC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE courseId = :courseId ORDER BY isCompletedLocal ASC, dueTimestamp ASC")
    fun observeByCourse(courseId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE remoteId = :remoteId LIMIT 1")
    suspend fun findByRemoteId(remoteId: String): TaskEntity?

    @Query("SELECT COUNT(*) FROM tasks")
    suspend fun countAll(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Query("UPDATE tasks SET isCompletedLocal = :completed WHERE remoteId = :remoteId")
    suspend fun setLocalCompleted(remoteId: String, completed: Boolean)

    @Query("UPDATE tasks SET isNew = 0 WHERE remoteId = :remoteId")
    suspend fun markSeen(remoteId: String)

    @Query("DELETE FROM tasks")
    suspend fun clear()

    /**
     * Atomically compares incoming remote tasks against stored tasks by remoteId.
     * Preserves local completion flags and returns only genuinely new tasks so
     * notifications never fire repeatedly for existing items.
     */
    @Transaction
    suspend fun syncTasksTransactionally(
        incoming: List<TaskEntity>,
        notifyOnInsert: Boolean
    ): List<TaskEntity> {
        val hadExistingTasks = countAll() > 0
        val newlyInserted = mutableListOf<TaskEntity>()

        for (item in incoming) {
            val existing = findByRemoteId(item.remoteId)
            if (existing == null) {
                val toInsert = item.copy(isNew = hadExistingTasks && notifyOnInsert)
                val insertedId = insert(toInsert)
                if (insertedId > 0L && toInsert.isNew) {
                    newlyInserted.add(toInsert.copy(id = insertedId))
                }
            } else {
                val merged = existing.copy(
                    homeworkId = item.homeworkId,
                    courseId = item.courseId,
                    courseName = item.courseName,
                    title = item.title,
                    description = item.description,
                    unit = item.unit,
                    topicName = item.topicName,
                    startDateRaw = item.startDateRaw,
                    dueDateRaw = item.dueDateRaw,
                    dueTimestamp = item.dueTimestamp,
                    attempts = item.attempts,
                    fileFilter = item.fileFilter,
                    isEnded = item.isEnded,
                    isSubmittedRemote = item.isSubmittedRemote,
                    isCompletedLocal = item.isSubmittedRemote || existing.isCompletedLocal,
                    lastUpdated = item.lastUpdated
                )
                update(merged)
            }
        }
        return newlyInserted
    }
}

@Dao
interface ForumDao {
    @Query("SELECT * FROM forums ORDER BY CASE WHEN closeTimestamp IS NULL THEN 1 ELSE 0 END, closeTimestamp ASC")
    fun observeAll(): Flow<List<ForumEntity>>

    @Query("SELECT * FROM forums WHERE courseId = :courseId ORDER BY closeTimestamp ASC")
    fun observeByCourse(courseId: String): Flow<List<ForumEntity>>

    @Query("SELECT * FROM forums WHERE remoteId = :remoteId LIMIT 1")
    suspend fun findByRemoteId(remoteId: String): ForumEntity?

    @Query("SELECT COUNT(*) FROM forums")
    suspend fun countAll(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(forum: ForumEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ForumEntity>)

    @Query("DELETE FROM forums")
    suspend fun clear()

    @Transaction
    suspend fun syncForumsTransactionally(
        incoming: List<ForumEntity>,
        notifyOnInsert: Boolean
    ): List<ForumEntity> {
        if (incoming.isEmpty()) return emptyList()
        val hadExisting = countAll() > 0
        val newlyInserted = mutableListOf<ForumEntity>()
        for (item in incoming) {
            val existing = findByRemoteId(item.remoteId)
            if (existing == null) {
                val toInsert = item.copy(isNew = hadExisting && notifyOnInsert)
                val insertedId = insert(toInsert)
                if (insertedId > 0L && toInsert.isNew) {
                    newlyInserted.add(toInsert.copy(id = insertedId))
                }
            } else {
                upsertAll(listOf(item.copy(id = existing.id, isNew = existing.isNew)))
            }
        }
        return newlyInserted
    }
}

@Dao
interface AnnouncementDao {
    @Query("SELECT * FROM announcements ORDER BY CASE WHEN publishedTimestamp IS NULL THEN 1 ELSE 0 END, publishedTimestamp DESC, id DESC")
    fun observeAll(): Flow<List<AnnouncementEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<AnnouncementEntity>)

    @Query("DELETE FROM announcements")
    suspend fun clear()
}

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedules ORDER BY dayOfWeek ASC, startTime ASC")
    fun observeAll(): Flow<List<ScheduleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(slot: ScheduleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(slots: List<ScheduleEntity>)

    @Query("DELETE FROM schedules WHERE remoteId = :remoteId")
    suspend fun deleteByRemoteId(remoteId: String)

    @Query("DELETE FROM schedules WHERE isManual = 0")
    suspend fun clearRemoteSlots()

    @Query("DELETE FROM schedules")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceRemoteSlotsIfNonEmpty(slots: List<ScheduleEntity>) {
        if (slots.isEmpty()) return
        clearRemoteSlots()
        upsertAll(slots)
    }
}

@Dao
interface ExamDao {
    @Query("SELECT * FROM exams ORDER BY CASE WHEN timestamp IS NULL THEN 1 ELSE 0 END, timestamp ASC")
    fun observeAll(): Flow<List<ExamEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ExamEntity>)

    @Query("DELETE FROM exams")
    suspend fun clear()
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY isPinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: NoteEntity): Long

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM notes")
    suspend fun clear()
}

@Dao
interface BusRouteDao {
    @Query("SELECT * FROM bus_routes ORDER BY isFavorite DESC, name ASC")
    fun observeAll(): Flow<List<BusRouteEntity>>

    @Query("SELECT COUNT(*) FROM bus_routes")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(routes: List<BusRouteEntity>)

    @Query("UPDATE bus_routes SET isFavorite = :favorite WHERE id = :routeId")
    suspend fun setFavorite(routeId: String, favorite: Boolean)
}

@Dao
interface SyncDao {
    @Query("SELECT * FROM sync_status ORDER BY lastSuccessAt DESC")
    fun observeAll(): Flow<List<SyncEntity>>

    @Query("SELECT * FROM sync_status WHERE module = :module LIMIT 1")
    suspend fun findByModule(module: String): SyncEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(sync: SyncEntity)

    @Query("DELETE FROM sync_status")
    suspend fun clear()
}
