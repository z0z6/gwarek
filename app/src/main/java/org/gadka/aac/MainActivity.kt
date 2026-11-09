package org.gadka.aac

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import org.gadka.aac.ui.BoardScreen
import org.gadka.aac.ui.MainViewModel
import org.gadka.aac.ui.theme.GadkaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Komunikator nie powinien gasnąć w trakcie rozmowy.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            GadkaTheme {
                val vm: MainViewModel = viewModel()
                BoardScreen(vm)
            }
        }
    }
}
