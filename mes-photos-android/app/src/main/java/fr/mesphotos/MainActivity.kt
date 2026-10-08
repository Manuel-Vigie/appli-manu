package fr.mesphotos

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import fr.mesphotos.storage.Access
import fr.mesphotos.ui.MainScreen
import fr.mesphotos.ui.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    // Android 10 et avant : simple autorisation d'accès au stockage.
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { viewModel.onResume() }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        setContent { MainScreen(viewModel, onRequestAccess = ::requestAccess) }
        if (savedInstanceState == null) handleShare(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShare(intent)
    }

    /** Des photos partagées depuis la galerie du téléphone : on les retrouve sur la carte et on propose de les renommer. */
    @Suppress("DEPRECATION")
    private fun handleShare(intent: Intent?) {
        if (intent == null) return
        val uris: List<android.net.Uri> = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM))
            Intent.ACTION_SEND_MULTIPLE -> intent.getParcelableArrayListExtra<android.net.Uri>(Intent.EXTRA_STREAM).orEmpty()
            else -> emptyList()
        }
        if (uris.isNotEmpty()) {
            viewModel.receiveShared(uris)
            // Pour ne pas recommencer si l'écran pivote ou si l'appli revient au premier plan.
            intent.action = Intent.ACTION_MAIN
        }
    }

    override fun onResume() {
        super.onResume()
        // Retour des réglages d'autorisation : on revérifie l'accès et on recompte les photos.
        viewModel.onResume()
    }

    override fun onStop() {
        super.onStop()
        // Le coffre-fort se referme dès qu'on quitte l'appli (sauf simple rotation de l'écran).
        if (!isChangingConfigurations) viewModel.lockVault()
    }

    private fun requestAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val specific = Access.settingsIntent(this)
            try {
                startActivity(specific)
            } catch (e: Exception) {
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                } catch (e2: Exception) {
                    viewModel.showNotice(
                        "Ouvrez les réglages d'Android, « Applications », « Mes Photos », « Autorisations », " +
                            "puis autorisez l'accès à tous les fichiers.",
                        isError = true,
                    )
                }
            }
        } else {
            permissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                ),
            )
        }
    }
}
