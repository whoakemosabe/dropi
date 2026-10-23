package app.dropi.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dropi.R
import app.dropi.data.Foods
import app.dropi.work.Ai
import app.dropi.work.Scan
import kotlinx.coroutines.launch

/**
 * What you ate: the foods on this reading as tags, a box to add one, the ones you log most as
 * one-tap suggestions (wrapping, never scrolling sideways), and, with on-device AI (Gemini Nano or Gemma), a
 * photo of the plate that fills in a guess for you to check.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FoodPicker(
    foods: List<String>,
    recent: List<String>,
    onChange: (List<String>) -> Unit,
    onMeal: (app.dropi.data.Meal) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var note by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (Ai.checking) Ai.refresh(context) }
    // The photo button is always there; without a model ready, it explains or offers the download.
    var needAi by remember { mutableStateOf(false) }

    var shotUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = shotUri
        if (!ok || u == null) return@rememberLauncherForActivityResult
        working = true
        note = Ai.warmUp ?: "Looking at your plate…"
        scope.launch {
            val g = Ai.mealFromPhoto(context, u)?.let { app.dropi.data.MealAi.applyUsual(it, app.dropi.work.MealWork.usual(context)) }
            Scan.forgetPlate(context)
            working = false
            note = if (g == null) "Couldn't make out the meal. Add the foods yourself."
            else {
                onMeal(g)
                "${Ai.name}'s guess: " + g.items.joinToString(", ") { "${Foods.display(it.name)} ${it.carbs} g" } +
                    ". About ${g.carbs} g carbs" + (g.sugars?.let { ", $it g sugars" } ?: "") + ". Check it against the label or your usual portions."
            }
        }
    }

    fun add(vararg raw: String) {
        val more = raw.map { Foods.normalize(it) }.filter { it.isNotEmpty() && it !in foods }.distinct()
        if (more.isNotEmpty()) onChange((foods + more).take(12))
        text = ""
    }

    if (needAi) {
        AiNeededDialog("Meal photos", "Take a photo", onDismiss = { needAi = false }) {
            needAi = false
            val u = Scan.photoUri(context, "plate.jpg")
            shotUri = u
            try { camera.launch(u) } catch (e: android.content.ActivityNotFoundException) { note = "No camera app found." }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ic(R.drawable.lc_utensils, C.TextMuted, 18.dp)
            Spacer(Modifier.width(10.dp))
            Text("What you ate", style = inter(15.sp, FontWeight.SemiBold), modifier = Modifier.weight(1f))
            Chip(if (working) "Looking…" else "Photo", icon = R.drawable.lc_camera, selected = false) {
                if (working) return@Chip
                if (!Ai.usable) {
                    needAi = true
                    return@Chip
                }
                val u = Scan.photoUri(context, "plate.jpg")
                shotUri = u
                try { camera.launch(u) } catch (e: android.content.ActivityNotFoundException) { note = "No camera app found." }
            }
        }
        if (foods.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                foods.forEach { f ->
                    Row(
                        Modifier
                            .heightIn(min = 36.dp)
                            .clip(RoundedCornerShape(50))
                            .background(C.Accent)
                            .tap { onChange(foods - f) }
                            .semantics { contentDescription = "${Foods.display(f)}, tap to remove" }
                            .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(Foods.display(f), style = inter(13.sp, FontWeight.SemiBold, C.OnAccent))
                        Spacer(Modifier.width(4.dp))
                        Ic(R.drawable.lc_x, C.OnAccent, 14.dp)
                    }
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(C.SurfaceLight)
                .border(1.dp, C.GlassBorder, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            if (text.isEmpty()) Text("Add a food, then Done", style = inter(14.sp, color = C.TextMuted))
            BasicTextField(
                value = text,
                onValueChange = { v ->
                    // A comma finishes a food; whatever follows it is the next one being typed.
                    if (v.contains(',') || v.contains('\n')) {
                        val parts = v.split(',', '\n')
                        add(*parts.dropLast(1).toTypedArray())
                        text = parts.last().trimStart().take(40)
                    } else text = v.take(40)
                },
                textStyle = inter(14.sp, color = C.Text),
                cursorBrush = SolidColor(C.AccentText),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add(text) }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        val suggestions = recent.filter { it !in foods }.take(8)
        if (suggestions.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                suggestions.forEach { f ->
                    Text(
                        "+ " + Foods.display(f),
                        style = inter(13.sp, FontWeight.Medium, C.TextSecondary),
                        modifier = Modifier
                            .heightIn(min = 36.dp)
                            .clip(RoundedCornerShape(50))
                            .border(1.dp, C.Border, RoundedCornerShape(50))
                            .tap { onChange(foods + f) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
        note?.let { Text(it, style = inter(12.sp, FontWeight.Medium, C.TextSecondary).copy(lineHeight = 17.sp)) }
    }
}
