package com.artisanai

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.artisanai.ui.screens.MainScreen
import com.artisanai.ui.theme.ArtisanTheme
import com.artisanai.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 启动时不自动弹出软键盘
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        setContent {
            ArtisanTheme {
                val uiState by viewModel.uiState.collectAsState()
                val windowSizeClass = calculateWindowSizeClass(this)

                MainScreen(
                    uiState = uiState,
                    viewModel = viewModel,
                    windowSizeClass = windowSizeClass
                )
            }
        }
    }
}
