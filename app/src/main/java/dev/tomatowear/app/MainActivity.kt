/*
 * Unofficial Tomato Wear adaptation, 2026. Inspired by nsh07/Tomato.
 * GPL-3.0-or-later. Not affiliated with the original developer.
 */
package dev.tomatowear.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.max

private val TomatoRed = Color(0xFFD95E4C)
private val TomatoGreen = Color(0xFF8DA863)
private val Cream = Color(0xFFF7F7EB)
private val Ink = Color(0xFF1F291C)
private val Dark = Color(0xFF101610)
private val DarkCard = Color(0xFF263023)
private val LightCard = Color(0xFFE3E9D8)

private data class WearPalette(val background: Color, val foreground: Color, val card: Color)

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* The timer works without notifications, but alerts may be hidden. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TimerRuntime.reconcile(this)
        setContent {
            val context = LocalContext.current
            val store = remember { TimerStore(context) }
            var page by remember { mutableStateOf("Timer") }
            var revision by remember { mutableIntStateOf(0) }
            var clearConfirm by remember { mutableStateOf(false) }
            val palette = if (isSystemInDarkTheme())
                WearPalette(Dark, Cream, DarkCard) else WearPalette(Cream, Ink, LightCard)

            LaunchedEffect(Unit) {
                while (true) {
                    delay(500L)
                    val s = store.state()
                    if (s.running && s.remaining(System.currentTimeMillis()) <= 0) {
                        TimerRuntime.reconcile(context)
                    }
                    revision++
                }
            }
            BackHandler(page != "Timer") { page = "Timer" }
            // Accessing revision makes this entire view update its displayed countdown.
            @Suppress("UNUSED_VARIABLE") val tick = revision
            val state = store.state()
            val config = store.config()
            val now = System.currentTimeMillis()
            val refresh: () -> Unit = { TimerRuntime.refresh(context); revision++ }
            val action: ((() -> Unit) -> Unit) = { operation ->
                TimerRuntime.reconcile(context)
                operation()
                refresh()
            }

            MaterialTheme {
                Box(Modifier.fillMaxSize().background(palette.background)) {
                    key(page) {
                        when (page) {
                            "Timer" -> TimerPage(
                                state, config, store.stats(), now, palette,
                                onToggle = {
                                    if (Build.VERSION.SDK_INT >= 33) {
                                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                    action { store.toggle() }
                                },
                                onReset = { action { store.reset() } },
                                onSkip = { action { store.skip() } },
                                onSetPhase = { p -> action { store.setPhase(p) } },
                                onGo = { page = it }
                            )
                            "Stats" -> StatsPage(store.stats(), state, now, palette) { page = "Timer" }
                            "Settings" -> SettingsPage(config, palette,
                                onUpdate = { updated -> action { store.updateConfig(updated) } },
                                onTopics = { page = "Topics" },
                                onExact = {
                                    try { startActivity(TimerRuntime.exactAlarmSettings(context)) }
                                    catch (_: ActivityNotFoundException) { /* no special-access UI on this watch */ }
                                },
                                exactEnabled = TimerRuntime.canUseExactAlarms(context),
                                clearConfirm = clearConfirm,
                                onClear = {
                                    if (clearConfirm) {
                                        action { store.clearHistory() }
                                        clearConfirm = false
                                    } else clearConfirm = true
                                },
                                onBack = { page = "Timer" }
                            )
                            "Topics" -> TopicsPage(
                                store.topics(), state.topic, palette,
                                onSelect = { topic -> action { store.selectTopic(topic) }; page = "Timer" },
                                onAdd = { name -> action { store.addTopic(name) } },
                                onBack = { page = "Settings" }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageColumn(palette: WearPalette, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(9.dp),
        content = content
    )
}

@Composable
private fun Caption(text: String, color: Color = TomatoGreen) {
    Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center)
}

@Composable
private fun Heading(text: String, palette: WearPalette) {
    Text(text, color = palette.foreground, fontSize = 20.sp,
        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
}

@Composable
private fun ActionPill(
    text: String, palette: WearPalette, modifier: Modifier = Modifier,
    featured: Boolean = false, onClick: () -> Unit
) {
    Box(modifier.clip(RoundedCornerShape(30.dp))
        .background(if (featured) TomatoRed else palette.card)
        .clickable(onClick = onClick)
        .padding(horizontal = 15.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            color = if (featured) Color.White else palette.foreground,
            textAlign = TextAlign.Center)
    }
}

@Composable
private fun TimerPage(
    state: TimerState, config: TimerConfig, stats: List<TimerStore.DayRecord>,
    now: Long, palette: WearPalette,
    onToggle: () -> Unit, onReset: () -> Unit, onSkip: () -> Unit,
    onSetPhase: (Phase) -> Unit, onGo: (String) -> Unit
) {
    val remaining = state.remaining(now)
    val total = config.duration(state.phase)
    val progress = (1f - remaining.toFloat() / total.coerceAtLeast(1L)).coerceIn(0f, 1f)
    val day = LocalDate.now().toString()
    val todayMs = stats.filter { it.date == day }.sumOf { it.focusMs } +
        if (state.running && state.phase == Phase.FOCUS) (now - state.startEpochMs).coerceIn(0L, state.remainingAtStartMs) - state.accruedMs else 0L
    val sessions = stats.filter { it.date == day }.sumOf { it.completed }

    PageColumn(palette) {
        Caption("TOMATO  /  WEAR")
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            Caption("${state.topic}  ·  ${state.phase.title}", palette.foreground)
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(192.dp), contentAlignment = Alignment.Center) {
            val ringSize = minOf(maxWidth, maxHeight) * 0.88f
            Box(Modifier.size(ringSize), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 10.dp.toPx()
                    drawArc(palette.card, -90f, 360f, false, style = Stroke(stroke, cap = StrokeCap.Round))
                    drawArc(if (state.phase == Phase.FOCUS) TomatoRed else TomatoGreen,
                        -90f, 360f * progress, false,
                        style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(TimerLogic.clock(remaining), color = palette.foreground,
                        fontSize = 35.sp, fontWeight = FontWeight.Bold)
                    Text(if (state.running) "IN PROGRESS" else if (remaining < total) "PAUSED" else "READY",
                        fontSize = 10.sp, color = TomatoGreen, fontWeight = FontWeight.SemiBold)
                    if (state.phase == Phase.FOCUS) {
                        Text("${(state.completedFocusInCycle % config.focusRounds) + 1} / ${config.focusRounds}",
                            fontSize = 11.sp, color = palette.foreground)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
            ActionPill("↺", palette, onClick = onReset)
            ActionPill(if (state.running) "Pause" else "Start", palette, featured = true, onClick = onToggle)
            ActionPill("Skip ›", palette, onClick = onSkip)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            ActionPill("Focus", palette, onClick = { onSetPhase(Phase.FOCUS) })
            ActionPill("Short", palette, onClick = { onSetPhase(Phase.SHORT_BREAK) })
            ActionPill("Long", palette, onClick = { onSetPhase(Phase.LONG_BREAK) })
        }
        Caption("Today  ·  ${max(0L, todayMs) / 60_000L} min focused  ·  $sessions sessions", palette.foreground)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionPill("Stats", palette, onClick = { onGo("Stats") })
            ActionPill("Settings", palette, onClick = { onGo("Settings") })
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun StatsPage(
    stats: List<TimerStore.DayRecord>, state: TimerState,
    now: Long, palette: WearPalette, onBack: () -> Unit
) {
    val today = LocalDate.now()
    val elapsedLive = if (state.running && state.phase == Phase.FOCUS)
        ((now - state.startEpochMs).coerceIn(0L, state.remainingAtStartMs) - state.accruedMs)
            .coerceAtLeast(0L) else 0L
    val todayMs = stats.filter { it.date == today.toString() }.sumOf { it.focusMs } + elapsedLive
    val weekKeys = (0..6).map { today.minusDays(it.toLong()).toString() }
    val monthKeys = (0..29).map { today.minusDays(it.toLong()).toString() }.toSet()
    val lastWeek = stats.filter { it.date in weekKeys }.sumOf { it.focusMs } + elapsedLive
    val lastMonth = stats.filter { it.date in monthKeys }.sumOf { it.focusMs } + elapsedLive
    val sessions = stats.filter { it.date in monthKeys }.sumOf { it.completed }
    val dayValues = weekKeys.reversed().map { date ->
        (stats.filter { it.date == date }.sumOf { it.focusMs } +
            if (date == today.toString()) elapsedLive else 0L) / 60_000L
    }
    val maximum = max(1L, dayValues.maxOrNull() ?: 0)
    val byTopic = stats.filter { it.date in monthKeys }.groupBy { it.topic }
        .mapValues { entry -> entry.value.sumOf { it.focusMs } / 60_000L }
        .toList().sortedByDescending { it.second }

    PageColumn(palette) {
        Heading("Focus statistics", palette)
        Caption("TODAY")
        Text("${todayMs / 60_000L} min", fontSize = 30.sp,
            fontWeight = FontWeight.Bold, color = palette.foreground)
        Caption("Last 7 days: ${lastWeek / 60_000L} min", palette.foreground)
        Caption("Last 30 days: ${lastMonth / 60_000L} min", palette.foreground)
        Caption("Completed sessions: $sessions", palette.foreground)
        Spacer(Modifier.height(5.dp))
        Caption("FOCUS BY DAY · LAST 7 DAYS")
        for ((i, min) in dayValues.withIndex()) {
            val date = weekKeys.reversed()[i]
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(date.takeLast(2), fontSize = 11.sp, color = palette.foreground)
                Box(Modifier.weight(1f).height(13.dp).clip(RoundedCornerShape(8.dp))
                    .background(palette.card)) {
                    if (min > 0) Box(Modifier.fillMaxWidth((min.toFloat() / maximum).coerceIn(0f, 1f))
                        .height(13.dp).clip(RoundedCornerShape(8.dp)).background(TomatoGreen))
                }
                Text("$min", fontSize = 11.sp, color = palette.foreground)
            }
        }
        Caption("TOPICS · LAST 30 DAYS")
        if (byTopic.isEmpty()) Caption("No sessions yet", palette.foreground)
        for ((topic, minutes) in byTopic) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(topic, fontSize = 12.sp, color = palette.foreground)
                Text("$minutes min", fontSize = 12.sp, color = palette.foreground)
            }
        }
        ActionPill("‹ Back", palette, onClick = onBack)
    }
}

@Composable
private fun Stepper(
    label: String, value: Int, range: IntRange,
    palette: WearPalette, update: (Int) -> Unit
) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Caption(label, palette.foreground)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionPill("−", palette, onClick = { update((value - 1).coerceIn(range)) })
            Text("$value", color = palette.foreground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            ActionPill("+", palette, onClick = { update((value + 1).coerceIn(range)) })
        }
    }
}

@Composable
private fun SettingSwitch(label: String, value: Boolean, palette: WearPalette, update: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(25.dp)).background(palette.card)
        .clickable { update(!value) }.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp, color = palette.foreground)
        Text(if (value) "ON" else "OFF", fontSize = 11.sp,
            color = if (value) TomatoGreen else palette.foreground, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SettingsPage(
    c: TimerConfig, palette: WearPalette,
    onUpdate: (TimerConfig) -> Unit, onTopics: () -> Unit,
    onExact: () -> Unit, exactEnabled: Boolean,
    clearConfirm: Boolean, onClear: () -> Unit, onBack: () -> Unit
) {
    PageColumn(palette) {
        Heading("Settings", palette)
        Stepper("Focus · minutes", c.focusMinutes, 1..180, palette) { onUpdate(c.copy(focusMinutes = it)) }
        Stepper("Short break · minutes", c.shortBreakMinutes, 1..60, palette) { onUpdate(c.copy(shortBreakMinutes = it)) }
        Stepper("Long break · minutes", c.longBreakMinutes, 1..90, palette) { onUpdate(c.copy(longBreakMinutes = it)) }
        Stepper("Rounds before long break", c.focusRounds, 1..12, palette) { onUpdate(c.copy(focusRounds = it)) }
        SettingSwitch("Vibrate when done", c.vibrate, palette) { onUpdate(c.copy(vibrate = it)) }
        SettingSwitch("Play notification sound", c.sound, palette) { onUpdate(c.copy(sound = it)) }
        SettingSwitch("Auto-start next round", c.autoStart, palette) { onUpdate(c.copy(autoStart = it)) }
        ActionPill("Choose focus topic", palette, onClick = onTopics)
        if (exactEnabled) Caption("Precise alarms enabled")
        else {
            Caption("Precise alarms are off. Alerts may be delayed.", TomatoRed)
            ActionPill("Enable precise alarms", palette, onClick = onExact)
        }
        ActionPill(if (clearConfirm) "Confirm clear history" else "Clear statistics", palette,
            onClick = onClear)
        Caption("Unofficial Wear OS adaptation. GPLv3.", palette.foreground)
        ActionPill("‹ Back", palette, onClick = onBack)
    }
}

@Composable
private fun TopicsPage(
    topics: List<String>, selected: String, palette: WearPalette,
    onSelect: (String) -> Unit, onAdd: (String) -> Unit, onBack: () -> Unit
) {
    var newTopic by remember { mutableStateOf("") }
    PageColumn(palette) {
        Heading("Focus topics", palette)
        Caption("Assign your timer to a topic", palette.foreground)
        for (topic in topics) {
            ActionPill(if (selected == topic) "✓  $topic" else topic, palette,
                featured = selected == topic, modifier = Modifier.fillMaxWidth(),
                onClick = { onSelect(topic) })
        }
        BasicTextField(
            value = newTopic,
            onValueChange = { newTopic = it.take(24) },
            singleLine = true,
            textStyle = TextStyle(color = palette.foreground, fontSize = 15.sp, textAlign = TextAlign.Center),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(palette.card).padding(12.dp),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.Center) {
                    if (newTopic.isEmpty()) Text("New topic name", color = palette.foreground, fontSize = 13.sp)
                    inner()
                }
            }
        )
        ActionPill("Add topic", palette, onClick = {
            if (newTopic.isNotBlank()) { onAdd(newTopic); newTopic = "" }
        })
        ActionPill("‹ Back", palette, onClick = onBack)
    }
}
