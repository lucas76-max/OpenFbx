package fr.openfbx

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import fr.openfbx.core.AppGraph
import fr.openfbx.core.log.AppLog

/**
 * Application OpenFbx : initialise le graphe de dépendances et capture les
 * exceptions non rattrapées dans le journal interne (puis les laisse remonter
 * au gestionnaire d'origine pour ne pas masquer un plantage).
 */
class OpenFbxApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
        AppLog.i("App", "OpenFbx ${BuildConfig.VERSION_NAME} démarré (API ${Build.VERSION.SDK_INT})")

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            AppLog.e("Crash", "${thread.name} : ${throwable.javaClass.simpleName} — ${throwable.message}")
            throwable.stackTrace.take(12).forEach { frame -> AppLog.e("Crash", "  at $frame") }
            previous?.uncaughtException(thread, throwable)
        }

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                AppLog.d("UI", "Activité créée : ${activity.javaClass.simpleName}")
            }

            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}
