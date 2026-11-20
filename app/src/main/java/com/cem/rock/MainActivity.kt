package com.cem.rock

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private enum class Screen { Home, Apps, Settings }

class MainActivity : ComponentActivity() {
    private lateinit var reader: TagReader
    private val resumeTick = mutableIntStateOf(0)
    private val pulse = mutableIntStateOf(0)
    private var flash by mutableStateOf<Int?>(null)
    private var adding by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        reader = TagReader(this, ::onTag)
        setContent {
            RockTheme {
                Root(
                    tick = resumeTick.intValue,
                    pulse = pulse.intValue,
                    flash = flash,
                    clearFlash = { flash = null },
                    adding = adding,
                    setAdding = { adding = it },
                    nfcAvailable = reader.available,
                    nfcOn = reader.enabled,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick.intValue++
        if (Store.state.value.ringing) {
            startActivity(Intent(this, AlarmActivity::class.java))
            return
        }
        reader.start()
    }

    // Samsung rejects reader mode in onResume ("Caller is not in foreground"). Retry once focused.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) reader.start()
    }

    override fun onPause() {
        reader.stop()
        super.onPause()
    }

    private fun onTag(id: String) {
        val s = Store.state.value
        pulse.intValue++
        when {
            s.ringing && s.isMine(id) -> stopAlarm(this)
            adding && s.isMine(id) -> flash = R.string.rock_exists
            adding || !s.paired -> {
                val name = getString(R.string.rock_default, s.rocks.size + 1)
                Store.update { it.copy(rocks = it.rocks + Pebble(id, name)) }
                adding = false
                flash = R.string.flash_paired
            }
            !s.isMine(id) -> flash = R.string.flash_wrong
            else -> Store.update { it.copy(locked = !it.locked) }
        }
    }
}

@Composable
private fun Root(
    tick: Int,
    pulse: Int,
    flash: Int?,
    clearFlash: () -> Unit,
    adding: Boolean,
    setAdding: (Boolean) -> Unit,
    nfcAvailable: Boolean,
    nfcOn: Boolean,
) {
    val s by Store.state.collectAsStateWithLifecycle()
    var screen by remember { mutableStateOf(Screen.Home) }
    LaunchedEffect(s.locked) { if (s.locked && screen == Screen.Apps) screen = Screen.Home }

    val bg by animateColorAsState(if (s.locked) Paint else Sand, tween(600), label = "bg")
    val fg by animateColorAsState(if (s.locked) Sand else Paint, tween(600), label = "fg")

    BackHandler(enabled = screen != Screen.Home) { setAdding(false); screen = Screen.Home }

    val view = LocalView.current
    LaunchedEffect(s.locked) {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !s.locked
            isAppearanceLightNavigationBars = !s.locked
        }
    }

    Box(Modifier.fillMaxSize().background(bg).systemBarsPadding().padding(horizontal = 28.dp)) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
            label = "screen",
        ) { current ->
            when (current) {
                Screen.Home -> Home(s, fg, pulse, flash, clearFlash, tick, nfcAvailable, nfcOn, { screen = Screen.Apps }, { screen = Screen.Settings })
                Screen.Apps -> Apps(s, fg) { screen = Screen.Home }
                Screen.Settings -> SettingsScreen(s, fg, tick, nfcOn, flash, clearFlash, adding, setAdding) { setAdding(false); screen = Screen.Home }
            }
        }
    }
}

