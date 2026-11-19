package com.robloxhihi.recallkeeper.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.security.MessageDigest

object Store {

    /**
     * Nhan app hard-code chu khong dung PackageManager de lay label.
     * Ly do: tu Android 11, muon doc thong tin app khac phai khai <queries> hoac
     * QUERY_ALL_PACKAGES. Tao chon KHONG xin them quyen nao.
     */
    private val LABELS = mapOf(
        "com.facebook.orca" to "Messenger",
        "com.facebook.katana" to "Facebook",
        "com.facebook.lite" to "Facebook Lite",
        "com.facebook.mlite" to "Messenger Lite"
    )

    val DEFAULT_WATCHED = setOf(
        "com.facebook.orca",
        "com.facebook.katana",
        "com.facebook.lite",
        "com.facebook.mlite"
    )

    private lateinit var prefs: SharedPreferences
    lateinit var db: AppDb
        private set

    val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(context: Context) {
        val app = context.applicationContext
        prefs = app.getSharedPreferences("rk", Context.MODE_PRIVATE)
        db = AppDb.get(app)
    }

    fun label(pkg: String): String = LABELS[pkg] ?: pkg

    fun watched(): Set<String> = prefs.getStringSet("watched", DEFAULT_WATCHED) ?: DEFAULT_WATCHED

    fun setWatched(value: Set<String>) = prefs.edit().putStringSet("watched", value).apply()

    var paused: Boolean
        get() = prefs.getBoolean("paused", false)
        set(value) = prefs.edit().putBoolean("paused", value).apply()

    /** Hien thong bao "app dang chay" khi listener noi duoc + lan bat tin dau tien. */
    var ping: Boolean
        get() = prefs.getBoolean("ping", true)
        set(value) = prefs.edit().putBoolean("ping", value).apply()

    var retentionDays: Int
        get() = prefs.getInt("retention", 30)
        set(value) = prefs.edit().putInt("retention", value.coerceIn(1, 365)).apply()

    var firstPingDone: Boolean
        get() = prefs.getBoolean("first_ping", false)
        set(value) = prefs.edit().putBoolean("first_ping", value).apply()

    var lastCaptureAt: Long
        get() = prefs.getLong("last_capture", 0L)
        set(value) = prefs.edit().putLong("last_capture", value).apply()

    var lastPruneAt: Long
        get() = prefs.getLong("last_prune", 0L)
        set(value) = prefs.edit().putLong("last_prune", value).apply()

    var listenerConnectedAt: Long
        get() = prefs.getLong("listener_at", 0L)
        set(value) = prefs.edit().putLong("listener_at", value).apply()

    fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
