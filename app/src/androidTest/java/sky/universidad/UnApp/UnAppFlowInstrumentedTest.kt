package sky.universidad.UnApp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UnAppFlowInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun loginToDemoFlow_navigatesAcrossAllPrimaryTabsAndMoreModules() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("UnApp").fetchSemanticsNodes().isNotEmpty()
        }

        // Login screen elements
        composeRule.onNodeWithText("Acceso institucional").assertIsDisplayed()
        composeRule.onNodeWithText("DNI o código de estudiante").assertIsDisplayed()
        composeRule.onNodeWithText("Explorar vista previa sin cuenta").performClick()

        // Home screen + 5-tab bar
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Tareas pendientes").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Cursos").performClick()
        composeRule.waitUntil(timeoutMillis = 3_000) {
            composeRule.onAllNodesWithText("Arquitectura de Software").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("Tareas").performClick()
        composeRule.waitUntil(timeoutMillis = 3_000) {
            composeRule.onAllNodesWithText("Pendientes").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("Horario").performClick()
        composeRule.waitUntil(timeoutMillis = 3_000) {
            composeRule.onAllNodesWithText("Horario semanal").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("Más").performClick()
        composeRule.waitUntil(timeoutMillis = 3_000) {
            composeRule.onAllNodesWithText("Bus universitario").fetchSemanticsNodes().isNotEmpty()
        }

        // Open Bus Universitario module
        composeRule.onNodeWithText("Bus universitario").performClick()
        composeRule.waitUntil(timeoutMillis = 3_000) {
            composeRule.onAllNodesWithText("Bus universitario UNAP").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("Ruta Centro - Ciudad Universitaria").onFirst().assertIsDisplayed()
    }
}
