package livan.chinese_chess.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import livan.chinese_chess.engine.Board
import livan.chinese_chess.engine.Xiangqi
import kotlin.math.hypot
import kotlin.math.roundToInt

/** board.js 的 easeOut：t*(2-t) */
private val EaseOutQuad = Easing { t -> t * (2f - t) }

private data class BoardLayout(
    val cell: Float,
    val marginX: Float,
    val marginY: Float,
    val pieceRadius: Float,
)

private val WoodLight = Color(0xFFD4B896)
private val WoodMid = Color(0xFFC4A574)
private val WoodDark = Color(0xFFB8956A)
private val WoodGrain = Color(0x0F5A3C1E) // rgba(90,60,30,0.06)
private val GridDark = Color(0xFF3D2914)
private val RiverText = Color(0xFF5C3D20)
private val LastMoveFill = Color(0x47D4A017) // rgba(212,160,23,0.28)
private val SelectStroke = Color(0xFFD4A017)
private val TargetEnemy = Color(0xBFB91C1C) // rgba(185,28,28,0.75)
private val TargetDot = Color(0x73287828) // rgba(40,120,40,0.45)
/** 教练建议着法闪烁绿色 */
private val CoachHintGreen = Color(0xFF35B535)
private val PieceShadow = Color(0x38000000) // rgba(0,0,0,0.22)
private val PieceLight = Color(0xFFFFF8E7)
private val PieceDark = Color(0xFFE8D5B0)
private val RedOuter = Color(0xFF9B1C1C)
private val RedInner = Color(0xFFC0392B)
private val RedText = Color(0xFFB91C1C)
private val BlackOuter = Color(0xFF1A1A1A)
private val BlackInner = Color(0xFF333333)

/** 炮兵位星位标记（board.js marks） */
private val MARKS = listOf(
    2 to 1, 2 to 7, 3 to 0, 3 to 2, 3 to 4, 3 to 6, 3 to 8,
    7 to 1, 7 to 7, 6 to 0, 6 to 2, 6 to 4, 6 to 6, 6 to 8,
)

/**
 * 棋盘 Canvas，逐条复刻 board.js：木纹背景、网格、河界、九宫斜线、
 * 星位标记、lastMove 高亮、选中描边、可走点提示、棋子与走子动画。
 */
@Composable
fun BoardCanvas(
    board: Board,
    selected: Xiangqi.Pos?,
    legalTargets: List<Xiangqi.Move>,
    lastMove: Xiangqi.Move?,
    anim: MoveAnim?,
    coachHint: Xiangqi.Move? = null,
    interactive: Boolean,
    onTap: (r: Int, c: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var layout by remember { mutableStateOf(BoardLayout(0f, 0f, 0f, 0f)) }

    // 走子动画进度（220ms easeOut）
    val progress = remember { Animatable(0f) }
    LaunchedEffect(anim) {
        if (anim != null) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(GameViewModel.MOVE_ANIM_MS.toInt(), easing = EaseOutQuad))
        }
    }

    // 教练建议着法闪烁透明度
    val blink = rememberInfiniteTransition(label = "coachHint")
    val hintAlpha by blink.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alpha",
    )

    // 棋子汉字 / 河界文字画笔（serif 粗体，楷体回退）
    val piecePaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
    }
    val riverPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.SERIF
            color = android.graphics.Color.rgb(0x5C, 0x3D, 0x20)
        }
    }

    Canvas(
        modifier = modifier
            .onSizeChanged { sz ->
                // cell 取横向/纵向较小者后居中；边距 = cell/2，大于棋子半径(0.42cell)，
                // 保证边缘棋子完整显示在棋盘内
                val cell = minOf(sz.width / 9f, sz.height / 10f)
                layout = BoardLayout(
                    cell = cell,
                    marginX = (sz.width - cell * 8f) / 2f,
                    marginY = (sz.height - cell * 9f) / 2f,
                    pieceRadius = cell * 0.42f,
                )
            }
            .pointerInput(interactive, anim) {
                detectTapGestures { offset ->
                    if (!interactive || anim != null) return@detectTapGestures
                    val l = layout
                    if (l.cell <= 0f) return@detectTapGestures
                    // pixelToPos：最近交叉点，>0.48cell 无效
                    val c = ((offset.x - l.marginX) / l.cell).roundToInt()
                    val r = ((offset.y - l.marginY) / l.cell).roundToInt()
                    if (r !in 0 until Xiangqi.ROWS || c !in 0 until Xiangqi.COLS) return@detectTapGestures
                    val px = l.marginX + c * l.cell
                    val py = l.marginY + r * l.cell
                    if (hypot(offset.x - px, offset.y - py) > l.cell * 0.48f) return@detectTapGestures
                    onTap(r, c)
                }
            }
            .fillMaxSize(),
    ) {
        val l = layout
        if (l.cell <= 0f) return@Canvas
        // 线宽等按 JS 540px 画布（cell≈56）的比例缩放
        val k = l.cell / 56f

        drawWoodBackground(k)
        drawGrid(l, k, riverPaint)
        drawHighlights(l, k, board, selected, legalTargets, lastMove)
        drawCoachHint(l, k, board, coachHint, hintAlpha)
        drawPieces(l, k, board, anim, progress.value, piecePaint)
    }
}

