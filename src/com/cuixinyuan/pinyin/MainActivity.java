package com.cuixinyuan.pinyin;

import android.app.Activity;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.VideoView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 拼音识字乐园 —— 面向小学生的识字练习工具（仅适配安卓手机）
 *
 * 玩法：
 *   1. 每轮展示 10 个汉字，学生为每个汉字选择匹配的“拼音”与“声调”。
 *   2. 本轮 10 题的拼音、声调全部作答正确后，联网加载公开网络资源，
 *      播放约一分钟的幼儿向奖励短视频。
 *   3. 视频播放完成后，自动开启新一轮 10 个汉字的循环练习。
 */
public class MainActivity extends Activity {

    // ====== 基础配置 ======
    private static final String WORD_ASSET = "words.json";
    private static final int ROUND_SIZE = 10;
    private static final int TONE_NEUTRAL = 0;

    // 联网加载的公开网络奖励视频（依次尝试，任意一个可播即可）。
    // 均为公开可访问的示例视频；如希望替换为更贴合“幼儿向”的内容，
    // 只需把下面的地址换成任意公开可访问的 mp4 直链即可。
    private static final String[] REWARD_VIDEOS = {
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyrides.mp4",
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
    };

    // 鼓励语（答对时随机显示）
    private static final String[] PRAISE = {
            "太棒啦！", "你真厉害！", "答对啦，真聪明！", "好样的！", "厉害厉害！", "你学会啦！"
    };

    // ====== 数据 ======
    private static class Word {
        final String hanzi;
        final String py;      // 不带声调的拼音音节，如 "ma"
        final int tone;       // 1~4，0 表示轻声
        Word(String hanzi, String py, int tone) {
            this.hanzi = hanzi; this.py = py; this.tone = tone;
        }
    }

    private final List<Word> dictionary = new ArrayList<>();
    private final Random rng = new Random();

    // ====== 运行时状态 ======
    private List<Word> roundWords = new ArrayList<>();
    private int currentIndex = 0;
    private String selectedSyll = null;
    private Integer selectedTone = null;
    private boolean itemSolved = false;

