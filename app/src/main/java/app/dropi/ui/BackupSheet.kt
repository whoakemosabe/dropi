package app.dropi.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dropi.R
import app.dropi.data.Backup
import app.dropi.tr
import app.dropi.trn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable
fun BackupSheet(state: AppState) {
    if (!state.backupOpen) return
    SlideSheet(onDismiss = { state.backupOpen = false }) { close -> BackupContent(state, close) }
}

/**
 * Back up to a password-locked file wherever the person chooses (Drive, Files, a USB stick), and
 * restore from one. Restoring adds readings that aren't here yet; nothing already here is lost.
 */
@Composable
private fun ColumnScope.BackupContent(state: AppState, close: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var pass by remember { mutableStateOf("") }
    var pass2 by remember { mutableStateOf("") }
    var note by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var restorePass by remember { mutableStateOf("") }
    val passOk = pass.length >= 6 && pass == pass2

    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            note = try {
                val bytes = state.backupBytes(pass.toCharArray())
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("no stream")
                }
                Haptics.confirm(view)
                pass = ""
                pass2 = ""
                tr("Backed up. Keep the password safe: without it the file can't be opened.")
            } catch (e: Exception) {
                Haptics.reject(view)
                tr("Couldn't save the backup there. Try another place.")
            }
            busy = false
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            restoreUri = uri
            note = null
        }
    }

    StickySheetBody(tr("Backup"), close) {
        Text(
            tr("Your readings, check times, targets and lab results in one file locked with a password. Save it to Drive or Files and you can restore it on a new phone."),
            style = inter(13.sp, color = C.TextSecondary).copy(lineHeight = 19.sp),
        )
        Text(tr("Back up"), style = SectionLabel, modifier = Modifier.padding(top = 4.dp))
        PasswordField(pass, tr("Password (6 characters or more)")) { pass = it }
        PasswordField(pass2, tr("The same password again")) { pass2 = it }
        if (pass2.isNotEmpty() && pass != pass2) Text(tr("The passwords don't match."), style = inter(13.sp, FontWeight.Medium, C.High))
        PrimaryButton(tr("Back up now"), Modifier.fillMaxWidth(), icon = R.drawable.lc_download, enabled = passOk && !busy) {
            create.launch("dropi-backup-${LocalDate.now(state.zone)}.dropi")
        }

        Text(tr("Restore"), style = SectionLabel, modifier = Modifier.padding(top = 12.dp))
        if (restoreUri == null) {
            GhostButton(tr("Choose a backup file"), Modifier.fillMaxWidth(), color = C.AccentText) {
                try { open.launch(arrayOf("*/*")) } catch (e: Exception) { note = tr("No file picker found.") }
            }
        } else {
            Text(tr("File chosen. Enter its password."), style = inter(13.sp, color = C.TextSecondary))
            PasswordField(restorePass, tr("Backup password")) { restorePass = it }
            PrimaryButton(tr("Restore"), Modifier.fillMaxWidth(), enabled = restorePass.isNotEmpty() && !busy) {
                val uri = restoreUri ?: return@PrimaryButton
                busy = true
                scope.launch {
                    note = try {
                        val bytes = withContext(Dispatchers.IO) {
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                // A backup of years of readings is a few MB; refuse anything far bigger.
                                val b = input.readBytes()
                                if (b.size > 50 * 1024 * 1024) error("too big")
                                b
                            } ?: error("no stream")
                        }
                        val added = state.restore(bytes, restorePass.toCharArray())
                        Haptics.confirm(view)
                        restoreUri = null
                        restorePass = ""
                        if (added == 0) tr("Restored. Every reading in the backup was already here.") else trn(added, "Restored {0} reading.", "Restored {0} readings.")
                    } catch (e: Backup.WrongPassword) {
                        Haptics.reject(view)
                        tr("That password doesn't open this file (or the file is damaged).")
                    } catch (e: Backup.NotABackup) {
                        Haptics.reject(view)
                        restoreUri = null
                        tr("That file isn't a Dropi backup.")
                    } catch (e: Exception) {
                        Haptics.reject(view)
                        tr("Couldn't read that file.")
                    }
                    busy = false
                }
            }
            GhostButton(tr("Choose a different file"), Modifier.fillMaxWidth()) { restoreUri = null; restorePass = "" }
        }
        note?.let { Text(it, style = inter(13.sp, FontWeight.Medium, C.Text)) }
    }
}

@Composable
private fun PasswordField(value: String, hint: String, onChange: (String) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Corner.M))
            .background(C.SurfaceLight)
            .padding(horizontal = 16.dp, vertical = 15.dp),
    ) {
        if (value.isEmpty()) Text(hint, style = inter(15.sp, color = C.TextMuted))
        BasicTextField(
            value = value,
            onValueChange = { onChange(it.take(128)) },
            singleLine = true,
            textStyle = inter(15.sp, color = C.Text),
            cursorBrush = SolidColor(C.AccentText),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
