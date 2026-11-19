package com.robloxhihi.recallkeeper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.robloxhihi.recallkeeper.ui.MainScreen
import com.robloxhihi.recallkeeper.ui.RecallTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RecallTheme { MainScreen() }
        }
    }
}
