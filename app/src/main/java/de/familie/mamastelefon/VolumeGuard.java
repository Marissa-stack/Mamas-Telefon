package de.familie.mamastelefon;

import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioManager;
import android.os.Build;
import android.view.KeyEvent;

/**
 * Hält Klingelton und Gesprächslautstärke auf den eingestellten Werten
 * und schaltet "Lautlos", "Vibration" und "Nicht stören" wieder ab.
 */
final class VolumeGuard {

    private VolumeGuard() {
    }

    static boolean isVolumeKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_VOLUME_UP
                || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE;
    }

    /** Stellt alles wieder richtig ein, falls etwas verstellt wurde. */
    static void enforce(Context context) {
        Store store = new Store(context);
        if (!store.guardOn()) return;

        AudioManager am = context.getSystemService(AudioManager.class);
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (am == null) return;

        // "Nicht stören" ausschalten (geht nur, wenn der Zugriff erlaubt wurde)
        if (nm != null && nm.isNotificationPolicyAccessGranted()
                && nm.getCurrentInterruptionFilter() != NotificationManager.INTERRUPTION_FILTER_ALL) {
            try {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL);
            } catch (RuntimeException ignored) {
            }
        }

        // Lautlos oder Vibration zurück auf normales Klingeln
        if (am.getRingerMode() != AudioManager.RINGER_MODE_NORMAL) {
            try {
                am.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
            } catch (RuntimeException ignored) {
            }
        }

        hold(am, AudioManager.STREAM_RING, lockedLevel(store, am, AudioManager.STREAM_RING));
        hold(am, AudioManager.STREAM_VOICE_CALL, lockedLevel(store, am, AudioManager.STREAM_VOICE_CALL));
    }

    /**
     * Die Stufe, auf der die Lautstärke gehalten wird. Beim allerersten Mal
     * wird die aktuelle Lautstärke übernommen (oder die höchste, falls das
     * Telefon gerade stumm ist), damit es nicht plötzlich viel lauter klingelt.
     */
    static int lockedLevel(Store store, AudioManager am, int stream) {
        boolean ring = stream == AudioManager.STREAM_RING;
        int stored = ring ? store.ringLevel() : store.callLevel();
        if (stored == Store.UNSET) {
            int current = am.getStreamVolume(stream);
            stored = current >= minLevel(am, stream) ? current : am.getStreamMaxVolume(stream);
            if (ring) {
                store.setRingLevel(stored);
            } else {
                store.setCallLevel(stored);
            }
        }
        return target(am, stream, stored);
    }

    /** Niedrigste erlaubte Stufe (nie 0, also nie stumm). */
    static int minLevel(AudioManager am, int stream) {
        int min = Build.VERSION.SDK_INT >= 28 ? am.getStreamMinVolume(stream) : 0;
        return Math.min(Math.max(1, min), am.getStreamMaxVolume(stream));
    }

    /** Die Stufe, auf der die Lautstärke gehalten wird. */
    static int target(AudioManager am, int stream, int wanted) {
        int max = am.getStreamMaxVolume(stream);
        if (wanted < 0 || wanted > max) return max;
        return Math.max(minLevel(am, stream), wanted);
    }

    private static void hold(AudioManager am, int stream, int t) {
        try {
            if (am.isStreamMute(stream)) {
                am.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, 0);
            }
            if (am.getStreamVolume(stream) != t) {
                am.setStreamVolume(stream, t, 0);
            }
        } catch (RuntimeException ignored) {
            // Manche Geräte verbieten das in Sonderfällen – nächster Versuch kommt bald
        }
    }
}
