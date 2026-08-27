package com.cuixinyuan.pinyin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * 坦克大战 —— 对标小霸王学习机《坦克大战》/ Battle City 玩法：
 * 格子地图、砖墙可摧毁、钢墙不可摧毁、基地守护、敌方坦克刷怪与反击。
 */
public class TankView extends View {

    public static final int DIR_UP = 0, DIR_DOWN = 1, DIR_LEFT = 2, DIR_RIGHT = 3;

    private static final int T_EMPTY = 0, T_BRICK = 1, T_STEEL = 2, T_WATER = 3, T_BASE = 4;
    private static final int COLS = 13, ROWS = 13;
    private static final int ENEMIES_PER_LEVEL = 12;
    private static final long TICK_MS = 40;
    private static final long MOVE_INTERVAL = 120;
    private static final long SHOT_COOLDOWN = 400;
    private static final long ENEMY_SHOT_MIN = 900;

    private final Paint bgPaint = new Paint();
    private final Paint brickPaint = new Paint();
    private final Paint steelPaint = new Paint();
    private final Paint waterPaint = new Paint();
    private final Paint basePaint = new Paint();
    private final Paint playerPaint = new Paint();
    private final Paint enemyPaint = new Paint();
    private final Paint bulletPaint = new Paint();
    private final Paint textPaint = new Paint();
    private final Random rnd = new Random();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Bullet> bullets = new ArrayList<>();
    private final List<EnemyTank> enemies = new ArrayList<>();
    private final int[][] map = new int[ROWS][COLS];

    private float cell;
    private int px = 4, py = ROWS - 2, pDir = DIR_UP;
    private int moveDir = -1;
    private long lastMove, lastShot, lastEnemySpawn, lastEnemyShot;
    private int score, lives = 3, level = 1;
    private int enemiesQuota, enemiesSpawned, enemiesKilled;
    private boolean running, baseAlive = true, playerAlive = true;
    private long respawnAt;
    private ScoreListener scoreListener;
    private LivesListener livesListener;

    public interface ScoreListener { void onScore(int s); }
    public interface LivesListener { void onLives(int l); }

    public TankView(Context context) {
        super(context);
        bgPaint.setColor(Color.BLACK);
        brickPaint.setColor(Color.parseColor("#D84315"));
        steelPaint.setColor(Color.parseColor("#B0BEC5"));
        waterPaint.setColor(Color.parseColor("#1565C0"));
        basePaint.setColor(Color.parseColor("#FFD54F"));
        playerPaint.setColor(Color.parseColor("#FDD835"));
        enemyPaint.setColor(Color.parseColor("#E53935"));
        bulletPaint.setColor(Color.WHITE);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(28);
        textPaint.setAntiAlias(true);
        loadLevelMap();
        resetPlayer();
    }

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

    public void setMoveDirection(int dir) { if (playerAlive) moveDir = dir; }
    public void clearMove() { moveDir = -1; }

    public void fire() {
        if (!running || !playerAlive) return;
        long now = System.currentTimeMillis();
        if (now - lastShot < SHOT_COOLDOWN) return;
        if (countPlayerBullets() >= 1) return;
        lastShot = now;
        spawnBullet(px + 0.5f, py + 0.5f, pDir, true);
    }

    private int countPlayerBullets() {
        int n = 0;
        for (Bullet b : bullets) if (b.fromPlayer) n++;
        return n;
    }

    private void loadLevelMap() {
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++) map[r][c] = T_EMPTY;

