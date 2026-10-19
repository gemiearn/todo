package com.personal.todo.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.personal.todo.data.BackgroundStyle
import com.personal.todo.data.ColorStyle

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
/**
 * 「已完成」任务标题的颜色：比正文淡，但**必须看得见**。
 *
 * 原来那版 #B4B9BF 在白卡片上的对比度只有 1.98:1（审查报告指出的问题）——完成态的标题
 * 几乎糊在卡片上，而这里没有删除线之类的第二信号，只有这一个颜色在说「做完了」。
 * 现在这一档约 4:1：仍然明显比正文淡，但读得清。
 */
private val DoneInkLight = Color(0xFF7F848A)
private val DoneInkDark = Color(0xFF6E747B)

/**
 * 未勾选时那个圈的颜色。它是**可点的控件**，不是装饰：原来 #D2D6DB 在白卡片上只有
 * 1.47:1，勾选钮看起来像一片空白（审查报告指出的问题）。现在约 3.1:1。
 */
private val CheckOutlineLight = Color(0xFF8E939A)
private val CheckOutlineDark = Color(0xFF464A52)
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
    /** 任务卡上「未勾选」那个圆圈的描边色（稿子⑤里的浅灰）。 */
    val checkOutline: Color,
    /** 四象限的四个颜色，随设置里的「色系」整体切换。 */
    val quadrants: QuadrantColors,
    /** 成功那种绿：已完成的专注时长用它，和象限 Ⅳ 的绿是两回事。 */
    val success: Color,
    /** 日历里的圆点：**还没完成**用琥珀橙。固定色，不随「色系」设置变。 */
    val dotOpen: Color,
    /** 日历里的圆点：**已完成**用绿。固定色，不随「色系」设置变。 */
    val dotDone: Color,
    /** 专注页的番茄红: the ring, the ± buttons and the focus page's selections. */
    val focusAccent: Color,
    /** The unfilled part of the focus ring. */
    val focusTrack: Color,
    /** The tint behind the linked task of a focus session. */
    val focusSurface: Color
)

/** 四象限的四个颜色：Ⅰ 重要紧急、Ⅱ 重要不紧急、Ⅲ 紧急不重要、Ⅳ 都不占。 */
@Immutable
data class QuadrantColors(
    val importantUrgent: Color,
    val importantNotUrgent: Color,
    val notImportantUrgent: Color,
    val notImportantNotUrgent: Color
)

/** 经典：红 / 琥珀 / 蓝 / 绿，就是从截图里取的那一套。 */
private val ClassicQuadrants = QuadrantColors(
    importantUrgent = Red,
    importantNotUrgent = Amber,
    notImportantUrgent = Blue,
    notImportantNotUrgent = Green
)

/** 马卡龙：浅而明快的玫瑰 / 蜜桃 / 天蓝 / 薄荷，最「卡通、清新」的一套。 */
private val MacaronQuadrants = QuadrantColors(
    importantUrgent = Color(0xFFF06292),
    importantNotUrgent = Color(0xFFFFB74D),
    notImportantUrgent = Color(0xFF4FC3F7),
    notImportantNotUrgent = Color(0xFF4DD0A6)
)

/** 莫兰迪：低饱和的灰玫红 / 灰橙 / 灰蓝 / 灰绿，安静、偏高级的一套。 */
private val MorandiQuadrants = QuadrantColors(
    importantUrgent = Color(0xFFB56A6A),
    importantNotUrgent = Color(0xFFC9A26B),
    notImportantUrgent = Color(0xFF7E93A8),
    notImportantNotUrgent = Color(0xFF7FA08A)
)

/** 设置页里选的色系对应哪四个象限色。 */
fun ColorStyle.quadrantColors(): QuadrantColors = when (this) {
    ColorStyle.CLASSIC -> ClassicQuadrants
    ColorStyle.MACARON -> MacaronQuadrants
    ColorStyle.MORANDI -> MorandiQuadrants
}

