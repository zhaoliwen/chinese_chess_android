package livan.chinese_chess.engine

import kotlin.math.abs

/**
 * 象棋局势 / 开局 / 杀法识别，供人声与界面提示
 *
 * 路数：红方视角从右到左 1-9 路，对应列 c = 8..0
 *
 * 移植自参考项目 js/tactics.js。
 */
object Tactics {
    val MATE_IDS: Set<String> = setOf(
        "stalemate", "mate", "iron_bolt", "horse_cannon", "smothered",
        "wocao", "double_mate", "double_rook", "heavy_cannon", "side_tiger",
        "fishing_horse", "bottom_cannon",
    )

    /** 走子上下文：boardBefore 走子前棋盘，captured 被吃子（无吃子为 [Xiangqi.EMPTY]），moveCount 已走完的总步数（含本步） */
    data class TacticContext(
        val boardBefore: Xiangqi.Board,
        val move: Xiangqi.Move,
        val piece: Char,
        val captured: Char,
        val moverIsRed: Boolean,
        val moveCount: Int,
    )

    /** 内部单条战术事件（对应 JS 中的事件对象，kind 可缺省） */
    data class TacticEvent(
        val id: String,
        val label: String,
        val voice: String,
        val kind: String? = null,
        val isCheckStyle: Boolean = false,
    )

    data class TacticResult(
        val id: String,
        val label: String,
        val voice: String,
        val kind: String,
        val isTerminal: Boolean,
        val events: List<TacticEvent> = emptyList(),
    )

    // 本局已播报过的开局，避免重复
    private var announcedOpenings = mutableSetOf<String>()

    fun resetSession() {
        announcedOpenings = mutableSetOf()
    }

    fun isMateId(id: String): Boolean = MATE_IDS.contains(id)

    /**
     * @param board 走子后棋盘
     * @param sideToMoveRed 当前应走方（刚被将军的一方）
     * @param ctx 走子上下文
     */
    fun analyze(board: Xiangqi.Board, sideToMoveRed: Boolean, ctx: TacticContext): TacticResult? {
        val events = mutableListOf<TacticEvent>()

        // 1) 开局招法（优先轻量提示，再叠加将军类）
        val opening = detectOpening(board, ctx)
        if (opening != null && !announcedOpenings.contains(opening.id)) {
            announcedOpenings.add(opening.id)
            events.add(opening)
        }

        val inCheck = Xiangqi.isInCheck(board, sideToMoveRed)
        val legal = Xiangqi.generateLegalMoves(board, sideToMoveRed)

        // 2) 终局
        if (!inCheck && legal.isEmpty()) {
            events.add(TacticEvent(id = "stalemate", label = "困毙", voice = "困毙"))
            return finalize(events, sideToMoveRed)
        }

        if (inCheck && legal.isEmpty()) {
            val mate = detectMatePattern(board, sideToMoveRed, ctx)
            events.add(mate)
            return finalize(events, sideToMoveRed)
        }

        // 3) 将军类
        if (inCheck) {
            val checkers = getCheckers(board, sideToMoveRed)
            val checkEvt = detectCheckEvent(board, sideToMoveRed, checkers, ctx)
            events.add(checkEvt)
            return finalize(events, sideToMoveRed)
        }

        // 4) 非将军的战术亮点（吃子、捉等）
        val quiet = detectQuietTactic(board, sideToMoveRed, ctx)
        if (quiet != null) events.add(quiet)

        return if (events.isNotEmpty()) finalize(events, sideToMoveRed) else null
    }

    /** 合并多条为一条展示/播报 */
    private fun finalize(events: List<TacticEvent>, sideToMoveRed: Boolean): TacticResult? {
        if (events.isEmpty()) return null
        // 终局/将军类用最后一条做主事件，开局可拼进 voice
        val main = events[events.size - 1]
        val opening = events.find { it.kind == "opening" }
        val labels = events.map { it.label }

        // 区分将军 / 被将军（对人视角：红方玩家）
        val voiceParts = events.map { e ->
            val v = e.voice
            if (!e.isCheckStyle) {
                v
            } else if (sideToMoveRed) {
                // 红方被将
                if (v == "将军") "被将军"
                else if (!v.startsWith("被")) "被将军，$v"
                else v
            } else if (v == "被将军") {
                "将军"
            } else {
                v
            }
        }

        // 开局 + 将军：例如「仙人指路」与后续不冲突时都播
        val voice = uniqueJoin(voiceParts)
        val label = labels[labels.size - 1]
        val prefix = if (opening != null && main.kind != "opening") {
            "${opening.label}·$label"
        } else {
            labels.joinToString("·")
        }

        return TacticResult(
            id = main.id,
            label = prefix,
            voice = voice,
            kind = main.kind ?: "tactic",
            isTerminal = isMateId(main.id),
            events = events,
        )
    }

