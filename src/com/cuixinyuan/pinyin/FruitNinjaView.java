package com.cuixinyuan.pinyin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 切水果小游戏视图（识字练习后的奖励游戏之一）。
 * - 水果从底部抛起，滑动手指即可切开计分；偶尔出现炸弹，切到会扣生命。
 * - 进度（得分、生命）以 JSON 持久化，跨奖励环节保留。
 */
public class FruitNinjaView extends View {

    private static final float GRAVITY = 1400f; // px/s^2
    private final List<Fruit> fruits = new ArrayList<>();
    private final List<Float> trailX = new ArrayList<>();
    private final List<Float> trailY = new ArrayList<>();

    private int score = 0;
    private int lives = 3;
    private boolean running = false;
    private long lastSpawn = 0;
    private long lastTime = 0;

    private final Paint fruitPaint = new Paint();
    private final Paint bombPaint = new Paint();
    private final Paint leafPaint = new Paint();
    private final Paint bladePaint = new Paint();
    private final Paint textPaint = new Paint();

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random rnd = new Random();
    private ScoreListener scoreListener;
    private LivesListener livesListener;

    private static final int[] FRUIT_COLORS = {
            Color.parseColor("#EF5350"), Color.parseColor("#AB47BC"),
            Color.parseColor("#FFA726"), Color.parseColor("#66BB6A"),
            Color.parseColor("#42A5F5"), Color.parseColor("#FFEE58")
    };

    private static class Fruit {
        float x, y, vx, vy, r;
        int color;
        boolean bomb;
        boolean sliced;
        float rot;
    }

    public FruitNinjaView(Context context) {
        super(context);
        fruitPaint.setStyle(Paint.Style.FILL);
        bombPaint.setColor(Color.parseColor("#37474F"));
        bombPaint.setStyle(Paint.Style.FILL);
        leafPaint.setColor(Color.parseColor("#2E7D32"));
        leafPaint.setStyle(Paint.Style.FILL);
        bladePaint.setColor(Color.parseColor("#FFFFFF"));
        bladePaint.setStyle(Paint.Style.STROKE);
        bladePaint.setStrokeWidth(6);
        bladePaint.setStrokeCap(Paint.Cap.ROUND);
        bladePaint.setAlpha(220);
        textPaint.setColor(Color.WHITE);
    }

    public interface ScoreListener { void onScore(int score); }
    public interface LivesListener { void onLives(int lives); }

    public void setScoreListener(ScoreListener l) { this.scoreListener = l; }
    public void setLivesListener(LivesListener l) { this.livesListener = l; }
    public int getScore() { return score; }
    public int getLives() { return lives; }
    public void setScore(int s) { this.score = s; }
    public void setLives(int l) { this.lives = l; }

