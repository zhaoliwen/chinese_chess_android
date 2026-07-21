package livan.chinese_chess.coach

import livan.chinese_chess.engine.Board
import livan.chinese_chess.engine.Xiangqi
import livan.chinese_chess.engine.XiangqiAI

/**
 * 训练模式教练：大师级分析 + 中文点评文案
 *
 * 逐行移植自参考项目 js/coach.js。
 */
object Coach {
    // 与最佳着分差超过此值视为「走得不好」（约半个兵以上）
    const val BAD_GAP = 70
    const val COACH_DEPTH = 3 // 点评用深度，平衡速度与质量

    private val FILE = listOf("九", "八", "七", "六", "五", "四", "三", "二", "一")

    /** 点评结果；type 沿用 JS 原值："bad_move" / "capture" / "good" */
    data class Review(
        val type: String,
        val title: String,
        val lines: List<String>,
        val gap: Int,
        /** 建议着法（相对玩家走子前的局面），供棋盘闪烁指示；无则 null */
        val suggestedMove: Xiangqi.Move? = null,
    )

    /** 失根子信息 */
    private data class HungPiece(val r: Int, val c: Int, val piece: Char, val value: Int)

    /** 可白吃的棋子信息 */
    private data class FreeCapture(val move: Xiangqi.Move, val piece: Char, val value: Int)

    private fun fileOf(c: Int): String =
        if (c in FILE.indices) FILE[c] else c.toString()

    private fun rankOf(r: Int): String = (10 - r).toString()

    private fun pieceName(p: Char): String =
        if (p == Xiangqi.EMPTY) "" else Xiangqi.PIECE_NAME[p] ?: p.toString()

    /** 着法中文描述 */
    private fun describeMoveOrNull(board: Board, move: Xiangqi.Move?): String {
        if (move == null) return "未知着法"
        val p = board[move.fr][move.fc]
        val name = pieceName(p)
        val cap = board[move.tr][move.tc]
        val from = "${fileOf(move.fc)}${rankOf(move.fr)}"
        val to = "${fileOf(move.tc)}${rankOf(move.tr)}"
        if (cap != Xiangqi.EMPTY) return "${name}吃${pieceName(cap)}（${from}→${to}）"
        if (move.fr == move.tr) return "${name}${from}平至${to}"
        if ((Xiangqi.isRed(p) && move.tr < move.fr) || (!Xiangqi.isRed(p) && move.tr > move.fr)) {
            return "${name}${from}进至${to}"
        }
        return "${name}${from}退至${to}"
    }

    /** 着法中文描述（公开 API） */
    fun describeMove(board: Board, move: Xiangqi.Move): String =
        describeMoveOrNull(board, move)

    /** 红方无保护且被黑攻击的棋子 */
    private fun findHangingRed(board: Board): List<HungPiece> {
        val hung = mutableListOf<HungPiece>()
        for (r in 0 until Xiangqi.ROWS) {
            for (c in 0 until Xiangqi.COLS) {
                val p = board[r][c]
                if (p == Xiangqi.EMPTY || !Xiangqi.isRed(p) || p == 'K') continue
                val attacked = Xiangqi.isSquareAttacked(board, r, c, false)
                if (!attacked) continue
                val defended = Xiangqi.isSquareAttacked(board, r, c, true)
                if (!defended) {
                    hung.add(HungPiece(r, c, p, XiangqiAI.pieceValue(p)))
                } else {
                    // 低价值守高价值：黑用小子打大子
                    // 简化：若被车/炮/马攻击且我方只有兵守，也算危险——略
                }
            }
        }
        hung.sortByDescending { it.value }
        return hung
    }

    /** 是否漏吃：存在可白吃的黑子 */
    private fun findFreeCaptures(board: Board, redToMove: Boolean): List<FreeCapture> {
        val moves = Xiangqi.generateLegalMoves(board, redToMove)
        val frees = mutableListOf<FreeCapture>()
        for (m in moves) {
            val cap = board[m.tr][m.tc]
            if (cap == Xiangqi.EMPTY) continue
            // 落点是否被对方保护
            val next = Xiangqi.applyMove(board, m.fr, m.fc, m.tr, m.tc)
            val protectedByEnemy = Xiangqi.isSquareAttacked(next, m.tr, m.tc, !redToMove)
            if (!protectedByEnemy) {
                frees.add(FreeCapture(m, cap, XiangqiAI.pieceValue(cap)))
            }
        }
        frees.sortByDescending { it.value }
        return frees
    }