    private fun uniqueJoin(parts: List<String>): String {
        val out = mutableListOf<String>()
        for (p in parts) {
            if (p.isEmpty()) continue
            if (out.isNotEmpty() && out[out.size - 1] == p) continue
            out.add(p)
        }
        return out.joinToString("，")
    }

    // ---------- 开局 ----------

    private fun detectOpening(board: Xiangqi.Board, ctx: TacticContext): TacticEvent? {
        if (ctx.piece == Xiangqi.EMPTY || ctx.moveCount > 12) return null
        val move = ctx.move
        val piece = ctx.piece
        val moverIsRed = ctx.moverIsRed
        val type = piece.uppercaseChar()
        val fr = move.fr
        val fc = move.fc
        val tr = move.tr
        val tc = move.tc

        // 仙人指路：三/七路兵挺一步
        if (type == 'P') {
            if (moverIsRed && fr == 6 && tr == 5 && (fc == 2 || fc == 6) && tc == fc) {
                return TacticEvent(id = "op_xianren", label = "仙人指路", voice = "仙人指路", kind = "opening")
            }
            if (!moverIsRed && fr == 3 && tr == 4 && (fc == 2 || fc == 6) && tc == fc) {
                return TacticEvent(id = "op_xianren_b", label = "仙人指路", voice = "仙人指路", kind = "opening")
            }
            // 中兵
            if (moverIsRed && fr == 6 && tr == 5 && fc == 4 && tc == 4) {
                return TacticEvent(id = "op_zhongbing", label = "中兵", voice = "中兵", kind = "opening")
            }
            if (!moverIsRed && fr == 3 && tr == 4 && fc == 4 && tc == 4) {
                return TacticEvent(id = "op_zhongbing_b", label = "中兵", voice = "中兵", kind = "opening")
            }
            // 边兵
            if (moverIsRed && fr == 6 && tr == 5 && (fc == 0 || fc == 8) && tc == fc) {
                return TacticEvent(id = "op_bianbing", label = "边兵", voice = "边兵", kind = "opening")
            }
            if (!moverIsRed && fr == 3 && tr == 4 && (fc == 0 || fc == 8) && tc == fc) {
                return TacticEvent(id = "op_bianbing_b", label = "边兵", voice = "边兵", kind = "opening")
            }
        }

        // 当头炮：炮平中路
        if (type == 'C' && tr == fr && tc == 4 && (fc == 1 || fc == 7)) {
            if (moverIsRed && fr == 7) {
                return TacticEvent(id = "op_dangtou", label = "当头炮", voice = "当头炮", kind = "opening")
            }
            if (!moverIsRed && fr == 2) {
                return TacticEvent(id = "op_dangtou_b", label = "当头炮", voice = "当头炮", kind = "opening")
            }
        }

        // 士角炮
        if (type == 'C' && tr == fr && (tc == 3 || tc == 5) && (fc == 1 || fc == 7)) {
            if ((moverIsRed && fr == 7) || (!moverIsRed && fr == 2)) {
                return TacticEvent(id = "op_shijiao", label = "士角炮", voice = "士角炮", kind = "opening")
            }
        }

        // 过宫炮：炮横穿中线到另一侧
        if (type == 'C' && tr == fr && ((fc < 4 && tc > 4) || (fc > 4 && tc < 4))) {
            if ((moverIsRed && fr == 7) || (!moverIsRed && fr == 2)) {
                return TacticEvent(id = "op_guogong", label = "过宫炮", voice = "过宫炮", kind = "opening")
            }
        }

        // 飞相局
        if (type == 'B') {
            if (moverIsRed && fr == 9 && (fc == 2 || fc == 6) && tr == 7 && tc == 4) {
                return TacticEvent(id = "op_feixiang", label = "飞相局", voice = "飞相局", kind = "opening")
            }
            if (!moverIsRed && fr == 0 && (fc == 2 || fc == 6) && tr == 2 && tc == 4) {
                return TacticEvent(id = "op_feixiang_b", label = "飞相局", voice = "飞相局", kind = "opening")
            }
        }

        // 起马局
        if (type == 'N') {
            if (moverIsRed && fr == 9 && ((fc == 1 && tr == 7 && tc == 2) || (fc == 7 && tr == 7 && tc == 6))) {
                return TacticEvent(id = "op_qima", label = "起马局", voice = "起马局", kind = "opening")
            }
            if (!moverIsRed && fr == 0 && ((fc == 1 && tr == 2 && tc == 2) || (fc == 7 && tr == 2 && tc == 6))) {
                return TacticEvent(id = "op_qima_b", label = "起马局", voice = "起马局", kind = "opening")
            }
        }

        // 屏风马：双方马到相肩（红 7,2 与 7,6）
        if (hasScreenHorses(board, true) && !announcedOpenings.contains("op_pingfeng")) {
            return TacticEvent(id = "op_pingfeng", label = "屏风马", voice = "屏风马", kind = "opening")
        }
        if (hasScreenHorses(board, false) && !announcedOpenings.contains("op_pingfeng_b")) {
            return TacticEvent(id = "op_pingfeng_b", label = "屏风马", voice = "屏风马", kind = "opening")
        }

        // 顺手炮：双方炮均已平中路对峙
        if (bothCenterCannons(board) && !announcedOpenings.contains("op_shunpao")) {
            return TacticEvent(id = "op_shunpao", label = "顺手炮", voice = "顺手炮", kind = "opening")
        }

        // 列手炮：双方炮在同一侧翼对攻（均在 2 或 6 路一带）
        if (isOppositeCannons(board) && !announcedOpenings.contains("op_liepao")) {
            return TacticEvent(id = "op_liepao", label = "列手炮", voice = "列手炮", kind = "opening")
        }

        return null
    }