/**
 * 背景风格往底色里掺的那点颜色；[BackgroundStyle.PLAIN] 是 null，什么都不掺。
 *
 * 只给一个「掺什么色」，具体掺多浓由 [tintBackground] 决定——那样深浅主题可以给不同的
 * 比例，不用为每种风格各写一套色。
 */
fun BackgroundStyle.tintColor(): Color? = when (this) {
    BackgroundStyle.PLAIN -> null
    BackgroundStyle.WARM -> Color(0xFFE4C08A)
    BackgroundStyle.MINT -> Color(0xFF93CFB8)
    BackgroundStyle.LILAC -> Color(0xFFB9ACDF)
    BackgroundStyle.DUSK -> Color(0xFF889FBE)
}

/** 浅色主题下掺得明显一点，深色主题下只轻轻提一下，别把暗色版面提花。 */
private const val CanvasTintLight = 0.18f
private const val CanvasTintDark = 0.10f
private const val CardTintLight = 0.09f
private const val CardTintDark = 0.05f

/**
 * 按背景风格给一份配色掺色：页面底、分组底、卡片底三处一起动，卡片仍与页面底拉开层次，
 * 文字与强调色一个都不碰。
 */
fun TodoColors.tintBackground(style: BackgroundStyle, dark: Boolean): TodoColors {
    val tint = style.tintColor() ?: return this
    val canvasAmount = if (dark) CanvasTintDark else CanvasTintLight
    val cardAmount = if (dark) CardTintDark else CardTintLight
    return copy(
        canvas = lerp(canvas, tint, canvasAmount),
        groupHeader = lerp(groupHeader, tint, canvasAmount),
        card = lerp(card, tint, cardAmount)
    )
}

internal val LightTodoColors = TodoColors(
    canvas = CanvasLight,
    card = CardLight,
    groupHeader = GroupLight,
    accent = Blue,
    dateToday = Blue,
    dateMuted = MutedLight,
    dateOverdue = Red,
    doneInk = DoneInkLight,
    checkOutline = CheckOutlineLight,
    quadrants = ClassicQuadrants,
    success = Green,
    dotOpen = Amber,
    dotDone = Green,
    focusAccent = Tomato,
    focusTrack = TomatoTrackLight,
    focusSurface = TomatoSurfaceLight
)

val LocalTodoColors = staticCompositionLocalOf { LightTodoColors }

/**
 * 标签调色板：新建标签时从这几个色里挑一个，卡片上的标签小片也用同一个色。
 *
 * 存进库的是 ARGB 整数（`tags.color`），所以这里给的是不随深浅主题变的具体颜色，
 * 标签的颜色在任何主题下读起来都该是一样的。
 */
val TagPalette: List<Color> = listOf(
    Color(0xFFE5484D),
    Color(0xFFF5A623),
    Color(0xFF3DBE7B),
    Color(0xFF3D7BF7),
    Color(0xFF8E7CF7),
    Color(0xFFF0665B),
    Color(0xFF00B4D8),
    Color(0xFF9A9FA5)
)

/** 新建标签时的默认色：调色板的第一个。 */
val DefaultTagColor: Color = TagPalette.first()

internal val DarkTodoColors = LightTodoColors.copy(
    canvas = CanvasDark,
    card = CardDark,
    groupHeader = GroupDark,
    accent = BlueDark,
    dateToday = BlueDark,
    dateMuted = MutedDark,
    doneInk = DoneInkDark,
    checkOutline = CheckOutlineDark,
    focusTrack = TomatoTrackDark,
    focusSurface = TomatoSurfaceDark
)

/**
 * App 的页面底色（已按「背景风格」掺过色）。
 *
 * `MainActivity` 用它刷**窗口背景**，壳层 `Scaffold` 用它当 `containerColor`：
 * 两处必须是同一个值——窗口底是系统在 Compose 画满之前露出来的那一层，颜色对不上，
 * 切页时就会闪一下白 / 黑。
 */
fun todoCanvasColor(dark: Boolean, backgroundStyle: BackgroundStyle): Color =
    (if (dark) DarkTodoColors else LightTodoColors)
        .tintBackground(backgroundStyle, dark)
        .canvas

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
