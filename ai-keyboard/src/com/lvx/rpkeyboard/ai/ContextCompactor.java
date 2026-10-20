package com.lvx.rpkeyboard.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * Reduit l'historique pour tenir dans un budget, sans appel reseau.
 *
 * Les messages recents partent tels quels — ce sont eux qui portent la scene.
 * Les plus anciens sont remplaces par un resume local : comptage, intervenants,
 * et quelques repères tronques. C'est gratuit et instantane, contrairement a un
 * resume demande au modele.
 */
public final class ContextCompactor {

    /** Budget par defaut, en caracteres (~3 000 tokens). */
    public static final int DEFAULT_BUDGET = 12_000;

    /** Nombre de messages recents toujours conserves mot pour mot. */
    public static final int KEEP_VERBATIM = 14;

    public static final class Result {
        /** Resume des anciens messages, vide s'il n'y en avait pas. */
        public final String summary;
        /** Messages conserves tels quels, du plus ancien au plus recent. */
        public final List<ChatMessage> kept;

        Result(String summary, List<ChatMessage> kept) {
            this.summary = summary;
            this.kept = kept;
        }

        public boolean hasSummary() {
            return !summary.isEmpty();
        }
    }

    private ContextCompactor() {
    }

    public static Result compact(List<ChatMessage> history) {
        return compact(history, DEFAULT_BUDGET, KEEP_VERBATIM);
    }

    public static Result compact(List<ChatMessage> history, int budget, int keepVerbatim) {
        List<ChatMessage> all = history == null
                ? new ArrayList<ChatMessage>()
                : new ArrayList<ChatMessage>(history);

        if (all.isEmpty()) {
            return new Result("", all);
        }

        // On part des plus recents et on remonte tant que le budget tient.
        List<ChatMessage> kept = new ArrayList<ChatMessage>();
        int used = 0;
        int i = all.size() - 1;
        for (; i >= 0; i--) {
            ChatMessage m = all.get(i);
            int cost = m.text.length() + 8;
            boolean mustKeep = kept.size() < keepVerbatim;
            if (!mustKeep && used + cost > budget) {
                break;
            }
            kept.add(0, m);
            used += cost;
        }

        if (i < 0) {
            return new Result("", kept);
        }

        List<ChatMessage> older = all.subList(0, i + 1);
        return new Result(summarize(older), kept);
    }

    /** Resume local : ce qu'on peut dire sans comprendre le texte. */
    static String summarize(List<ChatMessage> older) {
        if (older.isEmpty()) {
            return "";
        }
        int mine = 0;
        int theirs = 0;
        for (ChatMessage m : older) {
            if (m.sender == ChatMessage.Sender.ME) {
                mine++;
            } else {
                theirs++;
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Debut de conversation resume : ")
                .append(older.size()).append(" messages echanges avant cet extrait (")
                .append(mine).append(" de moi, ").append(theirs).append(" d'elle).");

        // Quelques reperes : premier message, et les plus longs, tronques.
        sb.append("\nPremier message : ").append(snippet(older.get(0)));

        List<ChatMessage> longest = new ArrayList<ChatMessage>(older);
        // Tri par longueur decroissante, insertion simple : les listes sont courtes.
        for (int a = 0; a < longest.size(); a++) {
            for (int b = a + 1; b < longest.size(); b++) {
                if (longest.get(b).text.length() > longest.get(a).text.length()) {
                    ChatMessage tmp = longest.get(a);
                    longest.set(a, longest.get(b));
                    longest.set(b, tmp);
                }
            }
        }
        int shown = Math.min(3, longest.size());
        if (shown > 0) {
            sb.append("\nMoments marquants :");
            for (int k = 0; k < shown; k++) {
                sb.append("\n- ").append(snippet(longest.get(k)));
            }
        }
        return sb.toString();
    }

    private static String snippet(ChatMessage m) {
        String who = m.sender == ChatMessage.Sender.ME ? "moi" : "elle";
        String t = m.text.trim().replaceAll("\\s+", " ");
        if (t.length() > 120) {
            t = t.substring(0, 120) + "…";
        }
        return who + " : " + t;
    }
}
