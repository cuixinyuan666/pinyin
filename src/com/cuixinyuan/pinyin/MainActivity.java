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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * 拼音识字乐园 —— 面向小学生的识字练习工具（仅适配安卓手机）
 *
 * 玩法：
 *   1. 每轮展示 10 个汉字，学生为每个汉字分别选择「声母」「韵母」「声调」。
 *   2. 拼音语音交互：
 *      - 点击声母/韵母按钮，按《汉语拼音方案》标准读法朗读（如 t→特、ao→奥），
 *        严禁读成英文字母。
 *      - 整题作答完成后整段拼读：声母音 + 韵母音 + 汉字。
 *      - 声调跟随对应汉字的韵母发声（点击声调按钮朗读带调整音节）。
 *   3. 每学完 10 个汉字 → 触发一轮检测复习（艾宾浩斯混合编排）→ 复习完成后弹出游戏选择。
 *      奖励游戏：贪吃蛇 / 俄罗斯方块 / 切水果，均持久化保存进度。
 *   4. 设置页可自定义游戏时长。
 */
public class MainActivity extends Activity implements TextToSpeech.OnInitListener {

    // ====== 基础配置 ======
    private static final String WORD_ASSET = "words.json";
    private static final int ROUND_SIZE = 10;
    private static final int REVIEW_SIZE = 10;
    private static final String PREFS = "pinyin_prefs";
    private static final String KEY_SNAKE = "game_snake";
    private static final String KEY_TETRIS = "game_tetris";
    private static final String KEY_FRUIT = "game_fruit";
    private static final String KEY_LEARNED = "learned_map";
    private static final String KEY_DURATION = "game_duration";
    private static final int DEFAULT_DURATION = 60;

    private static final String[] PRAISE = {
            "太棒啦！", "你真厉害！", "答对啦，真聪明！", "好样的！", "厉害厉害！", "你学会啦！"
    };

    private static final String[] TONE_MARK = {"ā", "á", "ǎ", "à"};
    private static final String[] TONE_NAME = {"一声", "二声", "三声", "四声"};

    // 声母标准读法（小学语文《汉语拼音方案》）：点击声母读作对应拼音音节，绝不读英文字母
    private static final Map<String, String> INITIAL_READ = new HashMap<>();
    // 韵母标准读法（单独成音节时的读法）
    private static final Map<String, String> FINAL_READ = new HashMap<>();
    static {
        INITIAL_READ.put("b", "bo"); INITIAL_READ.put("p", "po"); INITIAL_READ.put("m", "mo");
        INITIAL_READ.put("f", "fo"); INITIAL_READ.put("d", "de"); INITIAL_READ.put("t", "te");
        INITIAL_READ.put("n", "ne"); INITIAL_READ.put("l", "le"); INITIAL_READ.put("g", "ge");
        INITIAL_READ.put("k", "ke"); INITIAL_READ.put("h", "he"); INITIAL_READ.put("j", "ji");
        INITIAL_READ.put("q", "qi"); INITIAL_READ.put("x", "xi"); INITIAL_READ.put("zh", "zhi");
        INITIAL_READ.put("ch", "chi"); INITIAL_READ.put("sh", "shi"); INITIAL_READ.put("r", "ri");
        INITIAL_READ.put("z", "zi"); INITIAL_READ.put("c", "ci"); INITIAL_READ.put("s", "si");
        INITIAL_READ.put("y", "yi"); INITIAL_READ.put("w", "wu");

        FINAL_READ.put("a", "a"); FINAL_READ.put("o", "o"); FINAL_READ.put("e", "e");
        FINAL_READ.put("i", "yi"); FINAL_READ.put("u", "wu"); FINAL_READ.put("er", "er");
        FINAL_READ.put("ai", "ai"); FINAL_READ.put("ei", "ei"); FINAL_READ.put("ui", "wei");
        FINAL_READ.put("ao", "ao"); FINAL_READ.put("ou", "ou"); FINAL_READ.put("iu", "you");
        FINAL_READ.put("ie", "ye"); FINAL_READ.put("ue", "yue"); FINAL_READ.put("an", "an");
        FINAL_READ.put("en", "en"); FINAL_READ.put("in", "yin"); FINAL_READ.put("un", "wen");
        FINAL_READ.put("ang", "ang"); FINAL_READ.put("eng", "eng"); FINAL_READ.put("ing", "ying");
        FINAL_READ.put("ong", "weng"); FINAL_READ.put("ia", "ya"); FINAL_READ.put("ua", "wa");
        FINAL_READ.put("uo", "wo"); FINAL_READ.put("uai", "wai"); FINAL_READ.put("iao", "yao");
        FINAL_READ.put("ian", "yan"); FINAL_READ.put("uan", "wan"); FINAL_READ.put("uang", "wang");
        FINAL_READ.put("iong", "yong"); FINAL_READ.put("un2", "yun"); FINAL_READ.put("uan2", "yuan");
    }

