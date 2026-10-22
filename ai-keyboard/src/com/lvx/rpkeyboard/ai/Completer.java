package com.lvx.rpkeyboard.ai;

/**
 * Ce dont {@link AiEngine} a besoin pour parler a un modele.
 *
 * L'interface existe pour que le moteur soit testable sans reseau : un faux
 * completeur permet de verifier la politique de reessai.
 */
public interface Completer {

    /**
     * Envoie la requete et rend le corps brut, sans l'interpreter.
     *
     * @param jpeg capture d'ecran facultative, pour les modeles a vision
     */
    String complete(String system, String user, byte[] jpeg) throws Exception;

    /** Forme de reponse attendue du fournisseur. */
    Sanitizer.Shape shape();

    /** Nom affichable du modele, pour dire qui a repondu. */
    String label();
}
