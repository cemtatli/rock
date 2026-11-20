package com.cem.rock

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------- shared

/** A dialog surface: a slab of dark stone with a scratched edge. */
@Composable
fun Slab(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            Modifier
                .stone(Sand.copy(alpha = 0.3f), seed = 21, fill = Paint, chip = 9.dp)
                .padding(horizontal = 28.dp, vertical = 30.dp),
        ) { content() }
    }
}

/** label ....... value, one scratched line under it. */
@Composable
fun StoneRow(
    label: String,
    fg: Color,
    enabled: Boolean = true,
    sub: String? = null,
    @androidx.annotation.DrawableRes icon: Int? = null,
    pressable: Boolean = true, // off in long lists: no per-row animation state
    lineSeed: Int = label.hashCode(),
    onClick: (() -> Unit)? = null,
    end: @Composable () -> Unit,
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .alpha(if (enabled) 1f else 0.4f)
                .then(
                    when {
                        onClick == null -> Modifier
                        pressable -> Modifier.tap(enabled = enabled, onClick = onClick)
                        else -> Modifier.clickable(remember { MutableInteractionSource() }, null, enabled = enabled, onClick = onClick)
                    },
                )
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) { HandIcon(icon, fg, size = 28.dp); HGap(10) }
            Column(Modifier.weight(1f)) {
                Text(label, style = Type.Body, color = fg)
                if (sub != null) Text(sub, style = Type.Small, color = fg.copy(alpha = 0.45f))
            }
            end()
        }
        Hairline(fg, seed = lineSeed)
    }
}

@Composable
fun Value(text: String, fg: Color, strong: Boolean = false) =
    Text(text, style = Type.Body, color = if (strong) fg else fg.copy(alpha = 0.5f))

// ---------------------------------------------------------------- home

@Composable
fun Home(
    s: RockState,
    fg: Color,
    pulse: Int,
    flash: Int?,
    clearFlash: () -> Unit,
    tick: Int,
    nfcAvailable: Boolean,
    nfcOn: Boolean,
    toApps: () -> Unit,
    toSettings: () -> Unit,
) {
    val context = LocalContext.current
    var pickTime by remember { mutableStateOf(false) }
    val missing = remember(tick) { missingPermissions(context) }

    LaunchedEffect(flash) {
        if (flash != null) { delay(1800); clearFlash() }
    }

    Column(Modifier.fillMaxSize()) {
        Header(
            stringResource(R.string.app_name),
            fg,
            R.drawable.ic_wheel,
            if (missing > 0) stringResource(R.string.settings) + ", " + stringResource(R.string.permissions_missing) else stringResource(R.string.settings),
            nudge = missing > 0,
            onIcon = toSettings,
        )

        Spacer(Modifier.weight(1f))
        Centered {
            Rock(pulse = pulse, ink = fg)
            Gap(30)
            val word = when {
                !s.paired -> stringResource(R.string.state_pair)
                s.locked -> stringResource(R.string.state_locked)
                else -> stringResource(R.string.state_free)
            }
            Crossfade(word, animationSpec = tween(260), label = "word") {
                CrookedText(it, Type.Huge, fg, tilt = 4f, lift = 2.5.dp)
            }
            Gap(12)
            val hint = when {
                flash != null -> stringResource(flash)
                !nfcAvailable -> stringResource(R.string.hint_no_nfc)
                !nfcOn -> stringResource(R.string.hint_nfc_off)
                !s.paired -> stringResource(R.string.hint_pair)
                s.locked -> stringResource(R.string.hint_unlock)
                else -> stringResource(R.string.hint_lock)
            }
            TypeText(
                hint,
                style = Type.Body,
                color = if (!nfcOn && flash == null) fg else fg.copy(alpha = 0.6f),
                modifier = Modifier.tap(enabled = nfcAvailable && !nfcOn) {
                    context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
                },
            )
        }
        Spacer(Modifier.weight(1f))

        StoneRow(stringResource(R.string.apps), fg, enabled = !s.locked, onClick = toApps) {
            if (s.blocked.isEmpty()) Value(stringResource(R.string.apps_choose), fg)
            else Tally(s.blocked.size, fg.copy(alpha = 0.8f))
        }
        StoneRow(stringResource(R.string.alarm), fg, icon = R.drawable.ic_sun, onClick = { pickTime = true }) {
            Value(if (s.alarmOn) formatMinutes(s.alarmMinutes) else stringResource(R.string.alarm_off), fg, strong = s.alarmOn)
        }
        Gap(14)
    }

    if (pickTime) {
        TimeDialog(
            initial = s.alarmMinutes,
            isOn = s.alarmOn,
            onDismiss = { pickTime = false },
            onSet = { minutes, on ->
                Store.update { it.copy(alarmMinutes = minutes, alarmOn = on) }
                AlarmScheduler.sync(context)
                pickTime = false
            },
        )
    }
}

