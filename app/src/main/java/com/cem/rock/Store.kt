package com.cem.rock

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
    }
}

/** A paired NFC tag. [id] is the SHA-256 of the tag UID. */
data class Pebble(val id: String, val name: String)

data class RockState(
    val rocks: List<Pebble> = emptyList(),
    val locked: Boolean = false,
    val blocked: Set<String> = emptySet(),
    val alarmOn: Boolean = false,
    val alarmMinutes: Int = 7 * 60,
    val ringing: Boolean = false,
    val escapes: Int = MAX_ESCAPES,
) {
    val paired get() = rocks.isNotEmpty()
    fun isMine(id: String) = rocks.any { it.id == id }

    companion object {
        const val MAX_ESCAPES = 3
    }
}

/** Tiny single-process store. SharedPreferences on disk, StateFlow in memory. */
object Store {
    private lateinit var sp: SharedPreferences
    private val _state = MutableStateFlow(RockState())
    val state: StateFlow<RockState> = _state

    fun init(context: Context) {
        if (::sp.isInitialized) return
        sp = context.getSharedPreferences("tas", Context.MODE_PRIVATE)
        _state.value = RockState(
            rocks = readRocks(context),
            locked = sp.getBoolean("locked", false),
            blocked = sp.getStringSet("blocked", emptySet())!!.toSet(),
            alarmOn = sp.getBoolean("alarmOn", false),
            alarmMinutes = sp.getInt("alarmMinutes", 7 * 60),
            ringing = sp.getBoolean("ringing", false),
            escapes = sp.getInt("escapes", RockState.MAX_ESCAPES),
        )
    }

    // One "id<TAB>name" per line. Older builds stored a single tag under "tag".
    private fun readRocks(context: Context): List<Pebble> {
        val raw = sp.getString("rocks", null)
        if (raw == null) {
            val old = sp.getString("tag", null) ?: return emptyList()
            return listOf(Pebble(old, context.getString(R.string.rock_default, 1)))
        }
        return raw.lines().filter { it.isNotBlank() }.map {
            val (id, name) = it.split('\t', limit = 2).let { p -> p[0] to p.getOrElse(1) { "" } }
            Pebble(id, name)
        }
    }

    @Synchronized
    fun update(change: (RockState) -> RockState) {
        val s = change(_state.value)
        _state.value = s
        sp.edit()
            .putString("rocks", s.rocks.joinToString("\n") { it.id + "\t" + it.name.replace('\n', ' ').replace('\t', ' ') })
            .remove("tag")
            .putBoolean("locked", s.locked)
            .putStringSet("blocked", s.blocked)
            .putBoolean("alarmOn", s.alarmOn)
            .putInt("alarmMinutes", s.alarmMinutes)
            .putBoolean("ringing", s.ringing)
            .putInt("escapes", s.escapes)
            .commit()
    }
}
