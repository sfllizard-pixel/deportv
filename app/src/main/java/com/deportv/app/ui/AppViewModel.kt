package com.deportv.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.deportv.app.data.EventBuilder
import com.deportv.app.data.Repository
import com.deportv.app.data.Settings
import com.deportv.app.data.SportEvent
import com.deportv.app.update.Updater
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Screen { SETUP, HOME, GUIDE, SETTINGS }

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val settings = Settings(app)
    val repo = Repository(app, settings)

    var screen by mutableStateOf(if (settings.config == null) Screen.SETUP else Screen.HOME)
    var events by mutableStateOf<List<SportEvent>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var progress by mutableStateOf("")
        private set
    var error by mutableStateOf<String?>(null)
    var now by mutableStateOf(System.currentTimeMillis())
        private set

    init {
        viewModelScope.launch {
            events = repo.cached()
            if (settings.config != null && (events.isEmpty() || now - settings.lastRefresh > 3 * 3600_000L)) refresh()
        }
        checkUpdate()
        // Reloj + recálculo de "en directo" cada minuto
        viewModelScope.launch {
            while (true) {
                delay(60_000)
                now = System.currentTimeMillis()
                events = EventBuilder.rescore(events, settings.favoriteList, now)
            }
        }
    }

    fun refresh() {
        if (loading) return
        viewModelScope.launch {
            loading = true; error = null
            try {
                val r = repo.refresh { progress = it }
                events = r.events
                progress = if (r.events.isEmpty())
                    "No se encontraron eventos en ${r.channelCount} canales. Revisa las categorías en Ajustes."
                else ""
            } catch (e: Exception) {
                error = "Error al actualizar: ${e.message}"
            } finally {
                loading = false
                now = System.currentTimeMillis()
            }
        }
    }

    // ---------- Actualizaciones ----------
    var update by mutableStateOf<Updater.Release?>(null)
    var updateMsg by mutableStateOf<String?>(null)
    var downloading by mutableStateOf<Int?>(null)
        private set

    fun checkUpdate(manual: Boolean = false) {
        viewModelScope.launch {
            if (manual) updateMsg = "Buscando actualizaciones…"
            try {
                val r = Updater.check()
                update = r
                updateMsg = if (r == null && manual) "Ya tienes la última versión (${Updater.currentVersion})" else null
            } catch (e: Exception) {
                if (manual) updateMsg = "No se pudo comprobar: ${e.message}"
            }
        }
    }

    fun installUpdate() {
        val rel = update ?: return
        val ctx = getApplication<Application>()
        if (!Updater.canInstall(ctx)) {
            updateMsg = "Permite a DeporTV instalar apps (Fuentes desconocidas) y vuelve a pulsar Actualizar"
            Updater.openInstallPermission(ctx)
            return
        }
        viewModelScope.launch {
            try {
                downloading = 0
                val apk = Updater.download(ctx, rel) { downloading = it }
                downloading = null
                Updater.install(ctx, apk)
            } catch (e: Exception) {
                downloading = null
                updateMsg = "Error al descargar: ${e.message}"
            }
        }
    }

    fun reorder() {
        events = EventBuilder.rescore(events, settings.favoriteList, System.currentTimeMillis())
    }
}
