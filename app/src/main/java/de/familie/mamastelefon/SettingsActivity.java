package de.familie.mamastelefon;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * Einstellungen – nur für die Person, die das Telefon einrichtet.
 * Geschützt durch eine PIN. Die Home-Taste schließt sie automatisch.
 */
public class SettingsActivity extends Activity {

    private static final int REQ_CONTACT = 11;
    private static final int REQ_PHOTO = 12;
    private static final int REQ_PERMS = 13;
    private static final int REQ_PERMS_THEN_ADD = 14;
    private static final long IDLE_CLOSE_MS = 10 * 60_000;
    private static final int WHITE = 0xFFFFFFFF;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Store store;
    private LinearLayout page;
    private String photoForId;
    private Ringtone preview;
    private long pausedAt;

    private interface IntSink {
        void accept(int value);
    }

    private interface BoolSink {
        void accept(boolean value);
    }

    // ------------------------------------------------------------ Lebenszyklus

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new Store(this);
        if (savedInstanceState != null) photoForId = savedInstanceState.getString("photoFor");
        if (Build.VERSION.SDK_INT >= 27) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        page = Ui.vertical(this);
        int pad = dp(16);
        page.setPadding(pad, pad, pad, dp(40));
        scroll.addView(page, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);

        if (!store.hasPin()) askNewPin(true);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("photoFor", photoForId);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Lange liegen gelassen? Dann sicherheitshalber schließen.
        if (pausedAt > 0 && SystemClock.elapsedRealtime() - pausedAt > IDLE_CLOSE_MS) {
            finish();
            return;
        }
        pausedAt = 0;
        render();
    }

    @Override
    protected void onPause() {
        super.onPause();
        pausedAt = SystemClock.elapsedRealtime();
        stopPreview();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        stopPreview();
        super.onDestroy();
    }

    // ----------------------------------------------------------------- Aufbau

    private void render() {
        page.removeAllViews();

        LinearLayout head = Ui.horizontal(this);
        head.addView(Ui.text(this, "Einstellungen", 30, Ui.TEXT, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button done = Ui.button(this, "Fertig", Ui.GREEN, WHITE, 20);
        done.setOnClickListener(v -> finish());
        head.addView(done);
        page.addView(head, Ui.fullWidth(this, 0));

        renderSetup();
        renderPeople();
        renderVolume();
        renderCalls();
        renderMore();
    }

    private LinearLayout section(String title, String subtitle) {
        page.addView(Ui.text(this, title, 22, Ui.TEXT, true), Ui.fullWidth(this, 28));
        if (subtitle != null) {
            page.addView(Ui.text(this, subtitle, 17, Ui.MUTED, false), Ui.fullWidth(this, 4));
        }
        LinearLayout card = Ui.vertical(this);
        card.setBackground(Ui.card(this));
        int p = dp(16);
        card.setPadding(p, dp(6), p, p);
        page.addView(card, Ui.fullWidth(this, 10));
        return card;
    }

    // ------------------------------------------------------------- Einrichtung

    private void renderSetup() {
        LinearLayout card = section("1. Einrichtung",
                "Alles grün? Dann ist das Telefon fertig eingerichtet.");
        statusRow(card, "Als Startseite festgelegt", isDefaultHome(), "Festlegen", this::chooseHome);
        statusRow(card, "Darf direkt anrufen", granted(Manifest.permission.CALL_PHONE),
                "Erlauben", this::requestPerms);
        statusRow(card, "Darf Kontakte und Fotos übernehmen", granted(Manifest.permission.READ_CONTACTS),
                "Erlauben", this::requestPerms);
        statusRow(card, "Lautstärke-Schutz eingeschaltet", isGuardServiceEnabled(),
                "Einschalten", this::explainAccessibility);
        statusRow(card, "Darf „Nicht stören“ abschalten", dndGranted(),
                "Erlauben", this::explainDnd);
        card.addView(Ui.text(this,
                "Die Einstellungen öffnest du später, indem du auf der Startseite 5× schnell auf die Uhr tippst.",
                17, Ui.MUTED, false), Ui.fullWidth(this, 14));
    }

    private void statusRow(LinearLayout card, String label, boolean ok, String action, Runnable fix) {
        LinearLayout row = Ui.horizontal(this);

        TextView mark = Ui.text(this, ok ? "✓" : "!", 20, WHITE, true);
        mark.setGravity(Gravity.CENTER);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(ok ? Ui.GREEN : Ui.ORANGE);
        mark.setBackground(circle);
        row.addView(mark, new LinearLayout.LayoutParams(dp(32), dp(32)));

        TextView t = Ui.text(this, label, 18, Ui.TEXT, false);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tl.leftMargin = dp(12);
        tl.rightMargin = dp(8);
        row.addView(t, tl);

        if (!ok) {
            Button b = Ui.button(this, action, Ui.GREEN, WHITE, 16);
            b.setMinWidth(0);
            b.setMinimumWidth(0);
            b.setOnClickListener(v -> fix.run());
            row.addView(b);
        }
        card.addView(row, Ui.fullWidth(this, 10));
    }

    private boolean granted(String permission) {
        return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean isDefaultHome() {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_HOME);
        ResolveInfo ri = getPackageManager().resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY);
        return ri != null && ri.activityInfo != null
                && getPackageName().equals(ri.activityInfo.packageName);
    }

    private boolean isGuardServiceEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        ComponentName me = new ComponentName(this, VolumeGuardService.class);
        for (String s : enabled.split(":")) {
            ComponentName cn = ComponentName.unflattenFromString(s);
            if (me.equals(cn)) return true;
        }
        return false;
    }

    private boolean dndGranted() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        return nm != null && nm.isNotificationPolicyAccessGranted();
    }

    private void chooseHome() {
        info("Startseite festlegen",
                "Gleich öffnet sich die Auswahl der Startseite (heißt je nach Handy auch "
                        + "„Start-App“ oder „Startbildschirm“). Wähle dort „Mamas Telefon“.\n\n"
                        + "Fragt das Handy stattdessen beim Drücken der Home-Taste nach: "
                        + "„Mamas Telefon“ und „Immer“ wählen.",
                () -> openFirst(Settings.ACTION_HOME_SETTINGS,
                        Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS,
                        Settings.ACTION_SETTINGS),
                null, null);
    }

    private void requestPerms() {
        requestPermissions(new String[]{
                Manifest.permission.CALL_PHONE, Manifest.permission.READ_CONTACTS}, REQ_PERMS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_PERMS_THEN_ADD) {
            pickContact();
            return;
        }
        boolean blocked = false;
        for (int i = 0; i < permissions.length && i < results.length; i++) {
            if (results[i] != PackageManager.PERMISSION_GRANTED
                    && !shouldShowRequestPermissionRationale(permissions[i])) {
                blocked = true;
            }
        }
        if (blocked) {
            info("Berechtigung fehlt",
                    "Android fragt nicht mehr von selbst nach. Bitte in den App-Infos unter "
                            + "„Berechtigungen“ Telefon und Kontakte erlauben.",
                    this::openAppDetails, null, null);
        }
        render();
    }

    private void explainAccessibility() {
        StringBuilder sb = new StringBuilder();
        sb.append("Gleich öffnen sich die Bedienungshilfen.\n\n");
        sb.append("1. Auf „Lautstärke-Schutz“ tippen (je nach Handy unter „Installierte Apps“, ");
        sb.append("„Heruntergeladene Apps“ oder „Installierte Dienste“).\n");
        sb.append("2. Den Schalter einschalten und bestätigen.\n\n");
        sb.append("Android warnt dabei, die App könne das Gerät steuern. Diesen Hinweis zeigt Android ");
        sb.append("bei jeder solchen Funktion. Diese App liest keine Bildschirminhalte, sie sperrt nur ");
        sb.append("die Lautstärketasten.");
        String neutralLabel = null;
        Runnable neutral = null;
        if (Build.VERSION.SDK_INT >= 33) {
            sb.append("\n\nIst der Schalter grau oder erscheint „Eingeschränkte Einstellung“? Dann in den ");
            sb.append("App-Infos oben rechts auf ⋮ tippen → „Eingeschränkte Einstellungen zulassen“ ");
            sb.append("und es noch einmal versuchen.");
            neutralLabel = "App-Infos";
            neutral = this::openAppDetails;
        }
        info("Lautstärke-Schutz einschalten", sb.toString(),
                () -> openFirst(Settings.ACTION_ACCESSIBILITY_SETTINGS, Settings.ACTION_SETTINGS),
                neutralLabel, neutral);
    }

    private void explainDnd() {
        info("„Nicht stören“ erlauben",
                "Gleich öffnet sich eine Liste. Dort auf „Mamas Telefon“ tippen und den Zugriff erlauben.\n\n"
                        + "Dann kann die App „Nicht stören“ und „Lautlos“ wieder abschalten, falls es "
                        + "versehentlich eingeschaltet wird. Sonst kämen keine Anrufe durch.",
                () -> openFirst(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS, Settings.ACTION_SETTINGS),
                null, null);
    }

    // ---------------------------------------------------------------- Kontakte

    private void renderPeople() {
        LinearLayout card = section("2. Kontakte auf der Startseite",
                "Bis zu 3 Kontakte erscheinen als große Zeilen, ab 4 als Raster mit zwei Spalten.");
        List<Store.Person> people = store.people();
        if (people.isEmpty()) {
            card.addView(Ui.text(this, "Noch keine Kontakte.", 18, Ui.MUTED, false), Ui.fullWidth(this, 10));
        }
        for (int i = 0; i < people.size(); i++) {
            if (i > 0) {
                View line = new View(this);
                line.setBackgroundColor(Ui.LINE);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
                lp.topMargin = dp(14);
                card.addView(line, lp);
            }
            card.addView(personRow(people, i), Ui.fullWidth(this, 12));
        }
        Button add = Ui.button(this, "+ Kontakt hinzufügen", Ui.GREEN, WHITE, 20);
        add.setOnClickListener(v -> addContact());
        card.addView(add, Ui.fullWidth(this, 18));
    }

    private View personRow(List<Store.Person> people, int index) {
        Store.Person p = people.get(index);
        LinearLayout box = Ui.vertical(this);

        LinearLayout top = Ui.horizontal(this);
        top.addView(Ui.avatar(this, store, p, 10, 28), new LinearLayout.LayoutParams(dp(64), dp(64)));
        LinearLayout texts = Ui.vertical(this);
        texts.addView(Ui.text(this, p.name, 20, Ui.TEXT, true));
        texts.addView(Ui.text(this, p.number, 16, Ui.MUTED, false));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tl.leftMargin = dp(12);
        top.addView(texts, tl);
        top.addView(smallButton("▲", index > 0, () -> move(index, -1)), squareLp());
        top.addView(smallButton("▼", index < people.size() - 1, () -> move(index, 1)), squareLp());
        box.addView(top);

        LinearLayout actions = Ui.horizontal(this);
        actions.addView(smallButton("Foto", true, () -> pickPhoto(p.id)), weightLp(false));
        actions.addView(smallButton("Ändern", true, () -> editPerson(p.id, false)), weightLp(false));
        actions.addView(smallButton("Löschen", true, () -> deletePerson(p.id)), weightLp(true));
        box.addView(actions, Ui.fullWidth(this, 10));
        return box;
    }

    private Button smallButton(String label, boolean enabled, Runnable action) {
        Button b = Ui.button(this, label, Ui.GREY_BUTTON, Ui.TEXT, 16);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(dp(44));
        b.setMinimumHeight(dp(44));
        b.setPadding(dp(8), dp(4), dp(8), dp(4));
        b.setEnabled(enabled);
        b.setAlpha(enabled ? 1f : 0.3f);
        b.setOnClickListener(v -> action.run());
        return b;
    }

    private LinearLayout.LayoutParams squareLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(44), dp(44));
        lp.leftMargin = dp(6);
        return lp;
    }

    private LinearLayout.LayoutParams weightLp(boolean last) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        if (!last) lp.rightMargin = dp(8);
        return lp;
    }

    private void move(int index, int delta) {
        List<Store.Person> list = store.people();
        int other = index + delta;
        if (other < 0 || other >= list.size()) return;
        Store.Person a = list.get(index);
        list.set(index, list.get(other));
        list.set(other, a);
        store.savePeople(list);
        render();
    }

    private void addContact() {
        if (!granted(Manifest.permission.READ_CONTACTS)) {
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQ_PERMS_THEN_ADD);
            return;
        }
        pickContact();
    }

    private void pickContact() {
        try {
            startActivityForResult(new Intent(Intent.ACTION_PICK,
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI), REQ_CONTACT);
        } catch (RuntimeException e) {
            toast("Die Kontakte-App lässt sich nicht öffnen");
        }
    }

    private void pickPhoto(String personId) {
        photoForId = personId;
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(i, "Foto auswählen"), REQ_PHOTO);
        } catch (RuntimeException e) {
            toast("Die Galerie lässt sich nicht öffnen");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        if (requestCode == REQ_CONTACT) {
            onContactPicked(data.getData());
        } else if (requestCode == REQ_PHOTO) {
            onPhotoPicked(data.getData());
        }
    }

    private void onContactPicked(Uri uri) {
        String name = null;
        String number = null;
        String photoUri = null;
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                name = column(c, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                number = column(c, ContactsContract.CommonDataKinds.Phone.NUMBER);
                photoUri = column(c, ContactsContract.CommonDataKinds.Phone.PHOTO_URI);
            }
        } catch (RuntimeException ignored) {
        }
        if (number == null || number.trim().isEmpty()) {
            toast("Dieser Kontakt hat keine Telefonnummer");
            return;
        }
        String photo = null;
        if (photoUri != null) {
            photo = Photos.importPhoto(this, Uri.parse(photoUri), store);
        }
        Store.Person p = Store.Person.create(name != null ? name : number, number, photo);
        List<Store.Person> list = store.people();
        list.add(p);
        store.savePeople(list);
        render();
        editPerson(p.id, true);
    }

    private static String column(Cursor c, String name) {
        int i = c.getColumnIndex(name);
        return i >= 0 ? c.getString(i) : null;
    }

    private void onPhotoPicked(Uri uri) {
        if (photoForId == null) return;
        String file = Photos.importPhoto(this, uri, store);
        if (file == null) {
            toast("Das Foto konnte nicht geladen werden");
            return;
        }
        List<Store.Person> list = store.people();
        for (Store.Person p : list) {
            if (p.id.equals(photoForId)) {
                store.deletePhoto(p.photo);
                p.photo = file;
            }
        }
        store.savePeople(list);
        photoForId = null;
        render();
    }

    private void editPerson(String personId, boolean isNew) {
        Store.Person found = null;
        for (Store.Person p : store.people()) {
            if (p.id.equals(personId)) found = p;
        }
        if (found == null) return;
        final Store.Person person = found;

        LinearLayout wrap = Ui.vertical(this);
        int pad = dp(20);
        wrap.setPadding(pad, dp(8), pad, 0);
        wrap.addView(Ui.text(this,
                "So steht der Name auf der Startseite. Hilfreich ist die Beziehung dazu, "
                        + "z. B. „Anna (Tochter)“.", 16, Ui.MUTED, false));
        EditText name = new EditText(this);
        name.setText(person.name);
        name.setHint("Name");
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        wrap.addView(name, Ui.fullWidth(this, 10));
        EditText number = new EditText(this);
        number.setText(person.number);
        number.setHint("Telefonnummer");
        number.setInputType(InputType.TYPE_CLASS_PHONE);
        number.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        wrap.addView(number, Ui.fullWidth(this, 6));

        new AlertDialog.Builder(this)
                .setTitle(isNew ? "Wie soll der Kontakt heißen?" : "Kontakt ändern")
                .setView(wrap)
                .setPositiveButton("Speichern", (d, w) -> {
                    String n = name.getText().toString().trim();
                    String nr = number.getText().toString().trim();
                    List<Store.Person> list = store.people();
                    for (Store.Person p : list) {
                        if (p.id.equals(person.id)) {
                            if (!n.isEmpty()) p.name = n;
                            if (!nr.isEmpty()) p.number = nr;
                        }
                    }
                    store.savePeople(list);
                    render();
                })
                .setNegativeButton("Abbrechen", null)
                .show();
    }

    private void deletePerson(String personId) {
        Store.Person found = null;
        for (Store.Person p : store.people()) {
            if (p.id.equals(personId)) found = p;
        }
        if (found == null) return;
        final Store.Person person = found;
        new AlertDialog.Builder(this)
                .setTitle("Kontakt entfernen?")
                .setMessage("„" + person.name + "“ von der Startseite entfernen? "
                        + "Im Telefonbuch bleibt der Kontakt erhalten.")
                .setPositiveButton("Entfernen", (d, w) -> {
                    List<Store.Person> list = store.people();
                    for (int i = list.size() - 1; i >= 0; i--) {
                        if (list.get(i).id.equals(person.id)) {
                            store.deletePhoto(list.get(i).photo);
                            list.remove(i);
                        }
                    }
                    store.savePeople(list);
                    render();
                })
                .setNegativeButton("Abbrechen", null)
                .show();
    }

    // ------------------------------------------------------------- Lautstärke

    private void renderVolume() {
        LinearLayout card = section("3. Lautstärke-Schutz",
                "Die Lautstärketasten sind gesperrt. Wird trotzdem etwas verstellt (z. B. über das "
                        + "Schnellmenü oder „Lautlos“), stellt die App es sofort zurück.");
        card.addView(makeSwitch("Schutz eingeschaltet", store.guardOn(), on -> {
            store.setGuardOn(on);
            if (on) VolumeGuard.enforce(this);
        }), Ui.fullWidth(this, 6));

        AudioManager am = getSystemService(AudioManager.class);
        if (am == null) return;
        volumeSlider(card, am, AudioManager.STREAM_RING, "Klingelton",
                store::setRingLevel, true);
        volumeSlider(card, am, AudioManager.STREAM_VOICE_CALL, "Lautstärke beim Telefonieren",
                store::setCallLevel, false);
    }

    private void volumeSlider(LinearLayout card, AudioManager am, int stream, String title,
                              IntSink save, boolean withPreview) {
        final int min = VolumeGuard.minLevel(am, stream);
        final int max = am.getStreamMaxVolume(stream);
        int current = VolumeGuard.lockedLevel(store, am, stream);

        TextView label = Ui.text(this, levelText(title, current, max), 18, Ui.TEXT, true);
        card.addView(label, Ui.fullWidth(this, 18));

        SeekBar bar = new SeekBar(this);
        bar.setMax(Math.max(0, max - min));
        bar.setProgress(current - min);
        bar.setPadding(dp(16), dp(14), dp(16), dp(14));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                label.setText(levelText(title, progress + min, max));
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                int level = s.getProgress() + min;
                save.accept(level);
                try {
                    am.setStreamVolume(stream, level, 0);
                } catch (RuntimeException ignored) {
                }
                VolumeGuard.enforce(SettingsActivity.this);
                if (withPreview) previewRing();
            }
        });
        card.addView(bar, Ui.fullWidth(this, 2));

        if (withPreview) {
            Button listen = smallButton("Klingelton probehören", true, this::previewRing);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(44));
            lp.topMargin = dp(2);
            listen.setPadding(dp(14), dp(4), dp(14), dp(4));
            card.addView(listen, lp);
        }
    }

    private static String levelText(String title, int level, int max) {
        return title + ": Stufe " + level + " von " + max;
    }

    private void previewRing() {
        stopPreview();
        Uri uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE);
        if (uri == null) uri = Settings.System.DEFAULT_RINGTONE_URI;
        try {
            preview = RingtoneManager.getRingtone(this, uri);
            if (preview == null) return;
            preview.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            preview.play();
            handler.postDelayed(this::stopPreview, 4000);
        } catch (RuntimeException e) {
            toast("Klingelton kann nicht abgespielt werden");
        }
    }

    private void stopPreview() {
        handler.removeCallbacksAndMessages(null);
        if (preview != null) {
            try {
                preview.stop();
            } catch (RuntimeException ignored) {
            }
            preview = null;
        }
    }

    // ----------------------------------------------------------------- Anrufe

    private void renderCalls() {
        LinearLayout card = section("4. Anrufe", null);
        card.addView(makeSwitch("Vor jedem Anruf nachfragen („… anrufen? Ja / Nein“)",
                store.confirmCall(), store::setConfirmCall), Ui.fullWidth(this, 6));
    }

    // --------------------------------------------------------------- Sonstiges

    private void renderMore() {
        LinearLayout card = section("5. Sonstiges", null);

        Button pin = Ui.button(this, "PIN ändern", Ui.GREY_BUTTON, Ui.TEXT, 18);
        pin.setOnClickListener(v -> askNewPin(false));
        card.addView(pin, Ui.fullWidth(this, 10));

        Button sys = Ui.button(this, "Handy-Einstellungen öffnen", Ui.GREY_BUTTON, Ui.TEXT, 18);
        sys.setOnClickListener(v -> openFirst(Settings.ACTION_SETTINGS));
        card.addView(sys, Ui.fullWidth(this, 10));

        Button home = Ui.button(this, "Andere Startseite wählen", Ui.GREY_BUTTON, Ui.TEXT, 18);
        home.setOnClickListener(v -> chooseHome());
        card.addView(home, Ui.fullWidth(this, 10));

        TextView version = Ui.text(this, "Mamas Telefon · Version " + version(), 15, Ui.MUTED, false);
        version.setGravity(Gravity.CENTER_HORIZONTAL);
        page.addView(version, Ui.fullWidth(this, 24));
    }

    private void askNewPin(boolean firstTime) {
        LinearLayout wrap = Ui.vertical(this);
        int pad = dp(20);
        wrap.setPadding(pad, dp(8), pad, 0);
        if (firstTime) {
            wrap.addView(Ui.text(this,
                    "Die PIN schützt die Einstellungen, damit nichts aus Versehen verändert wird. "
                            + "Später öffnest du sie, indem du auf der Startseite 5× schnell auf die Uhr tippst.",
                    16, Ui.MUTED, false));
        }
        EditText first = pinField("Neue PIN (4 bis 8 Ziffern)");
        EditText second = pinField("PIN wiederholen");
        wrap.addView(first, Ui.fullWidth(this, 10));
        wrap.addView(second, Ui.fullWidth(this, 6));

        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle(firstTime ? "PIN festlegen" : "PIN ändern")
                .setView(wrap)
                .setPositiveButton("Speichern", null)
                .setNegativeButton("Abbrechen", (di, w) -> {
                    if (firstTime) finish();
                })
                .setCancelable(!firstTime)
                .create();
        d.setOnShowListener(di -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String a = first.getText().toString().trim();
            String b = second.getText().toString().trim();
            if (!a.matches("\\d{4,8}")) {
                toast("Bitte 4 bis 8 Ziffern eingeben");
                return;
            }
            if (!a.equals(b)) {
                toast("Die beiden PINs sind nicht gleich");
                return;
            }
            store.setPin(a);
            d.dismiss();
            toast("PIN gespeichert");
            render();
        }));
        d.show();
    }

    private EditText pinField(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        e.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
        return e;
    }

    // ------------------------------------------------------------------ Hilfe

    private Switch makeSwitch(String label, boolean checked, BoolSink sink) {
        Switch sw = new Switch(this);
        sw.setText(label);
        sw.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        sw.setTextColor(Ui.TEXT);
        sw.setChecked(checked);
        sw.setPadding(0, dp(10), 0, dp(10));
        sw.setOnCheckedChangeListener((button, on) -> sink.accept(on));
        return sw;
    }

    private void info(String title, String message, Runnable ok, String neutralLabel, Runnable neutral) {
        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Weiter", (d, w) -> ok.run())
                .setNegativeButton("Abbrechen", null);
        if (neutral != null) {
            b.setNeutralButton(neutralLabel, (d, w) -> neutral.run());
        }
        b.show();
    }

    private void openFirst(String... actions) {
        for (String a : actions) {
            try {
                startActivity(new Intent(a));
                return;
            } catch (RuntimeException ignored) {
            }
        }
        toast("Die Einstellungen lassen sich nicht öffnen");
    }

    private void openAppDetails() {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", getPackageName(), null)));
        } catch (RuntimeException e) {
            openFirst(Settings.ACTION_SETTINGS);
        }
    }

    private String version() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }
}
