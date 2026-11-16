package sky.universidad.UnApp

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import sky.universidad.UnApp.data.local.database.UnAppDatabase
import sky.universidad.UnApp.data.local.entities.NoteEntity
import sky.universidad.UnApp.data.local.entities.TaskEntity

@RunWith(AndroidJUnit4::class)
class UnAppDatabaseInstrumentedTest {

    private lateinit var db: UnAppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, UnAppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun syncTasksTransactionally_notifiesOnlyOnceForNewTasksAndPreservesCompletion() = runTest {
        val initial = TaskEntity(
            remoteId = "hw_101_1",
            courseId = "101",
            courseName = "Arquitectura",
            title = "Entrega inicial",
            description = "Detalle",
            unit = "U1",
            dueDateRaw = "20/11/2026 23:59",
            dueTimestamp = 1000L,
            isCompletedLocal = false,
            isSubmittedRemote = false,
            isNew = false,
            lastUpdated = 1000L
        )
        // First population does not spam notifications
        val firstInserted = db.taskDao().syncTasksTransactionally(listOf(initial), notifyOnInsert = true)
        assertEquals(0, firstInserted.size)

        // Student marks first task completed locally
        db.taskDao().setLocalCompleted("hw_101_1", true)

        // Second sync brings updated initial + genuinely new task
        val secondNew = initial.copy(
            remoteId = "hw_101_2",
            title = "Nueva práctica",
            lastUpdated = 2000L
        )
        val secondInserted = db.taskDao().syncTasksTransactionally(
            listOf(initial.copy(lastUpdated = 2000L), secondNew),
            notifyOnInsert = true
        )
        assertEquals(1, secondInserted.size)
        assertEquals("hw_101_2", secondInserted.first().remoteId)

        // Local completion must survive remote refresh
        val storedFirst = db.taskDao().findByRemoteId("hw_101_1")
        assertTrue(storedFirst?.isCompletedLocal == true)

        // Third sync with same items triggers zero new notifications
        val thirdInserted = db.taskDao().syncTasksTransactionally(
            listOf(initial, secondNew),
            notifyOnInsert = true
        )
        assertEquals(0, thirdInserted.size)
    }

    @Test
    fun clearAcademicCache_preservesPersonalNotesWhenRequested() = runTest {
        db.noteDao().upsert(
            NoteEntity(
                title = "Apunte personal",
                content = "Resumen de clase",
                courseId = null,
                courseName = null,
                isPinned = true,
                createdAt = 1000L,
                updatedAt = 1000L
            )
        )
        db.clearAcademicCache(clearPersonalNotes = false)
        assertEquals(1, db.noteDao().observeAll().first().size)

        db.clearAcademicCache(clearPersonalNotes = true)
        assertEquals(0, db.noteDao().observeAll().first().size)
    }
}
