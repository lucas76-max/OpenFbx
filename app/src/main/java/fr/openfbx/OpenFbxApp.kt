package fr.openfbx

import android.app.Application
import fr.openfbx.core.AppGraph

class OpenFbxApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
    }
}
