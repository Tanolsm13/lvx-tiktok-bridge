package com.lvx.rpkeyboard.ai;

/** Un message du fil, tel qu'il a ete lu a l'ecran. */
public final class ChatMessage {

    public enum Sender {
        /** Moi : bulle alignee a droite. */
        ME,
        /** L'autre : bulle alignee a gauche. */
        OTHER
    }

    public final Sender sender;
    public final String text;

    /**
     * Identite stable d'un message, pour le recollage et pour savoir si on a
     * deja traite un message. Volontairement base sur le contenu normalise :
     * deux « ok » successifs legitimes ont la meme empreinte, c'est pourquoi le
     * recollage se fait par alignement de recouvrement et non par
     * dedoublonnage.
     */
    public final String fingerprint;

    public ChatMessage(Sender sender, String text) {
        this.sender = sender;
        this.text = text == null ? "" : text;
        this.fingerprint = sender.name() + ":" + normalize(this.text);
    }

    public static ChatMessage mine(String text) {
        return new ChatMessage(Sender.ME, text);
    }

    public static ChatMessage other(String text) {
        return new ChatMessage(Sender.OTHER, text);
    }

    public boolean isBlank() {
        return text.trim().isEmpty();
    }

    private static String normalize(String s) {
        return s.trim().replaceAll("\\s+", " ");
    }

    @Override
    public String toString() {
        return (sender == Sender.ME ? "moi: " : "elle: ") + text;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ChatMessage)) {
            return false;
        }
        return fingerprint.equals(((ChatMessage) o).fingerprint);
    }

    @Override
    public int hashCode() {
        return fingerprint.hashCode();
    }
}
