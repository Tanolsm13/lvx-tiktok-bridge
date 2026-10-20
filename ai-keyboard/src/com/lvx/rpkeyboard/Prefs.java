package com.lvx.rpkeyboard;

import android.content.Context;
import android.content.SharedPreferences;

/** Reglages persistes (persos, univers, modele). */
public final class Prefs {

    /** Injectee a la compilation par build.sh. */
    public static final String API_KEY = "@WAVESPEED_KEY@";

    public static final String BASE_URL = "https://llm.wavespeed.ai/v1/chat/completions";

    public static final String DEFAULT_MODEL = "google/gemini-3.6-flash";

    public static final String[] MODEL_PRESETS = {
            "google/gemini-3.6-flash",
            "google/gemini-3.5-flash",
            "x-ai/grok-4.3",
            "qwen/qwen3.6-35b-a3b",
    };

    private static final String FILE = "rp_prefs";

    private final SharedPreferences sp;

    public Prefs(Context ctx) {
        sp = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public String model() {
        return sp.getString("model", DEFAULT_MODEL);
    }

    public String me() {
        return sp.getString("me", "");
    }

    public String partner() {
        return sp.getString("partner", "");
    }

    public String universe() {
        return sp.getString("universe", "");
    }

    public int count() {
        return sp.getInt("count", 3);
    }

    public void save(String model, String me, String partner, String universe, int count) {
        sp.edit()
                .putString("model", model)
                .putString("me", me)
                .putString("partner", partner)
                .putString("universe", universe)
                .putInt("count", count)
                .apply();
    }

    /** Prompt systeme construit a partir des reglages. */
    public String systemPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("Tu es l'assistant d'ecriture d'un joueur de jeu de role par messages.\n");
        sb.append("On te donne une capture de son ecran de conversation.\n\n");
        sb.append("Ta tache :\n");
        sb.append("1. Repere le DERNIER message recu (celui de l'interlocuteur, pas ceux du joueur).\n");
        sb.append("2. Propose ").append(count()).append(" reponses differentes que le joueur pourrait envoyer.\n");
        sb.append("3. Reste dans le ton et la continuite de la scene en cours.\n\n");

        if (!me().isEmpty()) {
            sb.append("Personnage joue par l'utilisateur : ").append(me()).append("\n");
        }
        if (!partner().isEmpty()) {
            sb.append("Personnage de l'interlocutrice : ").append(partner()).append("\n");
        }
        if (!universe().isEmpty()) {
            sb.append("Univers / ton du RP : ").append(universe()).append("\n");
        }

        sb.append("\nContraintes de forme :\n");
        sb.append("- Ecris a la premiere personne, comme si le joueur tapait le message.\n");
        sb.append("- Varie les propositions : une courte, une plus developpee, une qui relance l'action.\n");
        sb.append("- Pas de guillemets autour, pas de numerotation, pas de commentaire meta.\n");
        sb.append("- Reponds UNIQUEMENT par un JSON de cette forme exacte :\n");
        sb.append("{\"replies\":[\"...\",\"...\",\"...\"]}\n");
        return sb.toString();
    }
}
