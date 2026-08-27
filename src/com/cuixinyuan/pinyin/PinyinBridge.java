package com.cuixinyuan.pinyin;

import org.json.JSONObject;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 朗读例字桥接：数据由 UniPinyin（GitHub: nillith/UniPinyin）在构建时生成至 pinyin_speak.json。
 */
final class PinyinBridge {

    private static final Set<String> I_COMPOUND = new HashSet<>(Arrays.asList("ie", "in", "ing", "iu"));

    private final Map<String, String> initialSpeak;
    private final Map<String, String> finalSpeak;
    private final Set<String> wholeSyllables;

    PinyinBridge(JSONObject root) {
        initialSpeak = new java.util.HashMap<>();
        finalSpeak = new java.util.HashMap<>();
        wholeSyllables = new HashSet<>();
        try {
            initialSpeak.putAll(jsonToMap(root.optJSONObject("initial")));
            finalSpeak.putAll(jsonToMap(root.optJSONObject("final")));
            if (root.has("whole")) {
                for (int i = 0; i < root.getJSONArray("whole").length(); i++) {
                    wholeSyllables.add(root.getJSONArray("whole").getString(i));
                }
            }
        } catch (org.json.JSONException ignored) { /* 保持空映射 */ }
    }

    private static Map<String, String> jsonToMap(JSONObject o) throws org.json.JSONException {
        Map<String, String> m = new java.util.HashMap<>();
        if (o == null) return m;
        java.util.Iterator<String> it = o.keys();
        while (it.hasNext()) {
            String k = it.next();
            m.put(k, o.getString(k));
        }
        return m;
    }

    static String effectiveYm(String sm, String ym) {
        if ("u".equals(ym) && isJqxyOrY(sm)) return "ü";
        if ("un".equals(ym) && isJqxyOrY(sm)) return "ün";
        if ("ue".equals(ym) && "y".equals(sm)) return "üe";
        return ym;
    }

    static boolean isJqxyOrY(String sm) {
        return "j".equals(sm) || "q".equals(sm) || "x".equals(sm) || "y".equals(sm);
    }

    static List<String> splitFinalAscii(String ym) {
        if (ym == null || ym.isEmpty()) return Collections.emptyList();
        if (ym.length() > 1 && ym.charAt(0) == 'u') {
            String rest = ym.substring(1);
            if (rest.equals("a") || rest.equals("o") || rest.equals("ai") || rest.equals("ao")
                    || rest.equals("an") || rest.equals("ang") || rest.equals("eng")) {
                return Arrays.asList("u", rest);
            }
        }
        if (ym.length() > 1 && ym.charAt(0) == 'i' && !I_COMPOUND.contains(ym)) {
            String rest = ym.substring(1);
            if (rest.equals("a") || rest.equals("ao") || rest.equals("an") || rest.equals("ang")
                    || rest.equals("ong")) {
                return Arrays.asList("i", rest);
            }
        }
        return Collections.singletonList(ym);
    }

    boolean isWholeSyllable(String py) {
        return wholeSyllables.contains(py);
    }

    String initialHanzi(String sm) {
        return initialSpeak.get(sm);
    }

    String finalHanzi(String sm, String ym, int tone) {
        String key = effectiveYm(sm, ym) + ":" + tone;
        String h = finalSpeak.get(key);
        return h == null ? "" : h;
    }

    List<String> finalParts(String ym) {
        return splitFinalAscii(ym);
    }
}