/** 木纹渐变背景 + 横向木纹（board.js drawWoodBackground） */
private fun DrawScope.drawWoodBackground(k: Float) {
    drawRect(
        Brush.linearGradient(
            colors = listOf(WoodLight, WoodMid, WoodDark),
            start = Offset.Zero,
            end = Offset(size.width, size.height),
        ),
    )
    var gy = 0f
    while (gy < size.height) {
        val dy = if (gy.toInt() % 14 == 0) 2f else -1f
        drawLine(WoodGrain, Offset(0f, gy), Offset(size.width, gy + dy), strokeWidth = 1f * k)
        gy += 7f
    }
}

/** 网格线（河界断开）、九宫斜线、楚河汉界、星位标记（board.js drawGrid） */
private fun DrawScope.drawGrid(l: BoardLayout, k: Float, riverPaint: Paint) {
    val cell = l.cell
    val mx = l.marginX
    val my = l.marginY
    val w = 1.5f * k

    // 横线
    for (r in 0 until Xiangqi.ROWS) {
        val y = my + r * cell
        drawLine(GridDark, Offset(mx, y), Offset(mx + 8 * cell, y), strokeWidth = w)
    }
    // 竖线（楚河汉界中间断开）
    for (c in 0 until Xiangqi.COLS) {
        val x = mx + c * cell
        if (c == 0 || c == 8) {
            drawLine(GridDark, Offset(x, my), Offset(x, my + 9 * cell), strokeWidth = w)
        } else {
            drawLine(GridDark, Offset(x, my), Offset(x, my + 4 * cell), strokeWidth = w)
            drawLine(GridDark, Offset(x, my + 5 * cell), Offset(x, my + 9 * cell), strokeWidth = w)
        }
    }
    // 九宫斜线
    for (startRow in intArrayOf(0, 7)) {
        val x1 = mx + 3 * cell
        val x2 = mx + 5 * cell
        val y1 = my + startRow * cell
        val y2 = my + (startRow + 2) * cell
        drawLine(GridDark, Offset(x1, y1), Offset(x2, y2), strokeWidth = w)
        drawLine(GridDark, Offset(x2, y1), Offset(x1, y2), strokeWidth = w)
    }
    // 楚河汉界
    riverPaint.textSize = (cell * 0.38f).toInt().toFloat()
    val midY = my + 4.5f * cell
    val baseline = midY - (riverPaint.descent() + riverPaint.ascent()) / 2f
    drawContext.canvas.nativeCanvas.drawText("楚 河", mx + 2 * cell, baseline, riverPaint)
    drawContext.canvas.nativeCanvas.drawText("汉 界", mx + 6 * cell, baseline, riverPaint)

    // 炮兵位角标记
    val s = cell * 0.12f
    for ((r, c) in MARKS) {
        val x = mx + c * cell
        val y = my + r * cell
        val corners = buildList {
            if (c > 0) {
                add(-1 to -1)
                add(-1 to 1)
            }
            if (c < 8) {
                add(1 to -1)
                add(1 to 1)
            }
        }
        for ((sx, sy) in corners) {
            drawLine(
                GridDark,
                Offset(x + sx * 3f, y + sy * (s + 2f)),
                Offset(x + sx * 3f, y + sy * 3f),
                strokeWidth = 1f * k,
            )
            drawLine(
                GridDark,
                Offset(x + sx * 3f, y + sy * 3f),
                Offset(x + sx * (s + 2f), y + sy * 3f),
                strokeWidth = 1f * k,
            )
        }
    }
}

/** lastMove 金色高亮、选中描边、绿点/红圈可走点（board.js drawHighlights） */
private fun DrawScope.drawHighlights(
    l: BoardLayout,
    k: Float,
    board: Board,
    selected: Xiangqi.Pos?,
    legalTargets: List<Xiangqi.Move>,
    lastMove: Xiangqi.Move?,
) {
    val cell = l.cell
    val pr = l.pieceRadius

    if (lastMove != null) {
        for ((r, c) in listOf(lastMove.fr to lastMove.fc, lastMove.tr to lastMove.tc)) {
            drawCircle(
                LastMoveFill,
                radius = pr + 2f * k,
                center = Offset(l.marginX + c * cell, l.marginY + r * cell),
            )
        }
    }
    if (selected != null) {
        drawCircle(
            SelectStroke,
            radius = pr + 3f * k,
            center = Offset(l.marginX + selected.c * cell, l.marginY + selected.r * cell),
            style = Stroke(width = 3f * k),
        )
    }
    for (t in legalTargets) {
        val x = l.marginX + t.tc * cell
        val y = l.marginY + t.tr * cell
        if (board[t.tr][t.tc] != Xiangqi.EMPTY) {
            drawCircle(TargetEnemy, radius = pr + 2f * k, center = Offset(x, y), style = Stroke(width = 2.5f * k))
        } else {
            drawCircle(TargetDot, radius = 7f * k, center = Offset(x, y))
        }
    }
}

