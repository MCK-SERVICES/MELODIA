package com.perso.melodia

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.perso.melodia.ui.AuroraBackground
import com.perso.melodia.ui.FolderScreen
import com.perso.melodia.ui.GradientText
import com.perso.melodia.ui.MainScreen
import com.perso.melodia.ui.MelodiaTheme
import com.perso.melodia.ui.NeonButton
import com.perso.melodia.ui.TechLabel
import com.perso.melodia.ui.TextDim
import com.perso.melodia.ui.neonTint

class MainActivity : ComponentActivity() {

    private val vm: MusicViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            MelodiaTheme { App(vm) }
        }
    }
}

private val audioPermission =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

private enum class Stage { Permission, Folders, Library }

@Composable
private fun App(vm: MusicViewModel) {
    val context = LocalContext.current
    fun hasPermission() =
        ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED

    var granted by remember { mutableStateOf(hasPermission()) }
    val folders by vm.selectedFolders.collectAsStateWithLifecycle()

    val permissions = remember {
        buildList {
            add(audioPermission)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasPermission()
    }

    LaunchedEffect(Unit) { if (!granted) launcher.launch(permissions) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = hasPermission() }
    LaunchedEffect(granted) { if (granted) vm.loadLibrary() }

    val stage = when {
        !granted -> Stage.Permission
        folders == null -> Stage.Folders
        else -> Stage.Library
    }

    Crossfade(stage, animationSpec = tween(400), label = "stage") { s ->
        when (s) {
            Stage.Permission -> PermissionScreen(
                onAsk = { launcher.launch(permissions) },
                onSettings = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        )
                    )
                },
            )

            Stage.Folders -> FolderScreen(vm, firstRun = true, onDone = {})
            Stage.Library -> MainScreen(vm)
        }
    }
}

@Composable
private fun PermissionScreen(onAsk: () -> Unit, onSettings: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        AuroraBackground()
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Rounded.GraphicEq, null, tint = Color.White,
                modifier = Modifier.size(84.dp).neonTint(),
            )
            Spacer(Modifier.height(18.dp))
            TechLabel("// initialisation")
            GradientText("Mélodia", 40.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(10.dp))
            Text(
                "Pour lire les musiques de ton téléphone, l'appli a besoin d'accéder à tes fichiers audio.",
                color = TextDim, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            NeonButton("Autoriser l'accès", onAsk, Modifier.fillMaxWidth())
            TextButton(onSettings) { Text("Ouvrir les paramètres", color = TextDim) }
        }
    }
}