    public void startGame() {
        running = true;
        lastTime = System.currentTimeMillis();
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, 16);
    }

    public void stopGame() {
        running = false;
        handler.removeCallbacks(tick);
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            long now = System.currentTimeMillis();
            float dt = Math.min(0.05f, (now - lastTime) / 1000f);
            lastTime = now;

            // 生成
            if (now - lastSpawn > 850) {
                lastSpawn = now;
                int n = 1 + rnd.nextInt(2);
                for (int i = 0; i < n; i++) spawnFruit();
            }

            // 更新
            for (int i = fruits.size() - 1; i >= 0; i--) {
                Fruit f = fruits.get(i);
                f.vy += GRAVITY * dt;
                f.x += f.vx * dt;
                f.y += f.vy * dt;
                f.rot += dt * 4;
                if (f.y - f.r > getHeight() && f.vy > 0) {
                    if (!f.bomb && !f.sliced) loseLife();
                    fruits.remove(i);
                }
            }
            // 刀痕淡出
            if (trailX.size() > 12) { trailX.remove(0); trailY.remove(0); }

            invalidate();
            handler.postDelayed(tick, 16);
        }
    };

    private void spawnFruit() {
        if (getWidth() <= 0) return;
        Fruit f = new Fruit();
        f.r = 26 + rnd.nextInt(18);
        f.x = f.r + rnd.nextInt(Math.max(1, (int) (getWidth() - 2 * f.r)));
        f.y = getHeight() + f.r;
        f.vx = (float) (rnd.nextGaussian()) * 120f;
        // 向上初速度，使其大致到达屏幕中上部
        float peak = getHeight() * (0.35f + rnd.nextFloat() * 0.35f);
        f.vy = -(float) Math.sqrt(2 * GRAVITY * peak);
        f.bomb = rnd.nextFloat() < 0.16f;
        f.color = FRUIT_COLORS[rnd.nextInt(FRUIT_COLORS.length)];
        f.rot = rnd.nextFloat() * 6.28f;
        fruits.add(f);
    }

    private void loseLife() {
        lives--;
        if (livesListener != null) livesListener.onLives(lives);
        if (lives <= 0) {
            // 生命耗尽：重置场景但保留得分，继续本回合
            fruits.clear();
            lives = 3;
            if (livesListener != null) livesListener.onLives(lives);
        }
    }

    private void slice(Fruit f) {
        if (f.sliced) return;
        f.sliced = true;
        fruits.remove(f);
        if (f.bomb) loseLife();
        else {
            score++;
            if (scoreListener != null) scoreListener.onScore(score);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            trailX.clear(); trailY.clear();
            trailX.add(x); trailY.add(y);
        } else if (e.getAction() == MotionEvent.ACTION_MOVE) {
            float lx = trailX.isEmpty() ? x : trailX.get(trailX.size() - 1);
            float ly = trailY.isEmpty() ? y : trailY.get(trailY.size() - 1);
            // 检测本段与水果相交
            for (Fruit f : new ArrayList<>(fruits)) {
                if (pointSegDist(f.x, f.y, lx, ly, x, y) <= f.r) slice(f);
            }
            trailX.add(x); trailY.add(y);
            if (trailX.size() > 12) { trailX.remove(0); trailY.remove(0); }
        } else if (e.getAction() == MotionEvent.ACTION_UP) {
            trailX.clear(); trailY.clear();
        }
        return true;
    }

    private float pointSegDist(float px, float py, float ax, float ay, float bx, float by) {
        float dx = bx - ax, dy = by - ay;
        float len2 = dx * dx + dy * dy;
        float t = len2 > 0 ? ((px - ax) * dx + (py - ay) * dy) / len2 : 0;
        t = Math.max(0, Math.min(1, t));
        float cx = ax + t * dx, cy = ay + t * dy;
        return (float) Math.hypot(px - cx, py - cy);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.parseColor("#0E2A47"));
        for (Fruit f : fruits) {
            if (f.bomb) {
                canvas.drawCircle(f.x, f.y, f.r, bombPaint);
                // 引线
                canvas.drawLine(f.x, f.y - f.r, f.x + f.r * 0.4f, f.y - f.r * 1.5f, leafPaint);
            } else {
                canvas.drawCircle(f.x, f.y, f.r, fruitPaint);
                fruitPaint.setColor(f.color);
                canvas.drawCircle(f.x, f.y, f.r, fruitPaint);
                // 叶子
                canvas.drawCircle(f.x + f.r * 0.5f, f.y - f.r * 0.8f, f.r * 0.25f, leafPaint);
                fruitPaint.setColor(f.color);
            }
        }
        // 刀痕
        for (int i = 1; i < trailX.size(); i++) {
            bladePaint.setAlpha((int) (220 * (i / (float) trailX.size())));
            canvas.drawLine(trailX.get(i - 1), trailY.get(i - 1), trailX.get(i), trailY.get(i), bladePaint);
        }
        bladePaint.setAlpha(220);
    }

    // ===================== 持久化 =====================
    public JSONObject saveState() {
        try {
            JSONObject o = new JSONObject();
            o.put("score", score);
            o.put("lives", lives);
            return o;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public void restoreState(JSONObject o) {
        if (o == null) return;
        try {
            score = o.optInt("score", 0);
            lives = o.optInt("lives", 3);
        } catch (Exception e) { /* 忽略 */ }
    }
}
