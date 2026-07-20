package livan.chinese_chess.engine

/**
 * 中国象棋规则：棋盘表示、走法生成、将军/将死判定
 * 坐标系：行 0-9（上黑下红），列 0-8
 * 红方大写，黑方小写：K帅/将 R车 N马 B相/象 A仕/士 C炮 P兵/卒
 * 空位用 [EMPTY] 表示。
 *
 * 移植自参考项目 js/rules.js。
 */
object Xiangqi {
    const val ROWS = 10
    const val COLS = 9
    const val EMPTY = '.'

    val PIECE_NAME: Map<Char, String> = mapOf(
        'K' to "帅", 'k' to "将",
        'A' to "仕", 'a' to "士",
        'B' to "相", 'b' to "象",
        'N' to "马", 'n' to "马",
        'R' to "车", 'r' to "车",
        'C' to "炮", 'c' to "炮",
        'P' to "兵", 'p' to "卒",
    )

    data class Move(val fr: Int, val fc: Int, val tr: Int, val tc: Int)

    data class Pos(val r: Int, val c: Int)

    data class Attacker(val r: Int, val c: Int, val piece: Char)

    /** 棋盘：10×9，值为棋子字符或 [EMPTY] */
    typealias Board = Array<CharArray>

    /** 初始局面 */
    fun createInitialBoard(): Board = arrayOf(
        charArrayOf('r', 'n', 'b', 'a', 'k', 'a', 'b', 'n', 'r'),
        CharArray(COLS) { EMPTY },
        charArrayOf(EMPTY, 'c', EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, 'c', EMPTY),
        charArrayOf('p', EMPTY, 'p', EMPTY, 'p', EMPTY, 'p', EMPTY, 'p'),
        CharArray(COLS) { EMPTY },
        CharArray(COLS) { EMPTY },
        charArrayOf('P', EMPTY, 'P', EMPTY, 'P', EMPTY, 'P', EMPTY, 'P'),
        charArrayOf(EMPTY, 'C', EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, 'C', EMPTY),
        CharArray(COLS) { EMPTY },
        charArrayOf('R', 'N', 'B', 'A', 'K', 'A', 'B', 'N', 'R'),
    )

    fun cloneBoard(board: Board): Board = Array(ROWS) { board[it].copyOf() }

    fun isEmpty(piece: Char): Boolean = piece == EMPTY

    fun isRed(piece: Char): Boolean = piece != EMPTY && piece.isUpperCase()

    fun isBlack(piece: Char): Boolean = piece != EMPTY && piece.isLowerCase()

    fun sameSide(a: Char, b: Char): Boolean {
        if (a == EMPTY || b == EMPTY) return false
        return isRed(a) == isRed(b)
    }

    fun inBounds(r: Int, c: Int): Boolean = r in 0 until ROWS && c in 0 until COLS

    fun inPalace(r: Int, c: Int, red: Boolean): Boolean {
        if (c < 3 || c > 5) return false
        return if (red) r in 7..9 else r in 0..2
    }

    fun findKing(board: Board, red: Boolean): Pos? {
        val target = if (red) 'K' else 'k'
        for (r in 0 until ROWS) {
            for (c in 0 until COLS) {
                if (board[r][c] == target) return Pos(r, c)
            }
        }
        return null
    }

    /** 将帅是否照面（同列且中间无子） */
    fun kingsFacing(board: Board): Boolean {
        val rk = findKing(board, true) ?: return false
        val bk = findKing(board, false) ?: return false
        if (rk.c != bk.c) return false
        val c = rk.c
        val rMin = minOf(rk.r, bk.r)
        val rMax = maxOf(rk.r, bk.r)
        for (r in rMin + 1 until rMax) {
            if (board[r][c] != EMPTY) return false
        }
        return true
    }

    fun applyMove(board: Board, fromR: Int, fromC: Int, toR: Int, toC: Int): Board {
        val next = cloneBoard(board)
        next[toR][toC] = next[fromR][fromC]
        next[fromR][fromC] = EMPTY
        return next
    }

