package com.cuixinyuan.pinyin;

import android.media.AudioManager;
import android.media.ToneGenerator;

/**
 * 答题反馈短旋律：答对鼓励、答错安慰。
 */
final class FeedbackSfx {

    private FeedbackSfx() { }

    static void playEncourage() {
        playMelody(new int[]{
                ToneGenerator.TONE_PROP_ACK, 90,
                ToneGenerator.TONE_PROP_BEEP, 100,
                ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 110,
                ToneGenerator.TONE_PROP_BEEP2, 130
        });
    }

    static void playComfort() {
        playMelody(new int[]{
                ToneGenerator.TONE_PROP_NACK, 120,
                ToneGenerator.TONE_CDMA_SOFT_ERROR_LITE, 100,
                ToneGenerator.TONE_PROP_BEEP, 90,
                ToneGenerator.TONE_CDMA_ONE_MIN_BEEP, 110
        });
    }

    private static void playMelody(final int[] toneAndMs) {
        new Thread(() -> {
            ToneGenerator tg = null;
            try {
                tg = new ToneGenerator(AudioManager.STREAM_MUSIC, 85);
                for (int i = 0; i < toneAndMs.length; i += 2) {
                    tg.startTone(toneAndMs[i], toneAndMs[i + 1]);
                    try { Thread.sleep(toneAndMs[i + 1] + 30); } catch (InterruptedException ignored) { }
                }
            } catch (Exception ignored) {
            } finally {
                if (tg != null) tg.release();
            }
        }).start();
    }
}