    private fun hasScreenHorses(board: Xiangqi.Board, red: Boolean): Boolean {
        val r = if (red) 7 else 2
        val n = if (red) 'N' else 'n'
        return board[r][2] == n && board[r][6] == n
    }

    private fun bothCenterCannons(board: Xiangqi.Board): Boolean {
        var redC = false
        var blackC = false
        for (r in 0 until 10) {
            if (board[r][4] == 'C') redC = true
            if (board[r][4] == 'c') blackC = true
        }
        return redC && blackC
    }

    /** 列手炮粗判：红黑炮各在一侧（左对左 / 右对右）且未同时占中路 */
    private fun isOppositeCannons(board: Xiangqi.Board): Boolean {
        if (bothCenterCannons(board)) return false
        var redLeft = false
        var redRight = false
        var blackLeft = false
        var blackRight = false
        for (r in 0 until 10) {
            for (c in intArrayOf(1, 2, 3)) {
                if (board[r][c] == 'C') redLeft = true
                if (board[r][c] == 'c') blackLeft = true
            }
            for (c in intArrayOf(5, 6, 7)) {
                if (board[r][c] == 'C') redRight = true
                if (board[r][c] == 'c') blackRight = true
            }
        }
        return (redLeft && blackLeft) || (redRight && blackRight)
    }

    // ---------- 将军 / 杀法 ----------

    private fun getCheckers(board: Xiangqi.Board, matedRed: Boolean): List<Xiangqi.Attacker> {
        val king = Xiangqi.findKing(board, matedRed) ?: return emptyList()
        return Xiangqi.getAttackers(board, king.r, king.c, !matedRed)
    }

