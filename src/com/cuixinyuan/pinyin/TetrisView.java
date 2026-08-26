package com.cuixinyuan.pinyin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Random;

/**
 * 俄罗斯方块小游戏视图（识字练习后的奖励游戏之一）。
 * - 标准 10x20 棋盘，7 种方块，各含 4 个旋转态。
 * - 控制：左右移动、旋转、下落（由外部按钮调用）。
 * - 进度（棋盘、当前方块、得分）以 JSON 持久化，跨奖励环节保留。
 */
public class TetrisView extends View {

    private static final int COLS = 10;
    private static final int ROWS = 20;

    // 形状定义：SHAPES[type][state] = {x0,y0,x1,y1,x2,y2,x3,y3}
    private static final int[][][] SHAPES = {
            // I
            {{0,1,1,1,2,1,3,1}, {2,0,2,1,2,2,2,3}, {0,2,1,2,2,2,3,2}, {1,0,1,1,1,2,1,3}},
            // O
            {{1,0,2,0,1,1,2,1}, {1,0,2,0,1,1,2,1}, {1,0,2,0,1,1,2,1}, {1,0,2,0,1,1,2,1}},
            // T
            {{1,0,0,1,1,1,2,1}, {1,0,1,1,2,1,1,2}, {0,1,1,1,2,1,1,2}, {1,0,0,1,1,1,1,2}},
            // S
            {{1,0,2,0,0,1,1,1}, {1,0,1,1,2,1,2,2}, {1,1,2,1,0,2,1,2}, {0,0,0,1,1,1,1,2}},
            // Z
            {{0,0,1,0,1,1,2,1}, {2,0,1,1,2,1,1,2}, {0,1,1,1,1,2,2,2}, {1,0,0,1,1,1,0,2}},
            // J
            {{0,0,0,1,1,1,2,1}, {1,0,2,0,1,1,1,2}, {0,1,1,1,2,1,2,2}, {1,0,1,1,0,2,1,2}},
            // L
            {{2,0,0,1,1,1,2,1}, {1,0,1,1,1,2,2,2}, {0,1,1,1,2,1,0,2}, {0,0,1,0,1,1,1,2}},
    };

    private static final int[] COLORS = {
            Color.parseColor("#00E5FF"), // I
            Color.parseColor("#FFEB3B"), // O
            Color.parseColor("#AB47BC"), // T
            Color.parseColor("#66BB6A"), // S
            Color.parseColor("#EF5350"), // Z
            Color.parseColor("#42A5F5"), // J
            Color.parseColor("#FFA726"), // L
    };

    private int[][] board = new int[ROWS][COLS]; // 0 空，否则颜色值
    private int type = 0, state = 0, curX = 3, curY = 0;
    private int nextType = 0;
    private int score = 0;

    private float cell = 20;
    private boolean sized = false;
    private boolean running = false;

    private final Paint bgPaint = new Paint();
    private final Paint gridPaint = new Paint();
    private final Paint blockPaint = new Paint();

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random rnd = new Random();
    private ScoreListener scoreListener;

    private static final long SPEED = 500;

