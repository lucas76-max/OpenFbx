package fr.openfbx

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.openfbx.core.AppGraph
import fr.openfbx.core.model.AppSettings
import fr.openfbx.ui.OpenFbxRoot
import fr.openfbx.ui.theme.OpenFbxTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by AppGraph.store.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            OpenFbxTheme(settings) {
                OpenFbxRoot()
            }
        }
    }
}
