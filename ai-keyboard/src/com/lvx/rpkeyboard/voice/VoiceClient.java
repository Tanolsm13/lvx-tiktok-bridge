package com.lvx.rpkeyboard.voice;

import com.lvx.rpkeyboard.ai.WaveSpeed;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * Synthese vocale via WaveSpeed, qui heberge les modeles ElevenLabs.
 *
 * Une seule cle suffit donc pour le texte, l'image et le vocal. Le fichier
 * produit est un vrai MP3, a joindre en piece attachee : Android ne permet pas
 * d'injecter du son dans le micro d'une autre app, et un enregistrement du
 * haut-parleur serait etouffe.
 *
 * Reserve : les voix accessibles ici sont celles du catalogue ElevenLabs chez
 * WaveSpeed, pas une voix clonee sur un compte ElevenLabs personnel.
 */
public final class VoiceClient {

    private final String apiKey;
    private final String model;
    private final String voiceId;

    public VoiceClient(String apiKey, String model, String voiceId) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = (model == null || model.trim().isEmpty())
                ? WaveSpeed.DEFAULT_VOICE_MODEL : model.trim();
        this.voiceId = voiceId == null ? "" : voiceId.trim();
    }

    public boolean isConfigured() {
        return !apiKey.isEmpty() && !voiceId.isEmpty();
    }

    /**
     * Synthetise {@code text} et ecrit le fichier dans {@code outDir}.
     *
     * @return le fichier produit
     */
    public File synthesize(String text, File outDir, WaveSpeed.Progress progress)
            throws Exception {
        if (apiKey.isEmpty()) {
            throw new Exception("cle WaveSpeed manquante");
        }
        if (voiceId.isEmpty()) {
            throw new Exception("aucune voix choisie dans les reglages");
        }
        if (text == null || text.trim().isEmpty()) {
            throw new Exception("rien a dire");
        }

        JSONObject input = new JSONObject()
                .put("text", text.trim())
                .put("voice_id", voiceId)
                .put("stability", 0.45)
                .put("similarity_boost", 0.8)
                .put("speaker_boost", true);

        byte[] audio = WaveSpeed.runTask(apiKey, model, input, progress);

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
}
