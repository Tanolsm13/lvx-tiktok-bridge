package com.lvx.rpkeyboard.imagegen;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Generation d'image au format OpenAI ({@code /v1/images/generations}).
 *
 * L'URL et le modele sont libres : la plupart des passerelles compatibles
 * OpenAI exposent ce point d'entree. La reponse peut porter l'image en base64
 * ({@code b64_json}) ou une URL a telecharger ; les deux sont gerees.
 */
public final class ImageClient {

    public static final String DEFAULT_URL = "https://api.openai.com/v1/images/generations";
    public static final String DEFAULT_MODEL = "gpt-image-1";

    private final String url;
    private final String apiKey;
    private final String model;

    public ImageClient(String url, String apiKey, String model) {
        this.url = (url == null || url.trim().isEmpty()) ? DEFAULT_URL : url.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = (model == null || model.trim().isEmpty()) ? DEFAULT_MODEL : model.trim();
    }

    public boolean isConfigured() {
        return !apiKey.isEmpty();
    }

    /**
     * Genere une image depuis une description et l'ecrit dans {@code outDir}.
     *
     * @param size format demande, par exemple {@code 1024x1024}
     */
    public File generate(String prompt, String size, File outDir) throws Exception {
        if (!isConfigured()) {
            throw new Exception("cle image manquante");
        }
        if (prompt == null || prompt.trim().isEmpty()) {
            throw new Exception("aucune description d'image");
        }

        String body = new JSONObject()
                .put("model", model)
                .put("prompt", prompt.trim())
                .put("n", 1)
                .put("size", (size == null || size.isEmpty()) ? "1024x1024" : size)
                .toString();

        String response = post(body);
        byte[] png = extractImage(response);

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

    private byte[] extractImage(String response) throws Exception {
        JSONObject root = new JSONObject(response);
        if (root.has("error")) {
            throw new Exception(shorten(root.get("error").toString()));
        }
        JSONArray data = root.optJSONArray("data");
        if (data == null || data.length() == 0) {
            throw new Exception("aucune image renvoyee");
        }
        JSONObject first = data.getJSONObject(0);

        String b64 = first.optString("b64_json", "");
        if (!b64.isEmpty()) {
            return Base64.decode(b64, Base64.DEFAULT);
        }
        String link = first.optString("url", "");
        if (!link.isEmpty()) {
            return download(link);
        }
        throw new Exception("reponse image inattendue");
    }

    private String post(String json) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(20_000);
            conn.setReadTimeout(180_000);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);

            byte[] payload = json.getBytes("UTF-8");
            conn.setFixedLengthStreamingMode(payload.length);
            OutputStream out = conn.getOutputStream();
            out.write(payload);
            out.flush();
            out.close();

            int code = conn.getResponseCode();
            boolean fine = code >= 200 && code < 300;
            String text = new String(
                    readAll(fine ? conn.getInputStream() : conn.getErrorStream()), "UTF-8");
            if (!fine) {
                throw new Exception("HTTP " + code + " : " + shorten(text));
            }
            return text;
        } finally {
            conn.disconnect();
        }
    }

    private static byte[] download(String link) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(link).openConnection();
        try {
            conn.setConnectTimeout(20_000);
            conn.setReadTimeout(120_000);
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new Exception("telechargement image HTTP " + code);
            }
            return readAll(conn.getInputStream());
        } finally {
            conn.disconnect();
        }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        if (in == null) {
            return new byte[0];
        }
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            return bos.toByteArray();
        } finally {
            in.close();
        }
    }

    private static String shorten(String s) {
        String t = s.trim().replaceAll("\\s+", " ");
        return t.length() > 200 ? t.substring(0, 200) + "…" : t;
    }
}
