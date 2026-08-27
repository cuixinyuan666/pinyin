package com.cuixinyuan.pinyin;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/** 汉字答题统计与错题复习权重（持久化）。 */
final class WordStatsManager {

    static final class Stats {
        int appear;
        int wrong;
        int correctSinceWrong;
        boolean pendingRetry;

        JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("appear", appear);
                o.put("wrong", wrong);
                o.put("csr", correctSinceWrong);
                o.put("pending", pendingRetry);
            } catch (Exception ignored) { }
            return o;
        }

        static Stats fromJson(JSONObject o) {
            Stats s = new Stats();
            if (o == null) return s;
            s.appear = o.optInt("appear", 0);
            s.wrong = o.optInt("wrong", 0);
            s.correctSinceWrong = o.optInt("csr", 0);
            s.pendingRetry = o.optBoolean("pending", false);
            return s;
        }
    }

    private final Map<String, Stats> map = new HashMap<>();

    Stats get(String hanzi) {
        Stats s = map.get(hanzi);
        if (s == null) {
            s = new Stats();
            map.put(hanzi, s);
        }
        return s;
    }

    void recordAppear(String hanzi) {
        get(hanzi).appear++;
    }

    void recordFirstWrong(String hanzi) {
        Stats s = get(hanzi);
        s.wrong++;
        s.correctSinceWrong = 0;
        s.pendingRetry = true;
    }

    void recordFirstCorrectOnRetry(String hanzi) {
        Stats s = get(hanzi);
        s.correctSinceWrong++;
        s.pendingRetry = false;
    }

    /** 错题复习权重：错得越多越高；答对积累后逐步降低（例：错5次、对2次后降频）。 */
    double reviewWeight(String hanzi) {
        Stats s = get(hanzi);
        if (!s.pendingRetry && s.wrong == 0) return 0;
        int recovery = s.correctSinceWrong / 2;
        double w = s.wrong - recovery;
        if (s.pendingRetry) w += 3;
        return Math.max(0.5, w);
    }

    boolean needsReview(String hanzi) {
        Stats s = get(hanzi);
        return s.pendingRetry || s.wrong > 0;
    }

    JSONObject save() {
        JSONObject root = new JSONObject();
        try {
            for (Map.Entry<String, Stats> e : map.entrySet()) {
                root.put(e.getKey(), e.getValue().toJson());
            }
        } catch (Exception ignored) { }
        return root;
    }

    void load(JSONObject root) {
        map.clear();
        if (root == null) return;
        java.util.Iterator<String> it = root.keys();
        while (it.hasNext()) {
            String k = it.next();
            map.put(k, Stats.fromJson(root.optJSONObject(k)));
        }
    }
}