    private fun detectCheckEvent(
        board: Xiangqi.Board,
        sideToMoveRed: Boolean,
        checkers: List<Xiangqi.Attacker>,
        ctx: TacticContext,
    ): TacticEvent {
        val king = Xiangqi.findKing(board, sideToMoveRed)
        val attackerRed = !sideToMoveRed

        if (checkers.size >= 2) {
            return TacticEvent(
                id = "double_check",
                label = "双将",
                voice = "双将",
                kind = "check",
                isCheckStyle = true,
            )
        }

        if (isDiscoveredCheck(ctx.boardBefore, board, ctx.move, sideToMoveRed)) {
            return TacticEvent(
                id = "discovered",
                label = "闪将",
                voice = "闪将",
                kind = "check",
                isCheckStyle = true,
            )
        }

        if (king != null) {
            if (isHorseCannon(board, king, attackerRed, checkers)) {
                return TacticEvent(id = "horse_cannon_check", label = "马后炮", voice = "马后炮", kind = "check", isCheckStyle = true)
            }
            if (isWoCaoHorse(board, king, attackerRed, checkers)) {
                return TacticEvent(id = "wocao_check", label = "卧槽马", voice = "卧槽马", kind = "check", isCheckStyle = true)
            }
            if (isFishingHorse(board, king, attackerRed, checkers)) {
                return TacticEvent(id = "fishing_check", label = "钓鱼马", voice = "钓鱼马", kind = "check", isCheckStyle = true)
            }
            if (isSideTiger(board, king, attackerRed, checkers)) {
                return TacticEvent(id = "side_tiger_check", label = "侧面虎", voice = "侧面虎", kind = "check", isCheckStyle = true)
            }
            if (isHeavyCannon(board, king, attackerRed, checkers)) {
                return TacticEvent(id = "heavy_cannon_check", label = "重炮", voice = "重炮将军", kind = "check", isCheckStyle = true)
            }
            if (isBottomCannon(board, king, attackerRed, checkers)) {
                return TacticEvent(id = "bottom_cannon_check", label = "沉底炮", voice = "沉底炮", kind = "check", isCheckStyle = true)
            }
            if (isDoubleRook(board, king, attackerRed, checkers)) {
                return TacticEvent(id = "double_rook_check", label = "双车错", voice = "双车错", kind = "check", isCheckStyle = true)
            }
        }

        return TacticEvent(
            id = "check",
            label = if (sideToMoveRed) "被将军" else "将军",
            voice = if (sideToMoveRed) "被将军" else "将军",
            kind = "check",
            isCheckStyle = true,
        )
    }

    private fun detectMatePattern(board: Xiangqi.Board, matedRed: Boolean, ctx: TacticContext): TacticEvent {
        val king = Xiangqi.findKing(board, matedRed)
            ?: return TacticEvent(id = "mate", label = "绝杀", voice = "绝杀", kind = "mate")
        val attackerRed = !matedRed
        val checkers = getCheckers(board, matedRed)

        class MateCheck(
            val fn: (Xiangqi.Board, Xiangqi.Pos, Boolean, List<Xiangqi.Attacker>) -> Boolean,
            val id: String,
            val label: String,
            val voice: String,
        )

        val checks = listOf(
            MateCheck(::isIronBolt, "iron_bolt", "铁门栓", "铁门栓"),
            MateCheck(::isDoubleRook, "double_rook", "双车错", "双车错，绝杀"),
            MateCheck(::isHorseCannon, "horse_cannon", "马后炮", "马后炮，绝杀"),
            MateCheck(::isHeavyCannon, "heavy_cannon", "重炮", "重炮绝杀"),
            MateCheck({ b, k, ar, ch -> isSmotheredPalace(b, k, ar, ch, matedRed) }, "smothered", "闷宫", "闷宫"),
            MateCheck(::isWoCaoHorse, "wocao", "卧槽马", "卧槽马，绝杀"),
            MateCheck(::isFishingHorse, "fishing_horse", "钓鱼马", "钓鱼马，绝杀"),
            MateCheck(::isSideTiger, "side_tiger", "侧面虎", "侧面虎，绝杀"),
            MateCheck(::isBottomCannon, "bottom_cannon", "沉底炮", "沉底炮，绝杀"),
        )

        for (c in checks) {
            val ok = c.fn(board, king, attackerRed, checkers)
            if (ok) return TacticEvent(id = c.id, label = c.label, voice = c.voice, kind = "mate")
        }

        if (checkers.size >= 2) {
            return TacticEvent(id = "double_mate", label = "双将绝杀", voice = "双将，绝杀", kind = "mate")
        }
        if (isDiscoveredCheck(ctx.boardBefore, board, ctx.move, matedRed)) {
            return TacticEvent(id = "mate", label = "闪将绝杀", voice = "闪将，绝杀", kind = "mate")
        }
        return TacticEvent(id = "mate", label = "绝杀", voice = "绝杀", kind = "mate")
    }

