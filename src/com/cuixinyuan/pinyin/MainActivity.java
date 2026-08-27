package com.cuixinyuan.pinyin;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * 拼音识字乐园 —— 考试答题模式（直接选题作答，无分步声韵调学习流程）。
 */
public class MainActivity extends Activity implements TextToSpeech.OnInitListener {

    private static final String WORD_ASSET = "words.json";
    private static final int ROUND_SIZE = 10;
    private static final String PREFS = "pinyin_prefs";
    private static final String KEY_SNAKE = "game_snake";
    private static final String KEY_TETRIS = "game_tetris";
    private static final String KEY_FRUIT = "game_fruit";
    private static final String KEY_TANK = "game_tank";
    private static final String KEY_STATS = "word_stats";
    private static final String KEY_DURATION = "game_duration";
    private static final int DEFAULT_DURATION = 60;

    private static final String[] PRAISE = {
            "太棒啦！", "你真厉害！", "答对啦，真聪明！", "好样的！", "厉害厉害！", "你学会啦！"
    };

    private static class Word {
        final String hanzi, py, sm, ym;
        final int tone;
        Word(String hanzi, String py, String sm, String ym, int tone) {
            this.hanzi = hanzi; this.py = py; this.sm = sm; this.ym = ym; this.tone = tone;
        }
    }

    private static class ExamItem {
        final Word word;
        final boolean review;
        List<String> opts = new ArrayList<>();
        int correctIdx;
        ExamItem(Word w, boolean review) { this.word = w; this.review = review; }
    }

    private PinyinBridge pinyinBridge;
    private final List<Word> dictionary = new ArrayList<>();
    private final Set<String> tonedSet = new HashSet<>();
    private final Random rng = new Random();
    private final WordStatsManager stats = new WordStatsManager();

    private List<ExamItem> examQueue = new ArrayList<>();
    private int examIndex = 0;
    private boolean attemptLocked = false;
    private boolean firstAttemptWrong = false;
    private boolean questionPassed = false;

    private TextToSpeech tts;
    private boolean ttsReady = false;

    private static final int GAME_SNAKE = 0, GAME_TETRIS = 1, GAME_FRUIT = 2, GAME_TANK = 3;
    private int activeGame = -1;
    private boolean inGame = false;
    private int timeLeft = DEFAULT_DURATION;
    private View activeGameView = null;

    private FrameLayout root;
    private ScrollView quizScroll;
    private LinearLayout quizPanel, gameHost;
    private FrameLayout gameArea, controlPad, selectOverlay, settingsOverlay;

    private TextView tvProgress, tvHanzi, tvStatsBadge, tvFeedback, tvSelectSub;
    private ProgressBar progressBar;
    private Button btnNext, btnSettings;
    private LinearLayout examOpts;
    private List<Button> durationButtons = new ArrayList<>();

