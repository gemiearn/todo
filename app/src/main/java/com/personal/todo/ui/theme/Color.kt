package com.personal.todo.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The design language taken from the tick-list screenshots the user supplied:
 * a light grey canvas, one white rounded card per task with a coloured bar on
 * its left edge, and a single blue accent used for "today", the floating action
 * button and every selected state.
 */

private val Blue = Color(0xFF3D7BF7)
private val BlueDark = Color(0xFF6C9BFF)

private val CanvasLight = Color(0xFFF2F3F5)
private val CanvasDark = Color(0xFF121316)
private val CardLight = Color(0xFFFFFFFF)
private val CardDark = Color(0xFF1E2024)
private val GroupLight = Color(0xFFEDEEF1)
private val GroupDark = Color(0xFF25272C)

private val InkLight = Color(0xFF1C1C1E)
private val InkDark = Color(0xFFE6E7E9)
private val MutedLight = Color(0xFF9A9FA5)
private val MutedDark = Color(0xFF9AA0A6)
private val DoneInkLight = Color(0xFFB4B9BF)
private val DoneInkDark = Color(0xFF6E747B)
private val OutlineLight = Color(0xFFE7E9EC)
private val OutlineDark = Color(0xFF2C2F34)

private val Red = Color(0xFFE5484D)
private val Amber = Color(0xFFF5A623)
private val Green = Color(0xFF3DBE7B)

/**
 * The 专注（番茄钟）页的番茄红, taken from the focus screen the user supplied:
 * the ring, the selected mode, the ± buttons and the start button are this
 * colour, while every other page keeps the blue accent. It is a page of its own
 * on purpose - a timer reads better in tomato than in the app's blue.
 */
private val Tomato = Color(0xFFF0665B)
private val TomatoTrackLight = Color(0xFFF8DBD5)
private val TomatoTrackDark = Color(0xFF4A2E2A)
private val TomatoSurfaceLight = Color(0xFFFDE2DA)
private val TomatoSurfaceDark = Color(0xFF3A2622)

/**
 * The colors material3's [androidx.compose.material3.ColorScheme] has no slot
 * for: the two surfaces of the design, the priority bar of a card and the
 * states a date can be in. Read them through [LocalTodoColors].
 */
@Immutable
data class TodoColors(
    /** The grey background everything else sits on. */
    val canvas: Color,
    /** The surface of a single task or quadrant card. */
    val card: Color,
    /** Surface of the collapsed "已完成 N" group header. */
    val groupHeader: Color,
    /** The one accent: today, FAB, checkbox, selection. */
    val accent: Color,
    val dateToday: Color,
    val dateMuted: Color,
    val dateOverdue: Color,
    /** Text and checkbox fill of a finished task. */
    val doneInk: Color,
    val barNormal: Color,
    val barHigh: Color,
    val barUrgent: Color,
    /** 专注页的番茄红: the ring, the ± buttons and the focus page's selections. */
    val focusAccent: Color,
    /** The unfilled part of the focus ring. */
    val focusTrack: Color,
    /** The tint behind the linked task of a focus session. */
    val focusSurface: Color
)

internal val LightTodoColors = TodoColors(
    canvas = CanvasLight,
    card = CardLight,
    groupHeader = GroupLight,
    accent = Blue,
    dateToday = Blue,
    dateMuted = MutedLight,
    dateOverdue = Red,
    doneInk = DoneInkLight,
    barNormal = Green,
    barHigh = Amber,
    barUrgent = Red,
    focusAccent = Tomato,
    focusTrack = TomatoTrackLight,
    focusSurface = TomatoSurfaceLight
)

val LocalTodoColors = staticCompositionLocalOf { LightTodoColors }

internal val DarkTodoColors = LightTodoColors.copy(
    canvas = CanvasDark,
    card = CardDark,
    groupHeader = GroupDark,
    accent = BlueDark,
    dateToday = BlueDark,
    dateMuted = MutedDark,
    doneInk = DoneInkDark,
    focusTrack = TomatoTrackDark,
    focusSurface = TomatoSurfaceDark
)

/**
 * The material color scheme. Every slot the screens actually use is pinned to
 * the palette above - most importantly `primary`/`primaryContainer` (the FAB
 * and the checkbox) and `secondaryContainer` (the selected navigation entry),
 * which are the blue accent in the screenshots.
 */
internal val LightScheme = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    primaryContainer = Blue,
    onPrimaryContainer = Color.White,
    secondaryContainer = Blue,
    onSecondaryContainer = Color.White,
    background = CanvasLight,
    onBackground = InkLight,
    surface = CanvasLight,
    onSurface = InkLight,
    surfaceVariant = CanvasLight,
    onSurfaceVariant = MutedLight,
    surfaceContainerLowest = CardLight,
    surfaceContainerLow = CardLight,
    surfaceContainer = CanvasLight,
    surfaceContainerHigh = OutlineLight,
    surfaceContainerHighest = OutlineLight,
    outline = OutlineLight,
    outlineVariant = OutlineLight
)

internal val DarkScheme = darkColorScheme(
    primary = BlueDark,
    onPrimary = Color(0xFF10233F),
    primaryContainer = BlueDark,
    onPrimaryContainer = Color(0xFF10233F),
    secondaryContainer = BlueDark,
    onSecondaryContainer = Color(0xFF10233F),
    background = CanvasDark,
    onBackground = InkDark,
    surface = CanvasDark,
    onSurface = InkDark,
    surfaceVariant = CanvasDark,
    onSurfaceVariant = MutedDark,
    surfaceContainerLowest = CardDark,
    surfaceContainerLow = CardDark,
    surfaceContainer = CanvasDark,
    surfaceContainerHigh = OutlineDark,
    surfaceContainerHighest = OutlineDark,
    outline = OutlineDark,
    outlineVariant = OutlineDark
)
