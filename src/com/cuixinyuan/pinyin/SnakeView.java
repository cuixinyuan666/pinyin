package com.cuixinyuan.pinyin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 贪吃蛇小游戏视图（用于识字练习后的奖励环节）。
 * - 蛇头采用仿真样式（眼睛 + 舌头 + 朝向）。
 * - 食物生成避开屏幕边缘区域，降低难度。
 * - 游戏进度（蛇身、方向、食物、得分）以 JSON 形式持久化，跨多次奖励环节持续累积。
 */
public class SnakeView extends View {

    public static final int DIR_UP = 0, DIR_DOWN = 1, DIR_LEFT = 2, DIR_RIGHT = 3;

    private final int cols = 17;
    private int rows = 27;
    private float cell = 10;

    // 蛇身：index 0 为蛇头，存储 {x, y}
    private final List<int[]> snake = new ArrayList<>();
    private int[] food = {0, 0};

    private int dir = DIR_RIGHT;
    private int pendingDir = DIR_RIGHT;

    private int score = 0;
    private boolean running = false;
    private boolean sized = false;

    private final Paint bgPaint;
    private final Paint snakePaint;
    private final Paint headPaint;
    private final Paint foodPaint;
    private final Paint borderPaint;
    private final Paint eyeWhite;
    private final Paint eyeBlack;
    private final Paint tonguePaint;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random rnd = new Random();
    private ScoreListener scoreListener;

    private static final long SPEED = 150; // 每步毫秒

    public SnakeView(Context context) {
        super(context);
        bgPaint = new Paint();
        bgPaint.setColor(Color.parseColor("#1B5E20"));
        snakePaint = new Paint();
        snakePaint.setColor(Color.parseColor("#AED581"));
        snakePaint.setStyle(Paint.Style.FILL);
        headPaint = new Paint();
        headPaint.setColor(Color.parseColor("#66BB6A"));
        headPaint.setStyle(Paint.Style.FILL);
        foodPaint = new Paint();
        foodPaint.setColor(Color.parseColor("#EF5350"));
        foodPaint.setStyle(Paint.Style.FILL);
        borderPaint = new Paint();
        borderPaint.setColor(Color.parseColor("#0D3B0D"));
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(4);
        eyeWhite = new Paint();
        eyeWhite.setColor(Color.WHITE);
        eyeWhite.setStyle(Paint.Style.FILL);
        eyeBlack = new Paint();
        eyeBlack.setColor(Color.BLACK);
        eyeBlack.setStyle(Paint.Style.FILL);
        tonguePaint = new Paint();
        tonguePaint.setColor(Color.parseColor("#E53935"));
        tonguePaint.setStyle(Paint.Style.STROKE);
        tonguePaint.setStrokeWidth(3);
        tonguePaint.setStrokeCap(Paint.Cap.ROUND);
        reset();
    }

    public interface ScoreListener {
        void onScore(int score);
    }

    public void setScoreListener(ScoreListener l) { this.scoreListener = l; }

    /** 仅设置得分（用于跨进程恢复等场景） */
    public void setScore(int s) { this.score = s; }

    public int getScore() { return score; }