    public TetrisView(Context context) {
        super(context);
        bgPaint.setColor(Color.parseColor("#10131A"));
        gridPaint.setColor(Color.parseColor("#222A36"));
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1);
        nextType = rnd.nextInt(7);
        spawn();
    }

    public interface ScoreListener {
        void onScore(int score);
    }

    public void setScoreListener(ScoreListener l) { this.scoreListener = l; }
    public int getScore() { return score; }
    public void setScore(int s) { this.score = s; }

    public void startGame() {
        running = true;
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, SPEED);
    }

    public void stopGame() {
        running = false;
        handler.removeCallbacks(tick);
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            if (!move(0, 1)) {
                lock();
                clearLines();
                spawn();
            }
            invalidate();
            handler.postDelayed(tick, SPEED);
        }
    };

    private boolean collides(int t, int st, int x, int y) {
        int[] s = SHAPES[t][st];
        for (int i = 0; i < 4; i++) {
            int cx = x + s[i * 2];
            int cy = y + s[i * 2 + 1];
            if (cx < 0 || cx >= COLS || cy >= ROWS) return true;
            if (cy >= 0 && board[cy][cx] != 0) return true;
        }
        return false;
    }

    private void spawn() {
        type = nextType;
        nextType = rnd.nextInt(7);
        state = 0;
        curX = 3;
        curY = 0;
        if (collides(type, state, curX, curY)) {
            // 顶部即碰撞：游戏结束，清空棋盘但保留得分，继续本回合
            board = new int[ROWS][COLS];
            curY = 0;
        }
    }

    private void lock() {
        int[] s = SHAPES[type][state];
        for (int i = 0; i < 4; i++) {
            int cx = curX + s[i * 2];
            int cy = curY + s[i * 2 + 1];
            if (cy >= 0 && cy < ROWS && cx >= 0 && cx < COLS) board[cy][cx] = COLORS[type];
        }
    }

    private void clearLines() {
        int cleared = 0;
        for (int r = ROWS - 1; r >= 0; r--) {
            boolean full = true;
            for (int c = 0; c < COLS; c++) if (board[r][c] == 0) { full = false; break; }
            if (full) {
                cleared++;
                for (int k = r; k > 0; k--) System.arraycopy(board[k - 1], 0, board[k], 0, COLS);
                board[0] = new int[COLS];
                r++; // 重新检查当前行
            }
        }
        if (cleared > 0) {
            score += cleared * 100;
            if (scoreListener != null) scoreListener.onScore(score);
        }
    }

    // ===== 控制 =====
    public void moveLeft() { tryMove(-1, 0); }
    public void moveRight() { tryMove(1, 0); }
    public void softDrop() { if (move(0, 1)) { invalidate(); } else { lock(); clearLines(); spawn(); invalidate(); } }
    public void rotate() {
        int ns = (state + 1) % 4;
        if (!collides(type, ns, curX, curY)) { state = ns; invalidate(); }
        else if (!collides(type, ns, curX - 1, curY)) { state = ns; curX--; invalidate(); }
        else if (!collides(type, ns, curX + 1, curY)) { state = ns; curX++; invalidate(); }
    }

    private void tryMove(int dx, int dy) {
        if (!collides(type, state, curX + dx, curY + dy)) {
            curX += dx; curY += dy; invalidate();
        }
    }

    private boolean move(int dx, int dy) {
        if (!collides(type, state, curX + dx, curY + dy)) {
            curX += dx; curY += dy; return true;
        }
        return false;
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (w <= 0 || h <= 0) return;
        // 优先用满宽度（消除两侧留白），仅在棋盘高度超出可用高度时回退到高度限制
        cell = (float) w / COLS;
        if (cell * ROWS > h) cell = (float) h / ROWS;
        sized = true;
    }

    private float offX() { return (getWidth() - cell * COLS) / 2; }
    private float offY() { return (getHeight() - cell * ROWS) / 2; }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.parseColor("#10131A"));
        float ox = offX(), oy = offY();

        // 背景网格
        for (int r = 0; r <= ROWS; r++)
            canvas.drawLine(ox, oy + r * cell, ox + COLS * cell, oy + r * cell, gridPaint);
        for (int c = 0; c <= COLS; c++)
            canvas.drawLine(ox + c * cell, oy, ox + c * cell, oy + ROWS * cell, gridPaint);

        // 已落定方块
        blockPaint.setStyle(Paint.Style.FILL);
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (board[r][c] != 0) {
                    blockPaint.setColor(board[r][c]);
                    drawBlock(canvas, ox + c * cell, oy + r * cell);
                }
            }
        }
        // 当前方块
        blockPaint.setColor(COLORS[type]);
        int[] s = SHAPES[type][state];
        for (int i = 0; i < 4; i++) {
            int cx = curX + s[i * 2];
            int cy = curY + s[i * 2 + 1];
            if (cy >= 0) drawBlock(canvas, ox + cx * cell, oy + cy * cell);
        }
    }

    private void drawBlock(Canvas canvas, float x, float y) {
        canvas.drawRect(x + 1, y + 1, x + cell - 1, y + cell - 1, blockPaint);
        // 高光
        blockPaint.setColor(Color.parseColor("#FFFFFF"));
        blockPaint.setAlpha(60);
        canvas.drawRect(x + 1, y + 1, x + cell - 1, y + cell * 0.28f, blockPaint);
        blockPaint.setAlpha(255);
        blockPaint.setColor(COLORS[type]);
    }

    // ===================== 持久化 =====================
    public JSONObject saveState() {
        try {
            JSONObject o = new JSONObject();
            o.put("score", score);
            o.put("type", type);
            o.put("state", state);
            o.put("curX", curX);
            o.put("curY", curY);
            o.put("nextType", nextType);
            JSONArray b = new JSONArray();
            for (int r = 0; r < ROWS; r++)
                for (int c = 0; c < COLS; c++) b.put(board[r][c]);
            o.put("board", b);
            return o;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public void restoreState(JSONObject o) {
        if (o == null) return;
        try {
            score = o.optInt("score", score);
            type = o.optInt("type", 0);
            state = o.optInt("state", 0);
            curX = o.optInt("curX", 3);
            curY = o.optInt("curY", 0);
            nextType = o.optInt("nextType", 0);
            JSONArray b = o.optJSONArray("board");
            if (b != null && b.length() == ROWS * COLS) {
                board = new int[ROWS][COLS];
                for (int r = 0; r < ROWS; r++)
                    for (int c = 0; c < COLS; c++) board[r][c] = b.getInt(r * COLS + c);
            }
        } catch (Exception e) {
            // 忽略
        }
    }
}
