package livan.chinese_chess.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import livan.chinese_chess.ui.theme.AccentGold
import livan.chinese_chess.ui.theme.BubbleCoachBar
import livan.chinese_chess.ui.theme.BubbleGoodBar
import livan.chinese_chess.ui.theme.BubbleSystemBar
import livan.chinese_chess.ui.theme.BubbleThinkingBar
import livan.chinese_chess.ui.theme.BubbleWarnBar
import livan.chinese_chess.ui.theme.PanelBg
import livan.chinese_chess.ui.theme.PanelBorder
import livan.chinese_chess.ui.theme.PanelTop
import livan.chinese_chess.ui.theme.TextCream
import livan.chinese_chess.ui.theme.TextMuted

/**
 * 训练模式教练聊天面板，复刻 coachChat.js / style.css 的气泡样式。
 */
@Composable
fun CoachPanel(
    messages: List<CoachMessage>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(
                Brush.verticalGradient(listOf(PanelTop, PanelBg)),
                RoundedCornerShape(4.dp),
            )
            .border(1.dp, PanelBorder, RoundedCornerShape(4.dp)),
    ) {
        // 头部
        Column(modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 10.dp)) {
            Text(
                text = "大师教练",
                color = AccentGold,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(text = "逐步点评 · 训练模式", color = TextMuted, fontSize = 12.sp)
        }
        HorizontalDivider(color = PanelBorder, thickness = 1.dp)

        val listState = rememberLazyListState()
        // 新消息自动滚到底部
        LaunchedEffect(messages.size) {
            if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(messages, key = { it.id }) { msg ->
                ChatBubble(msg)
            }
        }
    }
}

@Composable
private fun ChatBubble(msg: CoachMessage) {
    val (barColor, bg, alpha) = when (msg.role) {
        BubbleRole.COACH -> Triple(BubbleCoachBar, Color(0x47000000), 1f) // rgba(0,0,0,0.28)
        BubbleRole.WARN -> Triple(BubbleWarnBar, Color(0x5950320A), 1f) // rgba(80,50,10,0.35)
        BubbleRole.GOOD -> Triple(BubbleGoodBar, Color(0x47000000), 1f)
        BubbleRole.SYSTEM -> Triple(BubbleSystemBar, Color(0x47000000), 0.9f)
        BubbleRole.THINKING -> Triple(BubbleThinkingBar, Color(0x47000000), 0.75f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .alpha(alpha)
            .background(bg, RoundedCornerShape(4.dp))
            .border(1.dp, PanelBorder, RoundedCornerShape(4.dp)),
    ) {
        // 左侧 3px 彩色边条
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(barColor),
        )
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = msg.title,
                color = AccentGold,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            for (line in msg.lines) {
                Text(
                    text = line,
                    color = TextCream,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            // 右下角时间戳
            Text(
                text = msg.time,
                color = TextMuted,
                fontSize = 11.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
        }
    }
}