    // ====== 数据 ======
    private static class Word {
        final String hanzi;
        final String py;
        final String sm;
        final String ym;
        final int tone;
        Word(String hanzi, String py, String sm, String ym, int tone) {
            this.hanzi = hanzi; this.py = py; this.sm = sm; this.ym = ym; this.tone = tone;
        }
    }

    private final List<Word> dictionary = new ArrayList<>();
    private final Map<String, Word> wordByHanzi = new HashMap<>();
    private final Set<String> tonedSet = new HashSet<>();
    private final List<String> smPool = new ArrayList<>();
    private final List<String> ymPool = new ArrayList<>();
    private final Random rng = new Random();

    // 已学习记录：hanzi -> {count, lastSeen}
    private final Map<String, long[]> learned = new LinkedHashMap<>();

    // ====== 运行时状态 ======
    private List<Word> roundWords = new ArrayList<>();
    private List<Word> lastRoundWords = new ArrayList<>();
    private int currentIndex = 0;
    private String selectedSm = null;
    private String selectedYm = null;
    private Integer selectedTone = null;
    private boolean itemSolved = false;

    // 复习
    private List<ReviewQ> reviewQs = new ArrayList<>();
    private int reviewIndex = 0;
    private static class ReviewQ { Word w; List<String> opts; int correct; int learnedCount; }

    // ====== 语音 ======
    private TextToSpeech tts;
    private boolean ttsReady = false;

    // ====== 游戏 ======
    private static final int GAME_SNAKE = 0, GAME_TETRIS = 1, GAME_FRUIT = 2;
    private int activeGame = -1;
    private boolean inGame = false;
    private int timeLeft = DEFAULT_DURATION;
    private View activeGameView = null;

    // ====== UI ======
    private FrameLayout root;
    private ScrollView quizScroll, reviewScroll;
    private LinearLayout quizPanel, reviewPanel;
    private LinearLayout gameHost;
    private FrameLayout gameArea, controlPad, selectOverlay, settingsOverlay;

    private TextView tvProgress, tvHanzi, tvFeedback, tvReviewProgress, tvReviewHint, tvReviewChar, tvReviewFeedback;
    private TextView tvSelectSub;
    private ProgressBar progressBar;
    private Button btnSpeak, btnNext, btnReviewNext, btnSettings;
    private LinearLayout smRow, ymRow, toneRow, reviewOpts;
    private List<Button> durationButtons = new ArrayList<>();