    private fun buildPlayerReview(
        boardBefore: Board,
        playerMove: Xiangqi.Move,
        analysis: XiangqiAI.RedAnalysis,
    ): Review {
        val playedDesc = describeMoveOrNull(boardBefore, playerMove)
        val bestDesc = describeMoveOrNull(boardBefore, analysis.best)
        val boardAfter = Xiangqi.applyMove(
            boardBefore,
            playerMove.fr,
            playerMove.fc,
            playerMove.tr,
            playerMove.tc,
        )
        val gap = analysis.gap
        val reasons = mutableListOf<String>()
        val whyBest = mutableListOf<String>()

        // 漏吃
        val freesBefore = findFreeCaptures(boardBefore, true)
        if (freesBefore.isNotEmpty()) {
            val top = freesBefore[0]
            val took = boardBefore[playerMove.tr][playerMove.tc]
            val tookFree =
                took != Xiangqi.EMPTY &&
                    freesBefore.any { f ->
                        f.move.fr == playerMove.fr &&
                            f.move.fc == playerMove.fc &&
                            f.move.tr == playerMove.tr &&
                            f.move.tc == playerMove.tc
                    }
            if (!tookFree && top.value >= 200) {
                reasons.add(
                    "这步没有吃掉对方无根的${pieceName(top.piece)}，等于放过了白捡的便宜。"
                )
            }
        }

        // 送吃 / 失根
        val hung = findHangingRed(boardAfter)
        if (hung.isNotEmpty()) {
            val h = hung[0]
            reasons.add(
                "走完后，你的${pieceName(h.piece)}落在${fileOf(h.c)}${rankOf(h.r)}，没有保护，下一手可能被吃。"
            )
        }

        // 最佳着吃子
        val bestMove = analysis.best
        val bestCap = if (bestMove != null) boardBefore[bestMove.tr][bestMove.tc] else Xiangqi.EMPTY
        if (bestCap != Xiangqi.EMPTY) {
            whyBest.add(
                "最佳着可以直接吃${pieceName(bestCap)}，先得实惠。"
            )
        }

        // 最佳着解除或制造将军
        val afterBest = if (bestMove != null) {
            Xiangqi.applyMove(
                boardBefore,
                bestMove.fr,
                bestMove.fc,
                bestMove.tr,
                bestMove.tc,
            )
        } else {
            boardBefore
        }
        if (Xiangqi.isInCheck(afterBest, false)) {
            whyBest.add("这步能将军，迫使对方应付，掌握主动。")
        }
        if (Xiangqi.isInCheck(boardBefore, true)) {
            if (!Xiangqi.isInCheck(afterBest, true)) {
                whyBest.add("这步能稳妥应将，比你的选择更安全。")
            }
            if (Xiangqi.isInCheck(boardAfter, true)) {
                reasons.add("你这步没有解除将军，属于漏应。")
            }
        }

        // 最佳着保护失根子
        val hungBefore = findHangingRed(boardBefore)
        if (hungBefore.isNotEmpty()) {
            val stillHung = findHangingRed(afterBest)
            val saved = hungBefore.any { h ->
                stillHung.none { x -> x.r == h.r && x.c == h.c && x.piece == h.piece }
            }
            if (saved) whyBest.add("最佳着能护住己方失根的棋子，避免丢子。")
        }

        if (whyBest.isEmpty()) {
            if (gap >= 200) whyBest.add("从子力与局面看，这步能明显改善形势。")
            else whyBest.add("这步在攻守上更均衡，后续变化也更有利。")
        }

        if (reasons.isEmpty()) {
            if (gap >= 250) reasons.add("这步进攻或防守效率偏低，局面优势被白白浪费。")
            else if (gap >= BAD_GAP) reasons.add("这步不够积极，错失了更好的攻防节奏。")
            else reasons.add("这步略软，还有更紧凑的走法。")
        }

        val severity = if (gap >= 300) "明显失误" else if (gap >= 150) "不佳" else "偏软"

        return Review(
            type = "bad_move",
            title = "教练点评：这步$severity",
            lines = listOf(
                "你走了：$playedDesc",
                "为什么不太好：${reasons.joinToString("")}",
                "建议走：$bestDesc",
                "这样走的原因：${whyBest.joinToString("")}",
            ),
            gap = gap,
            // 相对玩家走子前的局面；棋盘用坐标闪烁指示建议着法
            suggestedMove = analysis.best,
        )
    }

