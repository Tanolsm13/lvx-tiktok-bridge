package com.lvx.rpkeyboard.imagegen;

import com.lvx.rpkeyboard.ai.WaveSpeed;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/** Generation d'images via WaveSpeed, avec la meme cle que le texte et le vocal. */
public final class ImageClient {

    private final String apiKey;
    private final String model;

    public ImageClient(String apiKey, String model) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = (model == null || model.trim().isEmpty())
                ? WaveSpeed.DEFAULT_IMAGE_MODEL : model.trim();
    }

    public boolean isConfigured() {
        return !apiKey.isEmpty();
    }

    /**
     * Genere une image depuis une description et l'ecrit dans {@code outDir}.
     *
     * @param size format demande, par exemple {@code 1024*1024}
     */
    public File generate(String prompt, String size, File outDir, WaveSpeed.Progress progress)
            throws Exception {
        if (apiKey.isEmpty()) {
            throw new Exception("cle WaveSpeed manquante");
        }
        if (prompt == null || prompt.trim().isEmpty()) {
            throw new Exception("aucune description d'image");
        }

        JSONObject input = new JSONObject()
                .put("prompt", prompt.trim())
                .put("size", (size == null || size.isEmpty()) ? "1024*1024" : size)
                .put("num_images", 1)
                .put("enable_base64_output", false);

        byte[] png = WaveSpeed.runTask(apiKey, model, input, progress);

        if (!outDir.exists() && !outDir.mkdirs()) {
            throw new Exception("dossier image indisponible");
        }
        File out = new File(outDir, "image-" + System.currentTimeMillis() + ".png");
        OutputStream fos = new FileOutputStream(out);
        try {
            fos.write(png);
            fos.flush();
        } finally {
            fos.close();
        }
        return out;
    }
}
