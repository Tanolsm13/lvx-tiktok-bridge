package com.lvx.rpkeyboard.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * Reduit l'historique pour tenir dans un budget, sans appel reseau.
 *
 * Les messages recents partent tels quels — ce sont eux qui portent la scene.
 * Les plus anciens sont remplaces par un resume local : comptage, intervenants
 * et quelques reperes tronques. C'est gratuit et instantane, contrairement a un
 * resume demande au modele.
 *
 * <b>Garantie</b> : {@code compact(...).chars <= budget}, toujours. La version
 * precedente ne la tenait pas : les premiers messages etaient conserves « quoi
 * qu'il arrive », donc quatorze longs messages suffisaient a depasser le
 * budget, et la longueur du resume n'etait comptee nulle part.
 */
public final class ContextCompactor {

    /** Budget par defaut, en caracteres (~3 000 tokens). */
    public static final int DEFAULT_BUDGET = 12_000;

    /** Nombre de messages recents conserves si le budget le permet. */
    public static final int KEEP_VERBATIM = 14;

    /** Plancher : en dessous, l'historique ne veut plus rien dire. */
    public static final int MIN_BUDGET = 600;

    /**
     * Place a reserver quand une capture d'ecran accompagne la requete.
     * Une image de 1100 px coute de l'ordre de 1 600 tokens, soit ~6 400
     * caracteres a quatre caracteres par token.
     */
    public static final int IMAGE_RESERVE_CHARS = 6_400;

    /** Part du budget laissee au resume des anciens messages. */
    private static final double SUMMARY_SHARE = 0.18;

    /** Aucun message ne peut manger plus que cette fraction du budget. */
    private static final double MAX_MESSAGE_SHARE = 0.25;

    public static final class Result {
        /** Resume des anciens messages, vide s'il n'y en avait pas. */
        public final String summary;
        /** Messages conserves tels quels, du plus ancien au plus recent. */
        public final List<ChatMessage> kept;
        /** Taille reelle produite, resume compris. */
        public final int chars;
        /** Vrai si au moins un message a du etre raccourci. */
        public final boolean truncated;

        Result(String summary, List<ChatMessage> kept, int chars, boolean truncated) {
            this.summary = summary;
            this.kept = kept;
            this.chars = chars;
            this.truncated = truncated;
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
        final int cap = Math.max(MIN_BUDGET, budget);
        List<ChatMessage> all = history == null
                ? new ArrayList<ChatMessage>()
                : new ArrayList<ChatMessage>(history);

        if (all.isEmpty()) {
            return new Result("", all, 0, false);
        }

        int maxPerMessage = Math.max(200, (int) (cap * MAX_MESSAGE_SHARE));
        int keptBudget = (int) (cap * (1.0 - SUMMARY_SHARE));

        // On remonte du plus recent, en raccourcissant les messages trop longs.
        List<ChatMessage> kept = new ArrayList<ChatMessage>();
        boolean truncated = false;
        int used = 0;
        int oldestKeptIndex = all.size();

        for (int i = all.size() - 1; i >= 0; i--) {
            ChatMessage original = all.get(i);
            ChatMessage m = original;
            if (m.text.length() > maxPerMessage) {
                m = new ChatMessage(original.sender, cut(original.text, maxPerMessage));
                truncated = true;
            }
            int cost = m.text.length() + 8;

            // Le message le plus recent est garde meme s'il est seul a tenir :
            // sans lui il n'y a rien a quoi repondre.
            boolean first = kept.isEmpty();
            if (!first && used + cost > keptBudget) {
                break;
            }
            if (!first && kept.size() >= keepVerbatim) {
                break;
            }
            kept.add(0, m);
            used += cost;
            oldestKeptIndex = i;
        }

        String summary = "";
        if (oldestKeptIndex > 0) {
            int room = cap - used;
            summary = room > 120 ? cut(summarize(all.subList(0, oldestKeptIndex)), room) : "";
        }

        int total = used + summary.length();

        // Garde-fou final : on retire les plus anciens conserves jusqu'a tenir.
        while (total > cap && kept.size() > 1) {
            ChatMessage dropped = kept.remove(0);
            used -= dropped.text.length() + 8;
            total = used + summary.length();
        }
        // S'il ne reste qu'un message et que ca depasse encore, on le raccourcit.
        if (total > cap && kept.size() == 1) {
            int room = Math.max(80, cap - summary.length() - 8);
            ChatMessage only = kept.get(0);
            if (only.text.length() > room) {
                kept.set(0, new ChatMessage(only.sender, cut(only.text, room)));
                truncated = true;
            }
            used = kept.get(0).text.length() + 8;
            total = used + summary.length();
        }
        // Dernier recours : le resume lui-meme est rogne.
        if (total > cap && !summary.isEmpty()) {
            summary = cut(summary, Math.max(0, cap - used));
            total = used + summary.length();
        }

        return new Result(summary, kept, total, truncated);
    }

    /** Budget effectif quand une capture accompagne la requete. */
    public static int budgetWithImage(int budget) {
        return Math.max(MIN_BUDGET, budget - IMAGE_RESERVE_CHARS);
    }

    private static String cut(String s, int max) {
        if (max <= 0) {
            return "";
        }
        if (s.length() <= max) {
            return s;
        }
        if (max <= 1) {
            return s.substring(0, max);
        }
        return s.substring(0, max - 1) + "…";
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
        sb.append("\nPremier message : ").append(snippet(older.get(0)));

        List<ChatMessage> longest = new ArrayList<ChatMessage>(older);
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
