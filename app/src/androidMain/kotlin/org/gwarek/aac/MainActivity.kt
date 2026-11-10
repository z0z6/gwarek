package org.gwarek.aac

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import org.gwarek.aac.ai.LocalLlm
import org.gwarek.aac.data.SettingsStore
import org.gwarek.aac.platform.AndroidApp
import org.gwarek.aac.platform.AndroidSpeaker
import org.gwarek.aac.platform.AppAssets
import org.gwarek.aac.platform.AppDeps
import org.gwarek.aac.platform.createDatabase
import org.gwarek.aac.ui.BoardScreen
import org.gwarek.aac.ui.MainViewModel
import org.gwarek.aac.ui.theme.GwarekTheme
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Komunikator nie powinien gasnąć w trakcie rozmowy.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val app = applicationContext
        AndroidApp.context = app
        AppAssets.opener = { path -> app.assets.open(path) }
        LocalLlm.modelsDir = File(app.filesDir, "models")

        setContent {
            GwarekTheme {
                val vm: MainViewModel = viewModel {
                    MainViewModel(
                        AppDeps(
                            db = createDatabase(app),
                            speech = AndroidSpeaker(app),
                            settingsStore = SettingsStore(File(app.filesDir, "settings.properties")),
                            dataDir = app.filesDir,
                        )
                    )
                }
                BoardScreen(vm)
            }
        }
    }
}