        // 经典布局：砖墙迷宫 + 钢墙 + 水域 + 底部基地
        int[][] bricks = {
                {1,1,1,0,0,0,0,0,0,1,1,1,0},
                {1,0,0,0,1,1,0,1,1,0,0,1,0},
                {0,0,1,0,1,0,0,0,1,0,1,0,0},
                {0,1,1,0,0,0,2,0,0,0,1,1,0},
                {0,0,0,0,1,0,0,0,1,0,0,0,0},
                {1,1,0,0,1,1,0,1,1,0,0,1,1},
                {0,0,0,3,3,0,0,0,3,3,0,0,0},
                {0,1,0,3,3,0,2,0,3,3,0,1,0},
                {0,1,0,0,0,0,0,0,0,0,0,1,0},
                {1,0,0,1,1,0,0,0,1,1,0,0,1},
                {0,0,0,0,0,0,0,0,0,0,0,0,0},
                {0,0,1,1,0,1,1,1,0,1,1,0,0},
                {0,0,1,1,0,1,4,1,0,1,1,0,0},
        };
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++)
                if (bricks[r][c] > 0) map[r][c] = bricks[r][c];
    }

    private void resetPlayer() {
        px = 4; py = ROWS - 2; pDir = DIR_UP;
        playerAlive = true;
    }

    private void startLevel() {
        bullets.clear();
        enemies.clear();
        enemiesSpawned = 0;
        enemiesKilled = 0;
        enemiesQuota = ENEMIES_PER_LEVEL + (level - 1) * 4;
        baseAlive = true;
        loadLevelMap();
        resetPlayer();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        cell = Math.min(w / (float) COLS, h / (float) ROWS);
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            step();
            invalidate();
            handler.postDelayed(this, TICK_MS);
        }
    };

    private void step() {
        long now = System.currentTimeMillis();
        if (!playerAlive && now > respawnAt) {
            playerAlive = true;
            resetPlayer();
        }

        if (playerAlive && moveDir >= 0 && now - lastMove > MOVE_INTERVAL) {
            if (tryMovePlayer(moveDir)) {
                pDir = moveDir;
                lastMove = now;
            }
        }

        if (enemiesSpawned < enemiesQuota && enemies.size() < 3 && now - lastEnemySpawn > 2000) {
            spawnEnemy();
            lastEnemySpawn = now;
        }

        for (EnemyTank e : enemies) {
            if (now - e.lastMove > MOVE_INTERVAL + 40) {
                if (rnd.nextFloat() < 0.25f) e.dir = rnd.nextInt(4);
                if (!tryMoveEnemy(e, e.dir)) e.dir = rnd.nextInt(4);
                e.lastMove = now;
            }
            if (now - e.lastShot > ENEMY_SHOT_MIN + rnd.nextInt(800)) {
                spawnBullet(e.x + 0.5f, e.y + 0.5f, e.dir, false);
                e.lastShot = now;
            }
        }

        Iterator<Bullet> bi = bullets.iterator();
        while (bi.hasNext()) {
            Bullet b = bi.next();
            b.step();
            int bc = (int) b.x, br = (int) b.y;
            if (bc < 0 || bc >= COLS || br < 0 || br >= ROWS) { bi.remove(); continue; }

            int tile = map[br][bc];
            if (tile == T_BRICK) {
                map[br][bc] = T_EMPTY;
                bi.remove();
                continue;
            }
            if (tile == T_STEEL || tile == T_WATER) { bi.remove(); continue; }
            if (tile == T_BASE) {
                map[br][bc] = T_EMPTY;
                baseAlive = false;
                running = false;
                bi.remove();
                continue;
            }

            if (b.fromPlayer) {
                Iterator<EnemyTank> ei = enemies.iterator();
                while (ei.hasNext()) {
                    EnemyTank e = ei.next();
                    if ((int) e.x == bc && (int) e.y == br) {
                        ei.remove();
                        score += 100;
                        enemiesKilled++;
                        if (scoreListener != null) scoreListener.onScore(score);
                        bi.remove();
                        break;
                    }
                }
            } else if (playerAlive && bc == px && br == py) {
                hitPlayer();
                bi.remove();
            }
        }

        if (enemiesKilled >= enemiesQuota && enemies.isEmpty() && baseAlive) {
            level++;
            startLevel();
        }
    }

    private boolean tryMovePlayer(int dir) {
        int nx = px, ny = py;
        if (dir == DIR_UP) ny--;
        else if (dir == DIR_DOWN) ny++;
        else if (dir == DIR_LEFT) nx--;
        else nx++;
        if (canTankAt(nx, ny)) { px = nx; py = ny; return true; }
        return false;
    }

    private boolean tryMoveEnemy(EnemyTank e, int dir) {
        int nx = (int) e.x, ny = (int) e.y;
        if (dir == DIR_UP) ny--;
        else if (dir == DIR_DOWN) ny++;
        else if (dir == DIR_LEFT) nx--;
        else nx++;
        if (canTankAt(nx, ny) && !occupiedByPlayer(nx, ny)) {
            e.x = nx; e.y = ny; return true;
        }
        return false;
    }

    private boolean occupiedByPlayer(int x, int y) {
        return playerAlive && px == x && py == y;
    }

    private boolean canTankAt(int x, int y) {
        if (x < 0 || x >= COLS || y < 0 || y >= ROWS) return false;
        int t = map[y][x];
        return t == T_EMPTY || t == T_BASE;
    }

    private void spawnEnemy() {
        int[] slots = {0, COLS / 2, COLS - 1};
        for (int c : slots) {
            if (canTankAt(c, 0) && !hasEnemyAt(c, 0)) {
                EnemyTank e = new EnemyTank(c, 0, DIR_DOWN);
                enemies.add(e);
                enemiesSpawned++;
                return;
            }
        }
    }

    private boolean hasEnemyAt(int x, int y) {
        for (EnemyTank e : enemies) if ((int) e.x == x && (int) e.y == y) return true;
        return false;
    }

    private void spawnBullet(float x, float y, int dir, boolean fromPlayer) {
        bullets.add(new Bullet(x, y, dir, fromPlayer));
    }

    private void hitPlayer() {
        playerAlive = false;
        lives--;
        if (livesListener != null) livesListener.onLives(lives);
        if (lives <= 0) { running = false; return; }
        respawnAt = System.currentTimeMillis() + 1500;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float ox = (getWidth() - cell * COLS) / 2f;
        float oy = (getHeight() - cell * ROWS) / 2f;
        canvas.drawRect(0, 0, getWidth(), getHeight(), bgPaint);

        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                float x = ox + c * cell, y = oy + r * cell;
                int t = map[r][c];
                if (t == T_BRICK) canvas.drawRect(x, y, x + cell, y + cell, brickPaint);
                else if (t == T_STEEL) canvas.drawRect(x, y, x + cell, y + cell, steelPaint);
                else if (t == T_WATER) canvas.drawRect(x, y, x + cell, y + cell, waterPaint);
                else if (t == T_BASE) {
                    canvas.drawRect(x, y, x + cell, y + cell, basePaint);
                    canvas.drawRect(x + cell * 0.2f, y + cell * 0.2f,
                            x + cell * 0.8f, y + cell * 0.8f, enemyPaint);
                }
            }
        }

        for (EnemyTank e : enemies)
            drawTank(canvas, ox + e.x * cell, oy + e.y * cell, cell, e.dir, enemyPaint);
        if (playerAlive)
            drawTank(canvas, ox + px * cell, oy + py * cell, cell, pDir, playerPaint);
        for (Bullet b : bullets)
            canvas.drawCircle(ox + b.x * cell, oy + b.y * cell, cell * 0.12f, bulletPaint);

        canvas.drawText("关" + level + " 敌" + Math.max(0, enemiesQuota - enemiesKilled),
                ox + 4, oy - 6, textPaint);
        if (!running && !baseAlive)
            canvas.drawText("基地被毁!", getWidth() / 2f - 60, getHeight() / 2f, textPaint);
        else if (!running && lives <= 0)
            canvas.drawText("游戏结束", getWidth() / 2f - 50, getHeight() / 2f, textPaint);
    }

    private void drawTank(Canvas c, float x, float y, float size, int dir, Paint body) {
        float pad = size * 0.08f;
        c.drawRoundRect(new RectF(x + pad, y + pad, x + size - pad, y + size - pad), 4, 4, body);
        float cx = x + size / 2f, cy = y + size / 2f, len = size * 0.38f;
        float ex = cx, ey = cy;
        if (dir == DIR_UP) ey -= len;
        else if (dir == DIR_DOWN) ey += len;
        else if (dir == DIR_LEFT) ex -= len;
        else ex += len;
        Paint barrel = new Paint(body);
        barrel.setStrokeWidth(size * 0.14f);
        barrel.setStyle(Paint.Style.STROKE);
        c.drawLine(cx, cy, ex, ey, barrel);
        c.drawCircle(cx, cy, size * 0.1f, barrel);
    }

    public JSONObject saveState() {
        JSONObject o = new JSONObject();
        try {
            o.put("score", score);
            o.put("lives", lives);
            o.put("level", level);
        } catch (Exception ignored) { }
        return o;
    }

    public void restoreState(JSONObject o) {
        if (o == null) { startLevel(); return; }
        score = o.optInt("score", 0);
        lives = o.optInt("lives", 3);
        level = o.optInt("level", 1);
        startLevel();
    }

    private static class Bullet {
        float x, y;
        int dir;
        boolean fromPlayer;
        Bullet(float x, float y, int dir, boolean fromPlayer) {
            this.x = x; this.y = y; this.dir = dir; this.fromPlayer = fromPlayer;
        }
        void step() {
            float s = 0.35f;
            if (dir == DIR_UP) y -= s;
            else if (dir == DIR_DOWN) y += s;
            else if (dir == DIR_LEFT) x -= s;
            else x += s;
        }
    }

    private static class EnemyTank {
        float x, y;
        int dir;
        long lastMove, lastShot;
        EnemyTank(int x, int y, int dir) {
            this.x = x; this.y = y; this.dir = dir;
            lastMove = lastShot = System.currentTimeMillis();
        }
    }
}
