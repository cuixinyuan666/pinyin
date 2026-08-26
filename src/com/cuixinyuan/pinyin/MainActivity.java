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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * 拼音识字乐园 —— 面向小学生的识字练习工具（仅适配安卓手机）
 *
 * 玩法：
 *   1. 每轮展示 10 个汉字，学生为每个汉字分别选择匹配的「声母」「韵母」「声调」。
 *   2. 每道题作答结束后，自动用语音朗读该汉字的读音。
 *   3. 每完成 10 道题目，奖励一分钟的「贪吃蛇」小游戏游玩时长；
 *      游戏结束后回到答题环节；贪吃蛇的游戏进度（蛇身、得分）跨轮保留，
 *      条件达成后可继续进行游戏。
 */
public class MainActivity extends Activity implements TextToSpeech.OnInitListener {

    // ====== 基础配置 ======
    private static final String WORD_ASSET = "words.json";
    private static final int ROUND_SIZE = 10;
    private static final int REWARD_SECONDS = 60; // 奖励贪吃蛇游玩时长
    private static final String PREFS = "pinyin_prefs";
    private static final String KEY_SNAKE_SCORE = "snake_score";

    // 鼓励语（答对时随机显示）
    private static final String[] PRAISE = {
            "太棒啦！", "你真厉害！", "答对啦，真聪明！", "好样的！", "厉害厉害！", "你学会啦！"
    };

    // 声调按钮（一声~四声），含声调符号与名称
    private static final String[] TONE_MARK = {"ā", "á", "ǎ", "à"};
    private static final String[] TONE_NAME = {"一声", "二声", "三声", "四声"};

    // ====== 数据 ======
    private static class Word {
        final String hanzi;
        final String py;   // 不带声调的拼音音节，如 "ma"
        final String sm;   // 声母，如 "m"；零声母时为 ""
        final String ym;   // 韵母，如 "a"
        final int tone;    // 1~4
        Word(String hanzi, String py, String sm, String ym, int tone) {
            this.hanzi = hanzi; this.py = py; this.sm = sm; this.ym = ym; this.tone = tone;
        }
    }

    private final List<Word> dictionary = new ArrayList<>();
    private final List<String> smPool = new ArrayList<>();
    private final List<String> ymPool = new ArrayList<>();
    private final Random rng = new Random();

    // ====== 运行时状态 ======
    private List<Word> roundWords = new ArrayList<>();
    private int currentIndex = 0;
    private String selectedSm = null;
    private String selectedYm = null;
    private Integer selectedTone = null;
    private boolean itemSolved = false;

    // ====== 语音朗读 ======
    private TextToSpeech tts;
    private boolean ttsReady = false;

    // ====== 贪吃蛇奖励 ======
    private SnakeView snakeView;
    private boolean inGame = false;
    private int timeLeft = REWARD_SECONDS;

    // ====== UI ======
    private FrameLayout root;
    private ScrollView quizScroll;
    private LinearLayout quizPanel;
    private FrameLayout gamePanel;

    private TextView tvProgress;
    private ProgressBar progressBar;
    private TextView tvHanzi;
    private Button btnSpeak;
    private TextView tvFeedback;
    private LinearLayout smRow;
    private LinearLayout ymRow;
    private LinearLayout toneRow;
    private Button btnNext;

