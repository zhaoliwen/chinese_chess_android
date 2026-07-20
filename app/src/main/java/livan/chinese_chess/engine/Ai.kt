package livan.chinese_chess.engine

import kotlin.math.min
import kotlin.random.Random

/**
 * 人机 AI：极小极大 + Alpha-Beta 剪枝
 * 三级难度：小白 / 新手 / 大师
 *
 * 移植自参考项目 js/ai.js。
 */
object XiangqiAI {

    data class Difficulty(val depth: Int, val randomPool: Int)

    val DIFFICULTY: Map<String, Difficulty> = mapOf(
        "beginner" to Difficulty(depth = 2, randomPool = 5), // 小白
        "novice" to Difficulty(depth = 3, randomPool = 2),   // 新手
        "master" to Difficulty(depth = 4, randomPool = 1),   // 大师
    )

    // 子力分
    private val PIECE_VALUE: Map<Char, Int> = mapOf(
        'K' to 10000, 'k' to 10000,
        'R' to 900, 'r' to 900,
        'C' to 450, 'c' to 450,
        'N' to 400, 'n' to 400,
        'B' to 200, 'b' to 200,
        'A' to 200, 'a' to 200,
        'P' to 100, 'p' to 100,
    )

    // 位置分（红方视角，黑方行翻转）
    private val PST: Map<Char, Array<IntArray>> = mapOf(
        'K' to arrayOf(
            intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
            intArrayOf(0, 0, 0, 1, 5, 1, 0, 0, 0),
            intArrayOf(0, 0, 0, 5, 8, 5, 0, 0, 0),
            intArrayOf(0, 0, 0, 3, 5, 3, 0, 0, 0),
        ),
        'A' to arrayOf(
            intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
            intArrayOf(0, 0, 0, 2, 0, 2, 0, 0, 0),
            intArrayOf(0, 0, 0, 0, 3, 0, 0, 0, 0),
            intArrayOf(0, 0, 0, 2, 0, 2, 0, 0, 0),
        ),
        'B' to arrayOf(
            intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
            intArrayOf(0, 0, 2, 0, 0, 0, 2, 0, 0),
            intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
            intArrayOf(2, 0, 0, 0, 4, 0, 0, 0, 2),
            intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
            intArrayOf(0, 0, 2, 0, 0, 0, 2, 0, 0),
        ),
        'N' to arrayOf(
            intArrayOf(2, 2, 4, 4, 2, 4, 4, 2, 2),
            intArrayOf(2, 4, 8, 8, 6, 8, 8, 4, 2),
            intArrayOf(4, 6, 10, 10, 10, 10, 10, 6, 4),
            intArrayOf(4, 8, 12, 14, 14, 14, 12, 8, 4),
            intArrayOf(2, 8, 12, 14, 14, 14, 12, 8, 2),
            intArrayOf(2, 6, 10, 12, 12, 12, 10, 6, 2),
            intArrayOf(2, 4, 8, 10, 10, 10, 8, 4, 2),
            intArrayOf(0, 2, 4, 6, 6, 6, 4, 2, 0),
            intArrayOf(0, 0, 2, 4, 4, 4, 2, 0, 0),
            intArrayOf(0, 0, 2, 0, 2, 0, 2, 0, 0),
        ),
        'R' to arrayOf(
            intArrayOf(8, 10, 8, 12, 14, 12, 8, 10, 8),
            intArrayOf(10, 12, 12, 14, 16, 14, 12, 12, 10),
            intArrayOf(8, 10, 10, 12, 12, 12, 10, 10, 8),
            intArrayOf(8, 12, 12, 14, 14, 14, 12, 12, 8),
            intArrayOf(10, 14, 14, 16, 16, 16, 14, 14, 10),
            intArrayOf(10, 12, 12, 14, 14, 14, 12, 12, 10),
            intArrayOf(8, 10, 10, 12, 12, 12, 10, 10, 8),
            intArrayOf(6, 8, 8, 10, 12, 10, 8, 8, 6),
            intArrayOf(6, 8, 6, 10, 12, 10, 6, 8, 6),
            intArrayOf(4, 6, 4, 10, 12, 10, 4, 6, 4),
        ),
        'C' to arrayOf(
            intArrayOf(4, 4, 4, 6, 8, 6, 4, 4, 4),
            intArrayOf(4, 6, 6, 8, 10, 8, 6, 6, 4),
            intArrayOf(4, 6, 8, 10, 12, 10, 8, 6, 4),
            intArrayOf(4, 8, 10, 12, 14, 12, 10, 8, 4),
            intArrayOf(6, 10, 12, 14, 16, 14, 12, 10, 6),
            intArrayOf(6, 10, 12, 14, 14, 14, 12, 10, 6),
            intArrayOf(4, 8, 10, 12, 12, 12, 10, 8, 4),
            intArrayOf(4, 6, 8, 8, 10, 8, 8, 6, 4),
            intArrayOf(4, 4, 4, 6, 8, 6, 4, 4, 4),
            intArrayOf(2, 2, 2, 4, 6, 4, 2, 2, 2),
        ),
        'P' to arrayOf(
            intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
            intArrayOf(10, 10, 12, 14, 16, 14, 12, 10, 10),
            intArrayOf(8, 8, 10, 12, 14, 12, 10, 8, 8),
            intArrayOf(6, 8, 10, 12, 12, 12, 10, 8, 6),
            intArrayOf(2, 6, 8, 10, 10, 10, 8, 6, 2),
            intArrayOf(0, 0, 0, 2, 2, 2, 0, 0, 0),
            intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
            intArrayOf(2, 0, 2, 0, 4, 0, 2, 0, 2),
            intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
        ),
    )

