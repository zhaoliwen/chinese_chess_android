package livan.chinese_chess.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = AccentGold,
    onPrimary = XqBlack,
    secondary = Wood,
    onSecondary = XqBlack,
    tertiary = XqRed,
    background = BgDeep,
    onBackground = TextCream,
    surface = PanelBg,
    onSurface = TextCream,
    surfaceVariant = PanelTop,
    onSurfaceVariant = TextMuted,
    outline = PanelBorder,
)

@Composable
fun Chinese_chessTheme(
    content: @Composable () -> Unit,
) {
    // 固定深色国风配色（照抄 style.css），不随系统主题变化
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content,
    )
}