    private TextView tvTime, tvScore, tvLives, tvGameTitle;
    private Button btnEndGame;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadPinyinBridge();
        loadDictionary();
        loadStats();
        tts = new TextToSpeech(this, this);
        buildUi();
        startNewRound();
    }

    private void loadPinyinBridge() {
        try {
            InputStream is = getAssets().open("pinyin_speak.json");
            BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
            pinyinBridge = new PinyinBridge(new JSONObject(sb.toString()));
        } catch (Exception e) {
            try {
                JSONObject empty = new JSONObject();
                empty.put("initial", new JSONObject());
                empty.put("final", new JSONObject());
                empty.put("whole", new JSONArray());
                pinyinBridge = new PinyinBridge(empty);
            } catch (Exception ignored) {
                pinyinBridge = null;
            }
        }
    }

    private void loadDictionary() {
        try {
            InputStream is = getAssets().open(WORD_ASSET);
            BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
            JSONArray arr = new JSONArray(sb.toString());
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Word w = new Word(o.getString("word"), o.getString("py"),
                        o.getString("sm"), o.getString("ym"), o.getInt("tone"));
                dictionary.add(w);
                tonedSet.add(tonedPinyin(w.py, w.tone));
            }
        } catch (Exception e) {
            dictionary.add(new Word("你", "ni", "n", "i", 3));
            dictionary.add(new Word("好", "hao", "h", "ao", 3));
            for (Word w : dictionary) tonedSet.add(tonedPinyin(w.py, w.tone));
        }
    }

    private void loadStats() {
        try {
            String s = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_STATS, null);
            if (s != null) stats.load(new JSONObject(s));
        } catch (Exception ignored) { }
    }

    private void saveStats() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(KEY_STATS, stats.save().toString()).apply();
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#FFF7E6"));
        setContentView(root);
        buildQuiz();
        buildGameHost();
        buildSelect();
        buildSettings();
        quizScroll.setVisibility(View.VISIBLE);
        gameHost.setVisibility(View.GONE);
        selectOverlay.setVisibility(View.GONE);
        settingsOverlay.setVisibility(View.GONE);
    }

    private void buildQuiz() {
        quizPanel = new LinearLayout(this);
        quizPanel.setOrientation(LinearLayout.VERTICAL);
        quizPanel.setGravity(Gravity.CENTER_HORIZONTAL);
        quizPanel.setPadding(dp(12), dp(12), dp(12), dp(12));
        quizScroll = new ScrollView(this);
        quizScroll.addView(quizPanel);
        root.addView(quizScroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        btnSettings = button("⚙ 设置", Color.parseColor("#90A4AE"), Color.WHITE);
        btnSettings.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        btnSettings.setOnClickListener(v -> showSettings());
        bar.addView(btnSettings);
        quizPanel.addView(bar);

        tvProgress = textView("试题 1 / " + ROUND_SIZE, 18, Color.parseColor("#5D4037"), true);
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(ROUND_SIZE);
        progressBar.setProgress(1);
        progressBar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(12)));
        quizPanel.addView(tvProgress);
        quizPanel.addView(progressBar);

        FrameLayout hanziBox = new FrameLayout(this);
        hanziBox.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(100)));
        tvHanzi = textView("", 56, Color.parseColor("#E64A19"), true);
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hp.gravity = Gravity.CENTER;
        hanziBox.addView(tvHanzi, hp);
        tvStatsBadge = textView("", 12, Color.parseColor("#5D4037"), false);
        tvStatsBadge.setBackgroundColor(Color.parseColor("#FFE0B2"));
        tvStatsBadge.setPadding(dp(6), dp(2), dp(6), dp(2));
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.gravity = Gravity.END | Gravity.TOP;
        bp.setMargins(dp(0), dp(4), dp(4), dp(0));
        hanziBox.addView(tvStatsBadge, bp);
        quizPanel.addView(hanziBox);

        quizPanel.addView(textView("请选择正确拼音", 16, Color.parseColor("#5D4037"), false));
        examOpts = new LinearLayout(this);
        examOpts.setOrientation(LinearLayout.VERTICAL);
        examOpts.setPadding(dp(0), dp(6), dp(0), dp(6));
        quizPanel.addView(examOpts);

        tvFeedback = textView("", 17, Color.parseColor("#2E7D32"), true);
        quizPanel.addView(tvFeedback);

        btnNext = button("下一题 ➜", Color.parseColor("#FF7043"), Color.WHITE);
        btnNext.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        btnNext.setEnabled(false);
        btnNext.setOnClickListener(v -> goNext());
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(68));
        nlp.setMargins(dp(0), dp(12), dp(0), dp(8));
        btnNext.setLayoutParams(nlp);
        quizPanel.addView(btnNext);
    }

    private void buildGameHost() {
        gameHost = new LinearLayout(this);
        gameHost.setOrientation(LinearLayout.VERTICAL);
        root.addView(gameHost, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.HORIZONTAL);
        info.setGravity(Gravity.CENTER_VERTICAL);
        info.setPadding(dp(16), dp(16), dp(16), dp(16));
        info.setBackgroundColor(Color.parseColor("#CC000000"));
        tvGameTitle = textView("", 16, Color.WHITE, true);
        tvTime = textView("剩余 " + getDuration() + " 秒", 18, Color.WHITE, true);
        tvScore = textView("得分 0", 18, Color.parseColor("#FFEB3B"), true);
        tvLives = textView("❤ 3", 18, Color.parseColor("#FF8A80"), true);
        tvLives.setVisibility(View.GONE);
        btnEndGame = button("结束", Color.parseColor("#EF5350"), Color.WHITE);
        btnEndGame.setOnClickListener(v -> onGameEnded());
        tvGameTitle.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        info.addView(tvGameTitle);
        info.addView(tvTime);
        info.addView(tvScore);
        info.addView(tvLives);
        info.addView(btnEndGame);
        gameHost.addView(info);

        gameArea = new FrameLayout(this);
        gameHost.addView(gameArea, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        controlPad = new FrameLayout(this);
        controlPad.setPadding(dp(8), dp(8), dp(8), dp(12));
        gameHost.addView(controlPad, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void buildSelect() {
        selectOverlay = new FrameLayout(this);
        selectOverlay.setBackgroundColor(Color.parseColor("#E6FFF3E0"));
        root.addView(selectOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(dp(24), dp(24), dp(24), dp(24));
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.gravity = Gravity.CENTER;
        selectOverlay.addView(box, bp);

        box.addView(textView("🎉 本轮试题完成！选个小游戏放松一下吧", 22, Color.parseColor("#E64A19"), true));
        tvSelectSub = textView("", 15, Color.parseColor("#8D6E63"), false);
        box.addView(tvSelectSub);
        box.addView(gameSelectButton("🐍 贪吃蛇", "#43A047", GAME_SNAKE));
        box.addView(gameSelectButton("🧱 俄罗斯方块", "#1E88E5", GAME_TETRIS));
        box.addView(gameSelectButton("🍉 切水果", "#FB8C00", GAME_FRUIT));
        box.addView(gameSelectButton("🎖 坦克大战", "#6D4C41", GAME_TANK));

        Button skip = button("继续答题 ➜", Color.parseColor("#9E9E9E"), Color.WHITE);
        skip.setOnClickListener(v -> onGameEnded());
        box.addView(skip);
    }

    private Button gameSelectButton(String text, String color, final int game) {
        Button b = button(text, Color.parseColor(color), Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        b.setOnClickListener(v -> enterGame(game));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(58));
        lp.setMargins(dp(0), dp(8), dp(0), dp(0));
        b.setLayoutParams(lp);
        return b;
    }

    private void buildSettings() {
        settingsOverlay = new FrameLayout(this);
        settingsOverlay.setBackgroundColor(Color.parseColor("#EEFFFFFF"));
        root.addView(settingsOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(dp(24), dp(24), dp(24), dp(24));
        box.setBackgroundColor(Color.parseColor("#FFF7E6"));
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.gravity = Gravity.CENTER;
        settingsOverlay.addView(box, bp);

        box.addView(textView("⚙ 游戏时长设置", 22, Color.parseColor("#5D4037"), true));
        box.addView(textView("完成一轮试题（含错题复习）后可游玩：", 15, Color.parseColor("#8D6E63"), false));
        int[] options = {30, 60, 90, 120};
        durationButtons.clear();
        for (final int sec : options) {
            Button b = button(sec + " 秒", Color.WHITE, Color.parseColor("#37474F"));
            final int val = sec;
            b.setOnClickListener(v -> getSharedPreferences(PREFS, MODE_PRIVATE)
                    .edit().putInt(KEY_DURATION, val).apply());
            b.setTag(sec);
            durationButtons.add(b);
            box.addView(b);
        }
        refreshDurationButtons();
        Button done = button("完成", Color.parseColor("#FF7043"), Color.WHITE);
        done.setOnClickListener(v -> hideSettings());
        box.addView(done);
    }

    private void refreshDurationButtons() {
        int cur = getDuration();
        for (Button b : durationButtons) {
            int v = (Integer) b.getTag();
            b.setBackgroundColor(v == cur ? Color.parseColor("#FFCC80") : Color.WHITE);
            b.setTextColor(v == cur ? Color.parseColor("#E65100") : Color.parseColor("#37474F"));
        }
    }

    private int getDuration() {
        return getSharedPreferences(PREFS, MODE_PRIVATE).getInt(KEY_DURATION, DEFAULT_DURATION);
    }

    private void showSettings() {
        quizScroll.setVisibility(View.GONE);
        gameHost.setVisibility(View.GONE);
        selectOverlay.setVisibility(View.GONE);
        refreshDurationButtons();
        settingsOverlay.setVisibility(View.VISIBLE);
    }

    private void hideSettings() {
        settingsOverlay.setVisibility(View.GONE);
        quizScroll.setVisibility(View.VISIBLE);
    }

    // ===================== 考试流程 =====================

    private ExamItem currentItem() { return examQueue.get(examIndex); }

    private void startNewRound() {
        examQueue.clear();
        examIndex = 0;
        Set<String> used = new HashSet<>();
        List<Word> pool = new ArrayList<>(dictionary);
        Collections.shuffle(pool, rng);
        for (Word w : pool) {
            if (examQueue.size() >= ROUND_SIZE) break;
            examQueue.add(buildExamItem(w, false));
            used.add(w.hanzi);
        }
        appendReviewQuestions(used);
        showExamQuestion();
    }

    private void appendReviewQuestions(Set<String> usedInMain) {
        Set<String> queued = new HashSet<>();
        for (ExamItem it : examQueue) queued.add(it.word.hanzi);

        List<Word> candidates = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        for (Word w : dictionary) {
            if (queued.contains(w.hanzi)) continue;
            WordStatsManager.Stats st = stats.get(w.hanzi);
            double wt = stats.reviewWeight(w.hanzi);
            if (wt <= 0 && !st.pendingRetry && st.wrong == 0) continue;
            candidates.add(w);
            weights.add(Math.max(0.5, wt) + (st.pendingRetry ? 2 : 0));
        }
        if (candidates.isEmpty()) return;
        int n = Math.min(8, Math.max(1, candidates.size() / 3 + 1));
        for (Word w : weightedPick(candidates, weights, n)) {
            examQueue.add(buildExamItem(w, true));
            queued.add(w.hanzi);
        }
    }

    private List<Word> weightedPick(List<Word> pool, List<Double> weights, int k) {
        List<Word> out = new ArrayList<>();
        List<Word> p = new ArrayList<>(pool);
        List<Double> w = new ArrayList<>(weights);
        for (int n = 0; n < k && !p.isEmpty(); n++) {
            double total = 0;
            for (double x : w) total += x;
            double r = rng.nextDouble() * total, acc = 0;
            int idx = 0;
            for (int i = 0; i < p.size(); i++) {
                acc += w.get(i);
                if (r <= acc) { idx = i; break; }
            }
            out.add(p.remove(idx));
            w.remove(idx);
        }
        return out;
    }

    private ExamItem buildExamItem(Word w, boolean review) {
        ExamItem item = new ExamItem(w, review);
        String correct = tonedPinyin(w.py, w.tone);
        item.opts.add(correct);
        List<String> distract = new ArrayList<>(tonedSet);
        distract.remove(correct);
        Collections.shuffle(distract, rng);
        int added = 0;
        for (String d : distract) {
            if (added >= 3) break;
            if (!item.opts.contains(d)) { item.opts.add(d); added++; }
        }
        Collections.shuffle(item.opts, rng);
        if (stats.get(w.hanzi).wrong > 0) Collections.shuffle(item.opts, rng);
        item.correctIdx = item.opts.indexOf(correct);
        return item;
    }

    private void showExamQuestion() {
        attemptLocked = false;
        firstAttemptWrong = false;
        questionPassed = false;
        ExamItem item = currentItem();
        Word w = item.word;
        stats.recordAppear(w.hanzi);
        saveStats();

        WordStatsManager.Stats st = stats.get(w.hanzi);
        tvStatsBadge.setText("现" + st.appear + " 错" + st.wrong);
        tvHanzi.setText(w.hanzi);
        tvFeedback.setText("");
        btnNext.setEnabled(false);

        int mainIdx = 0;
        for (int i = 0; i <= examIndex; i++) {
            if (!examQueue.get(i).review) mainIdx++;
        }
        if (!item.review) {
            tvProgress.setText("试题 " + mainIdx + " / " + ROUND_SIZE);
            progressBar.setProgress(mainIdx);
            progressBar.setVisibility(View.VISIBLE);
        } else {
            int rev = 0;
            for (int i = 0; i <= examIndex; i++) if (examQueue.get(i).review) rev++;
            tvProgress.setText("错题复习 " + rev + "（不计入10题）");
            progressBar.setVisibility(View.INVISIBLE);
        }

        examOpts.removeAllViews();
        for (int i = 0; i < item.opts.size(); i++) {
            final int fi = i;
            Button b = button(item.opts.get(i), Color.WHITE, Color.parseColor("#37474F"));
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
            b.setOnClickListener(v -> onExamAnswer(item, fi, b));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
            lp.setMargins(dp(0), dp(5), dp(0), dp(5));
            b.setLayoutParams(lp);
            examOpts.addView(b);
        }
    }

    private void onExamAnswer(ExamItem item, int choice, Button b) {
        if (questionPassed) return;
        Word w = item.word;
        boolean correct = choice == item.correctIdx;

        if (!firstAttemptWrong && correct) {
            questionPassed = true;
            attemptLocked = true;
            if (stats.get(w.hanzi).pendingRetry) stats.recordFirstCorrectOnRetry(w.hanzi);
            saveStats();
            disableExamOpts();
            tvFeedback.setTextColor(Color.parseColor("#2E7D32"));
            tvFeedback.setText(PRAISE[rng.nextInt(PRAISE.length)]);
            b.setBackgroundColor(Color.parseColor("#C8E6C9"));
            btnNext.setEnabled(true);
            return;
        }

        if (!firstAttemptWrong && !correct) {
            firstAttemptWrong = true;
            stats.recordFirstWrong(w.hanzi);
            saveStats();
            tvStatsBadge.setText("现" + stats.get(w.hanzi).appear + " 错" + stats.get(w.hanzi).wrong);
            tvFeedback.setTextColor(Color.parseColor("#C62828"));
            tvFeedback.setText("答错了！正确：" + tonedPinyin(w.py, w.tone)
                    + "。首答错误，本题未通过（可再选，但不会算通过）");
            b.setBackgroundColor(Color.parseColor("#FFCDD2"));
            btnNext.setEnabled(true);
            return;
        }

        if (firstAttemptWrong && correct) {
            attemptLocked = true;
            disableExamOpts();
            tvFeedback.setTextColor(Color.parseColor("#F57C00"));
            tvFeedback.setText("答案对了，但首答已错，本题未通过，稍后会再出现");
            b.setBackgroundColor(Color.parseColor("#FFE0B2"));
            if (examOpts.getChildAt(item.correctIdx) instanceof Button)
                ((Button) examOpts.getChildAt(item.correctIdx)).setBackgroundColor(Color.parseColor("#C8E6C9"));
            btnNext.setEnabled(true);
            return;
        }

        tvFeedback.setTextColor(Color.parseColor("#C62828"));
        tvFeedback.setText("还是不对哦，正确：" + tonedPinyin(w.py, w.tone));
        b.setBackgroundColor(Color.parseColor("#FFCDD2"));
    }

    private void disableExamOpts() {
        for (int i = 0; i < examOpts.getChildCount(); i++) examOpts.getChildAt(i).setEnabled(false);
    }

    private void goNext() {
        if (!attemptLocked && !firstAttemptWrong) return;
        btnNext.setEnabled(false);
        final Word spoken = currentItem().word;
        final boolean roundDone = examIndex + 1 >= examQueue.size();
        examIndex++;
        handler.postDelayed(() -> {
            speakHanzi(spoken.hanzi, TextToSpeech.QUEUE_FLUSH);
            handler.postDelayed(() -> {
                if (roundDone) {
                    quizScroll.setVisibility(View.GONE);
                    showSelect();
                } else {
                    showExamQuestion();
                }
            }, 1200);
        }, 2500);
    }

    private void showSelect() {
        tvSelectSub.setText("（游戏进度自动保存 · 本次 " + getDuration() + " 秒）");
        selectOverlay.setVisibility(View.VISIBLE);
    }

    private void enterGame(int game) {
        activeGame = game;
        inGame = true;
        timeLeft = getDuration();
        selectOverlay.setVisibility(View.GONE);
        gameHost.setVisibility(View.VISIBLE);
        quizScroll.setVisibility(View.GONE);
        gameArea.removeAllViews();
        controlPad.removeAllViews();
        tvLives.setVisibility(View.GONE);

        if (game == GAME_SNAKE) {
            tvGameTitle.setText("🐍 贪吃蛇");
            SnakeView sv = new SnakeView(this);
            sv.restoreState(loadJson(KEY_SNAKE));
            sv.setScoreListener(s -> tvScore.setText("得分 " + s));
            gameArea.addView(sv, matchParent());
            activeGameView = sv;
            buildSnakeControls(sv);
        } else if (game == GAME_TETRIS) {
            tvGameTitle.setText("🧱 俄罗斯方块");
            TetrisView tv = new TetrisView(this);
            tv.restoreState(loadJson(KEY_TETRIS));
            tv.setScoreListener(s -> tvScore.setText("得分 " + s));
            gameArea.addView(tv, matchParent());
            activeGameView = tv;
            buildTetrisControls(tv);
        } else if (game == GAME_TANK) {
            tvGameTitle.setText("🎖 坦克大战");
            TankView tk = new TankView(this);
            tk.restoreState(loadJson(KEY_TANK));
            tk.setScoreListener(s -> tvScore.setText("得分 " + s));
            tk.setLivesListener(l -> { tvLives.setText("❤ " + l); tvLives.setVisibility(View.VISIBLE); });
            tvLives.setVisibility(View.VISIBLE);
            tvLives.setText("❤ " + tk.getLives());
            gameArea.addView(tk, matchParent());
            activeGameView = tk;
            buildTankControls(tk);
        } else {
            tvGameTitle.setText("🍉 切水果");
            FruitNinjaView fv = new FruitNinjaView(this);
            fv.restoreState(loadJson(KEY_FRUIT));
            fv.setScoreListener(s -> tvScore.setText("得分 " + s));
            fv.setLivesListener(l -> tvLives.setText("❤ " + l));
            tvLives.setVisibility(View.VISIBLE);
            gameArea.addView(fv, matchParent());
            activeGameView = fv;
        }
        tvTime.setText("剩余 " + timeLeft + " 秒");
        tvScore.setText("得分 " + getActiveScore());
        startActiveGame();
        startTimer();
    }

    private FrameLayout.LayoutParams matchParent() {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private void buildSnakeControls(final SnakeView sv) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.addView(dirBtn("←", () -> sv.setDirection(SnakeView.DIR_LEFT)));
        row.addView(dirBtn("↑", () -> sv.setDirection(SnakeView.DIR_UP)));
        row.addView(dirBtn("↓", () -> sv.setDirection(SnakeView.DIR_DOWN)));
        row.addView(dirBtn("→", () -> sv.setDirection(SnakeView.DIR_RIGHT)));
        controlPad.addView(row);
    }

    private void buildTetrisControls(final TetrisView tv) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.addView(dirBtn("←", tv::moveLeft));
        row.addView(dirBtn("↓", tv::softDrop));
        row.addView(dirBtn("→", tv::moveRight));
        row.addView(dirBtn("⟳", tv::rotate));
        controlPad.addView(row);
    }

    private void buildTankControls(final TankView tk) {
        LinearLayout pad = new LinearLayout(this);
        pad.setOrientation(LinearLayout.VERTICAL);
        pad.setGravity(Gravity.CENTER);
        LinearLayout rowMid = new LinearLayout(this);
        rowMid.setOrientation(LinearLayout.HORIZONTAL);
        rowMid.setGravity(Gravity.CENTER);
        rowMid.addView(bindTankBtn(tk, "←", TankView.DIR_LEFT));
        rowMid.addView(bindTankBtn(tk, "↑", TankView.DIR_UP));
        Button fire = tankDirBtn("🔥");
        fire.setOnClickListener(v -> tk.fire());
        rowMid.addView(fire);
        rowMid.addView(bindTankBtn(tk, "↓", TankView.DIR_DOWN));
        rowMid.addView(bindTankBtn(tk, "→", TankView.DIR_RIGHT));
        pad.addView(rowMid);
        controlPad.addView(pad);
    }

    private Button bindTankBtn(final TankView tk, String label, final int dir) {
        Button b = tankDirBtn(label);
        b.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) tk.setMoveDirection(dir);
            if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) tk.clearMove();
            return true;
        });
        return b;
    }

    private Button tankDirBtn(String t) {
        Button b = button(t, Color.parseColor("#37474F"), Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(64), dp(56));
        lp.setMargins(dp(4), dp(2), dp(4), dp(2));
        b.setLayoutParams(lp);
        return b;
    }

    private Button dirBtn(String t, final Runnable a) {
        Button b = button(t, Color.parseColor("#37474F"), Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        if (a != null) b.setOnClickListener(v -> a.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(72), dp(52));
        lp.setMargins(dp(6), dp(0), dp(6), dp(0));
        b.setLayoutParams(lp);
        return b;
    }

    private final Runnable timerTick = new Runnable() {
        @Override public void run() {
            if (!inGame) return;
            timeLeft--;
            if (timeLeft <= 0) { onGameEnded(); return; }
            tvTime.setText("剩余 " + timeLeft + " 秒");
            handler.postDelayed(this, 1000);
        }
    };

    private void startTimer() {
        handler.removeCallbacks(timerTick);
        handler.postDelayed(timerTick, 1000);
    }

    private void onGameEnded() {
        inGame = false;
        handler.removeCallbacks(timerTick);
        saveActiveGame();
        if (activeGameView != null) stopActiveGame();
        selectOverlay.setVisibility(View.GONE);
        gameHost.setVisibility(View.GONE);
        quizScroll.setVisibility(View.VISIBLE);
        startNewRound();
    }

    private void saveActiveGame() {
        if (activeGameView == null) return;
        SharedPreferences.Editor ed = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        if (activeGame == GAME_SNAKE) ed.putString(KEY_SNAKE, ((SnakeView) activeGameView).saveState().toString());
        else if (activeGame == GAME_TETRIS) ed.putString(KEY_TETRIS, ((TetrisView) activeGameView).saveState().toString());
        else if (activeGame == GAME_FRUIT) ed.putString(KEY_FRUIT, ((FruitNinjaView) activeGameView).saveState().toString());
        else if (activeGame == GAME_TANK) ed.putString(KEY_TANK, ((TankView) activeGameView).saveState().toString());
        ed.apply();
    }

    private JSONObject loadJson(String key) {
        try {
            String s = getSharedPreferences(PREFS, MODE_PRIVATE).getString(key, null);
            return s == null ? null : new JSONObject(s);
        } catch (Exception e) { return null; }
    }

    private int getActiveScore() {
        if (activeGameView instanceof SnakeView) return ((SnakeView) activeGameView).getScore();
        if (activeGameView instanceof TetrisView) return ((TetrisView) activeGameView).getScore();
        if (activeGameView instanceof FruitNinjaView) return ((FruitNinjaView) activeGameView).getScore();
        if (activeGameView instanceof TankView) return ((TankView) activeGameView).getScore();
        return 0;
    }

    private void startActiveGame() {
        if (activeGameView instanceof SnakeView) ((SnakeView) activeGameView).startGame();
        else if (activeGameView instanceof TetrisView) ((TetrisView) activeGameView).startGame();
        else if (activeGameView instanceof FruitNinjaView) ((FruitNinjaView) activeGameView).startGame();
        else if (activeGameView instanceof TankView) ((TankView) activeGameView).startGame();
    }

    private void stopActiveGame() {
        if (activeGameView instanceof SnakeView) ((SnakeView) activeGameView).stopGame();
        else if (activeGameView instanceof TetrisView) ((TetrisView) activeGameView).stopGame();
        else if (activeGameView instanceof FruitNinjaView) ((FruitNinjaView) activeGameView).stopGame();
        else if (activeGameView instanceof TankView) ((TankView) activeGameView).stopGame();
    }

    // ===================== 语音 =====================
    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int res = tts.setLanguage(Locale.CHINESE);
            ttsReady = (res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED);
        }
    }

    private void speakHanzi(String hanzi, int mode) {
        if (!ttsReady || tts == null || hanzi == null || hanzi.isEmpty()) return;
        tts.speak(hanzi, mode, null, null);
    }

    private void speakBlend(Word w) {
        if (!ttsReady || tts == null || w == null) return;
        if (pinyinBridge != null && pinyinBridge.isWholeSyllable(w.py)) {
            speakHanzi(w.hanzi, TextToSpeech.QUEUE_FLUSH);
            return;
        }
        List<String> parts = new ArrayList<>();
        if (pinyinBridge != null && !w.sm.isEmpty()) {
            String sm = pinyinBridge.initialHanzi(w.sm);
            if (sm != null && !sm.isEmpty()) parts.add(sm);
        }
        if (pinyinBridge != null) {
            for (int i = 0; i < pinyinBridge.finalParts(w.ym).size(); i++) {
                String part = pinyinBridge.finalParts(w.ym).get(i);
                int tone = (i == pinyinBridge.finalParts(w.ym).size() - 1) ? w.tone : 1;
                String h = pinyinBridge.finalHanzi(w.sm, part, tone);
                if (!h.isEmpty()) parts.add(h);
            }
        }
        parts.add(w.hanzi);
        boolean first = true;
        String prev = null;
        for (String p : parts) {
            if (p.equals(prev)) continue;
            speakHanzi(p, first ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD);
            first = false;
            prev = p;
        }
    }

    private String tonedPinyin(String py, int tone) {
        if (tone < 1 || tone > 4) return py;
        String[][] marks = {
                {"a", "ā", "á", "ǎ", "à"}, {"o", "ō", "ó", "ǒ", "ò"},
                {"e", "ē", "é", "ě", "è"}, {"i", "ī", "í", "ǐ", "ì"},
                {"u", "ū", "ú", "ǔ", "ù"}, {"ü", "ǖ", "ǘ", "ǚ", "ǜ"}
        };
        int idx = -1;
        if (py.contains("a")) idx = py.indexOf("a");
        else if (py.contains("o")) idx = py.indexOf("o");
        else if (py.contains("e")) idx = py.indexOf("e");
        else {
            int iu = py.indexOf("iu");
            if (iu >= 0) idx = iu + 1;
            else {
                int ui = py.indexOf("ui");
                if (ui >= 0) idx = ui + 1;
                else {
                    int last = -1;
                    for (int k = 0; k < py.length(); k++) {
                        char c = py.charAt(k);
                        if (c == 'i' || c == 'u' || c == 'ü') last = k;
                    }
                    idx = last;
                }
            }
        }
        if (idx < 0) return py;
        char c = py.charAt(idx);
        String rep = String.valueOf(c);
        for (String[] m : marks) if (m[0].equals(rep)) { rep = m[tone]; break; }
        return py.substring(0, idx) + rep + py.substring(idx + 1);
    }

    private TextView textView(String text, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        return t;
    }

    private Button button(String text, int bg, int fg) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(fg);
        b.setBackgroundColor(bg);
        b.setAllCaps(false);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(8), dp(4), dp(8), dp(4));
        return b;
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (inGame) { saveActiveGame(); stopActiveGame(); handler.removeCallbacks(timerTick); }
        saveStats();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (inGame) { startActiveGame(); startTimer(); }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (tts != null) tts.shutdown();
    }
}
