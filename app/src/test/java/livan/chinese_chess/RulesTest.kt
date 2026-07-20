package livan.chinese_chess

import livan.chinese_chess.engine.Xiangqi
import livan.chinese_chess.engine.Xiangqi.Move
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesTest {

    private fun emptyBoard(): Array<CharArray> = Array(Xiangqi.ROWS) { CharArray(Xiangqi.COLS) { Xiangqi.EMPTY } }

    @Test
    fun initialBoard_redHas44LegalMoves() {
        val board = Xiangqi.createInitialBoard()
        assertEquals(44, Xiangqi.generateLegalMoves(board, true).size)
    }

    @Test
    fun initialBoard_blackHas44LegalMoves() {
        val board = Xiangqi.createInitialBoard()
        assertEquals(44, Xiangqi.generateLegalMoves(board, false).size)
    }

    @Test
    fun rook_movesAlongOpenLines() {
        val board = emptyBoard()
        board[9][4] = 'K'
        board[0][4] = 'k'
        board[5][5] = 'R'
        // 中间有 K/k 在同列 4，车在 (5,5)：纵向被 (9,4)? 不在同列。横向/纵向空格数：
        // 纵向列5：r0..r9 除 r5 全空 → 9 个点；横向行5：c0..c8 除 c5 → 8 个点；共 17
        val moves = Xiangqi.generatePseudoMoves(board, 5, 5)
        assertEquals(17, moves.size)
    }

    @Test
    fun horse_blockedByLeg() {
        val board = emptyBoard()
        board[9][4] = 'K'
        board[0][4] = 'k'
        board[5][5] = 'N'
        // 马腿全空时 8 个落点
        assertEquals(8, Xiangqi.generatePseudoMoves(board, 5, 5).size)
        // 堵住上方马腿
        board[4][5] = 'P'
        // (3,4)(3,6) 两个落点失效
        assertEquals(6, Xiangqi.generatePseudoMoves(board, 5, 5).size)
    }

    @Test
    fun elephant_cannotCrossRiverAndEyeBlocked() {
        val board = emptyBoard()
        board[9][3] = 'K' // 不挡 (9,4) 落点
        board[0][4] = 'k'
        board[7][2] = 'B'
        // 红相在 (7,2)：可走 (5,0)(5,4)(9,0)(9,4)，不可过河到 r<5
        assertEquals(4, Xiangqi.generatePseudoMoves(board, 7, 2).size)
        // 塞象眼
        board[6][1] = 'P'
        assertEquals(3, Xiangqi.generatePseudoMoves(board, 7, 2).size)
    }

    @Test
    fun cannon_needsScreenToCapture() {
        val board = emptyBoard()
        board[9][4] = 'K'
        board[0][4] = 'k'
        board[5][0] = 'C'
        board[5][3] = 'p' // 炮架
        board[5][6] = 'r' // 可吃
        val moves = Xiangqi.generatePseudoMoves(board, 5, 0)
        // 横向：c1,c2 空(2)；c3 有子不可落；跳过炮架后 c4,c5 空不可落(已跳)，c6 可吃(1)
        // 纵向列0：r0..r9 除 r5 → 9 个（r0..r4 未跳可落 5 个，r6..r9 4 个）
        // 共 2+1+9 = 12
        assertEquals(12, moves.size)
        assertTrue(moves.any { it.tr == 5 && it.tc == 6 })
        assertFalse(moves.any { it.tr == 5 && it.tc == 3 })
    }

    @Test
    fun pawn_movesForwardOnlyBeforeRiver() {
        val board = emptyBoard()
        board[9][4] = 'K'
        board[0][4] = 'k'
        board[6][4] = 'P' // 未过河
        assertEquals(1, Xiangqi.generatePseudoMoves(board, 6, 4).size)
        board[4][4] = 'P' // 已过河（r<=4）
        assertEquals(3, Xiangqi.generatePseudoMoves(board, 4, 4).size)
    }

    @Test
    fun kingsFacing_moveFiltered() {
        val board = emptyBoard()
        board[9][4] = 'K'
        board[0][4] = 'k'
        board[5][4] = 'R' // 红车挡在两将之间
        // 车横走离开列4会导致将帅照面，只能纵向走（帅本身另有宫内存着）
        val legal = Xiangqi.generateLegalMoves(board, true)
        val rookMoves = legal.filter { it.fr == 5 && it.fc == 4 }
        assertTrue(rookMoves.isNotEmpty())
        assertTrue(rookMoves.all { it.tc == 4 })
        assertFalse(legal.isEmpty())
    }

    @Test
    fun selfCheck_moveFiltered() {
        val board = emptyBoard()
        board[9][4] = 'K'
        board[0][0] = 'k'
        board[5][4] = 'r' // 黑车将着红帅? 不在将状态: 车 r5c4 攻 K r9c4 直线无子 → 红被将军
        assertTrue(Xiangqi.isInCheck(board, true))
        // 红方所有合法着都必须应将
        val legal = Xiangqi.generateLegalMoves(board, true)
        assertFalse(legal.isEmpty())
        for (m in legal) {
            val next = Xiangqi.applyMove(board, m)
            assertFalse(Xiangqi.isInCheck(next, true))
        }
    }

    @Test
    fun flyingGeneral_kingCanCaptureFacingKing() {
        val board = emptyBoard()
        board[9][4] = 'K'
        board[0][4] = 'k'
        val moves = Xiangqi.generatePseudoMoves(board, 9, 4)
        assertTrue(moves.any { it.tr == 0 && it.tc == 4 })
    }

    @Test
    fun checkmate_detected() {
        // 黑将被红车在底线闷杀：底行全被车控住，唯一逃生格 (1,4) 被己方卒堵住
        val board = emptyBoard()
        board[0][4] = 'k'
        board[9][4] = 'K'
        board[1][4] = 'p' // 黑卒堵住将的退路
        board[0][0] = 'R' // 红车底行横扫将军，(0,1..3) 无遮挡
        assertTrue(Xiangqi.isInCheck(board, false))
        assertTrue(Xiangqi.isCheckmate(board, false))
    }

    @Test
    fun getMovesFrom_matchesLegal() {
        val board = Xiangqi.createInitialBoard()
        val moves = Xiangqi.getMovesFrom(board, 9, 1) // 红马
        assertEquals(2, moves.size)
        assertTrue(moves.all { it.fr == 9 && it.fc == 1 })
    }

    @Test
    fun applyMove_returnsNewBoard() {
        val board = Xiangqi.createInitialBoard()
        val next = Xiangqi.applyMove(board, Move(9, 1, 7, 2))
        assertEquals('N', board[9][1])
        assertEquals(Xiangqi.EMPTY, next[9][1])
        assertEquals('N', next[7][2])
    }
}
