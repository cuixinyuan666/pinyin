package com.cuixinyuan.pinyin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 贪吃蛇小游戏视图（用于识字练习后的奖励环节）。
 * 游戏进度（蛇身、得分）保存在本对象中，跨多次奖励环节持续累积，
 * 因此「条件达成后可继续进行游戏」。
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
        headPaint.setColor(Color.parseColor("#FFEB3B"));
        headPaint.setStyle(Paint.Style.FILL);
        foodPaint = new Paint();
        foodPaint.setColor(Color.parseColor("#EF5350"));
        foodPaint.setStyle(Paint.Style.FILL);
        borderPaint = new Paint();
        borderPaint.setColor(Color.parseColor("#0D3B0D"));
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(4);
        reset();
    }

    public interface ScoreListener {
        void onScore(int score);
    }

    public void setScoreListener(ScoreListener l) { this.scoreListener = l; }

    /** 仅重置得分（用于跨进程恢复等场景） */
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

    private void placeFood() {
        for (int tries = 0; tries < 1000; tries++) {
            int fx = rnd.nextInt(cols);
            int fy = rnd.nextInt(rows);
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
            // 尺寸变化后确保食物在范围内
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
            Paint p = (i == 0) ? headPaint : snakePaint;
            float x = ox + s[0] * cell;
            float y = oy + s[1] * cell;
            canvas.drawRect(x + 1, y + 1, x + cell - 1, y + cell - 1, p);
        }
        // 食物
        float fx = ox + food[0] * cell;
        float fy = oy + food[1] * cell;
        canvas.drawCircle(fx + cell / 2, fy + cell / 2, cell / 2 - 2, foodPaint);
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
}
