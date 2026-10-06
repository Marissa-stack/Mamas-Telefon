package de.familie.mamastelefon;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Die Startseite: große Uhr mit Wochentag und Tageszeit,
 * Akku-Hinweis und die Foto-Kacheln zum Anrufen.
 *
 * Einstellungen: 5× schnell auf die Uhr tippen, dann PIN eingeben.
 */
public class HomeActivity extends Activity {

    private static final Locale DE = Locale.GERMANY;
    private static final long DIALOG_TIMEOUT_MS = 30_000;
    private static final long PIN_TIMEOUT_MS = 60_000;
    private static final int REQ_CALL = 21;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Store store;

    private ScrollView scroll;
    private TextView weekdayView;
    private TextView timeView;
    private TextView daypartView;
    private TextView dateView;
    private TextView batteryView;
    private LinearLayout peopleBox;
    private LinearLayout newPhotoBox;
    private LinearLayout allPhotosBox;
    private Dialog openDialog;
    private Store.Person pendingCall;

    private final long[] tapTimes = new long[5];
    private int tapCount;

    private final BroadcastReceiver clockReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateClock();
        }
    };

    private final Runnable refreshPhotos = this::renderPhotos;

    /** Merkt, wenn WhatsApp ein neues Foto gespeichert hat. */
    private final ContentObserver photoObserver = new ContentObserver(handler) {
        @Override
        public void onChange(boolean selfChange) {
            handler.removeCallbacks(refreshPhotos);
            handler.postDelayed(refreshPhotos, 1500);
        }
    };
    private boolean photoObserverOn;

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateBattery(intent);
        }
    };

    // ------------------------------------------------------------ Lebenszyklus

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new Store(this);
        if (android.os.Build.VERSION.SDK_INT >= 27) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
        buildLayout();
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter clock = new IntentFilter();
        clock.addAction(Intent.ACTION_TIME_TICK);
        clock.addAction(Intent.ACTION_TIME_CHANGED);
        clock.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        clock.addAction(Intent.ACTION_DATE_CHANGED);
        registerReceiver(clockReceiver, clock);
        Intent sticky = registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        updateClock();
        updateBattery(sticky);
        renderPeople();
        renderPhotos();
        if (WhatsAppPhotos.canRead(this)) {
            try {
                getContentResolver().registerContentObserver(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, photoObserver);
                photoObserverOn = true;
            } catch (RuntimeException ignored) {
            }
        }
        VolumeGuard.enforce(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(clockReceiver);
        } catch (RuntimeException ignored) {
        }
        try {
            unregisterReceiver(batteryReceiver);
        } catch (RuntimeException ignored) {
        }
        if (photoObserverOn) {
            getContentResolver().unregisterContentObserver(photoObserver);
            photoObserverOn = false;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Bildschirm aus oder andere App vorne: beim nächsten Mal wieder die Startseite zeigen
        closeDialog();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home-Taste gedrückt: offene Fenster schließen, nach oben scrollen
        closeDialog();
        if (scroll != null) scroll.smoothScrollTo(0, 0);
    }

    @Override
    protected void onDestroy() {
        closeDialog();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        // Die Startseite bleibt die Startseite.
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (VolumeGuard.isVolumeKey(event.getKeyCode()) && store.guardOn()) {
            return true; // Lautstärketasten auf der Startseite gesperrt
        }
        return super.dispatchKeyEvent(event);
    }

    // ------------------------------------------------------------------ Aufbau

    private void buildLayout() {
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Ui.BG);

        LinearLayout page = Ui.vertical(this);
        int pad = Ui.dp(this, 16);
        page.setPadding(pad, Ui.dp(this, 8), pad, Ui.dp(this, 24));
        scroll.addView(page, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Uhr
        LinearLayout clock = Ui.vertical(this);
        clock.setGravity(Gravity.CENTER_HORIZONTAL);
        clock.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        weekdayView = Ui.text(this, "", 36, Ui.TEXT, true);
        timeView = Ui.text(this, "", 84, Ui.TEXT, true);
        timeView.setIncludeFontPadding(false);
        daypartView = Ui.text(this, "", 28, Ui.BLUE, true);
        dateView = Ui.text(this, "", 24, Ui.MUTED, false);
        for (TextView t : new TextView[]{weekdayView, timeView, daypartView, dateView}) {
            t.setGravity(Gravity.CENTER_HORIZONTAL);
            clock.addView(t);
        }
        clock.setOnClickListener(v -> onClockTapped());
        page.addView(clock, Ui.fullWidth(this, 0));

        // Akku
        batteryView = Ui.text(this, "", 24, Ui.TEXT, true);
        batteryView.setGravity(Gravity.CENTER);
        int bp = Ui.dp(this, 14);
        batteryView.setPadding(bp, bp, bp, bp);
        batteryView.setVisibility(View.GONE);
        page.addView(batteryView, Ui.fullWidth(this, 8));

        // Neues WhatsApp-Foto (nur in den ersten 24 Stunden)
        newPhotoBox = Ui.vertical(this);
        page.addView(newPhotoBox, Ui.fullWidth(this, 0));

        // Kontakte
        peopleBox = Ui.vertical(this);
        page.addView(peopleBox, Ui.fullWidth(this, 8));

        // Knopf "Fotos ansehen"
        allPhotosBox = Ui.vertical(this);
        page.addView(allPhotosBox, Ui.fullWidth(this, 0));

        setContentView(scroll);
    }

    // -------------------------------------------------------------------- Uhr

    private void updateClock() {
        Calendar now = Calendar.getInstance();
        weekdayView.setText(new SimpleDateFormat("EEEE", DE).format(now.getTime()));
        timeView.setText(new SimpleDateFormat("HH:mm", DE).format(now.getTime()));
        daypartView.setText(daypart(now.get(Calendar.HOUR_OF_DAY)));
        dateView.setText(new SimpleDateFormat("d. MMMM yyyy", DE).format(now.getTime()));
    }

    static String daypart(int hour) {
        // Bewusst "morgens" statt "Morgen" – sonst könnte man an den nächsten Tag denken.
        if (hour >= 5 && hour < 10) return "morgens";
        if (hour >= 10 && hour < 12) return "vormittags";
        if (hour >= 12 && hour < 14) return "mittags";
        if (hour >= 14 && hour < 18) return "nachmittags";
        if (hour >= 18 && hour < 22) return "abends";
        return "nachts";
    }

    /** 5× schnell tippen öffnet die PIN-Abfrage für die Einstellungen. */
    private void onClockTapped() {
        long now = SystemClock.elapsedRealtime();
        tapTimes[tapCount % tapTimes.length] = now;
        tapCount++;
        long oldest = tapTimes[tapCount % tapTimes.length];
        if (tapCount >= tapTimes.length && now - oldest < 3000) {
            tapCount = 0;
            askPin();
        }
    }

    // -------------------------------------------------------------------- Akku

    private void updateBattery(Intent intent) {
        if (intent == null) {
            batteryView.setVisibility(View.GONE);
            return;
        }
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        int pct = (level >= 0 && scale > 0) ? Math.round(level * 100f / scale) : -1;

        if (pct < 0) {
            batteryView.setVisibility(View.GONE);
        } else if (plugged != 0) {
            batteryView.setText("Wird aufgeladen · " + pct + " %");
            batteryView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 22);
            batteryView.setTextColor(Ui.GREEN);
            batteryView.setBackground(Ui.rounded(Ui.GREEN_SOFT, Ui.dp(this, 16)));
            batteryView.setVisibility(View.VISIBLE);
        } else if (pct <= 20) {
            boolean critical = pct <= 10;
            batteryView.setText("Akku fast leer (" + pct + " %)\nBitte das Telefon jetzt aufladen.");
            batteryView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 26);
            batteryView.setTextColor(0xFFFFFFFF);
            batteryView.setBackground(Ui.rounded(critical ? Ui.RED : Ui.ORANGE, Ui.dp(this, 16)));
            batteryView.setVisibility(View.VISIBLE);
        } else {
            batteryView.setVisibility(View.GONE);
        }
    }

    // ---------------------------------------------------------------- Kontakte

    private void renderPeople() {
        peopleBox.removeAllViews();

        if (!store.hasPin()) {
            renderWelcome();
            return;
        }

        List<Store.Person> people = store.people();
        if (people.isEmpty()) {
            TextView empty = Ui.text(this, "Noch keine Kontakte eingerichtet.", 22, Ui.MUTED, false);
            empty.setGravity(Gravity.CENTER);
            peopleBox.addView(empty, Ui.fullWidth(this, 24));
            return;
        }

        if (people.size() <= 3) {
            for (Store.Person p : people) {
                peopleBox.addView(wideTile(p), Ui.fullWidth(this, 12));
            }
        } else {
            for (int i = 0; i < people.size(); i += 2) {
                LinearLayout row = Ui.horizontal(this);
                row.setGravity(Gravity.TOP);
                row.addView(gridTile(people.get(i)), gridCell());
                if (i + 1 < people.size()) {
                    row.addView(gridTile(people.get(i + 1)), gridCell());
                } else {
                    View spacer = new View(this);
                    spacer.setVisibility(View.INVISIBLE);
                    row.addView(spacer, gridCell());
                }
                peopleBox.addView(row, Ui.fullWidth(this, 4));
            }
        }
    }

    private void renderWelcome() {
        LinearLayout box = Ui.vertical(this);
        box.setBackground(Ui.card(this));
        int p = Ui.dp(this, 20);
        box.setPadding(p, p, p, p);
        box.addView(Ui.text(this, "Willkommen!", 28, Ui.TEXT, true));
        TextView info = Ui.text(this,
                "Bevor es losgeht: Lege eine PIN fest und füge die Kontakte hinzu.", 20, Ui.MUTED, false);
        box.addView(info, Ui.fullWidth(this, 8));
        Button start = Ui.button(this, "Einrichtung starten", Ui.GREEN, 0xFFFFFFFF, 22);
        start.setOnClickListener(v -> openSettings());
        box.addView(start, Ui.fullWidth(this, 16));
        peopleBox.addView(box, Ui.fullWidth(this, 16));
    }

    /** Breite Kachel (bei bis zu 3 Kontakten): Foto links, Name, grüner Hörer. */
    private View wideTile(Store.Person p) {
        LinearLayout tile = Ui.horizontal(this);
        tile.setBackground(Ui.pressable(Ui.card(this)));
        int pad = Ui.dp(this, 12);
        tile.setPadding(pad, pad, pad, pad);

        View photo = Ui.avatar(this, store, p, 16, 52);
        tile.addView(photo, new LinearLayout.LayoutParams(Ui.dp(this, 112), Ui.dp(this, 112)));

        TextView name = Ui.text(this, p.name, 30, Ui.TEXT, true);
        name.setMaxLines(3);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nameLp.leftMargin = Ui.dp(this, 16);
        nameLp.rightMargin = Ui.dp(this, 8);
        tile.addView(name, nameLp);

        tile.addView(Ui.callCircle(this, 68));

        tile.setOnClickListener(v -> onPersonTapped(p));
        tile.setContentDescription(p.name + " anrufen");
        return tile;
    }

    /** Kachel im Raster (ab 4 Kontakten): Foto oben, Name, grüner Balken "Anrufen". */
    private View gridTile(Store.Person p) {
        LinearLayout tile = Ui.vertical(this);
        tile.setBackground(Ui.pressable(Ui.card(this)));
        int pad = Ui.dp(this, 10);
        tile.setPadding(pad, pad, pad, pad);

        tile.addView(Ui.avatar(this, store, p, 14, 64), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView name = Ui.text(this, p.name, 24, Ui.TEXT, true);
        name.setGravity(Gravity.CENTER_HORIZONTAL);
        name.setMaxLines(2);
        tile.addView(name, Ui.fullWidth(this, 8));

        LinearLayout bar = Ui.horizontal(this);
        bar.setGravity(Gravity.CENTER);
        bar.setBackground(Ui.rounded(Ui.GREEN, Ui.dp(this, 12)));
        android.widget.ImageView icon = new android.widget.ImageView(this);
        icon.setImageResource(R.drawable.ic_call);
        bar.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 26), Ui.dp(this, 26)));
        TextView label = Ui.text(this, "Anrufen", 20, 0xFFFFFFFF, true);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelLp.leftMargin = Ui.dp(this, 8);
        bar.addView(label, labelLp);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 52));
        barLp.topMargin = Ui.dp(this, 8);
        tile.addView(bar, barLp);

        tile.setOnClickListener(v -> onPersonTapped(p));
        tile.setContentDescription(p.name + " anrufen");
        return tile;
    }

    private LinearLayout.LayoutParams gridCell() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        int m = Ui.dp(this, 6);
        lp.setMargins(m, m, m, m);
        return lp;
    }

    // ------------------------------------------------------------------ Fotos

    private void renderPhotos() {
        newPhotoBox.removeAllViews();
        allPhotosBox.removeAllViews();
        if (!store.hasPin() || !store.photosOn()) return;
        List<WhatsAppPhotos.Item> items = WhatsAppPhotos.recent(this, 1);
        if (items.isEmpty()) return;
        WhatsAppPhotos.Item newest = items.get(0);

        if (WhatsAppPhotos.isNew(newest)) {
            LinearLayout tile = Ui.vertical(this);
            tile.setBackground(Ui.pressable(Ui.card(this)));
            int pad = Ui.dp(this, 12);
            tile.setPadding(pad, pad, pad, pad);

            LinearLayout head = Ui.horizontal(this);
            head.addView(Ui.text(this, "Neues Foto", 26, Ui.GREEN, true),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            head.addView(Ui.text(this, whenReceived(newest.receivedMs), 20, Ui.MUTED, false));
            tile.addView(head);

            ImageView img = new ImageView(this);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Bitmap bmp = Photos.decode(this, newest.uri, 900);
            if (bmp != null) img.setImageBitmap(bmp);
            Ui.clipRounded(img, Ui.dp(this, 14));
            LinearLayout.LayoutParams imgLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 210));
            imgLp.topMargin = Ui.dp(this, 10);
            tile.addView(img, imgLp);

            tile.setOnClickListener(v -> showPhotos());
            tile.setContentDescription("Neues Foto ansehen");
            newPhotoBox.addView(tile, Ui.fullWidth(this, 12));
        }

        Button all = Ui.button(this, "Fotos ansehen", Ui.GREY_BUTTON, Ui.TEXT, 24);
        all.setOnClickListener(v -> showPhotos());
        LinearLayout.LayoutParams allLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 68));
        allLp.topMargin = Ui.dp(this, 18);
        allPhotosBox.addView(all, allLp);
    }

    /** "heute, 14:20", "gestern, 18:05" oder "Dienstag, 6. Oktober". */
    private static String whenReceived(long ms) {
        Calendar then = Calendar.getInstance();
        then.setTimeInMillis(ms);
        Calendar now = Calendar.getInstance();
        String time = new SimpleDateFormat("HH:mm", DE).format(then.getTime());
        if (sameDay(then, now)) return "heute, " + time;
        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        if (sameDay(then, yesterday)) return "gestern, " + time;
        return new SimpleDateFormat("EEEE, d. MMMM", DE).format(then.getTime());
    }

    private static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    /** Ganzseitige Foto-Ansicht mit großen Knöpfen statt Wischen. */
    private void showPhotos() {
        final List<WhatsAppPhotos.Item> items = WhatsAppPhotos.recent(this, 30);
        if (items.isEmpty()) {
            Toast.makeText(this, "Noch keine Fotos", Toast.LENGTH_LONG).show();
            return;
        }
        closeDialog();
        Dialog d = new Dialog(this, R.style.AppTheme);

        LinearLayout box = Ui.vertical(this);
        box.setBackgroundColor(0xFF000000);
        int pad = Ui.dp(this, 12);
        box.setPadding(pad, pad, pad, pad);

        Button close = Ui.button(this, "Zurück zur Startseite", Ui.GREY_BUTTON, Ui.TEXT, 24);
        close.setOnClickListener(v -> closeDialog());
        box.addView(close, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 68)));

        ImageView img = new ImageView(this);
        img.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams imgLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        imgLp.topMargin = Ui.dp(this, 12);
        imgLp.bottomMargin = Ui.dp(this, 8);
        box.addView(img, imgLp);

        TextView caption = Ui.text(this, "", 22, 0xFFFFFFFF, true);
        caption.setGravity(Gravity.CENTER);
        box.addView(caption, Ui.fullWidth(this, 0));

        LinearLayout nav = Ui.horizontal(this);
        Button prev = Ui.button(this, "◀ Vorheriges", 0xFF3A3A3A, 0xFFFFFFFF, 22);
        Button next = Ui.button(this, "Nächstes ▶", 0xFF3A3A3A, 0xFFFFFFFF, 22);
        LinearLayout.LayoutParams prevLp = new LinearLayout.LayoutParams(0, Ui.dp(this, 76), 1f);
        prevLp.rightMargin = Ui.dp(this, 10);
        nav.addView(prev, prevLp);
        nav.addView(next, new LinearLayout.LayoutParams(0, Ui.dp(this, 76), 1f));
        box.addView(nav, Ui.fullWidth(this, 12));

        final int[] pos = {0};
        final Runnable show = () -> {
            WhatsAppPhotos.Item it = items.get(pos[0]);
            Bitmap bmp = Photos.decode(this, it.uri, 1400);
            img.setImageBitmap(bmp);
            caption.setText(bmp != null ? "Bekommen: " + whenReceived(it.receivedMs)
                    : "Dieses Foto lässt sich nicht öffnen");
            prev.setEnabled(pos[0] > 0);
            prev.setAlpha(pos[0] > 0 ? 1f : 0.3f);
            next.setEnabled(pos[0] < items.size() - 1);
            next.setAlpha(pos[0] < items.size() - 1 ? 1f : 0.3f);
        };
        prev.setOnClickListener(v -> {
            if (pos[0] > 0) {
                pos[0]--;
                show.run();
            }
        });
        next.setOnClickListener(v -> {
            if (pos[0] < items.size() - 1) {
                pos[0]++;
                show.run();
            }
        });

        d.setContentView(box);
        if (d.getWindow() != null) {
            d.getWindow().setStatusBarColor(0xFF000000);
            d.getWindow().setNavigationBarColor(0xFF000000);
            d.getWindow().getDecorView().setSystemUiVisibility(0);
        }
        blockVolumeKeys(d);
        show.run();
        showDialog(d);
    }

    // ----------------------------------------------------------------- Anrufen

    private void onPersonTapped(Store.Person p) {
        if (store.confirmCall()) {
            showConfirm(p);
        } else {
            call(p);
        }
    }

    /** Ganzseitige Nachfrage: großes Foto, "Inge anrufen?", Ja / Nein. */
    private void showConfirm(Store.Person p) {
        closeDialog();
        // Eigenes App-Design: füllt den ganzen Bildschirm, gleiche Farben wie die Startseite
        Dialog d = new Dialog(this, R.style.AppTheme);

        LinearLayout box = Ui.vertical(this);
        box.setBackgroundColor(Ui.BG);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = Ui.dp(this, 24);
        box.setPadding(pad, pad, pad, pad);

        View photo = Ui.avatar(this, store, p, 24, 96);
        LinearLayout.LayoutParams photoLp = new LinearLayout.LayoutParams(Ui.dp(this, 230), Ui.dp(this, 230));
        photoLp.topMargin = Ui.dp(this, 16);
        box.addView(photo, photoLp);

        TextView question = Ui.text(this, p.name + "\nanrufen?", 36, Ui.TEXT, true);
        question.setGravity(Gravity.CENTER);
        box.addView(question, Ui.fullWidth(this, 20));

        Button yes = Ui.button(this, "Ja, anrufen", Ui.GREEN, 0xFFFFFFFF, 30);
        yes.setOnClickListener(v -> {
            closeDialog();
            call(p);
        });
        LinearLayout.LayoutParams yesLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 84));
        yesLp.topMargin = Ui.dp(this, 28);
        box.addView(yes, yesLp);

        Button no = Ui.button(this, "Nein, zurück", Ui.GREY_BUTTON, Ui.TEXT, 26);
        no.setOnClickListener(v -> closeDialog());
        LinearLayout.LayoutParams noLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 72));
        noLp.topMargin = Ui.dp(this, 16);
        box.addView(no, noLp);

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackgroundColor(Ui.BG);
        sv.addView(box);
        d.setContentView(sv);
        blockVolumeKeys(d);
        showDialog(d);
        handler.postDelayed(() -> {
            if (openDialog == d) closeDialog();
        }, DIALOG_TIMEOUT_MS);
    }

    private void call(Store.Person p) {
        if (p.number == null || p.number.trim().isEmpty()) {
            Toast.makeText(this, "Keine Telefonnummer gespeichert", Toast.LENGTH_LONG).show();
            return;
        }
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            // Erlaubnis fehlt noch: jetzt nachfragen statt nur das Tastenfeld zu öffnen
            pendingCall = p;
            requestPermissions(new String[]{Manifest.permission.CALL_PHONE}, REQ_CALL);
            return;
        }
        placeCall(p, true);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != REQ_CALL || pendingCall == null) return;
        Store.Person p = pendingCall;
        pendingCall = null;
        boolean granted = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
        placeCall(p, granted);
    }

    private void placeCall(Store.Person p, boolean direct) {
        Uri uri = Uri.fromParts("tel", p.number.trim(), null);
        Intent i = new Intent(direct ? Intent.ACTION_CALL : Intent.ACTION_DIAL, uri);
        try {
            startActivity(i);
        } catch (RuntimeException e) {
            try {
                startActivity(new Intent(Intent.ACTION_DIAL, uri));
            } catch (RuntimeException e2) {
                Toast.makeText(this, "Anruf nicht möglich", Toast.LENGTH_LONG).show();
            }
        }
    }

    // ------------------------------------------------------------ Einstellungen

    private void askPin() {
        if (!store.hasPin()) {
            openSettings();
            return;
        }
        closeDialog();
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 28);
        input.setGravity(Gravity.CENTER);
        input.setHint("PIN");
        LinearLayout wrap = Ui.vertical(this);
        int p = Ui.dp(this, 20);
        wrap.setPadding(p, Ui.dp(this, 8), p, 0);
        wrap.addView(input, Ui.fullWidth(this, 0));

        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("Einstellungen")
                .setView(wrap)
                .setPositiveButton("Öffnen", (di, w) -> {
                    if (store.checkPin(input.getText().toString())) {
                        openSettings();
                    } else {
                        Toast.makeText(this, "Falsche PIN", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Abbrechen", null)
                .create();
        blockVolumeKeys(d);
        showDialog(d);
        handler.postDelayed(() -> {
            if (openDialog == d) closeDialog();
        }, PIN_TIMEOUT_MS);
    }

    private void openSettings() {
        closeDialog();
        startActivity(new Intent(this, SettingsActivity.class));
    }

    // -------------------------------------------------------------- Dialoge

    private void blockVolumeKeys(Dialog d) {
        d.setOnKeyListener((di, keyCode, event) ->
                VolumeGuard.isVolumeKey(keyCode) && store.guardOn());
    }

    private void showDialog(Dialog d) {
        openDialog = d;
        d.setOnDismissListener(di -> {
            if (openDialog == d) openDialog = null;
        });
        d.show();
    }

    private void closeDialog() {
        if (openDialog != null) {
            Dialog d = openDialog;
            openDialog = null;
            try {
                d.dismiss();
            } catch (RuntimeException ignored) {
            }
        }
    }
}