    private fun detectQuietTactic(board: Xiangqi.Board, sideToMoveRed: Boolean, ctx: TacticContext): TacticEvent? {
        if (ctx.piece == Xiangqi.EMPTY) return null
        val move = ctx.move
        val piece = ctx.piece
        val captured = ctx.captured
        val moverIsRed = ctx.moverIsRed

        // 吃士 / 吃象
        if (captured != Xiangqi.EMPTY) {
            val t = captured.uppercaseChar()
            if (t == 'A') {
                return TacticEvent(id = "eat_advisor", label = "破士", voice = "破士", kind = "quiet")
            }
            if (t == 'B') {
                return TacticEvent(id = "eat_elephant", label = "破相", voice = "破相", kind = "quiet")
            }
            // 吃车
            if (t == 'R') {
                return TacticEvent(id = "eat_rook", label = "吃车", voice = "吃车", kind = "quiet")
            }
        }

        // 沉底炮到位（未将军，每局报一次）
        if (piece.uppercaseChar() == 'C') {
            val back = if (moverIsRed) 0 else 9
            if (move.tr == back && !announcedOpenings.contains("quiet_bottom_cannon")) {
                announcedOpenings.add("quiet_bottom_cannon")
                return TacticEvent(id = "park_bottom_cannon", label = "沉底炮", voice = "沉底炮", kind = "quiet")
            }
        }

        // 马突入对方阵地（每局只报一次）
        if (
            piece.uppercaseChar() == 'N' &&
            moverIsRed &&
            move.tr <= 3 &&
            abs(move.tc - 4) <= 2 &&
            !announcedOpenings.contains("quiet_horse_rush")
        ) {
            announcedOpenings.add("quiet_horse_rush")
            return TacticEvent(id = "high_horse", label = "奔袭", voice = "马奔袭", kind = "quiet")
        }

        // 捉双：走子后该子同时攻击对方两个无根大子（车马炮）
        if (isFork(board, move, moverIsRed)) {
            return TacticEvent(id = "fork", label = "捉双", voice = "捉双", kind = "quiet")
        }

        return null
    }

    private fun isFork(board: Xiangqi.Board, move: Xiangqi.Move, moverIsRed: Boolean): Boolean {
        val p = board[move.tr][move.tc]
        if (p == Xiangqi.EMPTY) return false
        val attacks = Xiangqi.generatePseudoMoves(board, move.tr, move.tc)
        val big = mutableListOf<Char>()
        for (m in attacks) {
            val t = board[m.tr][m.tc]
            if (t == Xiangqi.EMPTY || Xiangqi.isRed(t) == moverIsRed) continue
            val u = t.uppercaseChar()
            if (u == 'R' || u == 'N' || u == 'C' || u == 'K') {
                // 无根或价值高：简化为打到大子即计
                big.add(u)
            }
        }
        val uniq = big.filter { it != 'K' }.toSet()
        return uniq.size >= 2
    }

    private fun isDiscoveredCheck(
        boardBefore: Xiangqi.Board,
        boardAfter: Xiangqi.Board,
        move: Xiangqi.Move,
        sideInCheck: Boolean,
    ): Boolean {
        if (Xiangqi.isInCheck(boardBefore, sideInCheck)) return false
        if (!Xiangqi.isInCheck(boardAfter, sideInCheck)) return false
        val king = Xiangqi.findKing(boardAfter, sideInCheck) ?: return false
        val attackerRed = !sideInCheck
        val checkers = Xiangqi.getAttackers(boardAfter, king.r, king.c, attackerRed)
        val moverChecks = checkers.any { ch -> ch.r == move.tr && ch.c == move.tc }
        // 走动的子本身不将军，却出现将军 => 闪将
        if (!moverChecks && checkers.isNotEmpty()) return true
        // 走动的子将军，同时另有子因让开线路而将军
        if (moverChecks && checkers.size >= 2) {
            for (ch in checkers) {
                if (ch.r == move.tr && ch.c == move.tc) continue
                if (onSegment(ch.r, ch.c, king.r, king.c, move.fr, move.fc)) return true
            }
        }
        return false
    }

    private fun onSegment(r1: Int, c1: Int, r2: Int, c2: Int, pr: Int, pc: Int): Boolean {
        if (r1 == r2 && pr == r1) {
            return pc > minOf(c1, c2) && pc < maxOf(c1, c2)
        }
        if (c1 == c2 && pc == c1) {
            return pr > minOf(r1, r2) && pr < maxOf(r1, r2)
        }
        return false
    }

