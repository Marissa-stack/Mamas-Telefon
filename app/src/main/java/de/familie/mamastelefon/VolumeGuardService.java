package de.familie.mamastelefon;

import android.accessibilityservice.AccessibilityService;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Bedienungshilfe "Lautstärke-Schutz".
 *
 * 1. Schluckt die Lautstärketasten, solange der Bildschirm an ist.
 * 2. Merkt sofort, wenn Lautstärke, Lautlos oder "Nicht stören" anders
 *    verstellt wurden (z. B. über das Schnellmenü oder bei ausgeschaltetem
 *    Bildschirm), und stellt alles wieder zurück.
 * 3. Prüft zur Sicherheit zusätzlich alle 20 Sekunden.
 *
 * Bildschirminhalte werden nicht gelesen.
 */
public class VolumeGuardService extends AccessibilityService {

    private static final long CHECK_INTERVAL_MS = 20_000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable enforceNow = () -> VolumeGuard.enforce(this);
    private final Runnable periodic = new Runnable() {
        @Override
        public void run() {
            VolumeGuard.enforce(VolumeGuardService.this);
            handler.postDelayed(this, CHECK_INTERVAL_MS);
        }
    };
    private final BroadcastReceiver changes = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            enforceSoon();
        }
    };
    private boolean registered;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        IntentFilter filter = new IntentFilter();
        filter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
        filter.addAction("android.media.VOLUME_CHANGED_ACTION");
        filter.addAction("android.media.STREAM_MUTE_CHANGED_ACTION");
        filter.addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        if (!registered) {
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(changes, filter, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(changes, filter);
            }
            registered = true;
        }
        handler.removeCallbacks(periodic);
        handler.post(periodic);
    }

    private void enforceSoon() {
        handler.removeCallbacks(enforceNow);
        handler.postDelayed(enforceNow, 300);
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (VolumeGuard.isVolumeKey(event.getKeyCode()) && new Store(this).guardOn()) {
            if (event.getAction() == KeyEvent.ACTION_UP) enforceSoon();
            return true; // Taste wird geschluckt
        }
        return super.onKeyEvent(event);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Keine Bildschirm-Ereignisse nötig
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (registered) {
            try {
                unregisterReceiver(changes);
            } catch (RuntimeException ignored) {
            }
            registered = false;
        }
        super.onDestroy();
    }
}
