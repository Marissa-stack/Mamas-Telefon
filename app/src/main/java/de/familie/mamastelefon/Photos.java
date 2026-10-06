package de.familie.mamastelefon;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.UUID;

/** Fotos übernehmen: drehen, quadratisch zuschneiden, verkleinern, speichern. */
final class Photos {

    private static final int SIZE = 600;

    private Photos() {
    }

    /**
     * Lädt ein Bild verkleinert (längste Seite etwa maxSide Pixel) und richtig
     * gedreht, z. B. ein WhatsApp-Foto zum Anzeigen. Gibt null zurück, wenn es nicht geht.
     */
    static Bitmap decode(Context c, Uri uri, int maxSide) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                if (in == null) return null;
                BitmapFactory.decodeStream(in, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
            int sample = 1;
            while (Math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) {
                sample *= 2;
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            Bitmap bmp;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                if (in == null) return null;
                bmp = BitmapFactory.decodeStream(in, null, opts);
            }
            if (bmp == null) return null;
            int rotation = readRotation(c, uri);
            if (rotation != 0) {
                Matrix m = new Matrix();
                m.postRotate(rotation);
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
            }
            return bmp;
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    /** Speichert das Bild als quadratisches Foto. Gibt den Dateinamen zurück oder null. */
    static String importPhoto(Context c, Uri uri, Store store) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                if (in == null) return null;
                BitmapFactory.decodeStream(in, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

            int sample = 1;
            while (Math.min(bounds.outWidth, bounds.outHeight) / (sample * 2) >= SIZE) {
                sample *= 2;
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            Bitmap bmp;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                if (in == null) return null;
                bmp = BitmapFactory.decodeStream(in, null, opts);
            }
            if (bmp == null) return null;

            int rotation = readRotation(c, uri);
            if (rotation != 0) {
                Matrix m = new Matrix();
                m.postRotate(rotation);
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
            }

            // Quadrat ausschneiden. Bei Hochformat etwas weiter oben,
            // damit Gesichter nicht abgeschnitten werden.
            int w = bmp.getWidth();
            int h = bmp.getHeight();
            int side = Math.min(w, h);
            int x = (w - side) / 2;
            int y = h > w ? Math.round((h - side) * 0.25f) : (h - side) / 2;
            Bitmap square = Bitmap.createBitmap(bmp, x, y, side, side);
            int out = Math.min(side, SIZE);
            Bitmap scaled = Bitmap.createScaledBitmap(square, out, out, true);

            String name = UUID.randomUUID() + ".jpg";
            try (FileOutputStream fos = new FileOutputStream(store.photoFile(name))) {
                scaled.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            }
            return name;
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    static Bitmap load(Store store, String fileName) {
        if (fileName == null) return null;
        File f = store.photoFile(fileName);
        if (!f.exists()) return null;
        try {
            return BitmapFactory.decodeFile(f.getAbsolutePath());
        } catch (OutOfMemoryError e) {
            return null;
        }
    }

    private static int readRotation(Context c, Uri uri) {
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            if (in == null) return 0;
            ExifInterface exif = new ExifInterface(in);
            int o = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            if (o == ExifInterface.ORIENTATION_ROTATE_90) return 90;
            if (o == ExifInterface.ORIENTATION_ROTATE_180) return 180;
            if (o == ExifInterface.ORIENTATION_ROTATE_270) return 270;
        } catch (Exception ignored) {
        }
        return 0;
    }
}