    /** 开始 / 继续游戏（不重置蛇身，保留进度） */
    public void startGame() {
        running = true;
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, SPEED);
    }

    public void stopGame() {
        running = false;
        handler.removeCallbacks(tick);
    }

    private void reset() {
        snake.clear();
        int cx = cols / 2;
        int cy = rows / 2;
        snake.add(new int[]{cx, cy});
        snake.add(new int[]{cx - 1, cy});
        snake.add(new int[]{cx - 2, cy});
        dir = DIR_RIGHT;
        pendingDir = DIR_RIGHT;
        placeFood();
    }

    /** 食物生成避开屏幕边缘（留出 2 格边距） */
    private void placeFood() {
        int minX = 2, maxX = cols - 3;
        int minY = 2, maxY = rows - 3;
        if (maxX < minX || maxY < minY) { // 极小屏兜底
            minX = 0; maxX = cols - 1; minY = 0; maxY = rows - 1;
        }
        for (int tries = 0; tries < 2000; tries++) {
            int fx = minX + rnd.nextInt(maxX - minX + 1);
            int fy = minY + rnd.nextInt(maxY - minY + 1);
            boolean hit = false;
            for (int[] s : snake) {
                if (s[0] == fx && s[1] == fy) { hit = true; break; }
            }
            if (!hit) { food[0] = fx; food[1] = fy; return; }
        }
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            step();
            invalidate();
            handler.postDelayed(tick, SPEED);
        }
    };

    private void step() {
        // 应用方向（禁止掉头）
        if (pendingDir == DIR_UP && dir != DIR_DOWN) dir = DIR_UP;
        else if (pendingDir == DIR_DOWN && dir != DIR_UP) dir = DIR_DOWN;
        else if (pendingDir == DIR_LEFT && dir != DIR_RIGHT) dir = DIR_LEFT;
        else if (pendingDir == DIR_RIGHT && dir != DIR_LEFT) dir = DIR_RIGHT;

        int[] head = snake.get(0);
        int nx = head[0];
        int ny = head[1];
        if (dir == DIR_UP) ny--;
        else if (dir == DIR_DOWN) ny++;
        else if (dir == DIR_LEFT) nx--;
        else nx++;

        // 撞墙或撞到自己：本局结束，重置蛇身但保留得分，继续本回合剩余时间
        if (nx < 0 || ny < 0 || nx >= cols || ny >= rows || hitsSelf(nx, ny)) {
            reset();
            return;
        }

        snake.add(0, new int[]{nx, ny});
        if (nx == food[0] && ny == food[1]) {
            score++;
            if (scoreListener != null) scoreListener.onScore(score);
            placeFood();
        } else {
            snake.remove(snake.size() - 1);
        }
    }

    private boolean hitsSelf(int x, int y) {
        for (int[] s : snake) {
            if (s[0] == x && s[1] == y) return true;
        }
        return false;
    }

    public void setDirection(int d) { pendingDir = d; }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (w <= 0 || h <= 0) return;
        cell = (float) w / cols;
        rows = Math.max(10, (int) (h / cell));
        if (!sized) {
            sized = true;
            reset();
        } else {
            if (food[1] >= rows) placeFood();
        }
    }

    private float offY() { return (getHeight() - cell * rows) / 2; }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.parseColor("#1B5E20"));
        float ox = 0;
        float oy = offY();

        // 蛇身（从尾到头，蛇头最后画以免被覆盖）
        for (int i = snake.size() - 1; i >= 0; i--) {
            int[] s = snake.get(i);
            float x = ox + s[0] * cell;
            float y = oy + s[1] * cell;
            if (i == 0) {
                drawHead(canvas, x, y);
            } else {
                canvas.drawRoundRect(new RectF(x + 1, y + 1, x + cell - 1, y + cell - 1),
                        cell * 0.3f, cell * 0.3f, snakePaint);
            }
        }
        // 食物（苹果样式）
        float fx = ox + food[0] * cell;
        float fy = oy + food[1] * cell;
        canvas.drawCircle(fx + cell / 2, fy + cell / 2, cell / 2 - 2, foodPaint);
        // 苹果梗
        tonguePaint.setColor(Color.parseColor("#33691E"));
        canvas.drawLine(fx + cell / 2, fy + 1, fx + cell / 2 + cell * 0.15f, fy - cell * 0.15f, tonguePaint);
        tonguePaint.setColor(Color.parseColor("#E53935"));
    }

    /** 仿真蛇头：圆角方块 + 眼睛（随朝向）+ 分叉舌头 */
    private void drawHead(Canvas canvas, float x, float y) {
        float pad = 1;
        canvas.drawRoundRect(new RectF(x + pad, y + pad, x + cell - pad, y + cell - pad),
                cell * 0.35f, cell * 0.35f, headPaint);

        float cx = x + cell / 2;
        float cy = y + cell / 2;
        float r = cell * 0.16f;           // 眼睛半径
        float off = cell * 0.22f;         // 眼睛距中心
        float front = cell * 0.30f;       // 眼睛朝向前方

        // 朝向单位向量
        float dx = 0, dy = 0;
        if (dir == DIR_UP) dy = -1;
        else if (dir == DIR_DOWN) dy = 1;
        else if (dir == DIR_LEFT) dx = -1;
        else dx = 1;

        // 眼睛位置：沿朝向前方，并向两侧（垂直方向）分开
        float px = -dy, py = dx; // 垂直向量
        for (int s = -1; s <= 1; s += 2) {
            float ex = cx + dx * front + px * off * s;
            float ey = cy + dy * front + py * off * s;
            canvas.drawCircle(ex, ey, r, eyeWhite);
            canvas.drawCircle(ex + dx * r * 0.3f, ey + dy * r * 0.3f, r * 0.5f, eyeBlack);
        }

        // 分叉舌头：从前方中心伸出
        float tx = cx + dx * (cell * 0.5f);
        float ty = cy + dy * (cell * 0.5f);
        float fx = cx + dx * (cell * 0.85f);
        float fy = cy + dy * (cell * 0.85f);
        canvas.drawLine(tx, ty, fx, fy, tonguePaint);
        // 分叉
        canvas.drawLine(fx, fy, fx + dx * cell * 0.12f + px * cell * 0.12f, fy + dy * cell * 0.12f + py * cell * 0.12f, tonguePaint);
        canvas.drawLine(fx, fy, fx + dx * cell * 0.12f - px * cell * 0.12f, fy + dy * cell * 0.12f - py * cell * 0.12f, tonguePaint);
    }

    // 滑动控制方向
    private float downX, downY;

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            downX = e.getX();
            downY = e.getY();
        } else if (e.getAction() == MotionEvent.ACTION_UP) {
            float dx = e.getX() - downX;
            float dy = e.getY() - downY;
            if (Math.abs(dx) < 24 && Math.abs(dy) < 24) return true;
            if (Math.abs(dx) > Math.abs(dy)) {
                setDirection(dx > 0 ? DIR_RIGHT : DIR_LEFT);
            } else {
                setDirection(dy > 0 ? DIR_DOWN : DIR_UP);
            }
        }
        return true;
    }

    // ===================== 持久化 =====================
    public JSONObject saveState() {
        try {
            JSONObject o = new JSONObject();
            o.put("score", score);
            o.put("dir", dir);
            o.put("pendingDir", pendingDir);
            JSONArray body = new JSONArray();
            for (int[] s : snake) {
                JSONArray seg = new JSONArray();
                seg.put(s[0]); seg.put(s[1]);
                body.put(seg);
            }
            o.put("body", body);
            JSONArray f = new JSONArray();
            f.put(food[0]); f.put(food[1]);
            o.put("food", f);
            return o;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public void restoreState(JSONObject o) {
        if (o == null) return;
        try {
            score = o.optInt("score", score);
            dir = o.optInt("dir", DIR_RIGHT);
            pendingDir = o.optInt("pendingDir", DIR_RIGHT);
            JSONArray body = o.optJSONArray("body");
            if (body != null && body.length() > 0) {
                snake.clear();
                for (int i = 0; i < body.length(); i++) {
                    JSONArray seg = body.getJSONArray(i);
                    snake.add(new int[]{seg.getInt(0), seg.getInt(1)});
                }
            }
            JSONArray f = o.optJSONArray("food");
            if (f != null && f.length() == 2) {
                food[0] = f.getInt(0); food[1] = f.getInt(1);
            }
        } catch (Exception e) {
            // 解析失败则保持现状
        }
    }
}
