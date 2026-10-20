package com.lvx.rpkeyboard;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Publie un fichier genere dans la mediatheque, puis ouvre le partage.
 *
 * On passe par {@link MediaStore} plutot que par un FileProvider : la classe
 * FileProvider vient d'AndroidX, absente de ce projet, alors que MediaStore est
 * dans le framework et n'exige aucune permission pour les fichiers que l'app
 * depose elle-meme (Android 10 et plus).
 */
public final class Share {

    private static final String FOLDER = "ClavierRP";

    private Share() {
    }

    public static Uri publishAudio(Context ctx, File mp3) throws Exception {
        ContentValues v = new ContentValues();
        v.put(MediaStore.Audio.Media.DISPLAY_NAME, mp3.getName());
        v.put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg");
        v.put(MediaStore.Audio.Media.RELATIVE_PATH,
                Environment.DIRECTORY_MUSIC + "/" + FOLDER);
        return insert(ctx, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, v, mp3);
    }

    public static Uri publishImage(Context ctx, File png) throws Exception {
        ContentValues v = new ContentValues();
        v.put(MediaStore.Images.Media.DISPLAY_NAME, png.getName());
        v.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        v.put(MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/" + FOLDER);
        return insert(ctx, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v, png);
    }

    private static Uri insert(Context ctx, Uri collection, ContentValues values, File source)
            throws Exception {
        ContentResolver cr = ctx.getContentResolver();
        Uri target = cr.insert(collection, values);
        if (target == null) {
            throw new Exception("impossible d'ecrire dans la mediatheque");
        }
        OutputStream out = cr.openOutputStream(target);
        if (out == null) {
            throw new Exception("mediatheque inaccessible");
        }
        InputStream in = new FileInputStream(source);
        try {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            out.flush();
        } finally {
            in.close();
            out.close();
        }
        return target;
    }

    /** Ouvre le selecteur de partage. Depuis un clavier, il faut une tache neuve. */
    public static void send(Context ctx, Uri uri, String mime, String title) {
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType(mime)
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Intent chooser = Intent.createChooser(send, title)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(chooser);
    }

    /** Dossier de travail pour les fichiers generes. */
    public static File outDir(Context ctx) {
        return new File(ctx.getFilesDir(), "sortie");
    }
}
