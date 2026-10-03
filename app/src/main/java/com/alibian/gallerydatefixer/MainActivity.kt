package com.alibian.gallerydatefixer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.alibian.gallerydatefixer.ui.App
import com.alibian.gallerydatefixer.ui.AppTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                App(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The user may have just granted "All files access" in system settings.
        viewModel.refreshPermission()
    }
}
