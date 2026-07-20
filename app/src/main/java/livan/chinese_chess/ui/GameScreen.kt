package livan.chinese_chess.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import livan.chinese_chess.engine.Xiangqi
import livan.chinese_chess.ui.theme.AccentGold
import livan.chinese_chess.ui.theme.BgDeep
import livan.chinese_chess.ui.theme.BgDeepEnd
import livan.chinese_chess.ui.theme.BtnBorder
import livan.chinese_chess.ui.theme.BtnBottom
import livan.chinese_chess.ui.theme.PanelBg
import livan.chinese_chess.ui.theme.PanelBorder
import livan.chinese_chess.ui.theme.PanelTop
import livan.chinese_chess.ui.theme.TextCream
import livan.chinese_chess.ui.theme.TextMuted
import livan.chinese_chess.ui.theme.TurnBlack
import livan.chinese_chess.ui.theme.TurnRed

/**
 * 主界面：顶部标题 + 横屏三栏（控制面板 / 棋盘 / 教练面板）。
 * 布局与文案照抄网页版 index.html 与 main.js。
 */
@Composable
fun GameScreen(vm: GameViewModel = viewModel()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(BgDeep, BgDeepEnd)))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        // 顶部标题
        Text(
            text = "中国象棋 · 人机对弈",
            color = AccentGold,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            letterSpacing = 6.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ControlPanel(
                vm = vm,
                modifier = Modifier
                    .width(200.dp)
                    .fillMaxHeight(),
            )
            // 中央棋盘：外框包在 Canvas 外（padding 让出边框宽度），避免遮挡边缘棋子
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                val targets by remember(vm.board, vm.selected) {
                    derivedStateOf {
                        val s = vm.selected
                        if (s != null && !vm.gameOver) {
                            Xiangqi.getMovesFrom(vm.board, s.r, s.c)
                        } else {
                            emptyList()
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .border(6.dp, Color(0xFF8B6914))
                        .border(3.dp, Color(0xFF5C4033))
                        .padding(9.dp),
                ) {
                    BoardCanvas(
                        board = vm.board,
                        selected = vm.selected,
                        legalTargets = targets,
                        lastMove = vm.lastMove,
                        anim = vm.anim,
                        coachHint = vm.coachHint,
                        interactive = !vm.gameOver && !vm.aiThinking && vm.redToMove,
                        onTap = vm::onUserClick,
                        modifier = Modifier.aspectRatio(9f / 10f),
                    )
                }
            }
            // 右侧教练面板（训练模式开启时显示）
            if (vm.trainMode) {
                CoachPanel(
                    messages = vm.coachMessages,
                    modifier = Modifier
                        .width(280.dp)
                        .fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun ControlPanel(vm: GameViewModel, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(
                Brush.verticalGradient(listOf(PanelTop, PanelBg)),
                RoundedCornerShape(4.dp),
            )
            .border(1.dp, PanelBorder, RoundedCornerShape(4.dp))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 电脑级别
        Column {
            Text("电脑级别", color = TextMuted, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            var expanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(3.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, PanelBorder),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = BgDeep,
                        contentColor = TextCream,
                    ),
                ) {
                    Text(GameViewModel.difficultyLabel(vm.difficulty))
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.background(PanelTop),
                ) {
                    for ((key, label) in GameViewModel.DIFFICULTY_OPTIONS) {
                        DropdownMenuItem(
                            text = { Text(label, color = TextCream) },
                            onClick = {
                                vm.onDifficultyChange(key)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }

        // 训练模式
        Column {
            Text("训练模式", color = TextMuted, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = vm.trainMode,
                    onCheckedChange = { vm.onTrainModeChange(it) },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = Color(0xFF3D2B1A),
                        checkedThumbColor = AccentGold,
                        checkedBorderColor = AccentGold,
                        uncheckedTrackColor = BgDeep,
                        uncheckedThumbColor = TextMuted,
                        uncheckedBorderColor = PanelBorder,
                    ),
                )
                Spacer(Modifier.width(10.dp))
                Text(if (vm.trainMode) "开" else "关", color = TextCream, fontSize = 15.sp)
            }
        }

        // 状态框
        val (turnText, turnColor) = when {
            vm.gameOver -> "对局结束" to AccentGold
            vm.aiThinking -> "电脑思考中…" to TextMuted
            vm.redToMove -> "红方行棋" to TurnRed
            else -> "黑方行棋" to TurnBlack
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .background(Color(0x40000000), RoundedCornerShape(3.dp)),
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(AccentGold),
            )
            Column(Modifier.padding(12.dp)) {
                Text(
                    text = turnText,
                    color = turnColor,
                    fontFamily = FontFamily.Serif,
                    fontSize = 20.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = vm.message,
                    color = TextMuted,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier.defaultMinSize(minHeight = 34.dp),
                )
            }
        }

        // 操作按钮
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PanelButton(text = "重新开始", onClick = { vm.newGame(playSound = true) })
            PanelButton(
                text = "悔棋",
                enabled = vm.historySize > 0 && !vm.aiThinking && !vm.gameOver,
                onClick = vm::undo,
            )
            PanelButton(
                text = if (vm.soundOn) "音效：开" else "音效：关",
                onClick = vm::toggleSound,
            )
        }

        // 操作说明
        Column {
            Text("红方在下，先手；黑方由电脑执子。", color = TextMuted, fontSize = 12.sp, lineHeight = 19.sp)
            Text(
                "点击己方棋子选中，再点击目标位置落子。",
                color = TextMuted,
                fontSize = 12.sp,
                lineHeight = 19.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                "开启训练模式后，大师教练会点评不佳着法与失子。",
                color = TextMuted,
                fontSize = 12.sp,
                lineHeight = 19.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun PanelButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(3.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BtnBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = BtnBottom,
            contentColor = TextCream,
            disabledContainerColor = BtnBottom.copy(alpha = 0.45f),
            disabledContentColor = TextCream.copy(alpha = 0.45f),
        ),
    ) {
        Text(text, fontSize = 15.sp)
    }
}
