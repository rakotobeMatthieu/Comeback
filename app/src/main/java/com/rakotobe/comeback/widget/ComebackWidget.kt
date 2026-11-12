package com.rakotobe.comeback.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.rakotobe.comeback.ComebackApp
import com.rakotobe.comeback.CrashLog
import com.rakotobe.comeback.MainActivity
import com.rakotobe.comeback.R
import com.rakotobe.comeback.data.AppState
import java.time.LocalDate
import java.util.Locale

private val WBg = Color(0xFF1C1D21)
private val WItem = Color(0xFF2A2C31)
private val WLime = Color(0xFFB8F25B)
private val WText = Color(0xFFF2F2F0)
private val WMuted = Color(0xFFA1A3A8)
private val WDark = Color(0xFF111214)

private data class WidgetItem(val id: Long, val name: String, val done: Boolean)

class ComebackWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = ComebackApp.repo(context)
        provideContent {
            val state by repo.liveState.collectAsState(initial = null)
            WidgetContent(state)
        }
    }
}

class ComebackWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ComebackWidget()
}

class ToggleHabitAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val habitId = parameters[HabitKey] ?: return
        try {
            ComebackApp.repo(context).toggle(habitId, LocalDate.now())
        } catch (t: Throwable) {
            CrashLog.record(context, t)
        }
    }

    companion object {
        val HabitKey = ActionParameters.Key<Long>("habitId")
    }
}

@Composable
private fun WidgetContent(state: AppState?) {
    val today = state?.todayDay
    val result = today?.result
    val items = if (state == null || result == null) emptyList() else {
        state.habitsOf(result).map { WidgetItem(it.id, it.name, it.id in result.done) }
    }
    val header = if (result == null) "Aujourd'hui" else "Aujourd'hui · ${result.doneCount}/${result.plannedCount}"
    val index = today?.index?.let { "%.1f".format(Locale.FRANCE, it) } ?: "—"

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WBg)
            .cornerRadius(24.dp)
            .padding(12.dp),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = header,
                style = TextStyle(color = ColorProvider(WText), fontSize = 14.sp, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier.defaultWeight(),
                maxLines = 1,
            )
            Text(
                text = "Indice $index",
                style = TextStyle(color = ColorProvider(WMuted), fontSize = 12.sp),
                maxLines = 1,
            )
        }
        Spacer(GlanceModifier.height(8.dp))
        if (items.isEmpty()) {
            Text(
                text = if (state == null) "Chargement…" else "Aucun objectif prévu aujourd'hui. Ajoute-en dans l'app.",
                style = TextStyle(color = ColorProvider(WMuted), fontSize = 13.sp),
                modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>()),
            )
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(items, itemId = { it.id }) { item ->
                    Column(modifier = GlanceModifier.fillMaxWidth()) {
                        WidgetRow(item)
                        Spacer(GlanceModifier.height(6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetRow(item: WidgetItem) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(44.dp)
            .background(if (item.done) WLime else WItem)
            .cornerRadius(14.dp)
            .padding(horizontal = 12.dp)
            .clickable(
                actionRunCallback<ToggleHabitAction>(
                    actionParametersOf(ToggleHabitAction.HabitKey to item.id),
                ),
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(if (item.done) R.drawable.ic_widget_check else R.drawable.ic_widget_circle),
            contentDescription = if (item.done) "Fait" else "À faire",
            modifier = GlanceModifier.size(20.dp),
        )
        Spacer(GlanceModifier.width(10.dp))
        Text(
            text = item.name,
            style = TextStyle(
                color = ColorProvider(if (item.done) WDark else WText),
                fontSize = 14.sp,
                fontWeight = if (item.done) FontWeight.Bold else FontWeight.Medium,
            ),
            maxLines = 1,
        )
    }
}