    fun applyMove(board: Board, m: Move): Board = applyMove(board, m.fr, m.fc, m.tr, m.tc)

    /** 生成某棋子的伪合法走法（尚未过滤送将） */
    fun generatePseudoMoves(board: Board, r: Int, c: Int): List<Move> {
        val piece = board[r][c]
        if (piece == EMPTY) return emptyList()
        val red = isRed(piece)
        val type = piece.uppercaseChar()
        val moves = mutableListOf<Move>()

        fun add(tr: Int, tc: Int) {
            if (!inBounds(tr, tc)) return
            val target = board[tr][tc]
            if (target != EMPTY && sameSide(piece, target)) return
            moves.add(Move(r, c, tr, tc))
        }

        when (type) {
            'K' -> {
                val dirs = arrayOf(intArrayOf(-1, 0), intArrayOf(1, 0), intArrayOf(0, -1), intArrayOf(0, 1))
                for (d in dirs) {
                    val tr = r + d[0]
                    val tc = c + d[1]
                    if (inPalace(tr, tc, red)) add(tr, tc)
                }
                // 将帅照面吃对方将
                val enemy = findKing(board, !red)
                if (enemy != null && enemy.c == c) {
                    var blocked = false
                    val rMin = minOf(r, enemy.r)
                    val rMax = maxOf(r, enemy.r)
                    for (rr in rMin + 1 until rMax) {
                        if (board[rr][c] != EMPTY) {
                            blocked = true
                            break
                        }
                    }
                    if (!blocked) add(enemy.r, enemy.c)
                }
            }
            'A' -> {
                val dirs = arrayOf(intArrayOf(-1, -1), intArrayOf(-1, 1), intArrayOf(1, -1), intArrayOf(1, 1))
                for (d in dirs) {
                    val tr = r + d[0]
                    val tc = c + d[1]
                    if (inPalace(tr, tc, red)) add(tr, tc)
                }
            }
            'B' -> {
                val eyes = arrayOf(intArrayOf(-1, -1), intArrayOf(-1, 1), intArrayOf(1, -1), intArrayOf(1, 1))
                for (d in eyes) {
                    val er = r + d[0]
                    val ec = c + d[1]
                    val tr = r + d[0] * 2
                    val tc = c + d[1] * 2
                    if (!inBounds(tr, tc)) continue
                    // 象不过河
                    if (red && tr < 5) continue
                    if (!red && tr > 4) continue
                    if (board[er][ec] != EMPTY) continue // 塞象眼
                    add(tr, tc)
                }
            }
            'N' -> {
                // 马腿：先走正交一步再斜
                val hops = arrayOf(
                    Pair(intArrayOf(-1, 0), arrayOf(intArrayOf(-2, -1), intArrayOf(-2, 1))),
                    Pair(intArrayOf(1, 0), arrayOf(intArrayOf(2, -1), intArrayOf(2, 1))),
                    Pair(intArrayOf(0, -1), arrayOf(intArrayOf(-1, -2), intArrayOf(1, -2))),
                    Pair(intArrayOf(0, 1), arrayOf(intArrayOf(-1, 2), intArrayOf(1, 2))),
                )
                for (h in hops) {
                    val lr = r + h.first[0]
                    val lc = c + h.first[1]
                    if (!inBounds(lr, lc) || board[lr][lc] != EMPTY) continue
                    for (d in h.second) {
                        add(r + d[0], c + d[1])
                    }
                }
            }
            'R' -> {
                val dirs = arrayOf(intArrayOf(-1, 0), intArrayOf(1, 0), intArrayOf(0, -1), intArrayOf(0, 1))
                for (d in dirs) {
                    var tr = r + d[0]
                    var tc = c + d[1]
                    while (inBounds(tr, tc)) {
                        if (board[tr][tc] == EMPTY) {
                            moves.add(Move(r, c, tr, tc))
                        } else {
                            if (!sameSide(piece, board[tr][tc])) {
                                moves.add(Move(r, c, tr, tc))
                            }
                            break
                        }
                        tr += d[0]
                        tc += d[1]
                    }
                }
            }
            'C' -> {
                val dirs = arrayOf(intArrayOf(-1, 0), intArrayOf(1, 0), intArrayOf(0, -1), intArrayOf(0, 1))
                for (d in dirs) {
                    var tr = r + d[0]
                    var tc = c + d[1]
                    var jumped = false
                    while (inBounds(tr, tc)) {
                        if (!jumped) {
                            if (board[tr][tc] == EMPTY) {
                                moves.add(Move(r, c, tr, tc))
                            } else {
                                jumped = true
                            }
                        } else {
                            if (board[tr][tc] != EMPTY) {
                                if (!sameSide(piece, board[tr][tc])) {
                                    moves.add(Move(r, c, tr, tc))
                                }
                                break
                            }
                        }
                        tr += d[0]
                        tc += d[1]
                    }
                }
            }
            'P' -> {
                val forward = if (red) -1 else 1
                add(r + forward, c)
                // 过河后可横走
                val crossed = if (red) r <= 4 else r >= 5
                if (crossed) {
                    add(r, c - 1)
                    add(r, c + 1)
                }
            }
        }

        return moves
    }

