package com.cem.rock

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Watches which app comes to the front. When locked, blocked apps get covered by [BlockActivity]. */
class BlockerService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return
        val s = Store.state.value

        if (s.ringing && pkg !in RING_ALLOWED) {
            open(Intent(this, AlarmActivity::class.java))
            return
        }
        if (s.locked && pkg in s.blocked) {
            open(Intent(this, BlockActivity::class.java).putExtra(BlockActivity.EXTRA_PKG, pkg))
        }
    }

    private fun open(intent: Intent) {
        runCatching {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
    }

    override fun onInterrupt() = Unit

    companion object {
        /** While the alarm rings, everything else is covered — except system UI and calls. */
        private val RING_ALLOWED = setOf(
            "com.android.systemui",
            "com.android.server.telecom",
            "com.android.dialer",
            "com.google.android.dialer",
            "com.samsung.android.incallui",
        )

        fun isOn(context: Context): Boolean {
            val list = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val me = ComponentName(context, BlockerService::class.java)
            return list.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}

class BlockActivity : ComponentActivity() {
    private lateinit var reader: TagReader
    private val pulse = mutableIntStateOf(0)
    private var pkg by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pkg = intent.getStringExtra(EXTRA_PKG)
        onBackPressedDispatcher.addCallback(this) { goHome() }

        reader = TagReader(this) { id ->
            if (Store.state.value.isMine(id)) {
                Store.update { it.copy(locked = false) }
                finish()
            } else {
                pulse.intValue++
            }
        }

        setContent {
            RockTheme {
                val s by Store.state.collectAsStateWithLifecycle()
                LaunchedEffect(s.locked) { if (!s.locked) finish() }
                Column(
                    Modifier.fillMaxSize().background(Paint).systemBarsPadding().padding(horizontal = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.weight(1f))
                    Rock(pulse = pulse.intValue, ink = Sand)
                    Gap(30)
                    CrookedText(stringResource(R.string.state_locked), Type.Huge, Sand, tilt = 4f, lift = 2.5.dp)
                    Gap(12)
                    TypeText(stringResource(R.string.block_sub, appLabel()), Type.Body, Sand.copy(alpha = 0.6f))
                    Spacer(Modifier.weight(1f))
                    Mark(stringResource(R.string.go_home), Sand) { goHome() }
                    Gap(28)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pkg = intent.getStringExtra(EXTRA_PKG)
    }

    override fun onResume() {
        super.onResume()
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

    private fun appLabel(): String {
        val p = pkg ?: return getString(R.string.block_this_app)
        return runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(p, 0)).toString().lowercase()
        }.getOrDefault(getString(R.string.block_this_app))
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    companion object {
        const val EXTRA_PKG = "pkg"
    }
}
