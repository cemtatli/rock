package com.cem.rock

import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun formatMinutes(m: Int) = "%02d:%02d".format(m / 60, m % 60)

object AlarmScheduler {
    /** Re-arms (or cancels) the single daily alarm from the current store state. */
    fun sync(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val fire = PendingIntent.getBroadcast(
            context, 1, Intent(context, AlarmReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        am.cancel(fire)
        val s = Store.state.value
        if (!s.alarmOn) return

        val at = nextTrigger(s.alarmMinutes)
        val show = PendingIntent.getActivity(
            context, 2, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), fire)
        } catch (e: SecurityException) {
            // Exact alarm permission missing (Android 12 only). Inexact beats nothing.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, fire)
        }
    }

    fun canExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun nextTrigger(minutes: Int): Long {
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minutes / 60)
            set(Calendar.MINUTE, minutes % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= System.currentTimeMillis()) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        Store.update { it.copy(ringing = true) }
        ContextCompat.startForegroundService(context, Intent(context, AlarmService::class.java))
        AlarmScheduler.sync(context) // arm tomorrow
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        // A reboot is the last-resort way out of a ringing alarm.
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) Store.update { it.copy(ringing = false) }
        AlarmScheduler.sync(context)
    }
}

fun stopAlarm(context: Context) {
    Store.update { it.copy(ringing = false) }
    context.stopService(Intent(context, AlarmService::class.java))
}

/** Plays the alarm sound until the paired tag is tapped. */
class AlarmService : Service() {
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Store.init(this)
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        ServiceCompat.startForeground(this, NOTIF_ID, notification(), type)
        if (!Store.state.value.ringing) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (player == null) ring()
        // Allowed when the full-screen intent is granted or our accessibility service is bound.
        runCatching {
            startActivity(Intent(this, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        return START_STICKY
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "alarm", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                setBypassDnd(true)
            },
        )
        val open = PendingIntent.getActivity(
            this, 3,
            Intent(this, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(getString(R.string.wake))
            .setContentText(getString(R.string.wake_tap))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .build()
    }

    private fun ring() {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: Settings.System.DEFAULT_ALARM_ALERT_URI
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(attrs)
                setDataSource(this@AlarmService, uri)
                isLooping = true
                setWakeMode(this@AlarmService, PowerManager.PARTIAL_WAKE_LOCK)
                prepare()
                start()
            }
        }.getOrNull()

        vibrator = if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION") getSystemService(Vibrator::class.java)
        }
        @Suppress("DEPRECATION")
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 500), 0), attrs)
    }

    override fun onDestroy() {
        player?.run { runCatching { stop() }; release() }
        player = null
        vibrator?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "alarm"
        private const val NOTIF_ID = 7
    }
}

class AlarmActivity : ComponentActivity() {
    private lateinit var reader: TagReader
    private val pulse = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        enableEdgeToEdge()
        onBackPressedDispatcher.addCallback(this) { /* only the stone can stop it */ }

        reader = TagReader(this) { id ->
            if (Store.state.value.isMine(id)) stopAlarm(this) else pulse.intValue++
        }
        val keyguard = getSystemService(KeyguardManager::class.java)

        setContent {
            RockTheme {
                val s by Store.state.collectAsStateWithLifecycle()
                var now by remember { mutableStateOf(clock()) }
                var lockedScreen by remember { mutableStateOf(keyguard.isKeyguardLocked) }
                LaunchedEffect(Unit) {
                    while (true) {
                        now = clock()
                        lockedScreen = keyguard.isKeyguardLocked
                        delay(1000)
                    }
                }
                LaunchedEffect(s.ringing) { if (!s.ringing) finish() }

                Column(
                    Modifier.fillMaxSize().background(Paint).systemBarsPadding().padding(horizontal = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.weight(1f))
                    Fire(ink = Sand)
                    Gap(30)
                    CrookedText(now, Type.Huge, Sand, tilt = 4f, lift = 2.5.dp)
                    CrookedText(stringResource(R.string.wake), Type.Mark, Sand, tilt = 6f)
                    Gap(12)
                    TypeText(
                        when {
                            !s.paired -> stringResource(R.string.wake_no_rock)
                            lockedScreen -> stringResource(R.string.wake_unlock_first)
                            else -> stringResource(R.string.wake_tap)
                        },
                        Type.Body, Sand.copy(alpha = 0.6f),
                    )
                    Spacer(Modifier.weight(1f))
                    when {
                        lockedScreen -> Mark(stringResource(R.string.unlock_screen), Sand) {
                            keyguard.requestDismissKeyguard(this@AlarmActivity, null)
                        }
                        !s.paired -> HoldToConfirm(stringResource(R.string.hold_to_stop), Sand, enabled = true) {
                            stopAlarm(this@AlarmActivity)
                        }
                        s.escapes > 0 -> HoldToConfirm(
                            stringResource(R.string.escape_short), Sand.copy(alpha = 0.6f), enabled = true, holdMs = 5000, tally = s.escapes,
                        ) {
                            Store.update { it.copy(escapes = it.escapes - 1) }
                            stopAlarm(this@AlarmActivity)
                        }
                    }
                    Gap(28)
                }
            }
        }
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

    private fun clock() = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
}
