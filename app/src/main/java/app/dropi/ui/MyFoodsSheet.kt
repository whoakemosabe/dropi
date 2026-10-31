package app.dropi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dropi.data.Foods
import app.dropi.data.Grams
import app.dropi.data.Portions
import app.dropi.tr
import app.dropi.trn
import app.dropi.work.MealWork

/**
 * More › My foods: every food saved as "my …", with its amount and five numbers, newest use
 * first. Each can be removed; to change one, edit it in a meal and turn on "Update my …".
 */
@Composable
fun MyFoodsSheet(state: AppState) {
    if (!state.myFoodsOpen) return
    SlideSheet(onDismiss = { state.myFoodsOpen = false }) { close -> MyFoodsContent(state, close) }
}

@Composable
private fun ColumnScope.MyFoodsContent(state: AppState, close: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    var version by remember { mutableStateOf(0) }
    val saved = remember(version) { MealWork.usual(context) }
    val rank = remember(state.meals) { Foods.recent(state.meals, 100).withIndex().associate { (i, f) -> f to i } }
    val foods = saved.entries.sortedWith(compareBy<Map.Entry<String, app.dropi.data.Usual>> { rank[it.key] ?: Int.MAX_VALUE }.thenBy { it.key })
    var confirm by remember { mutableStateOf<String?>(null) }
    StickySheetBody(tr("My foods"), close) {
        Text(
            tr("Foods saved with your own amounts. They're used before any guess, and come first when you add a food. To change one, tap it in a meal, change the numbers and keep \"Update my …\" on."),
            style = inter(13.sp, color = C.TextSecondary).copy(lineHeight = 19.sp),
        )
        if (foods.isEmpty()) {
            SheetHint(tr("None yet. When you add or change a food in a meal, turn on \"Remember as my …\"."))
            return@StickySheetBody
        }
        Column(Modifier.fillMaxWidth()) {
            RuleHeader(tr("Saved"), trn(foods.size, "{0} food", "{0} foods"))
            foods.forEachIndexed { i, (key, u) ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp).ruleTop(i > 0).padding(vertical = 10.dp).semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(Foods.display(key), style = inter(15.sp, FontWeight.SemiBold))
                        Text(
                            listOfNotNull(
                                Portions.shown(u.portion),
                                tr("{0} g carbs", Grams.text(u.carbs)),
                                u.sugars?.let { tr("{0} g sugars", Grams.text(it)) },
                                u.fibre?.let { tr("{0} g fibre", Grams.text(it)) },
                                u.protein?.let { tr("{0} g protein", Grams.text(it)) },
                                u.fat?.let { tr("{0} g fat", Grams.text(it)) },
                            ).joinToString(" · "),
                            style = mono(12.sp, color = C.TextMuted).copy(lineHeight = 17.sp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    val sure = confirm == key
                    MonoLink(if (sure) tr("Tap again") else tr("Remove")) {
                        if (sure) {
                            Haptics.confirm(view)
                            MealWork.forget(context, key)
                            confirm = null
                            version++
                        } else confirm = key
                    }
                }
            }
        }
    }
}
