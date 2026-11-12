package com.rakotobe.comeback.ui

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rakotobe.comeback.BuildConfig
import com.rakotobe.comeback.CrashLog
import com.rakotobe.comeback.data.AppState
import com.rakotobe.comeback.data.Repository
import com.rakotobe.comeback.reminders.Notifications
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable
fun SettingsScreen(state: AppState, repo: Repository, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var refresh by remember { mutableIntStateOf(0) }
    val crashLog = remember(refresh) { CrashLog.read(context) }
    var pendingImport by remember { mutableStateOf<String?>(null) }
    val canNotify = remember(refresh) { Notifications.canNotify(context) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val json = repo.exportJson()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                }
                Toast.makeText(context, "Sauvegarde exportée", Toast.LENGTH_SHORT).show()
            } catch (t: Throwable) {
                CrashLog.record(context, t)
                Toast.makeText(context, "Échec de l'export", Toast.LENGTH_LONG).show()
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                pendingImport = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                }
            } catch (t: Throwable) {
                CrashLog.record(context, t)
                Toast.makeText(context, "Impossible de lire ce fichier", Toast.LENGTH_LONG).show()
            }
        }
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BackRow("Aujourd'hui", onBack)
        ScreenTitle(overline = "ComeBack v${BuildConfig.VERSION_NAME}", title = "Réglages")

        SectionTitle("Sauvegarde")
        Panel {
            Text(
                "Tes données restent sur ton téléphone. Exporte régulièrement une sauvegarde (Drive, mail…) pour ne jamais perdre ton historique.",
                color = C.Muted, fontSize = 13.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { exportLauncher.launch("comeback-${LocalDate.now()}.json") },
                    colors = ButtonDefaults.buttonColors(containerColor = C.Lime, contentColor = C.Bg),
                    modifier = Modifier.weight(1f).height(48.dp),
                ) { Text("Exporter", fontWeight = FontWeight.SemiBold) }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                    modifier = Modifier.weight(1f).height(48.dp),
                ) { Text("Importer", color = C.Text) }
            }
        }

        SectionTitle("Rappels")
        Panel {
            Text(
                if (canNotify) "Notifications autorisées. Règle l'heure de chaque rappel dans l'objectif concerné."
                else "Les notifications sont désactivées : les rappels ne s'afficheront pas.",
                color = C.Muted, fontSize = 13.sp,
            )
            if (!canNotify && Build.VERSION.SDK_INT >= 33) {
                Button(
                    onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    colors = ButtonDefaults.buttonColors(containerColor = C.Lime, contentColor = C.Bg),
                ) { Text("Autoriser les notifications", fontWeight = FontWeight.SemiBold) }
            }
        }

        SectionTitle("Journal d'erreurs")
        Panel {
            if (crashLog.isBlank()) {
                Text("Aucune erreur enregistrée.", color = C.Muted, fontSize = 13.sp)
            } else {
                Text("Si l'app a planté, copie ce journal et envoie-le à Claude.", color = C.Muted, fontSize = 13.sp)
                Text(
                    crashLog.takeLast(1500),
                    color = C.Text, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(C.Bg)
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            clipboard.setText(AnnotatedString(crashLog))
                            Toast.makeText(context, "Journal copié", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = C.Lime, contentColor = C.Bg),
                        modifier = Modifier.weight(1f),
                    ) { Text("Copier", fontWeight = FontWeight.SemiBold) }
                    OutlinedButton(
                        onClick = {
                            CrashLog.clear(context)
                            refresh++
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Effacer", color = C.Text) }
                }
            }
        }

        SectionTitle("Infos")
        Panel {
            InfoLine("Début du suivi", dayFull(state.startDay))
            InfoLine("Départ des paliers", dayFull(LocalDate.ofEpochDay(state.settings.palierRefDay)))
            InfoLine("Objectifs actifs", "${state.activeHabits.size}")
            InfoLine("Version", BuildConfig.VERSION_NAME)
        }
        Spacer(Modifier.height(24.dp))
    }

    val importText = pendingImport
    if (importText != null) {
        ConfirmDialog(
            title = "Remplacer tes données ?",
            text = "L'import remplace tous tes objectifs et ton historique actuels par ceux du fichier. Pense à exporter avant si besoin.",
            confirmLabel = "Importer",
            onDismiss = { pendingImport = null },
            onConfirm = {
                pendingImport = null
                scope.launch {
                    try {
                        repo.importJson(importText)
                        Toast.makeText(context, "Sauvegarde importée", Toast.LENGTH_SHORT).show()
                    } catch (t: Throwable) {
                        CrashLog.record(context, t)
                        Toast.makeText(context, t.message ?: "Import impossible", Toast.LENGTH_LONG).show()
                    }
                }
            },
        )
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = C.Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = C.Text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
