package com.universalrp.appforge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.universalrp.appforge.ui.ForgeRoot
import com.universalrp.appforge.ui.ForgeTheme

/** AppForge v1.0 — offline app builder. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ForgeTheme {
                val vm: BuilderViewModel = viewModel()
                ForgeRoot(vm)
            }
        }
    }
}