    /** 代替 JS 的 Infinity；评估分绝对值远小于该值 */
    private const val INF = 1_000_000_000

    data class ScoredMove(val move: Xiangqi.Move, val score: Int)

    data class RedAnalysis(
        val best: Xiangqi.Move?,
        val bestScore: Int,
        val playerScore: Int,
        val gap: Int,
        val top: List<ScoredMove>,
    )

    /** negamax 的返回值：score 为行棋方视角分数，move 为最佳着（叶子节点为 null） */
    private data class SearchResult(val score: Int, val move: Xiangqi.Move? = null)

    private fun pstScore(type: Char, r: Int, c: Int, red: Boolean): Int {
        val table = PST[type] ?: return 0
        val rr = if (red) r else 9 - r
        return table[rr][c]
    }

    /** 评估：正分利于红方 */
    fun evaluate(board: Board): Int {
        var score = 0
        for (r in 0 until Xiangqi.ROWS) {
            for (c in 0 until Xiangqi.COLS) {
                val p = board[r][c]
                if (p == Xiangqi.EMPTY) continue
                val type = p.uppercaseChar()
                val red = Xiangqi.isRed(p)
                val v = pieceValue(p) + pstScore(type, r, c, red)
                score += if (red) v else -v
            }
        }
        if (Xiangqi.isInCheck(board, true)) score -= 35
        if (Xiangqi.isInCheck(board, false)) score += 35
        return score
    }

    /** MVV-LVA 走法排序键：被吃子价值×10 − 走子价值/100 */
    private fun moveOrderKey(board: Board, m: Xiangqi.Move): Int {
        val captured = board[m.tr][m.tc]
        var key = 0
        if (captured != Xiangqi.EMPTY) key += pieceValue(captured) * 10
        val mover = board[m.fr][m.fc]
        if (mover != Xiangqi.EMPTY) key -= pieceValue(mover) / 100
        return key
    }

    private fun negamax(
        board: Board,
        depth: Int,
        alpha0: Int,
        beta: Int,
        redToMove: Boolean,
        maxDepth: Int,
    ): SearchResult {
        if (depth == 0) {
            return SearchResult(score = evaluate(board) * (if (redToMove) 1 else -1))
        }

        val moves = Xiangqi.generateLegalMoves(board, redToMove)
        if (moves.isEmpty()) {
            if (Xiangqi.isInCheck(board, redToMove)) {
                // 被将死：越早被杀越糟（原 JS 硬编码 4，改为根深度 maxDepth）
                return SearchResult(score = -20000 + (maxDepth - depth))
            }
            return SearchResult(score = 0)
        }

        val sorted = moves.sortedByDescending { moveOrderKey(board, it) }

        var bestMove = sorted[0]
        var bestScore = -INF
        var alpha = alpha0

        for (m in sorted) {
            val next = Xiangqi.applyMove(board, m.fr, m.fc, m.tr, m.tc)
            val result = negamax(next, depth - 1, -beta, -alpha, !redToMove, maxDepth)
            val score = -result.score
            if (score > bestScore) {
                bestScore = score
                bestMove = m
            }
            if (score > alpha) alpha = score
            if (alpha >= beta) break
        }

        return SearchResult(score = bestScore, move = bestMove)
    }

