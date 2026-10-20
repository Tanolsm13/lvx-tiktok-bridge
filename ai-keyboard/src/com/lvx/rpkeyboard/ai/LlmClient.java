package com.lvx.rpkeyboard.ai;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Appel texte a WaveSpeed, au format OpenAI. Ne lit jamais la reponse : il la
 * rend brute, c'est {@link Sanitizer} qui la nettoie.
 */
public final class LlmClient implements Completer {

    public static final class HttpError extends Exception {
        public final int code;

        public HttpError(int code, String body) {
            super("HTTP " + code + (body == null || body.isEmpty()
                    ? "" : " : " + WaveSpeed.shorten(body)));
            this.code = code;
        }
    }

    private final String apiKey;
    private final String model;

    /** Plafond de sortie. 1200 coupait les reponses longues de jeu de role. */
    private int maxTokens = 4000;

    public LlmClient(String apiKey, String model) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = (model == null || model.trim().isEmpty())
                ? WaveSpeed.DEFAULT_LLM_MODEL : model.trim();
    }

    public LlmClient withMaxTokens(int tokens) {
        if (tokens > 0) {
            this.maxTokens = tokens;
        }
        return this;
    }

    @Override
    public Sanitizer.Shape shape() {
        return Sanitizer.Shape.OPENAI;
    }

    /**
     * Envoie une requete et rend le corps brut.
     *
     * @param jpeg capture d'ecran facultative, pour les modeles a vision
     */
    @Override
    public String complete(String system, String user, byte[] jpeg) throws Exception {
        if (apiKey.isEmpty()) {
            throw new Exception("aucune cle WaveSpeed configuree");
        }

        JSONArray content = new JSONArray();
        content.put(new JSONObject().put("type", "text").put("text", user));
        if (jpeg != null && jpeg.length > 0) {
            content.put(new JSONObject()
                    .put("type", "image_url")
                    .put("image_url", new JSONObject().put("url",
                            "data:image/jpeg;base64,"
                                    + Base64.encodeToString(jpeg, Base64.NO_WRAP))));
        }

        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "system").put("content", system));
        messages.put(new JSONObject().put("role", "user").put("content", content));

        String body = new JSONObject()
                .put("model", model)
                .put("messages", messages)
                .put("temperature", 0.9)
                .put("max_tokens", maxTokens)
                .put("stream", false)
                .toString();

        return post(body);
    }

    private String post(String json) throws Exception {
        HttpURLConnection conn =
                (HttpURLConnection) new URL(WaveSpeed.LLM_URL).openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(20_000);
            conn.setReadTimeout(120_000);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
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
                throw new HttpError(code, text);
            }
            return text;
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
}
