package com.lvx.rpkeyboard.voice;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Synthese vocale ElevenLabs.
 *
 * Produit un fichier audio reel, a joindre en piece attachee. On ne tente pas
 * de faire enregistrer le haut-parleur par le micro d'une autre app : Android
 * ne permet pas d'injecter de l'audio dans l'entree micro d'autrui, et le
 * resultat serait un enregistrement etouffe avec l'echo de la piece.
 *
 * Le mode automatique n'utilise jamais cette classe : une voix clonee envoyee
 * sans relecture contredirait l'annonce faite a l'interlocuteur.
 */
public final class ElevenLabsClient {

    public static final String BASE = "https://api.elevenlabs.io/v1/text-to-speech/";

    /** Identifiant de modele par defaut ; remplacable dans les reglages. */
    public static final String DEFAULT_MODEL = "eleven_multilingual_v2";

    private final String apiKey;
    private final String voiceId;
    private final String modelId;

    public ElevenLabsClient(String apiKey, String voiceId, String modelId) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.voiceId = voiceId == null ? "" : voiceId.trim();
        this.modelId = (modelId == null || modelId.trim().isEmpty())
                ? DEFAULT_MODEL : modelId.trim();
    }

    public boolean isConfigured() {
        return !apiKey.isEmpty() && !voiceId.isEmpty();
    }

    /**
     * Synthetise {@code text} et ecrit le MP3 dans {@code outDir}.
     *
     * @return le fichier produit
     */
    public File synthesize(String text, File outDir) throws Exception {
        if (!isConfigured()) {
            throw new Exception("cle ou voix ElevenLabs manquante");
        }
        if (text == null || text.trim().isEmpty()) {
            throw new Exception("rien a dire");
        }

        byte[] audio = request(text.trim());
        if (!outDir.exists() && !outDir.mkdirs()) {
            throw new Exception("dossier audio indisponible");
        }
        File out = new File(outDir, "vocal-" + System.currentTimeMillis() + ".mp3");
        OutputStream fos = new FileOutputStream(out);
        try {
            fos.write(audio);
            fos.flush();
        } finally {
            fos.close();
        }
        return out;
    }


    /** Une voix du compte. */
    public static final class Voice {
        public final String id;
        public final String name;

        Voice(String id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * Liste les voix du compte.
     *
     * Personne ne connait son identifiant de voix par coeur : sans cette
     * liste, le champ reste vide et le bouton micro ne sert a rien.
     */
    public static java.util.List<Voice> listVoices(String apiKey) throws Exception {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new Exception("cle ElevenLabs manquante");
        }
        HttpURLConnection conn =
                (HttpURLConnection) new URL("https://api.elevenlabs.io/v1/voices").openConnection();
        try {
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(20_000);
            conn.setReadTimeout(60_000);
            conn.setRequestProperty("xi-api-key", apiKey.trim());
            conn.setRequestProperty("Accept", "application/json");

            int code = conn.getResponseCode();
            boolean fine = code >= 200 && code < 300;
            String text = new String(
                    readAll(fine ? conn.getInputStream() : conn.getErrorStream()), "UTF-8");
            if (!fine) {
                throw new Exception("ElevenLabs HTTP " + code + " : " + shorten(text));
            }

            org.json.JSONArray arr = new org.json.JSONObject(text).optJSONArray("voices");
            java.util.List<Voice> out = new java.util.ArrayList<Voice>();
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    org.json.JSONObject v = arr.optJSONObject(i);
                    if (v == null) {
                        continue;
                    }
                    String id = v.optString("voice_id", "");
                    String name = v.optString("name", id);
                    if (!id.isEmpty()) {
                        out.add(new Voice(id, name));
                    }
                }
            }
            if (out.isEmpty()) {
                throw new Exception("aucune voix sur ce compte");
            }
            return out;
        } finally {
            conn.disconnect();
        }
    }

    private byte[] request(String text) throws Exception {
        JSONObject voiceSettings = new JSONObject()
                .put("stability", 0.45)
                .put("similarity_boost", 0.8)
                .put("style", 0.3)
                .put("use_speaker_boost", true);

        String body = new JSONObject()
                .put("text", text)
                .put("model_id", modelId)
                .put("voice_settings", voiceSettings)
                .toString();

        HttpURLConnection conn =
                (HttpURLConnection) new URL(BASE + voiceId).openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(20_000);
            conn.setReadTimeout(120_000);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "audio/mpeg");
            conn.setRequestProperty("xi-api-key", apiKey);

            byte[] payload = body.getBytes("UTF-8");
            conn.setFixedLengthStreamingMode(payload.length);
            OutputStream out = conn.getOutputStream();
            out.write(payload);
            out.flush();
            out.close();

            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                String err = new String(readAll(conn.getErrorStream()), "UTF-8");
                throw new Exception("ElevenLabs HTTP " + code + " : " + shorten(err));
            }
            byte[] audio = readAll(conn.getInputStream());
            if (audio.length == 0) {
                throw new Exception("audio vide");
            }
            return audio;
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
