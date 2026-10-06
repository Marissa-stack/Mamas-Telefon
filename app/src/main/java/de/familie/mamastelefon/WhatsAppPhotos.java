package de.familie.mamastelefon;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import java.util.ArrayList;
import java.util.List;

/**
 * Liest die Fotos, die WhatsApp beim Empfang auf dem Handy speichert
 * (Ordner "WhatsApp Images"). Selbst gesendete Fotos liegen im Unterordner
 * "Sent" und werden nicht angezeigt. Die App braucht dafür kein Internet.
 */
final class WhatsAppPhotos {

    static final long NEW_FOR_MS = 24L * 60 * 60 * 1000;

    static final class Item {
        final Uri uri;
        final long receivedMs;

        Item(Uri uri, long receivedMs) {
            this.uri = uri;
            this.receivedMs = receivedMs;
        }
    }

    private WhatsAppPhotos() {
    }

    static String permission() {
        return Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_IMAGES
                : Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    static boolean canRead(Context c) {
        return c.checkSelfPermission(permission()) == PackageManager.PERMISSION_GRANTED;
    }

    /** Die neuesten empfangenen WhatsApp-Fotos, neuestes zuerst. */
    static List<Item> recent(Context c, int limit) {
        List<Item> out = new ArrayList<>();
        if (!canRead(c)) return out;
        Uri collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {"_id", "date_added"};
        String selection = "bucket_display_name = ?";
        String[] args = {"WhatsApp Images"};
        try (Cursor cur = c.getContentResolver().query(
                collection, projection, selection, args, "date_added DESC")) {
            if (cur == null) return out;
            while (cur.moveToNext() && out.size() < limit) {
                long id = cur.getLong(0);
                long addedMs = cur.getLong(1) * 1000L;
                out.add(new Item(ContentUris.withAppendedId(collection, id), addedMs));
            }
        } catch (RuntimeException ignored) {
            // Kein Zugriff oder Speicher nicht bereit: einfach keine Fotos zeigen
        }
        return out;
    }

    static boolean isNew(Item item) {
        return System.currentTimeMillis() - item.receivedMs < NEW_FOR_MS;
    }
}
