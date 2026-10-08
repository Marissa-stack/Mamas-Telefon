package de.familie.mamastelefon;

import android.accessibilityservice.AccessibilityService;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
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

    // ---- Großer "Annehmen"-Knopf
    private String ringingNumber;
    private final Runnable showIncoming = this::launchIncoming;
    private final Runnable showIncomingAgain = this::launchIncoming;
    private final BroadcastReceiver phoneState = new BroadcastReceiver() {
        @SuppressWarnings("deprecation")
        @Override
        public void onReceive(Context context, Intent intent) {
            String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
            if (TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {
                String nr = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
                if (nr != null && !nr.isEmpty()) ringingNumber = nr;
                // Kurz warten, damit unser Fenster über dem vom Telefon liegt,
                // und zur Sicherheit noch einmal nach vorne holen
                handler.removeCallbacks(showIncoming);
                handler.removeCallbacks(showIncomingAgain);
                handler.postDelayed(showIncoming, 700);
                handler.postDelayed(showIncomingAgain, 2500);
            } else if (state != null) {
                ringingNumber = null;
                handler.removeCallbacks(showIncoming);
                handler.removeCallbacks(showIncomingAgain);
            }
            enforceSoon();
        }
    };
    private boolean phoneRegistered;

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
        if (!phoneRegistered) {
            IntentFilter pf = new IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(phoneState, pf, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(phoneState, pf);
            }
            phoneRegistered = true;
        }
        handler.removeCallbacks(periodic);
        handler.post(periodic);
    }

    private void launchIncoming() {
        Store store = new Store(this);
        if (!store.hasPin() || !store.bigAnswerOn()) return;
        if (checkSelfPermission(android.Manifest.permission.ANSWER_PHONE_CALLS)
                != PackageManager.PERMISSION_GRANTED) return;
        if (!stillRinging()) return;
        Intent i = new Intent(this, IncomingCallActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        i.putExtra(IncomingCallActivity.EXTRA_NUMBER, ringingNumber);
        try {
            startActivity(i);
        } catch (RuntimeException ignored) {
        }
    }

    private boolean stillRinging() {
        try {
            TelecomManager tm = getSystemService(TelecomManager.class);
            return tm == null || tm.isRinging();
        } catch (RuntimeException e) {
            return true;
        }
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
        if (phoneRegistered) {
            try {
                unregisterReceiver(phoneState);
            } catch (RuntimeException ignored) {
            }
            phoneRegistered = false;
        }
        super.onDestroy();
    }
}