/**
 * 教练建议着法闪烁指示（相对当前局面的坐标）：
 * 起点有红子则画绿圈；终点空位绿点、有子绿圈。
 */
private fun DrawScope.drawCoachHint(
    l: BoardLayout,
    k: Float,
    board: Board,
    hint: Xiangqi.Move?,
    alpha: Float,
) {
    if (hint == null) return
    val cell = l.cell
    val pr = l.pieceRadius
    val color = CoachHintGreen.copy(alpha = alpha)

    // 起点：仅当仍有红子时画圈（建议着通常是另一枚棋）
    val fromPiece = board[hint.fr][hint.fc]
    if (fromPiece != Xiangqi.EMPTY && Xiangqi.isRed(fromPiece)) {
        drawCircle(
            color,
            radius = pr + 3f * k,
            center = Offset(l.marginX + hint.fc * cell, l.marginY + hint.fr * cell),
            style = Stroke(width = 3f * k),
        )
    }

    val tx = l.marginX + hint.tc * cell
    val ty = l.marginY + hint.tr * cell
    if (board[hint.tr][hint.tc] != Xiangqi.EMPTY) {
        drawCircle(color, radius = pr + 2f * k, center = Offset(tx, ty), style = Stroke(width = 2.5f * k))
    } else {
        drawCircle(color, radius = 8f * k, center = Offset(tx, ty))
    }
}

/** 棋子与走子动画（board.js drawPieces） */
private fun DrawScope.drawPieces(
    l: BoardLayout,
    k: Float,
    board: Board,
    anim: MoveAnim?,
    animT: Float,
    piecePaint: Paint,
) {
    for (r in 0 until Xiangqi.ROWS) {
        for (c in 0 until Xiangqi.COLS) {
            val p = board[r][c]
            if (p == Xiangqi.EMPTY) continue
            // 动画中隐藏起终点棋子，由插值绘制的移动棋子代替
            if (anim != null &&
                ((anim.move.fr == r && anim.move.fc == c) || (anim.move.tr == r && anim.move.tc == c))
            ) {
                continue
            }
            drawPieceAt(p, l.marginX + c * l.cell, l.marginY + r * l.cell, l.pieceRadius, k, piecePaint)
        }
    }
    if (anim != null) {
        val fx = l.marginX + anim.move.fc * l.cell
        val fy = l.marginY + anim.move.fr * l.cell
        val tx = l.marginX + anim.move.tc * l.cell
        val ty = l.marginY + anim.move.tr * l.cell
        drawPieceAt(
            anim.piece,
            fx + (tx - fx) * animT,
            fy + (ty - fy) * animT,
            l.pieceRadius,
            k,
            piecePaint,
        )
    }
}

/** 单个棋子：阴影 + 径向渐变底盘 + 双描边 + 汉字（board.js drawPieceAt） */
private fun DrawScope.drawPieceAt(
    piece: Char,
    x: Float,
    y: Float,
    radius: Float,
    k: Float,
    piecePaint: Paint,
) {
    val red = Xiangqi.isRed(piece)
    val name = Xiangqi.PIECE_NAME[piece] ?: piece.toString()

    // 阴影
    drawCircle(PieceShadow, radius = radius, center = Offset(x + 1.5f * k, y + 2f * k))
    // 底盘径向渐变
    drawCircle(
        Brush.radialGradient(
            colors = listOf(PieceLight, PieceDark),
            center = Offset(x - radius * 0.3f, y - radius * 0.3f),
            radius = radius,
        ),
        radius = radius,
        center = Offset(x, y),
    )
    // 外圈 + 内圈描边
    drawCircle(
        if (red) RedOuter else BlackOuter,
        radius = radius,
        center = Offset(x, y),
        style = Stroke(width = 2.2f * k),
    )
    drawCircle(
        if (red) RedInner else BlackInner,
        radius = radius * 0.78f,
        center = Offset(x, y),
        style = Stroke(width = 1.2f * k),
    )
    // 汉字
    piecePaint.color = if (red) {
        android.graphics.Color.rgb(0xB9, 0x1C, 0x1C)
    } else {
        android.graphics.Color.rgb(0x1A, 0x1A, 0x1A)
    }
    piecePaint.textSize = (radius * 1.05f).toInt().toFloat()
    val baseline = y + 1f * k - (piecePaint.descent() + piecePaint.ascent()) / 2f
    drawContext.canvas.nativeCanvas.drawText(name, x, baseline, piecePaint)
}
