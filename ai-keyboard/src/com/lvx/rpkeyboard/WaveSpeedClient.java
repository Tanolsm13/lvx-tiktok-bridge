package com.lvx.rpkeyboard;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/** Appel de l'API WaveSpeed (compatible OpenAI /chat/completions). */
public final class WaveSpeedClient {

    private final Prefs prefs;

    public WaveSpeedClient(Prefs prefs) {
        this.prefs = prefs;
    }

    /**
     * Envoie la capture et renvoie les propositions de reponse.
     *
     * @param jpeg capture d'ecran encodee en JPEG
     */
    public List<String> suggest(byte[] jpeg) throws Exception {
        String dataUrl = "data:image/jpeg;base64,"
                + Base64.encodeToString(jpeg, Base64.NO_WRAP);

        JSONArray content = new JSONArray();
        content.put(new JSONObject()
                .put("type", "text")
                .put("text", "Voici la capture de ma conversation. "
                        + "Propose-moi " + prefs.count() + " reponses au dernier message recu."));
        content.put(new JSONObject()
                .put("type", "image_url")
                .put("image_url", new JSONObject().put("url", dataUrl)));

        JSONArray messages = new JSONArray();
        messages.put(new JSONObject()
                .put("role", "system")
                .put("content", prefs.systemPrompt()));
        messages.put(new JSONObject()
                .put("role", "user")
                .put("content", content));

        JSONObject body = new JSONObject()
                .put("model", prefs.model())
                .put("messages", messages)
                .put("temperature", 0.9)
                .put("max_tokens", 900);

        String raw = post(body.toString());
        return parseReplies(extractContent(raw));
    }

    /** Petit appel texte pour verifier la cle et le modele. */
    public String ping() throws Exception {
        JSONArray messages = new JSONArray();
        messages.put(new JSONObject()
                .put("role", "user")
                .put("content", "Reponds exactement : OK"));

        JSONObject body = new JSONObject()
                .put("model", prefs.model())
                .put("messages", messages)
                .put("max_tokens", 16);

        return extractContent(post(body.toString()));
    }

    private String post(String json) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(Prefs.BASE_URL).openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(120000);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + Prefs.API_KEY);

            byte[] payload = json.getBytes("UTF-8");
            conn.setFixedLengthStreamingMode(payload.length);
            OutputStream out = conn.getOutputStream();
            out.write(payload);
            out.flush();
            out.close();

            int code = conn.getResponseCode();
            InputStream in = (code >= 200 && code < 300)
                    ? conn.getInputStream()
                    : conn.getErrorStream();
            String text = readAll(in);

            if (code < 200 || code >= 300) {
                throw new Exception("HTTP " + code + " : " + shorten(text));
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
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            bos.write(buf, 0, n);
        }
        in.close();
        return new String(bos.toByteArray(), "UTF-8");
    }

    private static String extractContent(String raw) throws Exception {
        JSONObject root = new JSONObject(raw);
        if (root.has("error")) {
            throw new Exception(shorten(root.get("error").toString()));
        }
        JSONArray choices = root.optJSONArray("choices");
        if (choices == null || choices.length() == 0) {
            throw new Exception("Reponse vide de l'API");
        }
        JSONObject message = choices.getJSONObject(0).optJSONObject("message");
        if (message == null) {
            throw new Exception("Reponse inattendue de l'API");
        }
        Object content = message.opt("content");
        if (content instanceof JSONArray) {
            // Certains modeles renvoient le contenu en blocs.
            StringBuilder sb = new StringBuilder();
            JSONArray parts = (JSONArray) content;
            for (int i = 0; i < parts.length(); i++) {
                JSONObject part = parts.optJSONObject(i);
                if (part != null) {
                    sb.append(part.optString("text", ""));
                }
            }
            return sb.toString();
        }
        return content == null ? "" : content.toString();
    }

    /** Tolerant : JSON attendu, sinon on retombe sur un decoupage par lignes. */
    static List<String> parseReplies(String text) {
        List<String> out = new ArrayList<String>();
        if (text == null) {
            return out;
        }
        String trimmed = text.trim();

        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            try {
                JSONArray arr = new JSONObject(trimmed.substring(start, end + 1))
                        .optJSONArray("replies");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        String s = arr.optString(i, "").trim();
                        if (!s.isEmpty()) {
                            out.add(s);
                        }
                    }
                }
            } catch (Exception ignored) {
                // on passe au repli ci-dessous
            }
        }

        if (out.isEmpty()) {
            for (String line : trimmed.split("\n")) {
                String s = line.trim().replaceFirst("^[-*\\d.)\\s]+", "").trim();
                if (s.length() > 1 && !s.startsWith("{") && !s.startsWith("}")) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    private static String shorten(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }
}