    private fun buildCaptureReview(
        boardBeforePlayer: Board?,
        playerMove: Xiangqi.Move?,
        boardBeforeBlack: Board,
        blackMove: Xiangqi.Move,
        captured: Char,
    ): Review {
        val playerDesc = if (playerMove != null && boardBeforePlayer != null) {
            describeMoveOrNull(boardBeforePlayer, playerMove)
        } else {
            "上一手"
        }
        val capName = pieceName(captured)
        val blackDesc = describeMoveOrNull(boardBeforeBlack, blackMove)
        val lines = mutableListOf(
            "对方用${blackDesc}吃掉了你的${capName}。",
        )

        // 分析玩家上一步是否导致失子
        var analysis: XiangqiAI.RedAnalysis? = null
        if (playerMove != null && boardBeforePlayer != null) {
            analysis = XiangqiAI.analyzeRedMove(boardBeforePlayer, playerMove, COACH_DEPTH)
        }

        if (analysis != null && analysis.gap >= BAD_GAP && boardBeforePlayer != null && playerMove != null) {
            val bestDesc = describeMoveOrNull(boardBeforePlayer, analysis.best)
            lines.add("问题出在你刚才的${playerDesc}：留下了破绽。")
            lines.add("当时更好的走法是：${bestDesc}")
            val hung = findHangingRed(
                Xiangqi.applyMove(
                    boardBeforePlayer,
                    playerMove.fr,
                    playerMove.fc,
                    playerMove.tr,
                    playerMove.tc,
                )
            )
            if (hung.any { h -> h.r == blackMove.tr && h.c == blackMove.tc }) {
                lines.add("原因：你把${capName}放在了无保护的位置，对方顺势吃掉。")
            } else {
                lines.add("原因：这步让对方有了得子手段，局面吃了亏。")
            }
            lines.add("若改走建议着法，往往可以避免立刻丢${capName}。")
        } else {
            lines.add(
                "你上一手${playerDesc}并非明显坏棋，但对方抓住了战术机会。"
            )
            if (analysis != null && analysis.best != null && boardBeforePlayer != null) {
                lines.add(
                    "若想更稳，可考虑当时走：${describeMoveOrNull(boardBeforePlayer, analysis.best)}。"
                )
            }
        }

        return Review(
            type = "capture",
            title = "教练提醒：失子（${capName}）",
            lines = lines,
            gap = analysis?.gap ?: 0,
        )
    }

    /** 好棋/稳健着法的简短肯定点评（每步都给，文案控制在两三行） */
    private fun buildGoodReview(
        boardBefore: Board,
        playerMove: Xiangqi.Move,
        analysis: XiangqiAI.RedAnalysis,
    ): Review {
        val playedDesc = describeMoveOrNull(boardBefore, playerMove)
        val boardAfter = Xiangqi.applyMove(
            boardBefore,
            playerMove.fr,
            playerMove.fc,
            playerMove.tr,
            playerMove.tc,
        )
        val isBest = XiangqiAI.sameMove(analysis.best, playerMove)
        val lines = mutableListOf("你走了：$playedDesc")
        if (isBest) {
            lines.add("这正是当前局面的最佳着法，走得很准。")
        } else {
            lines.add("这步攻守稳健，与最佳着法相差无几。")
        }
        // 附加战术亮点
        val cap = boardBefore[playerMove.tr][playerMove.tc]
        if (cap != Xiangqi.EMPTY) {
            lines.add("顺势吃掉对方${pieceName(cap)}，先捞到实惠。")
        }
        if (Xiangqi.isInCheck(boardAfter, false)) {
            lines.add("这步将军，迫使对方应手，掌握主动。")
        }
        if (!isBest && analysis.best != null) {
            lines.add("若想更进一步，也可考虑：${describeMoveOrNull(boardBefore, analysis.best)}。")
        }
        return Review(
            type = "good",
            title = if (isBest) "教练点评：好棋！" else "教练点评：走得不错",
            lines = lines,
            gap = analysis.gap,
        )
    }

    /**
     * 点评玩家着法：每一步都返回点评——好棋给予肯定（type="good"），
     * 软棋给出原因与建议（type="bad_move"）；分析失败或无最佳着时返回 null。
     * JS 用 setTimeout 模拟异步，这里直接同步返回，调用方负责放到后台线程。
     */
    fun reviewPlayerMove(boardBefore: Board, playerMove: Xiangqi.Move): Review? {
        return try {
            val analysis = XiangqiAI.analyzeRedMove(boardBefore, playerMove, COACH_DEPTH)
            if (analysis.best == null) {
                null
            } else if (XiangqiAI.sameMove(analysis.best, playerMove) || analysis.gap < BAD_GAP) {
                buildGoodReview(boardBefore, playerMove, analysis)
            } else {
                buildPlayerReview(boardBefore, playerMove, analysis)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 对方吃子后的点评
     */
    fun reviewCapture(
        boardBeforePlayer: Board,
        playerMove: Xiangqi.Move,
        boardBeforeBlack: Board,
        blackMove: Xiangqi.Move,
        captured: Char,
    ): Review? {
        return try {
            if (captured == Xiangqi.EMPTY || !Xiangqi.isRed(captured)) {
                null
            } else {
                // 小子被吃可降低敏感度：兵被吃也报，但标题轻一些
                buildCaptureReview(
                    boardBeforePlayer,
                    playerMove,
                    boardBeforeBlack,
                    blackMove,
                    captured,
                )
            }
        } catch (e: Exception) {
            null
        }
    }
}
