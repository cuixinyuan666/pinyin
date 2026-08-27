package com.cuixinyuan.pinyin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * 简易坦克大战（Canvas 实现，参考经典坦克大战玩法，MIT 风格自研集成）。
 * 玩家坦克在底部左右移动并射击，击毁从上方向下移动的敌方坦克得分。
 */
public class TankView extends View {

    public static final int DIR_UP = 0, DIR_DOWN = 1, DIR_LEFT = 2, DIR_RIGHT = 3;

    private final Paint bgPaint = new Paint();
    private final Paint playerPaint = new Paint();
    private final Paint enemyPaint = new Paint();
    private final Paint bulletPaint = new Paint();
    private final Paint wallPaint = new Paint();
    private final Random rnd = new Random();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Bullet> bullets = new ArrayList<>();
    private final List<Enemy> enemies = new ArrayList<>();
    private final List<RectF> walls = new ArrayList<>();

    private float pw, ph;
    private float px, py;
    private int pDir = DIR_UP;
    private int score = 0;
    private int lives = 3;
    private boolean running = false;
    private int moveDir = -1;
    private long lastShot = 0;
    private long enemySpawn = 0;
    private ScoreListener scoreListener;
    private LivesListener livesListener;

    private static final long TICK_MS = 32;
    private static final long SHOT_COOLDOWN = 350;
    private static final long ENEMY_INTERVAL = 1800;

    public TankView(Context context) {
        super(context);
        bgPaint.setColor(Color.parseColor("#263238"));
        playerPaint.setColor(Color.parseColor("#66BB6A"));
        playerPaint.setStyle(Paint.Style.FILL);
        enemyPaint.setColor(Color.parseColor("#EF5350"));
        enemyPaint.setStyle(Paint.Style.FILL);
        bulletPaint.setColor(Color.parseColor("#FFEB3B"));
        bulletPaint.setStyle(Paint.Style.FILL);
        wallPaint.setColor(Color.parseColor("#546E7A"));
        wallPaint.setStyle(Paint.Style.FILL);
    }

    public interface ScoreListener { void onScore(int score); }
    public interface LivesListener { void onLives(int lives); }

    public void setScoreListener(ScoreListener l) { scoreListener = l; }
    public void setLivesListener(LivesListener l) { livesListener = l; }
    public int getScore() { return score; }
    public int getLives() { return lives; }