    /**
     * 为黑方（电脑）选着
     * @param level beginner|novice|master
     */
    fun chooseMove(board: Board, level: String): Xiangqi.Move? {
        val cfg = DIFFICULTY[level] ?: DIFFICULTY.getValue("novice")
        val moves = Xiangqi.generateLegalMoves(board, false)
        if (moves.isEmpty()) return null

        // 根节点逐着搜索，按分数排序后依难度在前若干好着中随机
        val sorted = moves.sortedByDescending { moveOrderKey(board, it) }
        val scored = mutableListOf<ScoredMove>()
        for (m in sorted) {
            val next = Xiangqi.applyMove(board, m.fr, m.fc, m.tr, m.tc)
            val result = negamax(next, cfg.depth - 1, -INF, INF, true, cfg.depth)
            scored.add(ScoredMove(move = m, score = -result.score))
        }

        scored.sortByDescending { it.score }
        val poolSize = min(cfg.randomPool, scored.size)
        return scored[Random.nextInt(poolSize)].move
    }

    fun sameMove(a: Xiangqi.Move?, b: Xiangqi.Move?): Boolean {
        return a != null && b != null && a.fr == b.fr && a.fc == b.fc && a.tr == b.tr && a.tc == b.tc
    }

    /**
     * 分析红方（玩家）着法：返回最佳着、玩家着评分等
     * score 均为红方视角（越大越好）
     */
    fun analyzeRedMove(boardBefore: Board, playerMove: Xiangqi.Move, depth: Int = 4): RedAnalysis {
        val d = if (depth > 0) depth else DIFFICULTY.getValue("master").depth
        val moves = Xiangqi.generateLegalMoves(boardBefore, true)
        if (moves.isEmpty()) return RedAnalysis(null, 0, 0, 0, emptyList())

        val scored = mutableListOf<ScoredMove>()
        val sorted = moves.sortedByDescending { moveOrderKey(boardBefore, it) }
        for (m in sorted) {
            val next = Xiangqi.applyMove(boardBefore, m.fr, m.fc, m.tr, m.tc)
            val result = negamax(next, d - 1, -INF, INF, false, d)
            // result.score 是黑方视角的 negamax；红方得分 = -result
            scored.add(ScoredMove(move = m, score = -result.score))
        }
        scored.sortByDescending { it.score }

        val best = scored[0]
        var played = scored.find { sameMove(it.move, playerMove) }
        if (played == null) {
            val next = Xiangqi.applyMove(boardBefore, playerMove.fr, playerMove.fc, playerMove.tr, playerMove.tc)
            val result = negamax(next, d - 1, -INF, INF, false, d)
            played = ScoredMove(move = playerMove, score = -result.score)
        }

        return RedAnalysis(
            best = best.move,
            bestScore = best.score,
            playerScore = played.score,
            gap = best.score - played.score,
            top = scored.take(3),
        )
    }

    /**
     * 分析黑方着法（用于解释对方为何能吃子）
     * score 均为黑方视角（越大对黑越好）
     */
    fun analyzeBlackMove(boardBefore: Board, blackMove: Xiangqi.Move, depth: Int = 4): RedAnalysis {
        val d = if (depth > 0) depth else DIFFICULTY.getValue("master").depth
        val moves = Xiangqi.generateLegalMoves(boardBefore, false)
        if (moves.isEmpty()) return RedAnalysis(null, 0, 0, 0, emptyList())

        val scored = mutableListOf<ScoredMove>()
        for (m in moves) {
            val next = Xiangqi.applyMove(boardBefore, m.fr, m.fc, m.tr, m.tc)
            val result = negamax(next, d - 1, -INF, INF, true, d)
            scored.add(ScoredMove(move = m, score = -result.score)) // 黑方得分（越大对黑越好）
        }
        scored.sortByDescending { it.score }

        val best = scored[0]
        var played = scored.find { sameMove(it.move, blackMove) }
        if (played == null) {
            val next = Xiangqi.applyMove(boardBefore, blackMove.fr, blackMove.fc, blackMove.tr, blackMove.tc)
            val result = negamax(next, d - 1, -INF, INF, true, d)
            played = ScoredMove(move = blackMove, score = -result.score)
        }

        return RedAnalysis(
            best = best.move,
            bestScore = best.score,
            playerScore = played.score,
            gap = best.score - played.score,
            top = scored.take(3),
        )
    }

    fun pieceValue(piece: Char): Int = PIECE_VALUE[piece] ?: 0
}
