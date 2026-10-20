package com.lvx.rpkeyboard;

import java.util.ArrayList;
import java.util.List;

/**
 * Lien entre le service de capture et les surfaces qui affichent le resultat.
 *
 * Il y en a deux desormais, le clavier et la bulle flottante, et elles peuvent
 * etre actives en meme temps : la liste remplace le listener unique d'avant.
 */
public final class Bus {

    public interface Listener {
        void onStatus(String message);

        void onSuggestions(List<String> replies);

        void onError(String message);
    }

    private static final List<Listener> listeners = new ArrayList<Listener>();

    private Bus() {
    }

    public static synchronized void add(Listener l) {
        if (l != null && !listeners.contains(l)) {
            listeners.add(l);
        }
    }

    public static synchronized void remove(Listener l) {
        listeners.remove(l);
    }

    private static synchronized List<Listener> snapshot() {
        return new ArrayList<Listener>(listeners);
    }

    public static void status(String message) {
        for (Listener l : snapshot()) {
            try {
                l.onStatus(message);
            } catch (Exception ignored) {
                // une surface defaillante ne doit pas bloquer les autres
            }
        }
    }

    public static void suggestions(List<String> replies) {
        for (Listener l : snapshot()) {
            try {
                l.onSuggestions(replies);
            } catch (Exception ignored) {
                // idem
            }
        }
    }

    public static void error(String message) {
        for (Listener l : snapshot()) {
            try {
                l.onError(message);
            } catch (Exception ignored) {
                // idem
            }
        }
    }
}
