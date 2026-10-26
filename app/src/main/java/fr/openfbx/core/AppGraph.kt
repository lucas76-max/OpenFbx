package fr.openfbx.core

import android.app.Application
import android.os.Build
import fr.openfbx.BuildConfig
import fr.openfbx.core.data.BoxRepository
import fr.openfbx.core.storage.BoxStore

/**
 * Conteneur de dépendances minimal (pas de kapt/KSP : le build reste rapide
 * et l'application n'a qu'une seule implémentation par contrat).
 */
object AppGraph {

    lateinit var store: BoxStore
        private set

    lateinit var repository: BoxRepository
        private set

    fun init(app: Application) {
        if (::store.isInitialized) return

        store = BoxStore(app)
        repository = BoxRepository(
            store = store,
            appId = app.packageName,
            appName = "OpenFbx",
            appVersion = BuildConfig.VERSION_NAME,
            deviceName = deviceName(),
        )
    }

    private fun deviceName(): String {
        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        return listOf(manufacturer, model)
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .replaceFirstChar { it.titlecase() }
            .ifBlank { "Android" }
    }
}