    /** 检测 (tr,tc) 是否被 byRed 一方攻击（用于将军判定，避免全量生成走法） */
    fun isSquareAttacked(board: Board, tr: Int, tc: Int, byRed: Boolean): Boolean {
        // 车/将直线
        val rookDirs = arrayOf(intArrayOf(-1, 0), intArrayOf(1, 0), intArrayOf(0, -1), intArrayOf(0, 1))
        for (d in rookDirs) {
            var r = tr + d[0]
            var c = tc + d[1]
            var steps = 0
            while (inBounds(r, c)) {
                val p = board[r][c]
                if (p != EMPTY) {
                    val enemy = isRed(p) == byRed
                    val type = p.uppercaseChar()
                    if (enemy) {
                        if (type == 'R') return true
                        // 将帅同一直线且中间无子：照面攻击
                        if (type == 'K') return true
                        if (type == 'P') {
                            // 兵从 (r,c) 攻击目标；前进一步方向为 forward
                            val forward = if (byRed) -1 else 1
                            if (steps == 0 && d[0] == -forward && d[1] == 0) return true
                            val crossed = if (byRed) r <= 4 else r >= 5
                            if (steps == 0 && crossed && d[0] == 0) return true
                        }
                    }
                    break
                }
                r += d[0]
                c += d[1]
                steps++
            }
        }

        // 炮：隔一子打
        for (d in rookDirs) {
            var r = tr + d[0]
            var c = tc + d[1]
            var seen = 0
            while (inBounds(r, c)) {
                val p = board[r][c]
                if (p != EMPTY) {
                    seen++
                    if (seen == 2) {
                        if (isRed(p) == byRed && p.uppercaseChar() == 'C') return true
                        break
                    }
                }
                r += d[0]
                c += d[1]
            }
        }

        // 马：从目标格反查八个马位，马腿在马旁（日字长边方向）
        val horseFrom = arrayOf(
            intArrayOf(tr - 2, tc - 1, tr - 1, tc - 1),
            intArrayOf(tr - 2, tc + 1, tr - 1, tc + 1),
            intArrayOf(tr + 2, tc - 1, tr + 1, tc - 1),
            intArrayOf(tr + 2, tc + 1, tr + 1, tc + 1),
            intArrayOf(tr - 1, tc - 2, tr - 1, tc - 1),
            intArrayOf(tr + 1, tc - 2, tr + 1, tc - 1),
            intArrayOf(tr - 1, tc + 2, tr - 1, tc + 1),
            intArrayOf(tr + 1, tc + 2, tr + 1, tc + 1),
        )
        for (h in horseFrom) {
            if (!inBounds(h[0], h[1])) continue
            val p = board[h[0]][h[1]]
            if (p == EMPTY || isRed(p) != byRed || p.uppercaseChar() != 'N') continue
            if (board[h[2]][h[3]] == EMPTY) return true
        }

        // 士
        val advisorD = arrayOf(intArrayOf(-1, -1), intArrayOf(-1, 1), intArrayOf(1, -1), intArrayOf(1, 1))
        for (d in advisorD) {
            val r = tr + d[0]
            val c = tc + d[1]
            if (!inBounds(r, c)) continue
            val p = board[r][c]
            if (p != EMPTY && isRed(p) == byRed && p.uppercaseChar() == 'A' && inPalace(tr, tc, byRed)) {
                return true
            }
        }

        // 象
        val eleD = arrayOf(intArrayOf(-2, -2), intArrayOf(-2, 2), intArrayOf(2, -2), intArrayOf(2, 2))
        for (d in eleD) {
            val r = tr + d[0]
            val c = tc + d[1]
            if (!inBounds(r, c)) continue
            val p = board[r][c]
            if (p == EMPTY || isRed(p) != byRed || p.uppercaseChar() != 'B') continue
            val eyeR = tr + d[0] / 2
            val eyeC = tc + d[1] / 2
            if (board[eyeR][eyeC] != EMPTY) continue
            if (byRed && r < 5) continue
            if (!byRed && r > 4) continue
            return true
        }

        return false
    }

