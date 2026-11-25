package com.deportv.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import com.deportv.app.data.SportEvent
import com.deportv.app.ui.*

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent)) {
                val ctx = LocalContext.current
                var picking by remember { mutableStateOf<SportEvent?>(null) }
                var updateDismissed by remember { mutableStateOf<String?>(null) }
                val open: (SportEvent) -> Unit = { e ->
                    if (e.channels.size == 1) play(ctx, vm.settings, e, e.channels.first()) else picking = e
                }
                BackHandler(enabled = vm.screen != Screen.HOME && !(vm.screen == Screen.SETUP && vm.settings.config == null)) {
                    vm.screen = Screen.HOME
                }
                when (vm.screen) {
                    Screen.SETUP -> SetupScreen(vm)
                    Screen.HOME -> HomeScreen(vm, open)
                    Screen.GUIDE -> GuideScreen(vm, open)
                    Screen.SETTINGS -> SettingsScreen(vm)
                }
                vm.update?.let { rel ->
                    if (updateDismissed != rel.version && picking == null && vm.screen != Screen.SETUP)
                        UpdateDialog(vm) { updateDismissed = rel.version }
                }
                picking?.let { e ->
                    ChannelPicker(e, onPick = { c -> picking = null; play(ctx, vm.settings, e, c) }, onDismiss = { picking = null })
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.reorder()
    }
}