    private fun isIronBolt(board: Xiangqi.Board, king: Xiangqi.Pos, attackerRed: Boolean, checkers: List<Xiangqi.Attacker>): Boolean {
        val boltRank = king.r
        var boltRook: Xiangqi.Pos? = null
        for (c in 0 until 9) {
            val p = board[boltRank][c]
            if (p == Xiangqi.EMPTY || Xiangqi.isRed(p) != attackerRed) continue
            if (p.uppercaseChar() != 'R' || c == king.c) continue
            boltRook = Xiangqi.Pos(boltRank, c)
            break
        }
        val br = boltRook ?: return false
        val onBack = boltRank == (if (attackerRed) 0 else 9)
        val hasFileCheck = checkers.any { ch ->
            if (ch.r == br.r && ch.c == br.c) false
            else ch.c == king.c
        }
        val otherChecker = checkers.any { ch ->
            !(ch.r == br.r && ch.c == br.c)
        }
        if (hasFileCheck) return true
        if (onBack && otherChecker) return true
        return false
    }

    private fun isHorseCannon(board: Xiangqi.Board, king: Xiangqi.Pos, attackerRed: Boolean, checkers: List<Xiangqi.Attacker>): Boolean {
        for (ch in checkers) {
            if (ch.piece.uppercaseChar() != 'C') continue
            if (ch.r != king.r && ch.c != king.c) continue
            val between = piecesBetween(board, ch.r, ch.c, king.r, king.c)
            if (between.size != 1) continue
            val mid = between[0]
            val mp = board[mid.r][mid.c]
            if (mp != Xiangqi.EMPTY && Xiangqi.isRed(mp) == attackerRed && mp.uppercaseChar() == 'N') return true
        }
        return false
    }

    private fun isSmotheredPalace(board: Xiangqi.Board, king: Xiangqi.Pos, attackerRed: Boolean, checkers: List<Xiangqi.Attacker>, matedRed: Boolean): Boolean {
        if (!checkers.any { ch -> ch.piece.uppercaseChar() == 'C' }) return false
        val dirs = arrayOf(intArrayOf(-1, 0), intArrayOf(1, 0), intArrayOf(0, -1), intArrayOf(0, 1))
        val ownBlock = dirs.any { d ->
            val r = king.r + d[0]
            val c = king.c + d[1]
            if (r < 0 || r > 9 || c < 0 || c > 8) {
                false
            } else {
                val p = board[r][c]
                p != Xiangqi.EMPTY && Xiangqi.isRed(p) == matedRed && p.uppercaseChar() == 'A'
            }
        }
        return ownBlock
    }

    private fun isWoCaoHorse(board: Xiangqi.Board, king: Xiangqi.Pos, attackerRed: Boolean, checkers: List<Xiangqi.Attacker>): Boolean {
        val horses = checkers.filter { ch -> ch.piece.uppercaseChar() == 'N' }
        val forward = if (attackerRed) 1 else -1
        for (ch in horses) {
            if (ch.r == king.r + 2 * forward && abs(ch.c - king.c) == 1) return true
            if (abs(ch.r - king.r) == 2 && abs(ch.c - king.c) == 1) {
                if (attackerRed && ch.r > king.r) return true
                if (!attackerRed && ch.r < king.r) return true
            }
        }
        return false
    }

    /** 钓鱼马：马在将的侧前方「日」字位将军（偏肋道） */
    private fun isFishingHorse(board: Xiangqi.Board, king: Xiangqi.Pos, attackerRed: Boolean, checkers: List<Xiangqi.Attacker>): Boolean {
        if (isWoCaoHorse(board, king, attackerRed, checkers)) return false
        val horses = checkers.filter { ch -> ch.piece.uppercaseChar() == 'N' }
        val forward = if (attackerRed) 1 else -1
        for (ch in horses) {
            // 典型：将前一格、旁两格
            if (ch.r == king.r + forward && abs(ch.c - king.c) == 2) return true
            if (abs(ch.r - king.r) == 1 && abs(ch.c - king.c) == 2) {
                if ((attackerRed && ch.r >= king.r) || (!attackerRed && ch.r <= king.r)) return true
            }
        }
        return false
    }

