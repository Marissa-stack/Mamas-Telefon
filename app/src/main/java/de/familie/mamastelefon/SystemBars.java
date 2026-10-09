package de.familie.mamastelefon;

import android.app.Dialog;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

/**
 * Blendet die Leisten oben (Uhrzeit, Benachrichtigungen) und unten
 * (Zurück, Startseite, App-Übersicht) aus, damit niemand aus Versehen
 * daraufkommt. Wischen vom Bildschirmrand holt sie kurz zurück, nach ein
 * paar Sekunden verschwinden sie von selbst wieder.
 */
final class SystemBars {

    private SystemBars() {
    }

    /**
     * @param hide      Leisten ausblenden (sonst normal zeigen)
     * @param lightBars dunkle Symbole auf hellem Grund (Startseite) statt helle auf schwarzem (Fotos)
     */
    @SuppressWarnings("deprecation")
    static void apply(Window w, boolean hide, boolean lightBars) {
        if (w == null) return;
        try {
            View decor = w.getDecorView();
            if (Build.VERSION.SDK_INT >= 30) {
                WindowInsetsController c = w.getInsetsController();
                if (c == null) c = decor.getWindowInsetsController();
                if (c == null) return;
                int light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                c.setSystemBarsAppearance(lightBars ? light : 0, light);
                if (hide) {
                    c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                    c.hide(WindowInsets.Type.systemBars());
                } else {
                    c.show(WindowInsets.Type.systemBars());
                }
            } else {
                int flags = 0;
                if (lightBars) {
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
                if (hide) {
                    // Inhalt liegt immer unter den Leisten: nichts springt, wenn sie kurz erscheinen
                    flags |= View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
                }
                decor.setSystemUiVisibility(flags);
            }
        } catch (RuntimeException ignored) {
            // Lieber Leisten sichtbar als ein Absturz
        }
    }

    /**
     * Zeigt einen Dialog, ohne dass die Leisten dabei kurz aufblitzen, und
     * hält sie danach ausgeblendet (z. B. nachdem die Tastatur weg ist).
     */
    static void showDialog(Dialog d, boolean hide, boolean lightBars) {
        Window w = d.getWindow();
        if (w == null) {
            d.show();
            return;
        }
        if (!hide) {
            d.show();
            apply(w, false, lightBars);
            return;
        }
        // Erst ohne Fokus anzeigen, Leisten ausblenden, dann Fokus erlauben
        w.setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        apply(w, true, lightBars);
        d.show();
        apply(w, true, lightBars);
        w.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        w.getDecorView().getViewTreeObserver().addOnWindowFocusChangeListener(hasFocus -> {
            if (hasFocus) apply(w, true, lightBars);
        });
    }
}