    private TextView tvTime;
    private TextView tvScore;
    private Button btnEndGame;

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadDictionary();
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
            Set<String> smSet = new HashSet<>();
            Set<String> ymSet = new HashSet<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Word w = new Word(o.getString("word"), o.getString("py"),
                        o.getString("sm"), o.getString("ym"), o.getInt("tone"));
                dictionary.add(w);
                smSet.add(w.sm);
                ymSet.add(w.ym);
            }
            smPool.addAll(smSet);
            ymPool.addAll(ymSet);
        } catch (Exception e) {
            // 兜底：即便资源缺失也能运行
            dictionary.add(new Word("你", "ni", "n", "i", 3));
            dictionary.add(new Word("好", "hao", "h", "ao", 3));
            dictionary.add(new Word("妈", "ma", "m", "a", 1));
            dictionary.add(new Word("爸", "ba", "b", "a", 4));
            smPool.add("n"); smPool.add("h"); smPool.add("m"); smPool.add("b");
            ymPool.add("i"); ymPool.add("ao"); ymPool.add("a");
        }
    }

    // ===================== UI 构建 =====================
    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#FFF7E6"));
        setContentView(root);

        buildQuizPanel();
        buildGamePanel();

        // 默认显示答题面板
        quizScroll.setVisibility(View.VISIBLE);
        gamePanel.setVisibility(View.GONE);
    }

    private void buildQuizPanel() {
        quizPanel = new LinearLayout(this);
        quizPanel.setOrientation(LinearLayout.VERTICAL);
        quizPanel.setGravity(Gravity.CENTER_HORIZONTAL);
        quizPanel.setPadding(dp(16), dp(16), dp(16), dp(16));

        quizScroll = new ScrollView(this);
        quizScroll.addView(quizPanel);
        root.addView(quizScroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 进度
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setGravity(Gravity.CENTER);
        tvProgress = textView("第 1 / " + ROUND_SIZE + " 题", 18, Color.parseColor("#5D4037"), true);
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(ROUND_SIZE);
        progressBar.setProgress(1);
        progressBar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(12)));
        top.addView(tvProgress);
        top.addView(progressBar);
        quizPanel.addView(top);

        // 汉字大字
        tvHanzi = textView("", 96, Color.parseColor("#E64A19"), true);
        tvHanzi.setPadding(dp(0), dp(14), dp(0), dp(6));
        quizPanel.addView(tvHanzi);

        // 听一听按钮
        btnSpeak = button("🔊 听一听", Color.parseColor("#FFB74D"), Color.WHITE);
        btnSpeak.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        btnSpeak.setOnClickListener(v -> speakCurrent());
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(44));
        sp.setMargins(dp(0), dp(2), dp(0), dp(6));
        btnSpeak.setLayoutParams(sp);
        quizPanel.addView(btnSpeak);

        quizPanel.addView(textView("请选择它的「声母」", 16, Color.parseColor("#5D4037"), false));
        smRow = new LinearLayout(this);
        smRow.setOrientation(LinearLayout.HORIZONTAL);
        smRow.setGravity(Gravity.CENTER);
        smRow.setPadding(dp(0), dp(6), dp(0), dp(6));
        quizPanel.addView(smRow);

        quizPanel.addView(textView("请选择它的「韵母」", 16, Color.parseColor("#5D4037"), false));
        ymRow = new LinearLayout(this);
        ymRow.setOrientation(LinearLayout.HORIZONTAL);
        ymRow.setGravity(Gravity.CENTER);
        ymRow.setPadding(dp(0), dp(6), dp(0), dp(6));
        quizPanel.addView(ymRow);

        quizPanel.addView(textView("请选择它的「声调」", 16, Color.parseColor("#5D4037"), false));
        toneRow = new LinearLayout(this);
        toneRow.setOrientation(LinearLayout.HORIZONTAL);
        toneRow.setGravity(Gravity.CENTER);
        toneRow.setPadding(dp(0), dp(6), dp(0), dp(6));
        quizPanel.addView(toneRow);

        tvFeedback = textView("", 20, Color.parseColor("#2E7D32"), true);
        tvFeedback.setPadding(dp(0), dp(8), dp(0), dp(8));
        quizPanel.addView(tvFeedback);

        btnNext = button("下一题 ➜", Color.parseColor("#FF7043"), Color.WHITE);
        btnNext.setEnabled(false);
        btnNext.setOnClickListener(v -> goNext());
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        np.setMargins(dp(0), dp(8), dp(0), dp(0));
        btnNext.setLayoutParams(np);
        quizPanel.addView(btnNext);
    }

    private void buildGamePanel() {
        gamePanel = new FrameLayout(this);
        root.addView(gamePanel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 贪吃蛇画布
        snakeView = new SnakeView(this);
        gamePanel.addView(snakeView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 顶部信息条：剩余时间 + 得分 + 结束按钮
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.HORIZONTAL);
        info.setGravity(Gravity.CENTER_VERTICAL);
        info.setPadding(dp(16), dp(16), dp(16), dp(16));
        info.setBackgroundColor(Color.parseColor("#CC000000"));

        tvTime = textView("剩余 60 秒", 18, Color.WHITE, true);
        tvScore = textView("得分 0", 18, Color.parseColor("#FFEB3B"), true);
        btnEndGame = button("结束游戏", Color.parseColor("#EF5350"), Color.WHITE);
        btnEndGame.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        btnEndGame.setOnClickListener(v -> exitGame());

        LinearLayout.LayoutParams wt = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        tvTime.setLayoutParams(wt);
        tvScore.setLayoutParams(wt);
        LinearLayout.LayoutParams wb = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
        btnEndGame.setLayoutParams(wb);

        info.addView(tvTime);
        info.addView(tvScore);
        info.addView(btnEndGame);

        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gamePanel.addView(info, ip);

        // 底部提示
        TextView hint = textView("滑动屏幕控制小蛇方向，吃到果子得分！", 16, Color.WHITE, false);
        hint.setPadding(dp(16), dp(10), dp(16), dp(20));
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hp.gravity = Gravity.BOTTOM;
        hint.setLayoutParams(hp);
        gamePanel.addView(hint);

        // 得分回调
        snakeView.setScoreListener(score -> {
            tvScore.setText("得分 " + score);
            SharedPreferences.Editor ed = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
            ed.putInt(KEY_SNAKE_SCORE, score);
            ed.apply();
        });

        // 恢复历史最高/当前得分
        int saved = getSharedPreferences(PREFS, MODE_PRIVATE).getInt(KEY_SNAKE_SCORE, 0);
        snakeView.setScore(saved);
    }

    // ===================== 答题流程 =====================
    private void startNewRound() {
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
        selectedSm = null;
        selectedYm = null;
        selectedTone = null;

        Word w = roundWords.get(currentIndex);
        tvProgress.setText("第 " + (currentIndex + 1) + " / " + ROUND_SIZE + " 题");
        progressBar.setProgress(currentIndex + 1);
        tvHanzi.setText(w.hanzi);
        tvFeedback.setText("");
        btnNext.setEnabled(false);

        buildSmOptions(w);
        buildYmOptions(w);
        buildToneOptions();
    }

    private void buildSmOptions(Word w) {
        smRow.removeAllViews();
        List<String> opts = new ArrayList<>();
        opts.add(w.sm);
        List<String> pool = new ArrayList<>(smPool);
        if (!w.sm.isEmpty()) pool.removeAll(Collections.singleton(""));
        pool.remove(w.sm);
        Collections.shuffle(pool, rng);
        for (int i = 0; i < 3 && i < pool.size(); i++) opts.add(pool.get(i));
        Collections.shuffle(opts, rng);

        for (final String s : opts) {
            String label = s.isEmpty() ? "∅" : s;
            Button b = button(label, Color.WHITE, Color.parseColor("#37474F"));
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34);
            b.setTag(s);
            b.setOnClickListener(v -> {
                selectedSm = s;
                highlight(smRow, s);
                evaluate();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(72), dp(72));
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
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
            b.setTag(s);
            b.setOnClickListener(v -> {
                selectedYm = s;
                highlight(ymRow, s);
                evaluate();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(78), dp(64));
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
                selectedTone = t;
                highlight(toneRow, t);
                evaluate();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(72), dp(72));
            lp.setMargins(dp(4), dp(4), dp(4), dp(4));
            b.setLayoutParams(lp);
            toneRow.addView(b);
        }
    }

    private void evaluate() {
        if (selectedSm == null || selectedYm == null || selectedTone == null) return;
        Word w = roundWords.get(currentIndex);
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
        // 每道题作答结束后朗读该汉字的读音
        speakCurrent();
    }

    private void goNext() {
        if (!itemSolved) return;
        currentIndex++;
        if (currentIndex >= ROUND_SIZE) {
            enterGame();
        } else {
            showQuestion();
        }
    }

    // ===================== 贪吃蛇奖励环节 =====================
    private void enterGame() {
        inGame = true;
        timeLeft = REWARD_SECONDS;
        tvTime.setText("剩余 " + timeLeft + " 秒");
        tvScore.setText("得分 " + snakeView.getScore());
        quizScroll.setVisibility(View.GONE);
        gamePanel.setVisibility(View.VISIBLE);
        snakeView.startGame();
        startTimer();
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
            if (timeLeft <= 0) {
                tvTime.setText("时间到");
                exitGame();
                return;
            }
            tvTime.setText("剩余 " + timeLeft + " 秒");
            handler.postDelayed(timerTick, 1000);
        }
    };

    private void exitGame() {
        inGame = false;
        handler.removeCallbacks(timerTick);
        snakeView.stopGame();
        gamePanel.setVisibility(View.GONE);
        quizScroll.setVisibility(View.VISIBLE);
        startNewRound();
    }

    // ===================== 语音朗读 =====================
    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int res = tts.setLanguage(Locale.CHINESE);
            ttsReady = (res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED);
        }
    }

    private void speakCurrent() {
        if (!ttsReady || tts == null) return;
        Word w = roundWords.get(currentIndex);
        tts.speak(w.hanzi, TextToSpeech.QUEUE_FLUSH, null, null);
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

    /** 将拼音音节 + 声调转为带声调符号的拼音，如 ("ma",1)->"mā" */
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
            if (iu >= 0) idx = iu + 1;          // iu -> 标 u
            else {
                int ui = py.indexOf("ui");
                if (ui >= 0) idx = ui + 1;       // ui -> 标 i
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
        if (inGame) {
            snakeView.stopGame();
            handler.removeCallbacks(timerTick);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (inGame) {
            snakeView.startGame();
            startTimer();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (tts != null) tts.shutdown();
        if (snakeView != null) snakeView.stopGame();
    }
}
