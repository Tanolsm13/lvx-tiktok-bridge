package com.lvx.rpkeyboard;

import java.util.List;

/**
 * Lien direct entre le service de capture et le clavier.
 * Les deux vivent dans le meme process, un singleton suffit.
 */
public final class Bus {

    public interface Listener {
        void onStatus(String message);

        void onSuggestions(List<String> replies);

        void onError(String message);
    }

    private static Listener listener;

    private Bus() {
    }

    public static synchronized void setListener(Listener l) {
        listener = l;
    }

    private static synchronized Listener get() {
        return listener;
    }

    public static void status(String message) {
        Listener l = get();
        if (l != null) {
            l.onStatus(message);
        }
    }

    public static void suggestions(List<String> replies) {
        Listener l = get();
        if (l != null) {
            l.onSuggestions(replies);
        }
    }

    public static void error(String message) {
        Listener l = get();
        if (l != null) {
            l.onError(message);
        }
    }
}
