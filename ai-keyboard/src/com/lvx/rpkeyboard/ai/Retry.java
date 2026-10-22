package com.lvx.rpkeyboard.ai;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

/**
 * Politique de reessai.
 *
 * Les passerelles LLM hoquettent : 500 passager, 502 derriere un equilibreur,
 * 429 quand on tape trop vite, reponse vide quand le flux est coupe. Rien de
 * tout cela ne merite une erreur seche a l'ecran, alors qu'une cle invalide ou
 * un modele inconnu ne seront pas repares par un reessai.
 *
 * Les decisions sont des fonctions pures, donc testables sans reseau.
 */
public final class Retry {

    /** Nombre total de tentatives, reessais compris. */
    public static final int MAX_ATTEMPTS = 3;

    private Retry() {
    }

    /** Un code HTTP vaut-il un reessai. */
    public static boolean shouldRetry(int httpCode) {
        if (httpCode == 429) {
            return true;    // trop de requetes : on laisse respirer
        }
        if (httpCode == 408) {
            return true;    // delai depasse cote serveur
        }
        return httpCode >= 500 && httpCode <= 599;
    }

    /** Une panne reseau vaut-elle un reessai. */
    public static boolean isTransient(Throwable e) {
        if (e == null) {
            return false;
        }
        if (e instanceof SocketTimeoutException
                || e instanceof SocketException
                || e instanceof InterruptedIOException
                || e instanceof UnknownHostException) {
            return true;
        }
        // Une IOException generique est presque toujours un incident de transport.
        return e instanceof IOException;
    }

    /**
     * Une reponse mal formee vaut-elle un reessai.
     *
     * Une reponse vide est le plus souvent un flux coupe : on retente. Du JSON
     * irreductible ou une erreur d'API sont deterministes : les retenter ne
     * ferait que couter un appel de plus.
     */
    public static boolean shouldRetry(Sanitizer.Kind kind) {
        return kind == Sanitizer.Kind.VIDE || kind == Sanitizer.Kind.STRUCTURE;
    }

    /**
     * Ce defaut peut-il se reparer en changeant de modele.
     *
     * Un filtre de contenu ne changera pas d'avis au troisieme appel, et un
     * modele qui depense tout son budget a reflechir le depensera encore. Dans
     * ces cas, reessayer le meme modele ne fait que couter ; un autre modele,
     * lui, a une vraie chance de repondre. C'est tout l'interet du repli.
     */
    public static boolean worthAnotherModel(Sanitizer.Kind kind) {
        return kind == Sanitizer.Kind.FILTRE
                || kind == Sanitizer.Kind.COUPE
                || kind == Sanitizer.Kind.RAISONNEMENT
                || kind == Sanitizer.Kind.ENCORE_JSON
                || kind == Sanitizer.Kind.VIDE
                || kind == Sanitizer.Kind.STRUCTURE;
    }

    /**
     * Un echec HTTP qui vise le modele demande, pas la cle ni le reseau.
     *
     * 404 pour un identifiant inconnu, 400 pour une requete que ce modele
     * n'accepte pas — une image envoyee a un modele sans vision, par exemple.
     * Un 401 reste fatal : aucun modele ne repondra avec une cle refusee.
     */
    public static boolean worthAnotherModel(int httpCode) {
        return httpCode == 400 || httpCode == 404 || httpCode == 413 || httpCode == 422;
    }

    /** Message affichable pour une reponse mal formee, avec le geste a faire. */
    public static String advise(Sanitizer.Kind kind, String detail) {
        String tail = (detail == null || detail.trim().isEmpty())
                ? "" : " (" + detail.trim() + ")";
        switch (kind) {
            case FILTRE:
                return "Le modele a refuse de repondre" + tail
                        + " : prends un modele plus permissif dans les reglages.";
            case COUPE:
                return "Coupe avant d'ecrire" + tail
                        + " : monte la longueur maximale de reponse.";
            case RAISONNEMENT:
                return "Le modele a tout depense en reflexion" + tail
                        + " : monte la longueur maximale, ou prends un modele sans reflexion.";
            case ENCORE_JSON:
                return "Le modele insiste avec du JSON" + tail + " : essaie un autre modele.";
            case ERREUR_API:
                return "L'API a renvoye une erreur" + tail + ".";
            case STRUCTURE:
                return "Reponse de forme inattendue" + tail + ".";
            default:
                return "Reponse vide du modele" + tail + " apres " + MAX_ATTEMPTS + " essais.";
        }
    }

    /** Attente avant la tentative suivante, croissante. */
    public static long backoffMs(int completedAttempts) {
        switch (completedAttempts) {
            case 1: return 700L;
            case 2: return 1_800L;
            default: return 3_500L;
        }
    }

    /** Message affichable pour un echec definitif, avec un conseil utile. */
    public static String advise(int httpCode, String detail) {
        String base = detail == null ? "" : detail.trim();
        if (httpCode == 401 || httpCode == 403) {
            return "Cle refusee (HTTP " + httpCode + ") : verifie la cle du fournisseur.";
        }
        if (httpCode == 404) {
            return "Modele ou URL introuvable (404) : verifie l'identifiant du modele"
                    + " — ils sont sensibles a la casse et portent un prefixe (vendeur/modele).";
        }
        if (httpCode == 400 && mentionsImage(base)) {
            return "Ce modele ne sait pas lire les images (400) : prends-en un marque Vision.";
        }
        if (httpCode == 400 || httpCode == 422) {
            return "Requete refusee (" + httpCode + ")"
                    + (base.isEmpty() ? "" : " : " + base);
        }
        if (httpCode == 402) {
            return "Credit WaveSpeed epuise (402) : recharge le compte.";
        }
        if (httpCode == 429) {
            return "Quota atteint (429) apres plusieurs essais : patiente un peu.";
        }
        if (httpCode >= 500) {
            return "Le fournisseur repond " + httpCode + " apres "
                    + MAX_ATTEMPTS + " essais : panne de leur cote, reessaie plus tard.";
        }
        return base.isEmpty() ? ("HTTP " + httpCode) : base;
    }

    /** Vrai si le message d'erreur parle d'image : le modele n'a pas de vision. */
    static boolean mentionsImage(String detail) {
        String d = detail == null ? "" : detail.toLowerCase();
        return d.contains("image") || d.contains("vision") || d.contains("multimodal")
                || d.contains("image_url");
    }
}
