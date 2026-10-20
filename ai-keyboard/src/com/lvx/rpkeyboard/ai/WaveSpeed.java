package com.lvx.rpkeyboard.ai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Acces a WaveSpeed, le seul fournisseur de l'app.
 *
 * Deux familles d'API y cohabitent :
 * <ul>
 *   <li>le texte, compatible OpenAI, en un seul appel synchrone ;</li>
 *   <li>l'image et le vocal, en taches : on soumet, on recoit un identifiant,
 *       puis on sonde jusqu'a obtenir le fichier.</li>
 * </ul>
 *
 * Les reponses arrivent tantot enveloppees dans {@code data}, tantot a plat :
 * les deux formes sont acceptees.
 */
public final class WaveSpeed {

    public static final String LLM_URL = "https://llm.wavespeed.ai/v1/chat/completions";
    public static final String API_BASE = "https://api.wavespeed.ai/api/v3/";

    public static final String DEFAULT_LLM_MODEL = "google/gemini-3.6-flash";
    public static final String DEFAULT_IMAGE_MODEL = "openai/gpt-image-2/text-to-image";
    public static final String DEFAULT_VOICE_MODEL = "elevenlabs/turbo-v2.5";

    /** Modeles a vision proposes dans les reglages. */
    public static final String[] VISION_MODELS = {
            "google/gemini-3.6-flash",
            "google/gemini-3.5-flash",
            "x-ai/grok-4.3",
            "qwen/qwen3.6-35b-a3b",
    };

    public static final String[] IMAGE_MODELS = {
            "openai/gpt-image-2/text-to-image",
            "bytedance/seedream-v5.0-pro",
            "wavespeed-ai/ernie-image/text-to-image",
    };

    private static final long POLL_START_MS = 1_500;
    private static final long POLL_MAX_MS = 8_000;
    private static final long TASK_TIMEOUT_MS = 180_000;

    private WaveSpeed() {
    }

    // ------------------------------------------------------------- analyse

    /** Etat d'une tache, tel que le rend le sondage. */
    public static final class TaskState {
        public final String status;
        public final List<String> outputs;
        public final String error;

        TaskState(String status, List<String> outputs, String error) {
            this.status = status;
            this.outputs = outputs;
            this.error = error;
        }

        public boolean isDone() {
            return "completed".equalsIgnoreCase(status) || "succeeded".equalsIgnoreCase(status);
        }

        public boolean isFailed() {
            return "failed".equalsIgnoreCase(status) || "cancelled".equalsIgnoreCase(status)
                    || "canceled".equalsIgnoreCase(status) || "timeout".equalsIgnoreCase(status)
                    || "deleted".equalsIgnoreCase(status) || "error".equalsIgnoreCase(status);
        }
    }

    /** Le corps peut etre enveloppe dans {@code data} ou non. */
    static JSONObject unwrap(JSONObject root) {
        JSONObject data = root.optJSONObject("data");
        return data != null ? data : root;
    }

    /** Identifiant rendu par la soumission. */
    public static String parseSubmitId(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        JSONObject body = unwrap(root);
        String id = body.optString("id", "");
        if (id.isEmpty()) {
            id = body.optString("request_id", "");
        }
        if (id.isEmpty()) {
            String message = root.optString("message", "");
            throw new Exception(message.isEmpty()
                    ? "reponse de soumission sans identifiant" : message);
        }
        return id;
    }

    /** Etat rendu par le sondage. */
    public static TaskState parseResult(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        JSONObject body = unwrap(root);

        String status = body.optString("status", "");
        String error = body.optString("error", "");
        if (error.isEmpty()) {
            error = root.optString("message", "");
        }

        List<String> outputs = new ArrayList<String>();
        JSONArray arr = body.optJSONArray("outputs");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                Object v = arr.opt(i);
                if (v instanceof String) {
                    outputs.add((String) v);
                } else if (v instanceof JSONObject) {
                    String u = ((JSONObject) v).optString("url", "");
                    if (!u.isEmpty()) {
                        outputs.add(u);
                    }
                }
            }
        }
        // Certains modeles rendent une seule sortie sous une autre cle.
        if (outputs.isEmpty()) {
            for (String key : new String[]{"output", "url", "audio", "image"}) {
                String u = body.optString(key, "");
                if (!u.isEmpty()) {
                    outputs.add(u);
                    break;
                }
            }
        }
        return new TaskState(status, outputs, error);
    }

    // --------------------------------------------------------------- taches

    /** Avertit de l'avancement d'une tache longue. */
    public interface Progress {
        void onWait(long elapsedMs);
    }

    /**
     * Soumet une tache et attend le fichier produit.
     *
     * @param modelPath chemin du modele, par exemple {@code elevenlabs/turbo-v2.5}
     * @return le contenu du premier fichier produit
     */
    public static byte[] runTask(String apiKey, String modelPath, JSONObject input,
                                 Progress progress) throws Exception {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new Exception("cle WaveSpeed manquante");
        }
        String submit = post(API_BASE + modelPath, apiKey, input.toString());
        String id = parseSubmitId(submit);

        long started = System.currentTimeMillis();
        long wait = POLL_START_MS;
        String resultUrl = API_BASE + "predictions/" + id + "/result";

        while (true) {
            long elapsed = System.currentTimeMillis() - started;
            if (elapsed > TASK_TIMEOUT_MS) {
                throw new Exception("la tache n'a pas abouti en "
                        + (TASK_TIMEOUT_MS / 1000) + " s");
            }
            Thread.sleep(wait);
            wait = Math.min(POLL_MAX_MS, (long) (wait * 1.4));
            if (progress != null) {
                progress.onWait(elapsed);
            }

            TaskState state = parseResult(get(resultUrl, apiKey));
            if (state.isFailed()) {
                throw new Exception(state.error.isEmpty()
                        ? ("tache " + state.status) : state.error);
            }
            if (state.isDone()) {
                if (state.outputs.isEmpty()) {
                    throw new Exception("tache terminee sans fichier");
                }
                return download(state.outputs.get(0));
            }
        }
    }

    // ----------------------------------------------------------------- HTTP

    static String post(String url, String apiKey, String json) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(20_000);
            conn.setReadTimeout(120_000);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey.trim());

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

    static String get(String url, String apiKey) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(20_000);
            conn.setReadTimeout(60_000);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey.trim());

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

    static byte[] download(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setConnectTimeout(20_000);
            conn.setReadTimeout(120_000);
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new Exception("telechargement HTTP " + code);
            }
            byte[] data = readAll(conn.getInputStream());
            if (data.length == 0) {
                throw new Exception("fichier vide");
            }
            return data;
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

    static String shorten(String s) {
        String t = s.trim().replaceAll("\\s+", " ");
        return t.length() > 200 ? t.substring(0, 200) + "…" : t;
    }
}
