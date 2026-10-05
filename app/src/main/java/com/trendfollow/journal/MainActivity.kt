package com.trendfollow.journal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trendfollow.journal.ui.AccountBar
import com.trendfollow.journal.ui.JournalViewModel
import com.trendfollow.journal.ui.MarketScreen
import com.trendfollow.journal.ui.SettingsScreen
import com.trendfollow.journal.ui.TodayScreen

private data class Tab(val title: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("오늘", Icons.Filled.Home),
    Tab("시장일지", Icons.Filled.DateRange),
    Tab("계산기", Icons.Filled.Settings),
)

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                val vm: JournalViewModel = viewModel()
                val state by vm.state.collectAsStateWithLifecycle()
                var selected by rememberSaveable { mutableIntStateOf(0) }

                Scaffold(
                    topBar = {
                        Column {
                            CenterAlignedTopAppBar(title = { Text("추세추종 매매일지 · ${tabs[selected].title}") })
                            AccountBar(state.accounts, state.selected, vm::selectAccount)
                        }
                    },
                    bottomBar = {
                        NavigationBar {
                            tabs.forEachIndexed { i, tab ->
                                NavigationBarItem(
                                    selected = selected == i,
                                    onClick = { selected = i },
                                    icon = { Icon(tab.icon, contentDescription = tab.title) },
                                    label = { Text(tab.title) },
                                )
                            }
                        }
                    },
                ) { padding ->
                    Box(Modifier.padding(padding)) {
                        when (selected) {
                            0 -> TodayScreen(state, vm)
                            1 -> MarketScreen(state, vm)
                            else -> SettingsScreen(state, vm)
                        }
                    }
                }
            }
        }
    }
}