    // ====== UI ======
    private FrameLayout root;
    private LinearLayout gamePanel;
    private LinearLayout rewardPanel;
    private TextView tvProgress;
    private ProgressBar progressBar;
    private TextView tvHanzi;
    private TextView tvFeedback;
    private LinearLayout syllRow;
    private LinearLayout toneRow;
    private Button btnNext;
    private VideoView videoView;
    private WebView rewardWebView;
    private int videoTryIndex = 0;

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadDictionary();
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
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                dictionary.add(new Word(o.getString("word"), o.getString("py"), o.getInt("tone")));
            }
        } catch (Exception e) {
            // 兜底：即便资源缺失也能运行
            dictionary.add(new Word("你", "ni", 3));
            dictionary.add(new Word("好", "hao", 3));
            dictionary.add(new Word("妈", "ma", 1));
            dictionary.add(new Word("爸", "ba", 4));
        }
    }

    // ===================== UI 构建 =====================
    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#FFF7E6"));
        setContentView(root);

        // ---------- 游戏面板 ----------
        gamePanel = new LinearLayout(this);
        gamePanel.setOrientation(LinearLayout.VERTICAL);
        gamePanel.setGravity(Gravity.CENTER_HORIZONTAL);
        gamePanel.setPadding(dp(16), dp(16), dp(16), dp(16));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(gamePanel);
        root.addView(scroll, new FrameLayout.LayoutParams(
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
        gamePanel.addView(top);

        // 汉字大字
        tvHanzi = textView("", 96, Color.parseColor("#E64A19"), true);
        tvHanzi.setPadding(dp(0), dp(18), dp(0), dp(18));
        gamePanel.addView(tvHanzi);

        // 提示
        gamePanel.addView(textView("请选择它的读音（拼音）", 16, Color.parseColor("#5D4037"), false));
        syllRow = new LinearLayout(this);
        syllRow.setOrientation(LinearLayout.HORIZONTAL);
        syllRow.setGravity(Gravity.CENTER);
        syllRow.setPadding(dp(0), dp(8), dp(0), dp(8));
        gamePanel.addView(syllRow);

        gamePanel.addView(textView("请选择它的声调", 16, Color.parseColor("#5D4037"), false));
        toneRow = new LinearLayout(this);
        toneRow.setOrientation(LinearLayout.HORIZONTAL);
        toneRow.setGravity(Gravity.CENTER);
        toneRow.setPadding(dp(0), dp(8), dp(0), dp(8));
        gamePanel.addView(toneRow);

        tvFeedback = textView("", 20, Color.parseColor("#2E7D32"), true);
        tvFeedback.setPadding(dp(0), dp(10), dp(0), dp(10));
        gamePanel.addView(tvFeedback);

        btnNext = button("下一题 ➜", Color.parseColor("#FF7043"), Color.WHITE);
        btnNext.setEnabled(false);
        btnNext.setOnClickListener(v -> goNext());
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        np.setMargins(dp(0), dp(8), dp(0), dp(0));
        btnNext.setLayoutParams(np);
        gamePanel.addView(btnNext);

        // ---------- 奖励面板 ----------
        rewardPanel = new LinearLayout(this);
        rewardPanel.setOrientation(LinearLayout.VERTICAL);
        rewardPanel.setGravity(Gravity.CENTER);
        rewardPanel.setBackgroundColor(Color.BLACK);
        rewardPanel.setVisibility(View.GONE);
        root.addView(rewardPanel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        videoView = new VideoView(this);
        rewardPanel.addView(videoView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        rewardWebView = new WebView(this);
        rewardWebView.getSettings().setJavaScriptEnabled(true);
        rewardWebView.setVisibility(View.GONE);
        rewardPanel.addView(rewardWebView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        videoView.setOnPreparedListener(mp -> {
            mp.setLooping(false);
            videoView.start();
        });
        videoView.setOnCompletionListener(mp -> onRewardFinished());
        videoView.setOnErrorListener((mp, what, extra) -> {
            videoTryIndex++;
            if (videoTryIndex < REWARD_VIDEOS.length) {
                playRewardVideo();
            } else {
                showLocalReward();
            }
            return true;
        });
    }

    // ===================== 游戏流程 =====================
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
        selectedSyll = null;
        selectedTone = null;

        Word w = roundWords.get(currentIndex);
        tvProgress.setText("第 " + (currentIndex + 1) + " / " + ROUND_SIZE + " 题");
        progressBar.setProgress(currentIndex + 1);
        tvHanzi.setText(w.hanzi);
        tvFeedback.setText("");
        btnNext.setEnabled(false);

        buildSyllOptions(w);
        buildToneOptions();
    }

    private void buildSyllOptions(Word w) {
        syllRow.removeAllViews();
        // 正确项 + 3 个不重复的错误项
        List<String> opts = new ArrayList<>();
        opts.add(w.py);
        List<String> others = new ArrayList<>();
        for (Word d : dictionary) {
            if (!d.py.equals(w.py) && !others.contains(d.py)) others.add(d.py);
        }
        Collections.shuffle(others, rng);
        for (int i = 0; i < 3 && i < others.size(); i++) opts.add(others.get(i));
        Collections.shuffle(opts, rng);

        for (final String s : opts) {
            Button b = button(s, Color.WHITE, Color.parseColor("#37474F"));
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40);
            b.setTag(s);
            b.setOnClickListener(v -> {
                selectedSyll = s;
                highlightRow(syllRow, s, Color.parseColor("#FFF3E0"));
                evaluate();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(72), dp(72));
            lp.setMargins(dp(6), dp(4), dp(6), dp(4));
            b.setLayoutParams(lp);
            syllRow.addView(b);
        }
    }

    private void buildToneOptions() {
        toneRow.removeAllViews();
        // 五个声调按钮：一声 二声 三声 四声 轻声
        int[] tones = {1, 2, 3, 4, TONE_NEUTRAL};
        String[] marks = {"ā", "á", "ǎ", "à", "a"};
        String[] names = {"一声", "二声", "三声", "四声", "轻声"};
        for (int i = 0; i < tones.length; i++) {
            final int t = tones[i];
            Button b = button(marks[i] + "\n" + names[i], Color.WHITE, Color.parseColor("#37474F"));
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            b.setTag(t);
            b.setOnClickListener(v -> {
                selectedTone = t;
                highlightRow(toneRow, t, Color.parseColor("#FFF3E0"));
                evaluate();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(60), dp(72));
            lp.setMargins(dp(4), dp(4), dp(4), dp(4));
            b.setLayoutParams(lp);
            toneRow.addView(b);
        }
    }

    private void evaluate() {
        if (selectedSyll == null || selectedTone == null) return;
        Word w = roundWords.get(currentIndex);
        boolean ok = selectedSyll.equals(w.py) && selectedTone == w.tone;
        if (ok) {
            itemSolved = true;
            tvFeedback.setTextColor(Color.parseColor("#2E7D32"));
            tvFeedback.setText(PRAISE[rng.nextInt(PRAISE.length)]);
            btnNext.setEnabled(true);
            markCorrect(syllRow, w.py);
            markCorrect(toneRow, w.tone);
        } else {
            tvFeedback.setTextColor(Color.parseColor("#C62828"));
            tvFeedback.setText("再想一想哦～");
        }
    }

    private void goNext() {
        if (!itemSolved) return;
        currentIndex++;
        if (currentIndex >= ROUND_SIZE) {
            startReward();
        } else {
            showQuestion();
        }
    }

    // ===================== 奖励环节 =====================
    private void startReward() {
        gamePanel.setVisibility(View.GONE);
        rewardPanel.setVisibility(View.VISIBLE);
        rewardWebView.setVisibility(View.GONE);
        videoView.setVisibility(View.VISIBLE);
        videoTryIndex = 0;
        playRewardVideo();
    }

    private void playRewardVideo() {
        if (videoTryIndex >= REWARD_VIDEOS.length) {
            showLocalReward();
            return;
        }
        try {
            videoView.setVideoURI(Uri.parse(REWARD_VIDEOS[videoTryIndex]));
            videoView.requestFocus();
        } catch (Exception e) {
            videoTryIndex++;
            playRewardVideo();
        }
    }

    private void showLocalReward() {
        // 网络视频不可用时的本地兜底：播放内置的庆祝动画（约 1 分钟）后进入下一轮
        videoView.setVisibility(View.GONE);
        rewardWebView.setVisibility(View.VISIBLE);
        rewardWebView.loadUrl("file:///android_asset/reward.html");
        handler.postDelayed(this::onRewardFinished, 60000);
    }

    private void onRewardFinished() {
        handler.removeCallbacksAndMessages(null);
        try { videoView.stopPlayback(); } catch (Exception ignored) {}
        rewardWebView.loadUrl("about:blank");
        rewardPanel.setVisibility(View.GONE);
        gamePanel.setVisibility(View.VISIBLE);
        startNewRound();
    }

    // ===================== 小工具 =====================
    private void highlightRow(LinearLayout row, Object tag, int color) {
        for (int i = 0; i < row.getChildCount(); i++) {
            View v = row.getChildAt(i);
            if (v instanceof Button) {
                Button b = (Button) v;
                if (b.getTag() != null && b.getTag().equals(tag)) {
                    b.setBackgroundColor(Color.parseColor("#FFE0B2"));
                } else {
                    b.setBackgroundColor(Color.WHITE);
                }
            }
        }
    }

    private void markCorrect(LinearLayout row, Object tag) {
        for (int i = 0; i < row.getChildCount(); i++) {
            View v = row.getChildAt(i);
            if (v instanceof Button) {
                Button b = (Button) v;
                if (b.getTag() != null && b.getTag().equals(tag)) {
                    b.setBackgroundColor(Color.parseColor("#C8E6C9"));
                }
            }
        }
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
}
