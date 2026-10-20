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
            return "Modele ou URL introuvable (404) : verifie l'identifiant du modele.";
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
}
