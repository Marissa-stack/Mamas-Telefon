package de.familie.mamastelefon;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Alle Einstellungen der App. Sie liegen nur auf dem Handy selbst,
 * nirgendwo sonst.
 */
final class Store {

    /** Ein Kontakt auf der Startseite. */
    static final class Person {
        final String id;
        String name;
        String number;
        /** Dateiname des Fotos im App-Speicher, oder null. */
        String photo;

        Person(String id, String name, String number, String photo) {
            this.id = id;
            this.name = name;
            this.number = number;
            this.photo = photo;
        }

        static Person create(String name, String number, String photo) {
            return new Person(UUID.randomUUID().toString(), name, number, photo);
        }
    }

    /** Noch nicht festgelegt: beim ersten Start wird die aktuelle Lautstärke übernommen. */
    static final int UNSET = -2;

    private static final String PREFS = "einstellungen";

    private final Context context;
    private final SharedPreferences prefs;

    Store(Context c) {
        context = c.getApplicationContext();
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ---------------------------------------------------------------- Kontakte

    List<Person> people() {
        List<Person> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString("people", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String photo = o.isNull("photo") ? null : o.optString("photo", null);
                list.add(new Person(
                        o.optString("id", UUID.randomUUID().toString()),
                        o.optString("name", ""),
                        o.optString("number", ""),
                        photo));
            }
        } catch (JSONException ignored) {
            // Kaputte Daten: lieber leere Liste als Absturz
        }
        return list;
    }

    void savePeople(List<Person> list) {
        JSONArray arr = new JSONArray();
        try {
            for (Person p : list) {
                JSONObject o = new JSONObject();
                o.put("id", p.id);
                o.put("name", p.name);
                o.put("number", p.number);
                if (p.photo != null) o.put("photo", p.photo);
                arr.put(o);
            }
        } catch (JSONException ignored) {
        }
        prefs.edit().putString("people", arr.toString()).apply();
    }

    File photoFile(String fileName) {
        File dir = new File(context.getFilesDir(), "fotos");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, fileName);
    }

    void deletePhoto(String fileName) {
        if (fileName == null) return;
        File f = photoFile(fileName);
        if (f.exists()) f.delete();
    }

    // --------------------------------------------------------------------- PIN

    boolean hasPin() {
        return prefs.contains("pin");
    }

    boolean checkPin(String pin) {
        return pin != null && hash(pin).equals(prefs.getString("pin", null));
    }

    void setPin(String pin) {
        prefs.edit().putString("pin", hash(pin)).apply();
    }

    // ---------------------------------------------------------------- Schalter

    boolean confirmCall() {
        return prefs.getBoolean("confirm_call", true);
    }

    void setConfirmCall(boolean on) {
        prefs.edit().putBoolean("confirm_call", on).apply();
    }

    boolean bigAnswerOn() {
        return prefs.getBoolean("big_answer", true);
    }

    void setBigAnswerOn(boolean on) {
        prefs.edit().putBoolean("big_answer", on).apply();
    }

    /** Sucht den Kontakt zu einer Telefonnummer (z. B. beim Anruf), sonst null. */
    Person findByNumber(String number) {
        String a = digits(number);
        if (a.length() < 5) return null;
        for (Person p : people()) {
            String b = digits(p.number);
            if (b.length() < 5) continue;
            if (android.telephony.PhoneNumberUtils.compare(number, p.number)) return p;
            int n = Math.min(9, Math.min(a.length(), b.length()));
            if (a.substring(a.length() - n).equals(b.substring(b.length() - n))) return p;
        }
        return null;
    }

    private static String digits(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char ch : s.toCharArray()) {
            if (ch >= '0' && ch <= '9') sb.append(ch);
        }
        return sb.toString();
    }

    /** Leisten oben und unten (Zurück, Startseite, Übersicht) ausblenden. */
    boolean fullscreenOn() {
        return prefs.getBoolean("fullscreen", true);
    }

    void setFullscreenOn(boolean on) {
        prefs.edit().putBoolean("fullscreen", on).apply();
    }

    boolean photosOn() {
        return prefs.getBoolean("photos", true);
    }

    void setPhotosOn(boolean on) {
        prefs.edit().putBoolean("photos", on).apply();
    }

    boolean guardOn() {
        return prefs.getBoolean("guard", true);
    }

    void setGuardOn(boolean on) {
        prefs.edit().putBoolean("guard", on).apply();
    }

    /** Gewünschte Klingelton-Stufe, -1 = höchste Stufe, UNSET = noch nicht festgelegt. */
    int ringLevel() {
        return prefs.getInt("ring_level", UNSET);
    }

    void setRingLevel(int level) {
        prefs.edit().putInt("ring_level", level).apply();
    }

    /** Gewünschte Gesprächs-Lautstärke, -1 = höchste Stufe, UNSET = noch nicht festgelegt. */
    int callLevel() {
        return prefs.getInt("call_level", UNSET);
    }

    void setCallLevel(int level) {
        prefs.edit().putInt("call_level", level).apply();
    }

    // ------------------------------------------------------------------- Hilfe

    private static String hash(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(("mamas-telefon:" + s).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(Integer.toHexString((b & 0xff) | 0x100).substring(1));
            }
            return sb.toString();
        } catch (Exception e) {
            return s;
        }
    }
}
