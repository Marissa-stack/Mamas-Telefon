package de.familie.mamastelefon;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Farben, Schriftgrößen und kleine Bausteine für die Oberfläche. */
final class Ui {

    static final int BG = 0xFFFFF8EE;        // warmes Weiß
    static final int CARD = 0xFFFFFFFF;
    static final int TEXT = 0xFF1A1A1A;
    static final int MUTED = 0xFF5C5650;
    static final int LINE = 0xFFE4D9C7;
    static final int GREEN = 0xFF1E7B34;
    static final int GREEN_SOFT = 0xFFE3F1E6;
    static final int ORANGE = 0xFFB45309;
    static final int ORANGE_SOFT = 0xFFFDECD3;
    static final int RED = 0xFFB3261E;
    static final int BLUE = 0xFF1F4E8C;
    static final int GREY_BUTTON = 0xFFEDE6DA;

    private static final int[] AVATAR_COLORS = {
            0xFF1F4E8C, 0xFF8C3B1F, 0xFF1E7B34, 0xFF6B3FA0, 0xFF8C6D1F, 0xFF1F7A8C
    };

    private Ui() {
    }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /**
     * Text in fester Größe (dp statt sp), damit eine sehr große
     * Systemschrift das Layout nicht sprengt – groß ist es ohnehin.
     */
    static TextView text(Context c, CharSequence s, float sizeDp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sizeDp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    static GradientDrawable rounded(int color, float radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusPx);
        return g;
    }

    static GradientDrawable card(Context c) {
        GradientDrawable g = rounded(CARD, dp(c, 20));
        g.setStroke(dp(c, 1), LINE);
        return g;
    }

    /** Hintergrund mit sichtbarer Rückmeldung beim Antippen. */
    static Drawable pressable(Drawable shape) {
        return new RippleDrawable(ColorStateList.valueOf(0x33000000), shape, null);
    }

    static Button button(Context c, String label, int bg, int fg, float textDp) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_DIP, textDp);
        b.setTextColor(fg);
        b.setStateListAnimator(null);
        b.setBackground(pressable(rounded(bg, dp(c, 14))));
        int ph = dp(c, 16);
        int pv = dp(c, 10);
        b.setPadding(ph, pv, ph, pv);
        b.setMinHeight(dp(c, 52));
        b.setMinimumHeight(dp(c, 52));
        return b;
    }

    static LinearLayout.LayoutParams fullWidth(Context c, int topMarginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, topMarginDp);
        return lp;
    }

    static LinearLayout vertical(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout horizontal(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    static void clipRounded(View v, final float radiusPx) {
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radiusPx);
            }
        });
        v.setClipToOutline(true);
    }

    /** Ein Rahmen, der immer so hoch wie breit ist. */
    static final class SquareFrame extends FrameLayout {
        SquareFrame(Context c) {
            super(c);
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            super.onMeasure(widthSpec, widthSpec);
        }
    }

    /** Foto des Kontakts, oder ein farbiges Feld mit dem Anfangsbuchstaben. */
    static View avatar(Context c, Store store, Store.Person p, float radiusDp, float letterDp) {
        SquareFrame frame = new SquareFrame(c);
        Bitmap bmp = Photos.load(store, p.photo);
        if (bmp != null) {
            ImageView iv = new ImageView(c);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageBitmap(bmp);
            frame.addView(iv, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            TextView t = text(c, initial(p.name), letterDp, 0xFFFFFFFF, true);
            t.setGravity(Gravity.CENTER);
            frame.addView(t, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            frame.setBackgroundColor(colorFor(p.name));
        }
        clipRounded(frame, dp(c, radiusDp));
        return frame;
    }

    /** Grüner Kreis mit Telefonhörer. */
    static View callCircle(Context c, int sizeDp) {
        FrameLayout f = new FrameLayout(c);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(GREEN);
        f.setBackground(circle);
        ImageView icon = new ImageView(c);
        icon.setImageResource(R.drawable.ic_call);
        int iconSize = dp(c, sizeDp * 0.55f);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER);
        f.addView(icon, lp);
        f.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return f;
    }

    static String initial(String name) {
        String n = name == null ? "" : name.trim();
        return n.isEmpty() ? "?" : n.substring(0, n.offsetByCodePoints(0, 1)).toUpperCase();
    }

    private static int colorFor(String name) {
        int h = name == null ? 0 : name.hashCode();
        return AVATAR_COLORS[Math.abs(h % AVATAR_COLORS.length)];
    }
}
