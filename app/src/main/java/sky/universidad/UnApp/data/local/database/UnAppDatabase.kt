package sky.universidad.UnApp.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import sky.universidad.UnApp.data.local.dao.AnnouncementDao
import sky.universidad.UnApp.data.local.dao.BusRouteDao
import sky.universidad.UnApp.data.local.dao.CourseDao
import sky.universidad.UnApp.data.local.dao.ExamDao
import sky.universidad.UnApp.data.local.dao.ForumDao
import sky.universidad.UnApp.data.local.dao.NoteDao
import sky.universidad.UnApp.data.local.dao.ScheduleDao
import sky.universidad.UnApp.data.local.dao.StudentDao
import sky.universidad.UnApp.data.local.dao.SyncDao
import sky.universidad.UnApp.data.local.dao.TaskDao
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

@Database(
    entities = [
        StudentEntity::class,
        CourseEntity::class,
        TaskEntity::class,
        ForumEntity::class,
        AnnouncementEntity::class,
        ScheduleEntity::class,
        ExamEntity::class,
        NoteEntity::class,
        BusRouteEntity::class,
        SyncEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class UnAppDatabase : RoomDatabase() {
    abstract fun studentDao(): StudentDao
    abstract fun courseDao(): CourseDao
    abstract fun taskDao(): TaskDao
    abstract fun forumDao(): ForumDao
    abstract fun announcementDao(): AnnouncementDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun examDao(): ExamDao
    abstract fun noteDao(): NoteDao
    abstract fun busRouteDao(): BusRouteDao
    abstract fun syncDao(): SyncDao

    suspend fun clearAcademicCache(clearPersonalNotes: Boolean) {
        withTransaction {
            studentDao().clear()
            courseDao().clear()
            taskDao().clear()
            forumDao().clear()
            announcementDao().clear()
            scheduleDao().clearRemoteSlots()
            examDao().clear()
            syncDao().clear()
            if (clearPersonalNotes) {
                noteDao().clear()
                scheduleDao().clearAll()
            }
        }
    }

    companion object {
        fun build(context: Context): UnAppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                UnAppDatabase::class.java,
                "unapp_database.db"
            )
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
        }
    }
}
