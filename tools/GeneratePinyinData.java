/**
 * 使用 UniPinyin 权威数据生成 words.json 校验结果与韵母/声母朗读例字表。
 * 运行: javac -cp lib/unipinyin.jar tools/GeneratePinyinData.java && java -cp lib/unipinyin.jar:tools GeneratePinyinData
 */
import com.nillith.pinyin.Pinyin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class GeneratePinyinData {

    private static final Set<String> WHOLE = new HashSet<>(Arrays.asList(
            "zhi", "chi", "shi", "ri", "zi", "ci", "si",
            "yi", "wu", "yu", "ye", "yue", "yin", "yun", "ying"));

    private static final Set<String> I_COMPOUND = new HashSet<>(Arrays.asList("ie", "in", "ing", "iu"));

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

    static String effectiveYm(String sm, String ym) {
        if ("u".equals(ym) && ("j".equals(sm) || "q".equals(sm) || "x".equals(sm) || "y".equals(sm))) return "ü";
        if ("un".equals(ym) && ("j".equals(sm) || "q".equals(sm) || "x".equals(sm) || "y".equals(sm))) return "ün";
        if ("ue".equals(ym) && "y".equals(sm)) return "üe";
        return ym;
    }

    static String smAscii(Pinyin p) {
        String init = p.getInitial();
        if (init == null || init.isEmpty()) return "";
        return p.toStringAsciiNoTone().substring(0, init.length());
    }

    public static void main(String[] args) throws Exception {
        String wordsPath = "assets/words.json";
        Map<String, String> finalSpeak = new HashMap<>();
        Map<String, String> initialSpeak = new HashMap<>();

        ingestChar(finalSpeak, initialSpeak, readWordsChars(wordsPath));

        for (int cp = 0x4E00; cp <= 0x9FFF; cp++) {
            ingestChar(finalSpeak, initialSpeak, String.valueOf((char) cp));
        }

        for (String key : new ArrayList<>(finalSpeak.keySet())) {
            String[] parts = key.split(":");
            String ym = parts[0];
            int tone = Integer.parseInt(parts[1]);
            for (String part : splitFinalAscii(ym)) {
                String pk = part + ":" + tone;
                if (!finalSpeak.containsKey(pk)) {
                    String alt = part + ":1";
                    if (finalSpeak.containsKey(alt)) finalSpeak.put(pk, finalSpeak.get(alt));
                }
            }
        }

        String json = readFile(wordsPath);
        int fixes = 0;
        String[] entries = json.replace("[", "").replace("]", "").split("\\},");
        StringBuilder out = new StringBuilder("[\n");
        boolean first = true;

        for (String raw : entries) {
            String e = raw.trim();
            if (!e.endsWith("}")) e += "}";
            if (!e.startsWith("{")) e = "{" + e;
            String word = extract(e, "word");
            if (word == null || word.isEmpty()) continue;
            char hc = word.charAt(0);
            Pinyin p = Pinyin.getPinyin(hc);
            String sm = smAscii(p);
            String ym = effectiveYm(sm, p.getFinalAscii());
            int tone = p.getTone();
            String py = p.toStringAsciiNoTone();
            if (tone < 1 || tone > 4) tone = extractInt(e, "tone");

            String oldSm = extract(e, "sm");
            String oldYm = extract(e, "ym");
            if (!sm.equals(oldSm) || !ym.equals(oldYm)) fixes++;

            if (!first) out.append(",\n");
            first = false;
            out.append("  {\n");
            out.append("    \"word\": \"").append(word).append("\",\n");
            out.append("    \"py\": \"").append(py).append("\",\n");
            out.append("    \"sm\": \"").append(sm).append("\",\n");
            out.append("    \"ym\": \"").append(ym).append("\",\n");
            out.append("    \"tone\": ").append(tone).append("\n");
            out.append("  }");
        }
        out.append("\n]\n");
        writeFile(wordsPath, out.toString());

        StringBuilder speak = new StringBuilder();
        speak.append("{\n  \"initial\": {\n");
        boolean fi = true;
        for (Map.Entry<String, String> en : initialSpeak.entrySet()) {
            if (!fi) speak.append(",\n");
            fi = false;
            speak.append("    \"").append(en.getKey()).append("\": \"").append(en.getValue()).append("\"");
        }
        speak.append("\n  },\n  \"final\": {\n");
        fi = true;
        for (Map.Entry<String, String> en : finalSpeak.entrySet()) {
            if (!fi) speak.append(",\n");
            fi = false;
            speak.append("    \"").append(en.getKey()).append("\": \"").append(en.getValue()).append("\"");
        }
        speak.append("\n  },\n  \"whole\": [");
        fi = true;
        for (String w : WHOLE) {
            if (!fi) speak.append(", ");
            fi = false;
            speak.append("\"").append(w).append("\"");
        }
        speak.append("]\n}\n");
        writeFile("assets/pinyin_speak.json", speak.toString());

        System.out.println("Updated words.json, fixed " + fixes + " entries");
        System.out.println("Generated pinyin_speak.json: initial=" + initialSpeak.size() + " final=" + finalSpeak.size());
    }

    static void ingestChar(Map<String, String> finalSpeak, Map<String, String> initialSpeak, String chars) {
        for (int i = 0; i < chars.length(); i++) {
            char c = chars.charAt(i);
            Pinyin p = Pinyin.getPinyin(c);
            int tone = p.getTone();
            if (tone < 1 || tone > 4) continue;
            if (p.toStringAsciiNoTone().isEmpty()) continue;

            String sm = smAscii(p);
            String ym = effectiveYm(sm, p.getFinalAscii());
            String keyFinal = ym + ":" + tone;
            if (!finalSpeak.containsKey(keyFinal)) finalSpeak.put(keyFinal, String.valueOf(c));

            if (!sm.isEmpty() && !initialSpeak.containsKey(sm)) {
                initialSpeak.put(sm, String.valueOf(c));
            }
        }
    }

    static String readWordsChars(String path) throws IOException {
        String json = readFile(path);
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (true) {
            int w = json.indexOf("\"word\"", i);
            if (w < 0) break;
            String word = extract(json.substring(w), "word");
            if (!word.isEmpty()) sb.append(word.charAt(0));
            i = w + 1;
        }
        return sb.toString();
    }

    static String extract(String json, String key) {
        String pat = "\"" + key + "\"";
        int i = json.indexOf(pat);
        if (i < 0) return "";
        int q = json.indexOf('"', json.indexOf(':', i) + 1);
        int q2 = json.indexOf('"', q + 1);
        return json.substring(q + 1, q2);
    }

    static int extractInt(String json, String key) {
        String pat = "\"" + key + "\"";
        int i = json.indexOf(pat);
        if (i < 0) return 1;
        int c = json.indexOf(':', i) + 1;
        return Integer.parseInt(json.substring(c, json.indexOf(',', c)).trim());
    }

    static String readFile(String path) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (InputStream is = new FileInputStream(path)) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0) baos.write(buf, 0, n);
        }
        return baos.toString(StandardCharsets.UTF_8.name());
    }

    static void writeFile(String path, String content) throws IOException {
        try (OutputStream os = new FileOutputStream(path)) {
            os.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }
}