    /** 某方是否被将军 */
    fun isInCheck(board: Board, red: Boolean): Boolean {
        val king = findKing(board, red) ?: return true
        return isSquareAttacked(board, king.r, king.c, !red)
    }

    /** 合法走法：不能送将，也不能造成将帅照面 */
    fun generateLegalMoves(board: Board, red: Boolean): List<Move> {
        val legal = mutableListOf<Move>()
        for (r in 0 until ROWS) {
            for (c in 0 until COLS) {
                val p = board[r][c]
                if (p == EMPTY || isRed(p) != red) continue
                val candidates = generatePseudoMoves(board, r, c)
                for (m in candidates) {
                    val next = applyMove(board, m.fr, m.fc, m.tr, m.tc)
                    if (kingsFacing(next)) continue
                    if (isInCheck(next, red)) continue
                    legal.add(m)
                }
            }
        }
        return legal
    }

    fun getMovesFrom(board: Board, r: Int, c: Int): List<Move> {
        val p = board[r][c]
        if (p == EMPTY) return emptyList()
        val red = isRed(p)
        return generateLegalMoves(board, red).filter { it.fr == r && it.fc == c }
    }

    fun isCheckmate(board: Board, red: Boolean): Boolean {
        if (!isInCheck(board, red)) return false
        return generateLegalMoves(board, red).isEmpty()
    }

    fun isStalemate(board: Board, red: Boolean): Boolean {
        if (isInCheck(board, red)) return false
        return generateLegalMoves(board, red).isEmpty()
    }

    /** 返回攻击 (tr,tc) 的 byRed 方棋子列表 */
    fun getAttackers(board: Board, tr: Int, tc: Int, byRed: Boolean): List<Attacker> {
        val list = mutableListOf<Attacker>()
        for (r in 0 until ROWS) {
            for (c in 0 until COLS) {
                val p = board[r][c]
                if (p == EMPTY || isRed(p) != byRed) continue
                val moves = generatePseudoMoves(board, r, c)
                for (m in moves) {
                    if (m.tr == tr && m.tc == tc) {
                        list.add(Attacker(r, c, p))
                        break
                    }
                }
            }
        }
        return list
    }

    /** 两格之间是否恰有 n 个子（不含端点）；不在同行同列返回 -1 */
    fun countBetween(board: Board, r1: Int, c1: Int, r2: Int, c2: Int): Int {
        if (r1 != r2 && c1 != c2) return -1
        var count = 0
        if (r1 == r2) {
            val cMin = minOf(c1, c2)
            val cMax = maxOf(c1, c2)
            for (c in cMin + 1 until cMax) {
                if (board[r1][c] != EMPTY) count++
            }
        } else {
            val rMin = minOf(r1, r2)
            val rMax = maxOf(r1, r2)
            for (r in rMin + 1 until rMax) {
                if (board[r][c1] != EMPTY) count++
            }
        }
        return count
    }
}