    /** 侧面虎：马在将侧翼近身将军（虎形） */
    private fun isSideTiger(board: Xiangqi.Board, king: Xiangqi.Pos, attackerRed: Boolean, checkers: List<Xiangqi.Attacker>): Boolean {
        val horses = checkers.filter { ch -> ch.piece.uppercaseChar() == 'N' }
        for (ch in horses) {
            if (abs(ch.r - king.r) == 2 && abs(ch.c - king.c) == 1) {
                // 偏在将的侧面（列差在九宫边）
                if (ch.c == 2 || ch.c == 6 || king.c != 4) return true
            }
        }
        // 车在将同一侧肋道将军也可称侧面攻击，偏侧面虎杀型时配合马
        return false
    }

    /** 重炮：两炮同线，一炮作架或重叠将军 */
    private fun isHeavyCannon(board: Xiangqi.Board, king: Xiangqi.Pos, attackerRed: Boolean, checkers: List<Xiangqi.Attacker>): Boolean {
        val cannons = mutableListOf<Xiangqi.Pos>()
        for (r in 0 until 10) {
            for (c in 0 until 9) {
                val p = board[r][c]
                if (p != Xiangqi.EMPTY && Xiangqi.isRed(p) == attackerRed && p.uppercaseChar() == 'C') {
                    cannons.add(Xiangqi.Pos(r, c))
                }
            }
        }
        if (cannons.size < 2) return false
        // 两炮同列或同行，且该线指向将
        for (i in 0 until cannons.size) {
            for (j in i + 1 until cannons.size) {
                val a = cannons[i]
                val b = cannons[j]
                val sameLine =
                    (a.r == b.r && a.r == king.r) || (a.c == b.c && a.c == king.c)
                if (!sameLine) continue
                if (checkers.any { ch -> ch.piece.uppercaseChar() == 'C' }) return true
            }
        }
        return false
    }

    /** 沉底炮将军 */
    private fun isBottomCannon(board: Xiangqi.Board, king: Xiangqi.Pos, attackerRed: Boolean, checkers: List<Xiangqi.Attacker>): Boolean {
        val back = if (attackerRed) 0 else 9
        return checkers.any { ch ->
            ch.piece.uppercaseChar() == 'C' && ch.r == back
        }
    }

    /** 双车错：双车将军，或一车将军且另一车控将的另一条逃线 */
    private fun isDoubleRook(board: Xiangqi.Board, king: Xiangqi.Pos, attackerRed: Boolean, checkers: List<Xiangqi.Attacker>): Boolean {
        val rookCheckers = checkers.filter { ch -> ch.piece.uppercaseChar() == 'R' }
        if (rookCheckers.size >= 2) return true
        if (rookCheckers.size != 1) return false
        val main = rookCheckers[0]
        for (r in 0 until 10) {
            for (c in 0 until 9) {
                val p = board[r][c]
                if (p == Xiangqi.EMPTY || Xiangqi.isRed(p) != attackerRed || p.uppercaseChar() != 'R') continue
                if (r == main.r && c == main.c) continue
                // 另一车与将同线，且与将军车不在同一条攻击线（形成交错）
                val shareKingLine = r == king.r || c == king.c
                val differentLine = r != main.r && c != main.c
                if (shareKingLine && (differentLine || (r == king.r && main.c == king.c) || (c == king.c && main.r == king.r))) {
                    return true
                }
            }
        }
        return false
    }

    private fun piecesBetween(board: Xiangqi.Board, r1: Int, c1: Int, r2: Int, c2: Int): List<Xiangqi.Pos> {
        val list = mutableListOf<Xiangqi.Pos>()
        if (r1 == r2) {
            val cMin = minOf(c1, c2)
            val cMax = maxOf(c1, c2)
            for (c in cMin + 1 until cMax) {
                if (board[r1][c] != Xiangqi.EMPTY) list.add(Xiangqi.Pos(r1, c))
            }
        } else if (c1 == c2) {
            val rMin = minOf(r1, r2)
            val rMax = maxOf(r1, r2)
            for (r in rMin + 1 until rMax) {
                if (board[r][c1] != Xiangqi.EMPTY) list.add(Xiangqi.Pos(r, c1))
            }
        }
        return list
    }
}
