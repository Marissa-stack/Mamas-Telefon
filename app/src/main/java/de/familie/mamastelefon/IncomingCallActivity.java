package de.familie.mamastelefon;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Erscheint, wenn es klingelt: Foto und Name der Anruferin (falls sie eine
 * der Kacheln ist) und ein riesiger grüner Knopf "Annehmen". Ein Tipp genügt,
 * Wischen ist nicht nötig. Danach übernimmt das normale Gesprächsfenster.
 */
public class IncomingCallActivity extends Activity {

    static final String EXTRA_NUMBER = "nummer";
    /** Probe aus den Einstellungen: zeigt den Bildschirm, ohne dass es klingelt. */
    static final String EXTRA_DEMO = "probe";

    private boolean demo;

    /** Zeigt den Annehmen-Bildschirm, wenn es gerade klingelt und alles erlaubt ist. */
    static void launchIfRinging(Context c, String number) {
        Store store = new Store(c);
        if (!store.hasPin() || !store.bigAnswerOn()) return;
        if (c.checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS)
                != PackageManager.PERMISSION_GRANTED) return;
        if (!isRingingNow(c)) return;
        Intent i = new Intent(c, IncomingCallActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        i.putExtra(EXTRA_NUMBER, number);
        try {
            c.startActivity(i);
        } catch (RuntimeException ignored) {
        }
    }

    @SuppressWarnings("deprecation")
    static boolean isRingingNow(Context c) {
        try {
            TelephonyManager t = c.getSystemService(TelephonyManager.class);
            return t == null || t.getCallState() == TelephonyManager.CALL_STATE_RINGING;
        } catch (RuntimeException e) {
            return true; // Im Zweifel anzeigen; das Ende des Klingelns schließt das Fenster
        }
    }

    private final BroadcastReceiver phoneState = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
            if (!demo && state != null && !TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {
                finish();
            }
        }
    };
    private boolean registered;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        build(getIntent());
        applyBars();
    }

    /** Leisten ausblenden: sonst landet man beim Annehmen leicht auf "Startseite" oder "Zurück". */
    private void applyBars() {
        SystemBars.apply(getWindow(), new Store(this).fullscreenOn(), true);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyBars();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        build(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyBars();
        if (!registered) {
            IntentFilter f = new IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(phoneState, f, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(phoneState, f);
            }
            registered = true;
        }
        if (!demo && !isRingingNow(this)) finish();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (registered) {
            try {
                unregisterReceiver(phoneState);
            } catch (RuntimeException ignored) {
            }
            registered = false;
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        // Nicht aus Versehen wegdrücken – nur die Probe lässt sich so schließen
        if (demo) finish();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (VolumeGuard.isVolumeKey(event.getKeyCode())) return true;
        return super.dispatchKeyEvent(event);
    }

    private void build(Intent intent) {
        String number = intent != null ? intent.getStringExtra(EXTRA_NUMBER) : null;
        demo = intent != null && intent.getBooleanExtra(EXTRA_DEMO, false);
        Store store = new Store(this);
        Store.Person who = number != null ? store.findByNumber(number) : null;
        if (demo && who == null && !store.people().isEmpty()) who = store.people().get(0);

        LinearLayout box = Ui.vertical(this);
        box.setBackgroundColor(Ui.BG);
        box.setGravity(Gravity.CENTER);
        int pad = Ui.dp(this, 24);
        box.setPadding(pad, pad, pad, pad);

        TextView title = Ui.text(this, "Das Telefon klingelt", 30, Ui.MUTED, true);
        title.setGravity(Gravity.CENTER);
        box.addView(title, Ui.fullWidth(this, 0));

        View picture;
        if (who != null) {
            picture = Ui.avatar(this, store, who, 24, 96);
        } else {
            Ui.FitSquare frame = new Ui.FitSquare(this);
            View circle = Ui.callCircle(this, 140);
            frame.addView(circle, new android.widget.FrameLayout.LayoutParams(
                    Ui.dp(this, 140), Ui.dp(this, 140), Gravity.CENTER));
            picture = frame;
        }
        LinearLayout.LayoutParams picLp = new LinearLayout.LayoutParams(Ui.dp(this, 250), 0, 1f);
        picLp.gravity = Gravity.CENTER_HORIZONTAL;
        picLp.topMargin = Ui.dp(this, 16);
        box.addView(picture, picLp);

        String name;
        if (who != null) {
            name = who.name;
        } else if (number != null && !number.trim().isEmpty()) {
            name = number;
        } else {
            name = "Anruf";
        }
        TextView nameView = Ui.text(this, name, 38, Ui.TEXT, true);
        nameView.setGravity(Gravity.CENTER);
        nameView.setMaxLines(2);
        box.addView(nameView, Ui.fullWidth(this, 16));

        Button answer = Ui.button(this, "Annehmen", Ui.GREEN, 0xFFFFFFFF, 42);
        answer.setOnClickListener(v -> answer());
        LinearLayout.LayoutParams answerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 150));
        answerLp.topMargin = Ui.dp(this, 24);
        box.addView(answer, answerLp);

        if (demo) {
            TextView hint = Ui.text(this, "Probe: So sieht es aus, wenn es klingelt.", 18, Ui.MUTED, false);
            hint.setGravity(Gravity.CENTER);
            box.addView(hint, Ui.fullWidth(this, 14));
        }

        setContentView(box);
    }

    @SuppressWarnings("deprecation")
    private void answer() {
        if (demo) {
            android.widget.Toast.makeText(this, "Probe: Jetzt würde das Gespräch beginnen",
                    android.widget.Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        TelecomManager tm = getSystemService(TelecomManager.class);
        if (tm != null && checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS)
                == PackageManager.PERMISSION_GRANTED) {
            try {
                tm.acceptRingingCall();
            } catch (RuntimeException ignored) {
            }
        }
        finish();
    }

}
