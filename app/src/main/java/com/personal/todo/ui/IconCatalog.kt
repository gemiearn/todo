package com.personal.todo.ui

import androidx.annotation.StringRes
import com.personal.todo.R

/**
 * 清单 / 习惯名字前面那个小图标的**种类**目录。
 *
 * 这里只按「工作、学习、饮食……这些是什么东西」分组，不按「清单用 / 习惯用」分组：
 * 同一个图标在两个地方都能选，用户不用先想「这是清单还是习惯」再去找。
 *
 * 用 emoji 文本而不是 Material 图标集：用户参照的是嘀嗒清单那种**彩色**小图标，而本项目
 * 只依赖 `material-icons-core`（能选的图标少、还是单色的）。库里存的就是这个字符串，
 * 空＝没有图标——若以后换成图标集，换的只是这一行与存值格式，列不用改。
 *
 * 每个种类 20 个，共 15 类 = 300 个；`IconCatalogTest` 钉住数量与不重复。
 */
private val WorkIcons = listOf(
    "💼", "📊", "📈", "📉", "📋", "📌", "📎", "🗂️", "🗃️", "🗒️",
    "🗓️", "🖥️", "💻", "⌨️", "📁", "📂", "🖇️", "🔖", "🗄️", "🪪"
)
private val StudyIcons = listOf(
    "📚", "📖", "📕", "📗", "📘", "📙", "📓", "📔", "📒", "✏️",
    "🖊️", "📝", "🎓", "🏫", "🧪", "🔬", "🔭", "🧮", "📐", "📏"
)
private val DailyIcons = listOf(
    "🏠", "🏡", "🚿", "🛁", "🧼", "🧴", "🪥", "🧹", "🧺", "🧻",
    "🪣", "🧽", "🗑️", "🪞", "🕯️", "🔌", "🔋", "🧸", "🚪", "🪟"
)
private val HealthIcons = listOf(
    "❤️", "🩺", "💊", "💉", "🏥", "🧬", "🧠", "🦷", "👁️", "👂",
    "🫀", "🫁", "🩸", "🚑", "😴", "🛌", "🌡️", "💤", "🧑‍⚕️", "👩‍⚕️"
)
private val SportIcons = listOf(
    "🏃", "🚶", "🏋️", "🤸", "🧗", "🚴", "🏊", "⚽", "🏀", "🎾",
    "🏐", "🏓", "🏸", "🥊", "🏈", "⚾", "🏉", "🥋", "🛹", "⛳"
)
private val FoodIcons = listOf(
    "🍎", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓", "🍒", "🍑", "🥭",
    "🍍", "🥝", "🍅", "🥑", "🍳", "🥞", "🍔", "🍕", "🍜", "🍣"
)
private val TravelIcons = listOf(
    "🚗", "🚕", "🚌", "🚎", "🚓", "🚒", "🚐", "🚚", "🛵", "🏍️",
    "🚲", "🛴", "🚆", "✈️", "🚢", "⛵", "🛸", "🧳", "🗼", "🏝️"
)
private val HomeIcons = listOf(
    "🛋️", "🛏️", "🪑", "🪒", "🧷", "🧵", "🪡", "🧶", "🪆", "🖼️",
    "🪔", "🔦", "🧰", "🛠️", "🪜", "🧲", "🪝", "📦", "🧯", "🪤"
)
private val MoneyIcons = listOf(
    "🛒", "🛍️", "💰", "💵", "💴", "💶", "💷", "💸", "💳", "🧾",
    "🪙", "💎", "🏦", "🏧", "🏪", "🏬", "💱", "💲", "🎫", "🧧"
)
private val SocialIcons = listOf(
    "👋", "🤝", "👍", "👏", "🙌", "🙏", "💪", "✌️", "🤞", "🤟",
    "🤘", "👌", "🫶", "😊", "😄", "😁", "😃", "😀", "😆", "😅"
)
private val FunIcons = listOf(
    "🎮", "🕹️", "🎲", "🧩", "🎯", "🎳", "🎪", "🎨", "🖌️", "🖍️",
    "🎭", "🎬", "🎥", "📷", "🎤", "🎧", "🎹", "🎸", "📺", "🪄"
)
private val NatureIcons = listOf(
    "🌱", "🌿", "☘️", "🍀", "🎋", "🌵", "🌴", "🌲", "🌳", "🪴",
    "🌷", "🌸", "🌹", "🌺", "🌊", "🌙", "☀️", "🌈", "❄️", "🍄"
)
private val AnimalIcons = listOf(
    "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐨", "🐯",
    "🦁", "🐮", "🐷", "🐸", "🐧", "🐦", "🦆", "🦅", "🦉", "🦋"
)
private val FestivalIcons = listOf(
    "🎉", "🎊", "🎈", "🎁", "🎂", "🎄", "🎅", "🤶", "🎃", "🎆",
    "🎇", "🧨", "🪅", "🎀", "🏆", "🥇", "🥈", "🥉", "🏅", "🎗️"
)
private val SymbolIcons = listOf(
    "⭐", "🌟", "✨", "⚡", "🔥", "💧", "✅", "❌", "❓", "❗",
    "➕", "➖", "✖️", "✔️", "🔴", "🟠", "🟡", "🟢", "🔵", "🟣"
)

/** 图标的种类。顺序就是弹层里那排分类页签的顺序。 */
internal enum class IconCategory(@StringRes val labelRes: Int, val icons: List<String>) {
    WORK(R.string.icon_category_work, WorkIcons),
    STUDY(R.string.icon_category_study, StudyIcons),
    DAILY(R.string.icon_category_daily, DailyIcons),
    HEALTH(R.string.icon_category_health, HealthIcons),
    SPORT(R.string.icon_category_sport, SportIcons),
    FOOD(R.string.icon_category_food, FoodIcons),
    TRAVEL(R.string.icon_category_travel, TravelIcons),
    HOME(R.string.icon_category_home, HomeIcons),
    MONEY(R.string.icon_category_money, MoneyIcons),
    SOCIAL(R.string.icon_category_social, SocialIcons),
    FUN(R.string.icon_category_fun, FunIcons),
    NATURE(R.string.icon_category_nature, NatureIcons),
    ANIMAL(R.string.icon_category_animal, AnimalIcons),
    FESTIVAL(R.string.icon_category_festival, FestivalIcons),
    SYMBOL(R.string.icon_category_symbol, SymbolIcons)
}

/**
 * 全部可选图标（按种类顺序摊平），共 300 个。
 *
 * `by lazy` 不是性能考虑：`IconCategory` 的构造参数引用了上面那些列表，而这里又要读
 * `IconCategory.entries`——直接初始化会在「枚举还没建完」时读到 null（静态初始化环）。
 */
internal val IconChoices: List<String> by lazy {
    IconCategory.entries.flatMap { it.icons }
}

/** 图标备选里「没有图标」那一项：点它就清掉。 */
internal const val NoIcon = ""
