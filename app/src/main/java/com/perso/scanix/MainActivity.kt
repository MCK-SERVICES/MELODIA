package com.perso.scanix

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.remember
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.perso.scanix.ui.ScanixApp
import com.perso.scanix.ui.ScanixTheme

class MainActivity : ComponentActivity() {

    private val vm: ScanViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )

        setContent {
            ScanixTheme {
                // Scanner Google : cadrage automatique, redressement, filtres, plusieurs pages
                val scanner = remember {
                    GmsDocumentScanning.getClient(
                        GmsDocumentScannerOptions.Builder()
                            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                            .setGalleryImportAllowed(true)
                            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                            .build()
                    )
                }
                val scanLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartIntentSenderForResult()
                ) { res ->
                    if (res.resultCode == Activity.RESULT_OK) {
                        val result = GmsDocumentScanningResult.fromActivityResultIntent(res.data)
                        vm.onPages(result?.pages?.map { it.imageUri }.orEmpty())
                    } else {
                        vm.scanTarget = null
                    }
                }
                // Import direct de photos (sans recadrage)
                val pickLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.PickMultipleVisualMedia(50)
                ) { uris -> vm.onPages(uris) }

                ScanixApp(
                    vm = vm,
                    onScan = { target ->
                        vm.scanTarget = target
                        scanner.getStartScanIntent(this@MainActivity)
                            .addOnSuccessListener { sender ->
                                scanLauncher.launch(IntentSenderRequest.Builder(sender).build())
                            }
                            .addOnFailureListener {
                                vm.scanTarget = null
                                vm.say(
                                    "Le scanner Google se prépare (téléchargement du module). " +
                                        "Vérifie ta connexion et réessaie dans un instant."
                                )
                            }
                    },
                    onImport = {
                        vm.scanTarget = null
                        pickLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    launch = { intent: Intent ->
                        runCatching { startActivity(intent) }
                            .onFailure { vm.say("Aucune application pour ouvrir ce fichier") }
                    },
                )
            }
        }
    }
}