    public void startGame() {
        running = true;
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, TICK_MS);
    }

    public void stopGame() {
        running = false;
        handler.removeCallbacks(tick);
    }

    public void setMoveDirection(int dir) { moveDir = dir; }
    public void clearMove() { moveDir = -1; }

    public void fire() {
        if (!running) return;
        long now = System.currentTimeMillis();
        if (now - lastShot < SHOT_COOLDOWN) return;
        lastShot = now;
        float bx = px + pw / 2f;
        float by = py;
        int bd = DIR_UP;
        bullets.add(new Bullet(bx, by, bd, true));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        pw = w * 0.12f;
        ph = pw * 0.9f;
        px = w / 2f - pw / 2f;
        py = h - ph - dp(8);
        buildWalls(w, h);
    }

    private void buildWalls(int w, int h) {
        walls.clear();
        float bw = w * 0.18f;
        float bh = dp(12);
        walls.add(new RectF(w * 0.2f, h * 0.45f, w * 0.2f + bw, h * 0.45f + bh));
        walls.add(new RectF(w * 0.6f, h * 0.55f, w * 0.6f + bw, h * 0.55f + bh));
        walls.add(new RectF(w * 0.35f, h * 0.3f, w * 0.35f + bw * 0.7f, h * 0.3f + bh));
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            step();
            invalidate();
            handler.postDelayed(tick, TICK_MS);
        }
    };

    private void step() {
        float speed = pw * 0.08f;
        if (moveDir == DIR_LEFT) px -= speed;
        if (moveDir == DIR_RIGHT) px += speed;
        px = Math.max(0, Math.min(getWidth() - pw, px));

        RectF player = new RectF(px, py, px + pw, py + ph);
        for (RectF w : walls) {
            if (RectF.intersects(player, w)) {
                if (moveDir == DIR_LEFT) px = w.right;
                if (moveDir == DIR_RIGHT) px = w.left - pw;
            }
        }

        long now = System.currentTimeMillis();
        if (now - enemySpawn > ENEMY_INTERVAL && enemies.size() < 5) {
            enemySpawn = now;
            float ex = rnd.nextFloat() * (getWidth() - pw);
            enemies.add(new Enemy(ex, -ph, pw, ph, DIR_DOWN));
        }

        Iterator<Bullet> bi = bullets.iterator();
        while (bi.hasNext()) {
            Bullet b = bi.next();
            b.step(pw * 0.18f);
            if (b.x < -20 || b.x > getWidth() + 20 || b.y < -20 || b.y > getHeight() + 20) {
                bi.remove();
                continue;
            }
            boolean hitWall = false;
            for (RectF w : walls) {
                if (w.contains(b.x, b.y)) { hitWall = true; break; }
            }
            if (hitWall) { bi.remove(); continue; }

            if (b.fromPlayer) {
                Iterator<Enemy> ei = enemies.iterator();
                while (ei.hasNext()) {
                    Enemy e = ei.next();
                    if (e.contains(b.x, b.y)) {
                        ei.remove();
                        score += 10;
                        if (scoreListener != null) scoreListener.onScore(score);
                        bi.remove();
                        break;
                    }
                }
            }
        }

        Iterator<Enemy> ei = enemies.iterator();
        while (ei.hasNext()) {
            Enemy e = ei.next();
            e.step(pw * 0.04f, getWidth(), getHeight());
            if (e.y > getHeight() + ph) {
                ei.remove();
                loseLife();
                continue;
            }
            if (RectF.intersects(e.bounds(), player)) {
                ei.remove();
                loseLife();
            }
        }
    }

    private void loseLife() {
        lives--;
        if (livesListener != null) livesListener.onLives(lives);
        if (lives <= 0) stopGame();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawRect(0, 0, getWidth(), getHeight(), bgPaint);
        for (RectF w : walls) canvas.drawRect(w, wallPaint);
        drawTank(canvas, px, py, pw, ph, pDir, playerPaint);
        for (Enemy e : enemies) drawTank(canvas, e.x, e.y, e.w, e.h, e.dir, enemyPaint);
        for (Bullet b : bullets) canvas.drawCircle(b.x, b.y, pw * 0.08f, bulletPaint);
    }

    private void drawTank(Canvas c, float x, float y, float w, float h, int dir, Paint paint) {
        c.drawRoundRect(new RectF(x, y, x + w, y + h), 6, 6, paint);
        float cx = x + w / 2f, cy = y + h / 2f;
        float len = w * 0.45f;
        float ex = cx, ey = cy;
        if (dir == DIR_UP) ey -= len;
        else if (dir == DIR_DOWN) ey += len;
        else if (dir == DIR_LEFT) ex -= len;
        else ex += len;
        Paint barrel = new Paint(paint);
        barrel.setStrokeWidth(w * 0.15f);
        barrel.setStyle(Paint.Style.STROKE);
        c.drawLine(cx, cy, ex, ey, barrel);
    }

    public JSONObject saveState() {
        JSONObject o = new JSONObject();
        try {
            o.put("score", score);
            o.put("lives", lives);
        } catch (Exception ignored) { }
        return o;
    }

    public void restoreState(JSONObject o) {
        if (o == null) return;
        score = o.optInt("score", 0);
        lives = o.optInt("lives", 3);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private static class Bullet {
        float x, y;
        int dir;
        boolean fromPlayer;
        Bullet(float x, float y, int dir, boolean fromPlayer) {
            this.x = x; this.y = y; this.dir = dir; this.fromPlayer = fromPlayer;
        }
        void step(float s) {
            if (dir == DIR_UP) y -= s;
            else if (dir == DIR_DOWN) y += s;
            else if (dir == DIR_LEFT) x -= s;
            else x += s;
        }
    }

    private static class Enemy {
        float x, y, w, h;
        int dir;
        Enemy(float x, float y, float w, float h, int dir) {
            this.x = x; this.y = y; this.w = w; this.h = h; this.dir = dir;
        }
        void step(float s, int maxW, int maxH) {
            if (dir == DIR_DOWN) y += s;
            else if (dir == DIR_LEFT) { x -= s; if (x < 0) dir = DIR_RIGHT; }
            else if (dir == DIR_RIGHT) { x += s; if (x + w > maxW) dir = DIR_LEFT; }
        }
        RectF bounds() { return new RectF(x, y, x + w, y + h); }
        boolean contains(float bx, float by) { return bounds().contains(bx, by); }
    }
}