// ---------------------------------------------------------------- alarm time

@Composable
private fun TimeDialog(initial: Int, isOn: Boolean, onDismiss: () -> Unit, onSet: (Int, Boolean) -> Unit) {
    var hour by remember { mutableIntStateOf(initial / 60) }
    var minute by remember { mutableIntStateOf(initial % 60) }
    Slab(onDismiss) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CrookedText(stringResource(R.string.alarm), Type.Mark, Sand)
            Text(stringResource(R.string.alarm_sub), style = Type.Small, color = Sand.copy(alpha = 0.55f))
            Gap(18)
            Box(contentAlignment = Alignment.Center) {
                Column(Modifier.width(210.dp)) {
                    Hairline(Sand, alpha = 0.4f)
                    Gap(60)
                    Hairline(Sand, alpha = 0.4f)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Wheel(24, hour, Sand) { hour = it }
                    Text(":", style = Type.Huge.copy(fontSize = 44.sp), color = Sand.copy(alpha = 0.6f))
                    Wheel(60, minute, Sand) { minute = it }
                }
            }
            Gap(22)
            Mark(stringResource(R.string.alarm_set), Sand) { onSet(hour * 60 + minute, true) }
            if (isOn) {
                Text(
                    stringResource(R.string.alarm_turn_off),
                    style = Type.Small,
                    color = Sand.copy(alpha = 0.5f),
                    modifier = Modifier.tap { onSet(hour * 60 + minute, false) }.padding(top = 14.dp, bottom = 2.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------- apps

private data class AppItem(val pkg: String, val label: String)

@Composable
fun Apps(s: RockState, fg: Color, close: () -> Unit) {
    val context = LocalContext.current
    val apps by produceState<List<AppItem>?>(null) {
        value = withContext(Dispatchers.IO) { launchableApps(context, s.blocked) }
    }
    Column(Modifier.fillMaxSize()) {
        Header(stringResource(R.string.apps), fg, R.drawable.ic_back, stringResource(R.string.close), iconFirst = true, onIcon = close)
        Text(stringResource(R.string.apps_sub), style = Type.Small, color = fg.copy(alpha = 0.55f))
        Gap(6)
        val list = apps
        if (list == null) {
            Gap(24)
            Text(stringResource(R.string.loading), style = Type.Small, color = fg.copy(alpha = 0.4f))
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(list, key = { it.pkg }, contentType = { 0 }) { app ->
                    val on = app.pkg in s.blocked
                    StoneRow(app.label, fg, pressable = false, lineSeed = app.pkg.hashCode(), onClick = {
                        Store.update { it.copy(blocked = if (on) it.blocked - app.pkg else it.blocked + app.pkg) }
                    }) {
                        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                            if (on) HandIcon(R.drawable.ic_check, fg, size = 30.dp)
                            else HandIcon(R.drawable.ic_ring, fg.copy(alpha = 0.3f), size = 26.dp)
                        }
                    }
                }
            }
        }
    }
}

private fun launchableApps(context: Context, selected: Set<String>): List<AppItem> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
        .filter { it.first != context.packageName }
        .distinctBy { it.first }
        .map { AppItem(it.first, it.second.lowercase()) }
        .sortedWith(compareBy<AppItem> { it.pkg !in selected }.thenBy { it.label })
}

// ---------------------------------------------------------------- settings

private data class Perm(
    val title: String,
    val sub: String,
    val ok: Boolean,
    val missing: Boolean = false, // hardware not there: nothing to grant
    val action: () -> Unit,
)

@Composable
fun SettingsScreen(
    s: RockState,
    fg: Color,
    tick: Int,
    nfcOn: Boolean,
    flash: Int?,
    clearFlash: () -> Unit,
    adding: Boolean,
    setAdding: (Boolean) -> Unit,
    close: () -> Unit,
) {
    val context = LocalContext.current
    var renaming by remember { mutableStateOf<Pebble?>(null) }
    var localTick by remember { mutableIntStateOf(0) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { localTick++ }
    val perms = remember(tick, localTick, nfcOn) { permissions(context) { notifLauncher.launch(it) } }
    val a11yOff = remember(tick) { !BlockerService.isOn(context) }

    LaunchedEffect(flash) {
        if (flash != null) { delay(1800); clearFlash() }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header(stringResource(R.string.settings), fg, R.drawable.ic_back, stringResource(R.string.close), iconFirst = true, onIcon = close)

        // powers: name + check, or a box to tap
        Gap(10)
        perms.forEach { p ->
            StoneRow(p.title, fg, sub = p.sub, onClick = if (p.ok || p.missing) null else p.action) {
                when {
                    p.missing -> Value(stringResource(R.string.alarm_off), fg)
                    p.ok -> HandIcon(R.drawable.ic_check, fg, size = 30.dp)
                    else -> Mark(stringResource(R.string.perm_grant), fg, onClick = p.action)
                }
            }
        }
        if (a11yOff) {
            Gap(8)
            Text(
                stringResource(R.string.restricted_hint),
                style = Type.Small,
                color = fg.copy(alpha = 0.5f),
                modifier = Modifier.tap {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
                    )
                },
            )
        }

        // rocks
        Gap(30)
        CrookedText(stringResource(R.string.section_rock), Type.Mark, fg, tilt = 3f)
        s.rocks.forEach { rock ->
            StoneRow(rock.name, fg, onClick = { renaming = rock }) {
                Text(
                    stringResource(R.string.rock_delete),
                    style = Type.Body,
                    color = fg.copy(alpha = if (s.locked) 0.2f else 0.55f),
                    modifier = Modifier
                        .tap(enabled = !s.locked) { // locked: never strand yourself
                            Store.update { st -> st.copy(rocks = st.rocks.filterNot { it.id == rock.id }) }
                        }
                        .padding(start = 18.dp, top = 6.dp, bottom = 6.dp),
                )
            }
        }
        val addLabel = when {
            flash != null -> stringResource(flash)
            adding -> stringResource(R.string.rock_adding)
            else -> stringResource(R.string.rock_add)
        }
        Column(Modifier.fillMaxWidth().tap(enabled = nfcOn) { setAdding(!adding) }.padding(vertical = 14.dp)) {
            TypeText(addLabel, Type.Body, if (adding) fg else fg.copy(alpha = 0.7f))
        }

        // run away (only while locked)
        if (s.locked) {
            Gap(24)
            HoldToConfirm(
                if (s.escapes > 0) stringResource(R.string.escape_hold) else stringResource(R.string.escape_none),
                fg,
                enabled = s.escapes > 0,
                holdMs = 5000,
                tally = s.escapes,
            ) { Store.update { it.copy(locked = false, escapes = it.escapes - 1) } }
        }
        Gap(40)
    }

    renaming?.let { rock ->
        RenameDialog(rock.name, onDismiss = { renaming = null }) { name ->
            Store.update { st -> st.copy(rocks = st.rocks.map { if (it.id == rock.id) it.copy(name = name) else it }) }
            renaming = null
        }
    }
}

@Composable
private fun RenameDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val save = { text.text.trim().take(24).ifEmpty { initial }.let(onSave) }
    Slab(onDismiss) {
        Column(Modifier.width(250.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            CrookedText(stringResource(R.string.rock_rename), Type.Mark, Sand)
            Gap(20)
            CompositionLocalProvider(
                LocalTextSelectionColors provides TextSelectionColors(Sand, Sand.copy(alpha = 0.3f)),
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { if (it.text.length <= 24) text = it },
                    singleLine = true,
                    textStyle = Type.Mark.copy(color = Sand),
                    cursorBrush = SolidColor(Sand),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            Hairline(Sand, alpha = 0.5f)
            Gap(24)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Mark(stringResource(R.string.alarm_set), Sand) { save() }
            }
        }
    }
}

private fun permissions(context: Context, requestNotif: (String) -> Unit): List<Perm> {
    val list = mutableListOf<Perm>()
    val nfc = android.nfc.NfcAdapter.getDefaultAdapter(context)
    list += Perm(context.getString(R.string.perm_nfc_why), context.getString(R.string.perm_nfc), nfc?.isEnabled == true, missing = nfc == null) {
        if (nfc != null) context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
    }
    list += Perm(context.getString(R.string.perm_a11y_why), context.getString(R.string.perm_a11y), BlockerService.isOn(context)) {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }
    if (Build.VERSION.SDK_INT >= 33) {
        val ok = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        list += Perm(context.getString(R.string.perm_notif_why), context.getString(R.string.perm_notif), ok) {
            requestNotif(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    if (Build.VERSION.SDK_INT >= 34) {
        val ok = context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        list += Perm(context.getString(R.string.perm_fsi_why), context.getString(R.string.perm_fsi), ok) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}")),
            )
        }
    }
    if (Build.VERSION.SDK_INT in 31..32) {
        list += Perm(context.getString(R.string.perm_exact_why), context.getString(R.string.perm_exact), AlarmScheduler.canExact(context)) {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
            )
        }
    }
    return list
}

fun missingPermissions(context: Context) = permissions(context) {}.count { !it.ok && !it.missing }
