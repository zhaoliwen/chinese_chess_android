package livan.chinese_chess.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import livan.chinese_chess.coach.Coach
import livan.chinese_chess.engine.Board
import livan.chinese_chess.engine.Tactics
import livan.chinese_chess.engine.Xiangqi
import livan.chinese_chess.engine.XiangqiAI
import livan.chinese_chess.sound.Sfx
import livan.chinese_chess.voice.Voice
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 走子动画（220ms easeOut，与 board.js 一致） */
data class MoveAnim(val move: Xiangqi.Move, val piece: Char)

/** 终局结果（相对玩家执红） */
enum class GameResultKind { WIN, LOSE, DRAW }

data class GameResult(
    val kind: GameResultKind,
    /** 杀法/结局标签，如「铁门栓」「困毙」 */
    val label: String,
)

/** 教练气泡类型，对应 coachChat.js 的 role */
enum class BubbleRole { COACH, WARN, GOOD, SYSTEM, THINKING }

data class CoachMessage(
    val id: Long,
    val role: BubbleRole,
    val title: String,
    val lines: List<String>,
    val time: String,
)

private data class HistoryEntry(
    val board: Board,
    val redToMove: Boolean,
    val lastMove: Xiangqi.Move?,
    val moveCount: Int,
)

/** 供失子点评：玩家上一步的局面与着法（main.js lastPlayerCtx） */
private data class PlayerCtx(
    val boardBefore: Board,
    val move: Xiangqi.Move,
    val piece: Char,
)

/**
 * 游戏状态机，镜像网页版 js/main.js。
 * AI 走子与教练分析跑在 Dispatchers.Default，教练任务用 Mutex 串行。
 */
class GameViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val MOVE_ANIM_MS = 220L

        val DIFFICULTY_OPTIONS: List<Pair<String, String>> = listOf(
            "beginner" to "小白",
            "novice" to "新手",
            "master" to "大师",
        )

        fun difficultyLabel(level: String): String =
            DIFFICULTY_OPTIONS.firstOrNull { it.first == level }?.second ?: ""
    }

    private val voice = Voice(application)

    var board by mutableStateOf(Xiangqi.createInitialBoard())
        private set
    var redToMove by mutableStateOf(true)
        private set
    var gameOver by mutableStateOf(false)
        private set
    /** 终局结果；新局/悔棋清除；关掉结果条后仍保留，左侧状态可继续显示胜负 */
    var gameResult by mutableStateOf<GameResult?>(null)
        private set
    /** 是否显示棋盘旁的胜负条（可关，关了仍能看残局） */
    var showResultUi by mutableStateOf(false)
        private set
    var selected by mutableStateOf<Xiangqi.Pos?>(null)
        private set
    var lastMove by mutableStateOf<Xiangqi.Move?>(null)
        private set
    var aiThinking by mutableStateOf(false)
        private set
    var moveCount by mutableIntStateOf(0)
        private set
    var trainMode by mutableStateOf(false)
        private set
    var difficulty by mutableStateOf("novice")
        private set
    var soundOn by mutableStateOf(Sfx.isEnabled())
        private set
    var message by mutableStateOf("")
        private set
    var anim by mutableStateOf<MoveAnim?>(null)
        private set
    /** 教练建议着法提示；走子/悔棋/新局/关训练时清除，AI 走子后由 runCoachNextMoveHint 重算 */
    var coachHint by mutableStateOf<Xiangqi.Move?>(null)
        private set
    var historySize by mutableIntStateOf(0)
        private set

    val coachMessages = mutableStateListOf<CoachMessage>()

    private val history = ArrayDeque<HistoryEntry>()
    private var lastPlayerCtx: PlayerCtx? = null
    private val coachMutex = Mutex()
    private var bubbleId = 0L
    /** 新局世代号：防止旧协程（AI/动画）在新局上落子 */
    private var gameGen = 0

    init {
        newGame(playSound = false)
    }

    override fun onCleared() {
        voice.shutdown()
        super.onCleared()
    }

    // ---------- 点击 ----------

    fun onUserClick(r: Int, c: Int) {
        if (gameOver || aiThinking || !redToMove || anim != null) return
        val piece = board[r][c]
        val sel = selected
        if (sel != null) {
            val moves = Xiangqi.getMovesFrom(board, sel.r, sel.c)
            val hit = moves.find { it.tr == r && it.tc == c }
            if (hit != null) {
                doMove(hit, fromPlayer = true)
                return
            }
            // 点到己方另一子则改选
            if (piece != Xiangqi.EMPTY && Xiangqi.isRed(piece)) {
                selected = Xiangqi.Pos(r, c)
                Sfx.select()
                return
            }
            selected = null
            Sfx.illegal()
            return
        }
        if (piece != Xiangqi.EMPTY && Xiangqi.isRed(piece)) {
            selected = Xiangqi.Pos(r, c)
            Sfx.select()
        }
    }

    // ---------- 走子 ----------

    private fun pushHistory() {
        history.addLast(HistoryEntry(Xiangqi.cloneBoard(board), redToMove, lastMove, moveCount))
        historySize = history.size
    }

    fun doMove(move: Xiangqi.Move, fromPlayer: Boolean) {
        val piece = board[move.fr][move.fc]
        val captured = board[move.tr][move.tc]
        val boardBefore = Xiangqi.cloneBoard(board)
        val moverIsRed = Xiangqi.isRed(piece)
        pushHistory()
        board = Xiangqi.applyMove(board, move)
        lastMove = move
        moveCount += 1
        selected = null
        // 走子后旧提示相对的局面已不存在，一律清除；训练模式下 AI 走子后会给出新建议
        coachHint = null
        redToMove = !redToMove

        val ctx = Tactics.TacticContext(
            boardBefore = boardBefore,
            move = move,
            piece = piece,
            captured = captured,
            moverIsRed = moverIsRed,
            moveCount = moveCount,
        )
        if (fromPlayer) lastPlayerCtx = PlayerCtx(boardBefore, move, piece)

        if (captured != Xiangqi.EMPTY) Sfx.capture() else Sfx.move()

        // 走子动画结束后再做终局判定与教练点评（board.js animateMove 回调）
        val gen = gameGen
        anim = MoveAnim(move, piece)
        viewModelScope.launch {
            delay(MOVE_ANIM_MS)
            anim = null
            if (gen != gameGen) return@launch
            afterMove(ctx, fromPlayer, boardBefore, move, captured)
        }
    }

    private suspend fun afterMove(
        ctx: Tactics.TacticContext,
        fromPlayer: Boolean,
        boardBefore: Board,
        move: Xiangqi.Move,
        captured: Char,
    ) {
        val ended = resolveAfterMove(ctx)

        // 训练点评：玩家着法 / 被吃子
        if (fromPlayer && trainMode) {
            runCoachAfterPlayer(boardBefore, move)
        } else if (!fromPlayer && captured != Xiangqi.EMPTY && Xiangqi.isRed(captured) && trainMode) {
            runCoachAfterCapture(boardBefore, move, captured)
        }

        if (ended) return
        // 训练模式：对方走子后，给出当前局面的下一步建议（气泡 + 棋盘绿色闪烁指示）
        if (!fromPlayer && trainMode && redToMove && !gameOver) {
            runCoachNextMoveHint()
        }
        val inCheck = Xiangqi.isInCheck(board, redToMove)
        if (!inCheck) {
            val hasBanner = message.startsWith("【")
            if (!hasBanner) {
                if (!fromPlayer) {
                    message = "轮到你了（电脑：${difficultyLabel(difficulty)}）"
                } else if (redToMove) {
                    message = "请走棋，你执红方"
                }
            }
        }
        if (!redToMove && !gameOver) scheduleAI()
    }

    /** 根据走子上下文播报开局/战术/将军；返回是否终局（main.js resolveAfterMove） */
    private suspend fun resolveAfterMove(ctx: Tactics.TacticContext): Boolean {
        val b = board
        val red = redToMove
        val tactic = withContext(Dispatchers.Default) { Tactics.analyze(b, red, ctx) }
            ?: return false

        if (tactic.isTerminal || Tactics.isMateId(tactic.id)) {
            gameOver = true
            if (tactic.id == "stalemate") {
                gameResult = GameResult(GameResultKind.DRAW, tactic.label)
                message = "【${tactic.label}】和棋"
                Sfx.draw()
            } else {
                // redToMove 为被将死一方：红被将死则玩家负
                val playerLost = red
                gameResult = GameResult(
                    kind = if (playerLost) GameResultKind.LOSE else GameResultKind.WIN,
                    label = tactic.label,
                )
                val winner = if (playerLost) "黑方" else "红方"
                val tip = if (playerLost) "你被将死了" else "恭喜，你将死了电脑"
                message = "【${tactic.label}】${winner}胜！${tip}"
                if (playerLost) Sfx.lose() else Sfx.win()
            }
            showResultUi = true
            voice.speak(tactic.voice, force = true)
            return true
        }

        val inCheck = Xiangqi.isInCheck(b, red)
        if (inCheck) {
            val base = if (red) "请应将" else "电脑被将军"
            message = "【${tactic.label}】$base"
            Sfx.check()
            voice.speak(tactic.voice, force = true)
            return false
        }

        // 开局或吃士破相等安静战术
        message = "【${tactic.label}】"
        voice.speak(tactic.voice, force = true)
        return false
    }

    // ---------- AI ----------

    private fun scheduleAI() {
        aiThinking = true
        message = "电脑正在思考…"
        val gen = gameGen
        viewModelScope.launch {
            delay(80) // 让 UI 先刷新再算
            val b = board
            val move = withContext(Dispatchers.Default) {
                XiangqiAI.chooseMove(b, difficulty)
            }
            aiThinking = false
            if (gen != gameGen) return@launch
            if (move == null) {
                val ctx = Tactics.TacticContext(
                    boardBefore = Xiangqi.cloneBoard(board),
                    move = Xiangqi.Move(0, 0, 0, 0),
                    piece = Xiangqi.EMPTY,
                    captured = Xiangqi.EMPTY,
                    moverIsRed = false,
                    moveCount = moveCount,
                )
                resolveAfterMove(ctx)
            } else {
                doMove(move, fromPlayer = false)
            }
        }
    }

    // ---------- 教练 ----------

    private fun timeNow(): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

    private fun appendSystem(text: String) {
        coachMessages.add(CoachMessage(++bubbleId, BubbleRole.SYSTEM, "系统", listOf(text), timeNow()))
    }

    private fun appendThinking(): Long {
        val id = ++bubbleId
        coachMessages.add(
            CoachMessage(id, BubbleRole.THINKING, "大师教练", listOf("正在分析你刚才的棋步…"), timeNow()),
        )
        return id
    }

    private fun removeBubble(id: Long) {
        coachMessages.removeAll { it.id == id }
    }

    private fun appendReview(review: Coach.Review) {
        val role = when (review.type) {
            "good" -> BubbleRole.GOOD
            "capture" -> BubbleRole.WARN
            else -> BubbleRole.COACH
        }
        coachMessages.add(CoachMessage(++bubbleId, role, review.title, review.lines, timeNow()))
    }

    private fun runCoachAfterPlayer(boardBefore: Board, move: Xiangqi.Move) {
        if (!trainMode) return
        viewModelScope.launch {
            coachMutex.withLock {
                val tipId = appendThinking()
                val review = withContext(Dispatchers.Default) {
                    Coach.reviewPlayerMove(boardBefore, move)
                }
                removeBubble(tipId)
                if (review != null) {
                    appendReview(review)
                    if (trainMode) coachHint = review.suggestedMove
                }
            }
        }
    }

    private fun runCoachAfterCapture(
        boardBeforeBlack: Board,
        blackMove: Xiangqi.Move,
        captured: Char,
    ) {
        if (!trainMode || captured == Xiangqi.EMPTY || !Xiangqi.isRed(captured)) return
        viewModelScope.launch {
            coachMutex.withLock {
                val tipId = appendThinking()
                val prev = lastPlayerCtx
                val review = if (prev == null) {
                    null
                } else {
                    withContext(Dispatchers.Default) {
                        Coach.reviewCapture(
                            prev.boardBefore,
                            prev.move,
                            boardBeforeBlack,
                            blackMove,
                            captured,
                        )
                    }
                }
                removeBubble(tipId)
                if (review != null) appendReview(review)
            }
        }
    }

    /** 训练模式：对方走子后计算当前局面红方最佳着，给出下一步建议 */
    private fun runCoachNextMoveHint() {
        if (!trainMode) return
        val gen = gameGen
        val mc = moveCount
        viewModelScope.launch {
            coachMutex.withLock {
                val snapshot = Xiangqi.cloneBoard(board)
                val best = withContext(Dispatchers.Default) {
                    // analyzeRedMove 需一个参照着法：传任意合法着即可，只取其 best（无随机）
                    val moves = Xiangqi.generateLegalMoves(snapshot, true)
                    if (moves.isEmpty()) {
                        null
                    } else {
                        XiangqiAI.analyzeRedMove(snapshot, moves[0], Coach.COACH_DEPTH).best
                    }
                }
                // 计算期间玩家已走子/悔棋/新局/关训练则丢弃
                if (gen != gameGen || moveCount != mc || !trainMode || best == null) return@withLock
                coachHint = best
                coachMessages.add(
                    CoachMessage(
                        ++bubbleId,
                        BubbleRole.COACH,
                        "教练建议：下一步",
                        listOf("建议走：${Coach.describeMove(snapshot, best)}"),
                        timeNow(),
                    ),
                )
            }
        }
    }

    // ---------- 控制 ----------

    fun newGame(playSound: Boolean) {
        gameGen++
        board = Xiangqi.createInitialBoard()
        redToMove = true
        gameOver = false
        gameResult = null
        showResultUi = false
        selected = null
        coachHint = null
        history.clear()
        historySize = 0
        lastMove = null
        moveCount = 0
        aiThinking = false
        anim = null
        lastPlayerCtx = null
        Tactics.resetSession()
        message = "新局开始，你执红方先行"
        if (playSound) {
            Sfx.newGame()
            voice.speak("新局开始")
        }
        // 棋局重新开始：无论训练模式开关，都清空上一局的教练气泡
        coachMessages.clear()
        if (trainMode) {
            appendSystem("新局开始。我会盯着你的每一步，有问题随时说。")
        }
    }

    fun undo() {
        if (history.isEmpty() || aiThinking || gameOver) return

        // 轮黑悔 1 步；轮红悔 2 步（悔回双方）
        val steps = if (!redToMove) 1 else minOf(2, history.size)
        var state: HistoryEntry? = null
        repeat(steps) { state = history.removeLast() }
        val s = state ?: return
        historySize = history.size

        board = s.board
        redToMove = s.redToMove
        lastMove = s.lastMove
        moveCount = s.moveCount
        selected = null
        coachHint = null
        gameOver = false
        gameResult = null
        showResultUi = false
        anim = null
        message = "已悔棋"
        Sfx.undo()
    }

    fun toggleSound() {
        val next = !Sfx.isEnabled()
        Sfx.setEnabled(next)
        voice.setEnabled(next)
        soundOn = next
        if (next) {
            Sfx.select()
            voice.speak("音效开启")
        }
    }

    fun onTrainModeChange(on: Boolean) {
        trainMode = on
        if (on) {
            // 不清空历史气泡：同一局内关闭再开启时保留之前的点评，仅新局才重置
            if (coachMessages.isEmpty()) {
                appendSystem(
                    "训练模式已开启。大师教练会点评你的每一步：好棋给予肯定，走软或失子时会说明原因与更好的走法。",
                )
            } else {
                appendSystem("训练模式已开启，继续为你点评本局的每一步。")
            }
            voice.speak("训练模式开启")
        } else {
            coachHint = null
        }
    }

    fun onDifficultyChange(level: String) {
        difficulty = level
        if (!gameOver) {
            message = "已切换难度：${difficultyLabel(level)}（本局立即生效）"
        }
    }

    /** 关闭胜负条，棋盘残局仍保留可查看 */
    fun dismissResultUi() {
        showResultUi = false
    }

    /** 终局后再次打开胜负条 */
    fun showResultUiAgain() {
        if (gameResult != null) showResultUi = true
    }
}
