package com.deportv.app.data

import android.content.Context

class Settings(ctx: Context) {
    private val p = ctx.getSharedPreferences("deportv", Context.MODE_PRIVATE)

    var config: XtreamConfig?
        get() {
            val s = p.getString("server", null) ?: return null
            val u = p.getString("user", null) ?: return null
            val pw = p.getString("pass", null) ?: return null
            return XtreamConfig(s, u, pw, p.getString("format", "ts") ?: "ts")
        }
        set(v) {
            p.edit().apply {
                if (v == null) { remove("server"); remove("user"); remove("pass") }
                else { putString("server", v.server); putString("user", v.username); putString("pass", v.password); putString("format", v.format) }
            }.apply()
        }

    /** Equipos favoritos separados por comas, p.ej. "Barcelona, Girona" */
    var favoriteTeams: String
        get() = p.getString("fav", "") ?: ""
        set(v) = p.edit().putString("fav", v).apply()

    val favoriteList: List<String> get() = favoriteTeams.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    /** true = reproductor propio; false = VLC/MX u otro externo */
    var internalPlayer: Boolean
        get() = p.getBoolean("internal", true)
        set(v) = p.edit().putBoolean("internal", v).apply()

    /** Categorías elegidas a mano (vacío = detección automática de deportes) */
    var categoryIds: Set<String>
        get() = p.getStringSet("cats", emptySet()) ?: emptySet()
        set(v) = p.edit().putStringSet("cats", v).apply()

    var lastRefresh: Long
        get() = p.getLong("last", 0)
        set(v) = p.edit().putLong("last", v).apply()
}
