package sky.universidad.UnApp

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sky.universidad.UnApp.presentation.MainViewModel
import sky.universidad.UnApp.presentation.navigation.UnAppRoot
import sky.universidad.UnApp.ui.theme.UnAppTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        val app = application as UnAppApplication
        MainViewModel.Factory(app.container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                AndroidColor.TRANSPARENT,
                AndroidColor.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                AndroidColor.TRANSPARENT,
                AndroidColor.TRANSPARENT
            )
        )
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            UnAppTheme(themeMode = state.preferences.themeMode) {
                UnAppRoot(
                    state = state,
                    viewModel = viewModel
                )
            }
        }
    }
}
