package ca.raymond.livresaudio

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.raymond.livresaudio.ui.AppTheme
import ca.raymond.livresaudio.ui.LibraryScreen
import ca.raymond.livresaudio.ui.PlayerScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        setContent {
            AppTheme {
                AppRoot(viewModel())
            }
        }
    }
}

@Composable
private fun AppRoot(vm: MainViewModel) {
    var showPlayer by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = showPlayer) { showPlayer = false }

    AnimatedContent(
        targetState = showPlayer,
        transitionSpec = {
            if (targetState) {
                (slideInVertically { it / 3 } + fadeIn()) togetherWith fadeOut()
            } else {
                fadeIn() togetherWith (slideOutVertically { it / 3 } + fadeOut())
            }
        },
        label = "navigation",
    ) { player ->
        if (player) {
            PlayerScreen(vm, onBack = { showPlayer = false })
        } else {
            LibraryScreen(vm, onOpenPlayer = { showPlayer = true })
        }
    }
}
