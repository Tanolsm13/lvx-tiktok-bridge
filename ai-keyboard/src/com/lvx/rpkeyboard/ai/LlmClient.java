package com.lvx.rpkeyboard.ai;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Appel HTTP au fournisseur. Ne lit jamais la reponse : il la rend brute. */
public final class LlmClient implements Completer {

    public static final class HttpError extends Exception {
        public final int code;

        public HttpError(int code, String body) {
            super("HTTP " + code + (body == null || body.isEmpty() ? "" : " : " + shorten(body)));
            this.code = code;
        }
    }

    private final Provider provider;
    private final String url;
    private final String apiKey;
    private final String model;

    /** Plafond de sortie. 1200 coupait les reponses longues de jeu de role. */
    private int maxTokens = 4000;

    public LlmClient withMaxTokens(int tokens) {
        if (tokens > 0) {
            this.maxTokens = tokens;
        }
        return this;
    }

    public LlmClient(Provider provider, String url, String apiKey, String model) {
        this.provider = provider;
        this.url = (url == null || url.trim().isEmpty()) ? provider.defaultUrl : url.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = (model == null || model.trim().isEmpty()) ? provider.defaultModel : model.trim();
    }

    @Override
    public Sanitizer.Shape shape() {
        return provider.shape;
    }

    /**
     * Envoie une requete et rend le corps brut.
     *
     * @param jpeg capture d'ecran facultative, pour les modeles a vision
     */
    @Override
    public String complete(String system, String user, byte[] jpeg) throws Exception {
        if (apiKey.isEmpty()) {
            throw new Exception("aucune cle API configuree");
        }
        if (url.isEmpty()) {
            throw new Exception("aucune URL configuree pour ce fournisseur");
        }
        String body = provider.shape == Sanitizer.Shape.ANTHROPIC
                ? anthropicBody(system, user, jpeg)
                : openAiBody(system, user, jpeg);
        return post(body);
    }

    // ------------------------------------------------------------- requetes

    private String openAiBody(String system, String user, byte[] jpeg) throws Exception {
        JSONArray content = new JSONArray();
        content.put(new JSONObject().put("type", "text").put("text", user));
        if (jpeg != null && jpeg.length > 0) {
            content.put(new JSONObject()
                    .put("type", "image_url")
                    .put("image_url", new JSONObject().put("url", dataUrl(jpeg))));
        }

        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "system").put("content", system));
        messages.put(new JSONObject().put("role", "user").put("content", content));

        return new JSONObject()
                .put("model", model)
                .put("messages", messages)
                .put("temperature", 0.9)
                .put("max_tokens", maxTokens)
                .put("stream", false)
                .toString();
    }

    private String anthropicBody(String system, String user, byte[] jpeg) throws Exception {
        JSONArray content = new JSONArray();
        content.put(new JSONObject().put("type", "text").put("text", user));
        if (jpeg != null && jpeg.length > 0) {
            content.put(new JSONObject()
                    .put("type", "image")
                    .put("source", new JSONObject()
                            .put("type", "base64")
                            .put("media_type", "image/jpeg")
                            .put("data", Base64.encodeToString(jpeg, Base64.NO_WRAP))));
        }

        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "user").put("content", content));

        return new JSONObject()
                .put("model", model)
                .put("system", system)
                .put("messages", messages)
                .put("temperature", 0.9)
                .put("max_tokens", maxTokens)
                .toString();
    }

    private static String dataUrl(byte[] jpeg) {
        return "data:image/jpeg;base64," + Base64.encodeToString(jpeg, Base64.NO_WRAP);
    }

    // ----------------------------------------------------------------- HTTP

    private String post(String json) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(20_000);
            conn.setReadTimeout(120_000);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");

            if (provider.auth == Provider.Auth.X_API_KEY) {
                conn.setRequestProperty("x-api-key", apiKey);
                conn.setRequestProperty("anthropic-version", "2023-06-01");
            } else {
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            }

            byte[] payload = json.getBytes("UTF-8");
            conn.setFixedLengthStreamingMode(payload.length);
            OutputStream out = conn.getOutputStream();
            out.write(payload);
            out.flush();
            out.close();

            int code = conn.getResponseCode();
            boolean fine = code >= 200 && code < 300;
            String text = readAll(fine ? conn.getInputStream() : conn.getErrorStream());
            if (!fine) {
                throw new HttpError(code, text);
            }
            return text;
        } finally {
            conn.disconnect();
        }
    }

    private static String readAll(InputStream in) throws Exception {
        if (in == null) {
            return "";
        }
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            return new String(bos.toByteArray(), "UTF-8");
        } finally {
            in.close();
        }
    }

    private static String shorten(String s) {
        String t = s.trim().replaceAll("\\s+", " ");
        return t.length() > 200 ? t.substring(0, 200) + "…" : t;
    }
}
