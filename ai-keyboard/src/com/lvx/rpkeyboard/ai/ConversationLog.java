package com.lvx.rpkeyboard.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * Journal du fil de conversation.
 *
 * Un ecran ne montre qu'une fenetre du fil. Pour reconstituer l'historique
 * sans doublon ni perte, {@link #merge} aligne la fenetre fraichement lue sur
 * le journal par <b>recouvrement</b>, et non par dedoublonnage : deux messages
 * identiques successifs (« ok », « ok ») sont legitimes et doivent survivre.
 *
 * Trois situations sont traitees :
 * <ol>
 *   <li>nouveaux messages en bas : la fenetre prolonge le journal ;</li>
 *   <li>remontee du fil : la fenetre precede le journal et le complete par le haut ;</li>
 *   <li>aucun recouvrement : on ajoute la fenetre a la suite, faute de mieux.</li>
 * </ol>
 */
public final class ConversationLog {

    /** Au-dela, on oublie les plus anciens : le compactage prend le relais. */
    public static final int MAX = 400;

    private final List<ChatMessage> items = new ArrayList<ChatMessage>();

    public List<ChatMessage> items() {
        return new ArrayList<ChatMessage>(items);
    }

    public int size() {
        return items.size();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public void clear() {
        items.clear();
    }

    public void replaceAll(List<ChatMessage> all) {
        items.clear();
        items.addAll(all);
        trim();
    }

    /** Absorbe une fenetre lue a l'ecran. */
    public void absorb(List<ChatMessage> window) {
        replaceAll(merge(items, clean(window)));
    }

    public ChatMessage last() {
        return items.isEmpty() ? null : items.get(items.size() - 1);
    }

    /** Dernier message recu de l'autre, ou null. */
    public ChatMessage lastFromOther() {
        for (int i = items.size() - 1; i >= 0; i--) {
            if (items.get(i).sender == ChatMessage.Sender.OTHER) {
                return items.get(i);
            }
        }
        return null;
    }

    private void trim() {
        while (items.size() > MAX) {
            items.remove(0);
        }
    }

    // ------------------------------------------------------------ recollage

    static List<ChatMessage> clean(List<ChatMessage> window) {
        List<ChatMessage> out = new ArrayList<ChatMessage>();
        if (window == null) {
            return out;
        }
        for (ChatMessage m : window) {
            if (m != null && !m.isBlank()) {
                out.add(m);
            }
        }
        return out;
    }

    /**
     * Recolle {@code window} sur {@code log}.
     *
     * @return une nouvelle liste, les entrees d'origine ne sont pas modifiees
     */
    public static List<ChatMessage> merge(List<ChatMessage> log, List<ChatMessage> window) {
        List<ChatMessage> base = new ArrayList<ChatMessage>(log == null ? new ArrayList<ChatMessage>() : log);
        List<ChatMessage> win = new ArrayList<ChatMessage>(window == null ? new ArrayList<ChatMessage>() : window);

        if (win.isEmpty()) {
            return base;
        }
        if (base.isEmpty()) {
            return win;
        }

        // Cas 1 : la fenetre prolonge le journal.
        // On cherche le plus grand k tel que les k derniers du journal
        // soient les k premiers de la fenetre.
        int tail = overlap(base, win);
        if (tail > 0) {
            List<ChatMessage> out = new ArrayList<ChatMessage>(base);
            out.addAll(win.subList(tail, win.size()));
            return out;
        }

        // Cas 2 : remontee du fil. La fenetre precede le journal :
        // les k derniers de la fenetre sont les k premiers du journal.
        int head = overlap(win, base);
        if (head > 0) {
            List<ChatMessage> out = new ArrayList<ChatMessage>(win.subList(0, win.size() - head));
            out.addAll(base);
            return out;
        }

        // Cas 3 : fenetre deja entierement connue ?
        if (containsRun(base, win)) {
            return base;
        }

        // Cas 4 : aucun recouvrement, on ajoute a la suite.
        List<ChatMessage> out = new ArrayList<ChatMessage>(base);
        out.addAll(win);
        return out;
    }

    /**
     * Plus grand k tel que les k derniers de {@code a} egalent les k premiers
     * de {@code b}. Zero s'il n'y a aucun recouvrement.
     */
    static int overlap(List<ChatMessage> a, List<ChatMessage> b) {
        int max = Math.min(a.size(), b.size());
        for (int k = max; k >= 1; k--) {
            boolean same = true;
            for (int i = 0; i < k; i++) {
                if (!a.get(a.size() - k + i).fingerprint.equals(b.get(i).fingerprint)) {
                    same = false;
                    break;
                }
            }
            if (same) {
                return k;
            }
        }
        return 0;
    }

    /** La fenetre apparait-elle telle quelle a l'interieur du journal. */
    static boolean containsRun(List<ChatMessage> base, List<ChatMessage> win) {
        if (win.size() > base.size()) {
            return false;
        }
        outer:
        for (int start = 0; start <= base.size() - win.size(); start++) {
            for (int i = 0; i < win.size(); i++) {
                if (!base.get(start + i).fingerprint.equals(win.get(i).fingerprint)) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    // -------------------------------------------------------- serialisation

    /** Format texte, une ligne par message : {@code M|texte} ou {@code O|texte}. */
    public String serialize() {
        StringBuilder sb = new StringBuilder();
        for (ChatMessage m : items) {
            sb.append(m.sender == ChatMessage.Sender.ME ? "M|" : "O|")
                    .append(m.text.replace("\n", "\\n"))
                    .append('\n');
        }
        return sb.toString();
    }

    public static ConversationLog parse(String data) {
        ConversationLog log = new ConversationLog();
        if (data == null || data.isEmpty()) {
            return log;
        }
        for (String line : data.split("\n")) {
            if (line.length() < 2 || line.charAt(1) != '|') {
                continue;
            }
            String text = line.substring(2).replace("\\n", "\n");
            log.items.add(line.charAt(0) == 'M'
                    ? ChatMessage.mine(text)
                    : ChatMessage.other(text));
        }
        log.trim();
        return log;
    }
}