    private TextView tvTime, tvScore, tvLives, tvGameTitle;
    private Button btnEndGame;

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadDictionary();
        loadLearned();
        tts = new TextToSpeech(this, this);
        buildUi();
        startNewRound();
    }

    // ===================== 数据加载 =====================
    private void loadDictionary() {
        try {
            InputStream is = getAssets().open(WORD_ASSET);
            BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
            JSONArray arr = new JSONArray(sb.toString());
            Set<String> smSet = new HashSet<>(), ymSet = new HashSet<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Word w = new Word(o.getString("word"), o.getString("py"),
                        o.getString("sm"), o.getString("ym"), o.getInt("tone"));
                dictionary.add(w);
                wordByHanzi.put(w.hanzi, w);
                smSet.add(w.sm); ymSet.add(w.ym);
                tonedSet.add(tonedPinyin(w.py, w.tone));
            }
            smPool.addAll(smSet); ymPool.addAll(ymSet);
        } catch (Exception e) {
            dictionary.add(new Word("你", "ni", "n", "i", 3));
            dictionary.add(new Word("好", "hao", "h", "ao", 3));
            dictionary.add(new Word("妈", "ma", "m", "a", 1));
            dictionary.add(new Word("爸", "ba", "b", "a", 4));
            smPool.add("n"); smPool.add("h"); smPool.add("m"); smPool.add("b");
            ymPool.add("i"); ymPool.add("ao"); ymPool.add("a");
            for (Word w : dictionary) { wordByHanzi.put(w.hanzi, w); tonedSet.add(tonedPinyin(w.py, w.tone)); }
        }
    }

    private void loadLearned() {
        try {
            String s = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_LEARNED, null);
            if (s != null) {
                JSONObject o = new JSONObject(s);
                JSONArray keys = o.names();
                if (keys != null) for (int i = 0; i < keys.length(); i++) {
                    String k = keys.getString(i);
                    JSONArray v = o.getJSONArray(k);
                    learned.put(k, new long[]{v.getLong(0), v.getLong(1)});
                }
            }
        } catch (Exception e) { /* 忽略 */ }
    }

    private void saveLearned() {
        try {
            JSONObject o = new JSONObject();
            for (Map.Entry<String, long[]> e : learned.entrySet()) {
                JSONArray v = new JSONArray();
                v.put(e.getValue()[0]); v.put(e.getValue()[1]);
                o.put(e.getKey(), v);
            }
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_LEARNED, o.toString()).apply();
        } catch (Exception e) { /* 忽略 */ }
    }

    private void markLearned(Word w) {
        long[] v = learned.get(w.hanzi);
        long now = System.currentTimeMillis();
        if (v == null) learned.put(w.hanzi, new long[]{1, now});
        else { v[0]++; v[1] = now; }
        saveLearned();
    }

    private int getDuration() {
        return getSharedPreferences(PREFS, MODE_PRIVATE).getInt(KEY_DURATION, DEFAULT_DURATION);
    }

    // ===================== UI 构建 =====================
    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#FFF7E6"));
        setContentView(root);

        buildQuiz();
        buildReview();
        buildGameHost();
        buildSelect();
        buildSettings();

        quizScroll.setVisibility(View.VISIBLE);
        reviewScroll.setVisibility(View.GONE);
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

        // 顶部：进度条 + 设置按钮
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        btnSettings = button("⚙ 设置", Color.parseColor("#90A4AE"), Color.WHITE);
        btnSettings.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        btnSettings.setOnClickListener(v -> showSettings());
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
        bp.gravity = Gravity.END;
        btnSettings.setLayoutParams(bp);
        bar.addView(btnSettings);
        quizPanel.addView(bar);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setGravity(Gravity.CENTER);
        tvProgress = textView("第 1 / " + ROUND_SIZE + " 题", 18, Color.parseColor("#5D4037"), true);
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(ROUND_SIZE);
        progressBar.setProgress(1);
        progressBar.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(12)));
        top.addView(tvProgress); top.addView(progressBar);
        quizPanel.addView(top);

        tvHanzi = textView("", 48, Color.parseColor("#E64A19"), true);
        tvHanzi.setPadding(dp(0), dp(4), dp(0), dp(2));
        quizPanel.addView(tvHanzi);

        btnSpeak = button("🔊 听一听", Color.parseColor("#FFB74D"), Color.WHITE);
        btnSpeak.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        btnSpeak.setOnClickListener(v -> speakBlend(currentWord()));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38));
        sp.setMargins(dp(0), dp(2), dp(0), dp(2));
        btnSpeak.setLayoutParams(sp);
        quizPanel.addView(btnSpeak);

        quizPanel.addView(textView("请选择它的「声母」", 16, Color.parseColor("#5D4037"), false));
        smRow = new LinearLayout(this);
        smRow.setOrientation(LinearLayout.HORIZONTAL); smRow.setGravity(Gravity.CENTER);
        smRow.setPadding(dp(0), dp(4), dp(0), dp(4));
        quizPanel.addView(smRow);

        quizPanel.addView(textView("请选择它的「韵母」", 16, Color.parseColor("#5D4037"), false));
        ymRow = new LinearLayout(this);
        ymRow.setOrientation(LinearLayout.HORIZONTAL); ymRow.setGravity(Gravity.CENTER);
        ymRow.setPadding(dp(0), dp(4), dp(0), dp(4));
        quizPanel.addView(ymRow);

        quizPanel.addView(textView("请选择它的「声调」", 16, Color.parseColor("#5D4037"), false));
        toneRow = new LinearLayout(this);
        toneRow.setOrientation(LinearLayout.HORIZONTAL); toneRow.setGravity(Gravity.CENTER);
        toneRow.setPadding(dp(0), dp(4), dp(0), dp(4));
        quizPanel.addView(toneRow);

        tvFeedback = textView("", 17, Color.parseColor("#2E7D32"), true);
        tvFeedback.setPadding(dp(0), dp(3), dp(0), dp(3));
        quizPanel.addView(tvFeedback);

        btnNext = button("下一题 ➜", Color.parseColor("#FF7043"), Color.WHITE);
        btnNext.setEnabled(false);
        btnNext.setOnClickListener(v -> goNext());
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        np.setMargins(dp(0), dp(4), dp(0), dp(0));
        btnNext.setLayoutParams(np);
        quizPanel.addView(btnNext);
    }

    private void buildReview() {
        reviewPanel = new LinearLayout(this);
        reviewPanel.setOrientation(LinearLayout.VERTICAL);
        reviewPanel.setGravity(Gravity.CENTER_HORIZONTAL);
        reviewPanel.setPadding(dp(16), dp(16), dp(16), dp(16));
        reviewScroll = new ScrollView(this);
        reviewScroll.addView(reviewPanel);
        root.addView(reviewScroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        tvReviewProgress = textView("复习 1 / " + REVIEW_SIZE, 18, Color.parseColor("#5D4037"), true);
        reviewPanel.addView(tvReviewProgress);

        tvReviewHint = textView("", 15, Color.parseColor("#8D6E63"), false);
        reviewPanel.addView(tvReviewHint);

        tvReviewChar = textView("", 90, Color.parseColor("#E64A19"), true);
        tvReviewChar.setPadding(dp(0), dp(10), dp(0), dp(6));
        reviewPanel.addView(tvReviewChar);

        reviewOpts = new LinearLayout(this);
        reviewOpts.setOrientation(LinearLayout.VERTICAL);
        reviewOpts.setGravity(Gravity.CENTER_HORIZONTAL);
        reviewOpts.setPadding(dp(0), dp(8), dp(0), dp(8));
        reviewPanel.addView(reviewOpts);

        tvReviewFeedback = textView("", 20, Color.parseColor("#2E7D32"), true);
        tvReviewFeedback.setPadding(dp(0), dp(8), dp(0), dp(8));
        reviewPanel.addView(tvReviewFeedback);

        btnReviewNext = button("下一题 ➜", Color.parseColor("#FF7043"), Color.WHITE);
        btnReviewNext.setEnabled(false);
        btnReviewNext.setOnClickListener(v -> nextReview());
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        rp.setMargins(dp(0), dp(8), dp(0), dp(0));
        btnReviewNext.setLayoutParams(rp);
        reviewPanel.addView(btnReviewNext);
    }

    private void buildGameHost() {
        gameHost = new LinearLayout(this);
        gameHost.setOrientation(LinearLayout.VERTICAL);
        root.addView(gameHost, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 顶部信息条
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
        btnEndGame.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        btnEndGame.setOnClickListener(v -> onGameEnded());

        tvGameTitle.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout.LayoutParams wt = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        tvTime.setLayoutParams(wt); tvScore.setLayoutParams(wt); tvLives.setLayoutParams(wt);
        LinearLayout.LayoutParams wb = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
        info.addView(tvGameTitle); info.addView(tvTime); info.addView(tvScore);
        info.addView(tvLives); info.addView(btnEndGame);
        gameHost.addView(info);

        gameArea = new FrameLayout(this);
        LinearLayout.LayoutParams ga = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        gameHost.addView(gameArea, ga);

        controlPad = new FrameLayout(this);
        controlPad.setPadding(dp(8), dp(8), dp(8), dp(12));
        gameHost.addView(controlPad, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void buildSelect() {
        selectOverlay = new FrameLayout(this);
        selectOverlay.setBackgroundColor(Color.parseColor("#E6FFF3E0"));
        selectOverlay.setClickable(false);
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

        box.addView(textView("🎉 复习完成！选一个小游戏玩一会儿吧", 22, Color.parseColor("#E64A19"), true));
        tvSelectSub = textView("", 15, Color.parseColor("#8D6E63"), false);
        box.addView(tvSelectSub);

        box.addView(gameSelectButton("🐍 贪吃蛇", "#43A047", GAME_SNAKE));
        box.addView(gameSelectButton("🧱 俄罗斯方块", "#1E88E5", GAME_TETRIS));
        box.addView(gameSelectButton("🍉 切水果", "#FB8C00", GAME_FRUIT));

        Button skip = button("先不玩，继续答题 ➜", Color.parseColor("#9E9E9E"), Color.WHITE);
        skip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        skip.setOnClickListener(v -> onGameEnded());
        LinearLayout.LayoutParams sk = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        sk.setMargins(dp(0), dp(8), dp(0), dp(0));
        skip.setLayoutParams(sk);
        box.addView(skip);
    }

    private Button gameSelectButton(String text, String color, final int game) {
        Button b = button(text, Color.parseColor(color), Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        b.setOnClickListener(v -> enterGame(game));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64));
        lp.setMargins(dp(0), dp(10), dp(0), dp(0));
        b.setLayoutParams(lp);
        return b;
    }

    private void buildSettings() {
        settingsOverlay = new FrameLayout(this);
        settingsOverlay.setBackgroundColor(Color.parseColor("#EEFFFFFF"));
        settingsOverlay.setClickable(false);
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
        box.addView(textView("完成 10 题后，每段游戏可自由游玩的时长：", 15, Color.parseColor("#8D6E63"), false));

        int[] options = {30, 60, 90, 120};
        int cur = getDuration();
        durationButtons.clear();
        for (final int sec : options) {
            Button b = button(sec + " 秒", Color.WHITE, Color.parseColor("#37474F"));
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            final int val = sec;
            b.setOnClickListener(v -> {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(KEY_DURATION, val).apply();
                refreshDurationButtons();
            });
            b.setTag(sec);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
            lp.setMargins(dp(0), dp(8), dp(0), dp(0));
            b.setLayoutParams(lp);
            durationButtons.add(b);
            box.addView(b);
        }
        refreshDurationButtons();

        Button done = button("完成", Color.parseColor("#FF7043"), Color.WHITE);
        done.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        done.setOnClickListener(v -> hideSettings());
        LinearLayout.LayoutParams dp2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        dp2.setMargins(dp(0), dp(12), dp(0), dp(0));
        done.setLayoutParams(dp2);
        box.addView(done);
    }

    private void refreshDurationButtons() {
        int cur = getDuration();
        for (Button b : durationButtons) {
            int v = (Integer) b.getTag();
            if (v == cur) {
                b.setBackgroundColor(Color.parseColor("#FFCC80"));
                b.setTextColor(Color.parseColor("#E65100"));
            } else {
                b.setBackgroundColor(Color.WHITE);
                b.setTextColor(Color.parseColor("#37474F"));
            }
        }
    }

    private void showSettings() {
        quizScroll.setVisibility(View.GONE);
        reviewScroll.setVisibility(View.GONE);
        gameHost.setVisibility(View.GONE);
        selectOverlay.setVisibility(View.GONE);
        refreshDurationButtons();
        settingsOverlay.setVisibility(View.VISIBLE);
    }

    private void hideSettings() {
        settingsOverlay.setVisibility(View.GONE);
        quizScroll.setVisibility(View.VISIBLE);
    }

    // ===================== 答题流程 =====================
    private Word currentWord() { return roundWords.get(currentIndex); }

    private void startNewRound() {
        lastRoundWords = new ArrayList<>(roundWords);
        roundWords = pickWords(ROUND_SIZE);
        currentIndex = 0;
        showQuestion();
    }

    private List<Word> pickWords(int n) {
        List<Word> pool = new ArrayList<>(dictionary);
        Collections.shuffle(pool, rng);
        List<Word> out = new ArrayList<>();
        for (int i = 0; i < Math.min(n, pool.size()); i++) out.add(pool.get(i));
        return out;
    }

    private void showQuestion() {
        itemSolved = false;
        selectedSm = null; selectedYm = null; selectedTone = null;
        Word w = currentWord();
        tvProgress.setText("第 " + (currentIndex + 1) + " / " + ROUND_SIZE + " 题");
        progressBar.setProgress(currentIndex + 1);
        tvHanzi.setText(w.hanzi);
        tvFeedback.setText("");
        btnNext.setEnabled(false);
        buildSmOptions(w); buildYmOptions(w); buildToneOptions();
    }

    private void buildSmOptions(Word w) {
        smRow.removeAllViews();
        List<String> opts = new ArrayList<>();
        opts.add(w.sm);
        List<String> pool = new ArrayList<>(smPool);
        pool.remove("");
        pool.remove(w.sm);
        Collections.shuffle(pool, rng);
        for (int i = 0; i < 3 && i < pool.size(); i++) opts.add(pool.get(i));
        Collections.shuffle(opts, rng);
        for (final String s : opts) {
            String label = s.isEmpty() ? "∅（无）" : s;
            Button b = button(label, Color.WHITE, Color.parseColor("#37474F"));
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
            b.setTag(s);
            b.setOnClickListener(v -> {
                if (s.isEmpty()) speakSyllable(w.py);   // 零声母：朗读整个音节
                else speakInitial(s);                    // 声母按标准拼音读法
                selectedSm = s; highlight(smRow, s); evaluate();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(64), dp(44));
            lp.setMargins(dp(6), dp(4), dp(6), dp(4));
            b.setLayoutParams(lp);
            smRow.addView(b);
        }
    }

    private void buildYmOptions(Word w) {
        ymRow.removeAllViews();
        List<String> opts = new ArrayList<>();
        opts.add(w.ym);
        List<String> pool = new ArrayList<>(ymPool);
        pool.remove(w.ym);
        Collections.shuffle(pool, rng);
        for (int i = 0; i < 3 && i < pool.size(); i++) opts.add(pool.get(i));
        Collections.shuffle(opts, rng);
        for (final String s : opts) {
            Button b = button(s, Color.WHITE, Color.parseColor("#37474F"));
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
            b.setTag(s);
            b.setOnClickListener(v -> {
                speakFinal(s);                           // 韵母按标准拼音读法
                selectedYm = s; highlight(ymRow, s); evaluate();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(72), dp(40));
            lp.setMargins(dp(6), dp(4), dp(6), dp(4));
            b.setLayoutParams(lp);
            ymRow.addView(b);
        }
    }

    private void buildToneOptions() {
        toneRow.removeAllViews();
        for (int i = 0; i < 4; i++) {
            final int t = i + 1;
            Button b = button(TONE_MARK[i] + "\n" + TONE_NAME[i], Color.WHITE, Color.parseColor("#37474F"));
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            b.setTag(t);
            b.setOnClickListener(v -> {
                // 声调跟随对应汉字的韵母发声：朗读带调整个音节（声调落在韵母上）
                speakSyllable(tonedPinyin(currentWord().py, t));
                selectedTone = t; highlight(toneRow, t); evaluate();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(64), dp(48));
            lp.setMargins(dp(4), dp(4), dp(4), dp(4));
            b.setLayoutParams(lp);
            toneRow.addView(b);
        }
    }

    private void evaluate() {
        if (selectedSm == null || selectedYm == null || selectedTone == null) return;
        Word w = currentWord();
        boolean ok = selectedSm.equals(w.sm) && selectedYm.equals(w.ym) && selectedTone == w.tone;
        if (ok) {
            itemSolved = true;
            tvFeedback.setTextColor(Color.parseColor("#2E7D32"));
            tvFeedback.setText(PRAISE[rng.nextInt(PRAISE.length)]);
            colorSelected(smRow, selectedSm, Color.parseColor("#C8E6C9"));
            colorSelected(ymRow, selectedYm, Color.parseColor("#C8E6C9"));
            colorSelected(toneRow, selectedTone, Color.parseColor("#C8E6C9"));
            btnNext.setEnabled(true);
        } else {
            tvFeedback.setTextColor(Color.parseColor("#C62828"));
            tvFeedback.setText("再想一想哦～ 正确答案：" + tonedPinyin(w.py, w.tone));
            colorSelected(smRow, selectedSm, Color.parseColor("#FFCDD2"));
            colorSelected(ymRow, selectedYm, Color.parseColor("#FFCDD2"));
            colorSelected(toneRow, selectedTone, Color.parseColor("#FFCDD2"));
        }
        // 整题作答完成后执行完整拼读播报
        speakBlend(w);
    }

    private void goNext() {
        if (!itemSolved) return;
        markLearned(currentWord());
        currentIndex++;
        if (currentIndex >= ROUND_SIZE) {
            lastRoundWords = new ArrayList<>(roundWords); // 记录本轮新学 10 字
            startReview();                               // 学完 10 字 → 先触发检测复习
        } else {
            showQuestion();
        }
    }

    // ===================== 游戏选择 =====================
    private void showSelect() {
        tvSelectSub.setText("（每款游戏进度都会自动保存 · 本次时长 " + getDuration() + " 秒）");
        quizScroll.setVisibility(View.GONE);
        reviewScroll.setVisibility(View.GONE);
        gameHost.setVisibility(View.GONE);
        selectOverlay.setVisibility(View.VISIBLE);
    }

    private void enterGame(int game) {
        activeGame = game;
        inGame = true;
        timeLeft = getDuration();
        selectOverlay.setVisibility(View.GONE);
        gameHost.setVisibility(View.VISIBLE);
        reviewScroll.setVisibility(View.GONE);
        quizScroll.setVisibility(View.GONE);

        gameArea.removeAllViews();
        controlPad.removeAllViews();
        tvLives.setVisibility(View.GONE);

        if (game == GAME_SNAKE) {
            tvGameTitle.setText("🐍 贪吃蛇");
            SnakeView sv = new SnakeView(this);
            sv.restoreState(loadJson(KEY_SNAKE));
            sv.setScoreListener(s -> { tvScore.setText("得分 " + s); });
            gameArea.addView(sv, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            activeGameView = sv;
            buildSnakeControls(sv);
        } else if (game == GAME_TETRIS) {
            tvGameTitle.setText("🧱 俄罗斯方块");
            TetrisView tv = new TetrisView(this);
            tv.restoreState(loadJson(KEY_TETRIS));
            tv.setScoreListener(s -> tvScore.setText("得分 " + s));
            gameArea.addView(tv, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            activeGameView = tv;
            buildTetrisControls(tv);
        } else {
            tvGameTitle.setText("🍉 切水果");
            FruitNinjaView fv = new FruitNinjaView(this);
            fv.restoreState(loadJson(KEY_FRUIT));
            fv.setScoreListener(s -> tvScore.setText("得分 " + s));
            fv.setLivesListener(l -> tvLives.setText("❤ " + l));
            tvLives.setVisibility(View.VISIBLE);
            tvLives.setText("❤ " + fv.getLives());
            gameArea.addView(fv, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            activeGameView = fv;
        }

        tvTime.setText("剩余 " + timeLeft + " 秒");
        tvScore.setText("得分 " + getActiveScore());
        startActiveGame();
        startTimer();
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

    private Button dirBtn(String t, final Runnable a) {
        Button b = button(t, Color.parseColor("#37474F"), Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        b.setOnClickListener(v -> a.run());
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(64), dp(56)));
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) b.getLayoutParams();
        lp.setMargins(dp(8), dp(0), dp(8), dp(0));
        b.setLayoutParams(lp);
        return b;
    }

    private void startTimer() {
        handler.removeCallbacks(timerTick);
        handler.postDelayed(timerTick, 1000);
    }

    private final Runnable timerTick = new Runnable() {
        @Override
        public void run() {
            if (!inGame) return;
            timeLeft--;
            if (timeLeft <= 0) { tvTime.setText("时间到"); onGameEnded(); return; }
            tvTime.setText("剩余 " + timeLeft + " 秒");
            handler.postDelayed(timerTick, 1000);
        }
    };

    private void onGameEnded() {
        inGame = false;
        handler.removeCallbacks(timerTick);
        saveActiveGame();
        if (activeGameView != null) stopActiveGame();
        gameHost.setVisibility(View.GONE);
        // 游戏结束 → 回到下一轮 10 题练习
        quizScroll.setVisibility(View.VISIBLE);
        startNewRound();
    }

    private void saveActiveGame() {
        if (activeGameView == null) return;
        SharedPreferences.Editor ed = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        if (activeGame == GAME_SNAKE) ed.putString(KEY_SNAKE, ((SnakeView) activeGameView).saveState().toString());
        else if (activeGame == GAME_TETRIS) ed.putString(KEY_TETRIS, ((TetrisView) activeGameView).saveState().toString());
        else if (activeGame == GAME_FRUIT) ed.putString(KEY_FRUIT, ((FruitNinjaView) activeGameView).saveState().toString());
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
        return 0;
    }

    private void startActiveGame() {
        if (activeGameView instanceof SnakeView) ((SnakeView) activeGameView).startGame();
        else if (activeGameView instanceof TetrisView) ((TetrisView) activeGameView).startGame();
        else if (activeGameView instanceof FruitNinjaView) ((FruitNinjaView) activeGameView).startGame();
    }

    private void stopActiveGame() {
        if (activeGameView instanceof SnakeView) ((SnakeView) activeGameView).stopGame();
        else if (activeGameView instanceof TetrisView) ((TetrisView) activeGameView).stopGame();
        else if (activeGameView instanceof FruitNinjaView) ((FruitNinjaView) activeGameView).stopGame();
    }

    // ===================== 复习模块（艾宾浩斯） =====================
    private void startReview() {
        reviewQs = buildReviewQuestions();
        reviewIndex = 0;
        reviewScroll.setVisibility(View.VISIBLE);
        quizScroll.setVisibility(View.GONE);
        showReviewQuestion();
    }

    private List<ReviewQ> buildReviewQuestions() {
        List<ReviewQ> qs = new ArrayList<>();
        // 新学 10 字
        List<Word> newPool = new ArrayList<>(lastRoundWords);
        Collections.shuffle(newPool, rng);
        // 旧字池（排除本轮新字），按艾宾浩斯权重采样
        List<Word> oldPool = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        long now = System.currentTimeMillis();
        Set<String> newSet = new HashSet<>();
        for (Word w : lastRoundWords) newSet.add(w.hanzi);
        for (Map.Entry<String, long[]> e : learned.entrySet()) {
            if (newSet.contains(e.getKey())) continue;
            Word w = wordByHanzi.get(e.getKey());
            if (w == null) continue;
            long[] v = e.getValue();
            double gap = (now - v[1]) / 60000.0 + 1;   // 距上次学习越久权重越高
            double weight = gap / Math.max(1, v[0]);   // 学习次数越少权重越高
            oldPool.add(w); weights.add(weight);
        }
        // 5 新 + 5 旧（旧不足则补新）
        List<Word> chosen = new ArrayList<>();
        chosen.addAll(takeRandom(newPool, 5));
        chosen.addAll(weightedSample(oldPool, weights, 5));
        while (chosen.size() < REVIEW_SIZE) {
            chosen.add(newPool.get(rng.nextInt(newPool.size())));
        }
        Collections.shuffle(chosen, rng);

        for (Word w : chosen) {
            ReviewQ q = new ReviewQ();
            q.w = w;
            q.correct = 0;
            long[] v = learned.get(w.hanzi);
            q.learnedCount = v == null ? 0 : (int) v[0];
            String correct = tonedPinyin(w.py, w.tone);
            q.opts = new ArrayList<>();
            q.opts.add(correct);
            List<String> distract = new ArrayList<>(tonedSet);
            distract.remove(correct);
            Collections.shuffle(distract, rng);
            int added = 0;
            for (String d : distract) { if (added >= 3) break; if (!q.opts.contains(d)) { q.opts.add(d); added++; } }
            Collections.shuffle(q.opts, rng);
            q.correct = q.opts.indexOf(correct);
            qs.add(q);
        }
        return qs;
    }

    private List<Word> takeRandom(List<Word> pool, int k) {
        List<Word> copy = new ArrayList<>(pool);
        Collections.shuffle(copy, rng);
        List<Word> out = new ArrayList<>();
        for (int i = 0; i < Math.min(k, copy.size()); i++) out.add(copy.get(i));
        return out;
    }

    private List<Word> weightedSample(List<Word> pool, List<Double> weights, int k) {
        List<Word> out = new ArrayList<>();
        List<Word> p = new ArrayList<>(pool);
        List<Double> w = new ArrayList<>(weights);
        for (int n = 0; n < k && !p.isEmpty(); n++) {
            double total = 0;
            for (double x : w) total += x;
            double r = rng.nextDouble() * total;
            double acc = 0; int idx = 0;
            for (int i = 0; i < p.size(); i++) {
                acc += w.get(i);
                if (r <= acc) { idx = i; break; }
            }
            out.add(p.remove(idx));
            w.remove(idx);
        }
        return out;
    }

    private void showReviewQuestion() {
        ReviewQ q = reviewQs.get(reviewIndex);
        tvReviewProgress.setText("复习 " + (reviewIndex + 1) + " / " + REVIEW_SIZE);
        tvReviewHint.setText("这个字你已学习 " + q.learnedCount + " 次，选出它的正确拼音：");
        tvReviewChar.setText(q.w.hanzi);
        tvReviewFeedback.setText("");
        btnReviewNext.setEnabled(false);
        reviewOpts.removeAllViews();
        for (int i = 0; i < q.opts.size(); i++) {
            final int fi = i;
            Button b = button(q.opts.get(i), Color.WHITE, Color.parseColor("#37474F"));
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
            b.setOnClickListener(v -> answerReview(q, fi, b));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
            lp.setMargins(dp(0), dp(6), dp(0), dp(6));
            b.setLayoutParams(lp);
            reviewOpts.addView(b);
        }
    }

    private void answerReview(ReviewQ q, int choice, Button b) {
        for (int i = 0; i < reviewOpts.getChildCount(); i++) reviewOpts.getChildAt(i).setEnabled(false);
        if (choice == q.correct) {
            tvReviewFeedback.setTextColor(Color.parseColor("#2E7D32"));
            tvReviewFeedback.setText("答对啦！" + q.w.hanzi + " 读 " + tonedPinyin(q.w.py, q.w.tone));
            b.setBackgroundColor(Color.parseColor("#C8E6C9"));
            speakBlend(q.w);
        } else {
            tvReviewFeedback.setTextColor(Color.parseColor("#C62828"));
            tvReviewFeedback.setText("正确答案：" + tonedPinyin(q.w.py, q.w.tone));
            b.setBackgroundColor(Color.parseColor("#FFCDD2"));
            if (reviewOpts.getChildAt(q.correct) instanceof Button)
                ((Button) reviewOpts.getChildAt(q.correct)).setBackgroundColor(Color.parseColor("#C8E6C9"));
        }
        btnReviewNext.setEnabled(true);
    }

    private void nextReview() {
        reviewIndex++;
        if (reviewIndex >= reviewQs.size()) {
            // 检测完成后 → 进入游戏选择界面
            reviewScroll.setVisibility(View.GONE);
            showSelect();
        } else {
            showReviewQuestion();
        }
    }

    // ===================== 语音朗读 =====================
    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int res = tts.setLanguage(Locale.CHINESE);
            ttsReady = (res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED);
        }
    }

    /** 朗读声母：按《汉语拼音方案》标准读法（如 t→特），绝不读英文字母 */
    private void speakInitial(String s) {
        if (!ttsReady || tts == null || s == null || s.isEmpty()) return;
        String read = INITIAL_READ.get(s);
        if (read != null && !read.isEmpty()) tts.speak(read, TextToSpeech.QUEUE_FLUSH, null, null);
    }

    /** 朗读韵母：按标准读法（如 ao→奥），绝不读英文字母 */
    private void speakFinal(String s) {
        if (!ttsReady || tts == null || s == null || s.isEmpty()) return;
        String read = FINAL_READ.get(s);
        if (read != null && !read.isEmpty()) tts.speak(read, TextToSpeech.QUEUE_FLUSH, null, null);
    }

    /** 朗读一个完整拼音音节（带调或不带调） */
    private void speakSyllable(String s) {
        if (!ttsReady || tts == null || s == null || s.isEmpty()) return;
        tts.speak(s, TextToSpeech.QUEUE_FLUSH, null, null);
    }

    /** 整段拼读：声母音 + 韵母音 + 汉字（零声母则直接拼读音节 + 汉字） */
    private void speakBlend(Word w) {
        if (!ttsReady || tts == null || w == null) return;
        if (w.sm.isEmpty()) {
            tts.speak(tonedPinyin(w.py, w.tone), TextToSpeech.QUEUE_FLUSH, null, null);
        } else {
            tts.speak(INITIAL_READ.get(w.sm), TextToSpeech.QUEUE_FLUSH, null, null);
            tts.speak(FINAL_READ.get(w.ym), TextToSpeech.QUEUE_ADD, null, null);
        }
        tts.speak(w.hanzi, TextToSpeech.QUEUE_ADD, null, null);
    }

    // ===================== 小工具 =====================
    private void highlight(LinearLayout row, Object tag) {
        for (int i = 0; i < row.getChildCount(); i++) {
            View v = row.getChildAt(i);
            if (v instanceof Button) {
                Button b = (Button) v;
                b.setBackgroundColor(b.getTag() != null && b.getTag().equals(tag)
                        ? Color.parseColor("#FFE0B2") : Color.WHITE);
            }
        }
    }

    private void colorSelected(LinearLayout row, Object tag, int color) {
        for (int i = 0; i < row.getChildCount(); i++) {
            View v = row.getChildAt(i);
            if (v instanceof Button) {
                Button b = (Button) v;
                if (b.getTag() != null && b.getTag().equals(tag)) b.setBackgroundColor(color);
            }
        }
    }

    /** 将拼音音节 + 声调转为带声调符号的拼音，如 ("ma",1)->"mā"；
     *  同样可用于韵母（如 ("ia",4)->"ià"），实现声调跟随韵母发声。 */
    private String tonedPinyin(String py, int tone) {
        if (tone < 1 || tone > 4) return py;
        String[][] marks = {
                {"a", "ā", "á", "ǎ", "à"},
                {"o", "ō", "ó", "ǒ", "ò"},
                {"e", "ē", "é", "ě", "è"},
                {"i", "ī", "í", "ǐ", "ì"},
                {"u", "ū", "ú", "ǔ", "ù"},
                {"ü", "ǖ", "ǘ", "ǚ", "ǜ"}
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
        for (String[] m : marks) {
            if (m[0].equals(rep)) { rep = m[tone]; break; }
        }
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
        b.setPadding(dp(10), dp(8), dp(10), dp(8));
        return b;
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }

    // ===================== 生命周期 =====================
    @Override
    protected void onPause() {
        super.onPause();
        if (inGame) { saveActiveGame(); stopActiveGame(); handler.removeCallbacks(timerTick); }
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
