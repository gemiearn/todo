package com.personal.todo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.personal.todo.R
import com.personal.todo.data.db.Tag
import com.personal.todo.data.db.TaskList
import com.personal.todo.model.TaskFilter
import com.personal.todo.ui.theme.LocalTodoColors

/**
 * 顶栏那一行：圆角搜索框 + 「取消」。
 *
 * 用户 2026-10-21 要「搜索的样式、框」重做：原来直接把一个 `OutlinedTextField` 塞进
 * `TopAppBar` 的标题槽，带着 1dp 描边和 Material 的大内边距，看上去像一张没对齐的表单。
 * 现在是一个填色胶囊（无描边、无下划线）、行首小放大镜、行尾清空叉，顶栏右上角放一个
 * 「取消」——搜索态下它就是独占一整行的东西，点哪儿都能退出去。
 */
@Composable
internal fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onCancel: () -> Unit,
    hint: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TodoSearchField(
            query = query,
            onQueryChange = onQueryChange,
            hint = hint,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onCancel) {
            Text(
                text = stringResource(R.string.search_cancel),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

/** 那个填色胶囊搜索框本身（其它页面也能直接用）。 */
@Composable
internal fun TodoSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier
) {
    val colors = LocalTodoColors.current
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.dateMuted,
                maxLines = 1
            )
        },
        singleLine = true,
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = colors.dateMuted,
                modifier = Modifier.size(17.dp)
            )
        },
        // 有内容时才给「清空」那个叉：空框上摆一个叉只会让人以为框里已经有东西。
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.search_clear),
                        tint = colors.dateMuted,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        },
        textStyle = MaterialTheme.typography.bodyMedium,
        shape = RoundedCornerShape(14.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = colors.groupHeader,
            unfocusedContainerColor = colors.groupHeader,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            cursorColor = colors.accent
        ),
        modifier = modifier.height(48.dp)
    )
}

/**
 * 顶栏上的筛选按钮：一个胶囊，里面有漏斗 + 「筛选」两个字；真的筛过东西之后底色变成
 * 强调色的淡版，并在末尾挂一个小圆点报「筛了几条」。
 *
 * 原来是一个只有图标的灰漏斗：看不出是筛什么的，也看不出当前到底有没有在筛。用户
 * 2026-10-21 点名的「上方第二个按钮筛选」就是它。
 */
@Composable
internal fun FilterPill(
    activeCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTodoColors.current
    val active = activeCount > 0
    val label = stringResource(R.string.filter_action)
    val description = if (active) {
        stringResource(R.string.filter_active_badge, activeCount)
    } else {
        label
    }
    val tint = if (active) colors.accent else colors.dateMuted

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (active) colors.accent.copy(alpha = 0.14f) else colors.groupHeader)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description }
            .padding(start = 9.dp, end = if (active) 6.dp else 10.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterGlyph(tint = tint)
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            color = if (active) colors.accent else MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
        if (active) {
            Spacer(Modifier.width(5.dp))
            CountBadge(count = activeCount, tint = colors.accent)
        }
    }
}

/** 淡色底的小圆点计数：筛选胶囊、日历筛选条共用。 */
@Composable
internal fun CountBadge(count: Int, tint: Color) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(tint)
            .padding(horizontal = 5.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            maxLines = 1
        )
    }
}

/** 一份筛选里勾了几个值（三个维度加在一起）：胶囊上的小圆点和筛选面板的分区计数都用它。 */
internal fun TaskFilter.selectedCount(): Int =
    listIds.size + priorities.size + tagIds.size

