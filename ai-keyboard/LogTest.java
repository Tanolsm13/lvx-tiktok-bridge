import com.lvx.rpkeyboard.ai.ChatMessage;
import com.lvx.rpkeyboard.ai.ConversationLog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Banc de test du recollage par recouvrement. */
public class LogTest {

    static int ok = 0;
    static int ko = 0;

    public static void main(String[] args) {

        // Cas 1 : nouveaux messages en bas.
        check("prolonge le journal",
                merge(log("O:salut", "M:hey"), log("M:hey", "O:tu fais quoi ?")),
                "O:salut", "M:hey", "O:tu fais quoi ?");

        // Cas 2 : remontee du fil, complete par le haut.
        check("remontee du fil",
                merge(log("M:hey", "O:tu fais quoi ?"), log("O:salut", "M:hey")),
                "O:salut", "M:hey", "O:tu fais quoi ?");

        // Cas 3 : fenetre deja connue, rien ne bouge.
        check("fenetre deja connue",
                merge(log("O:a", "M:b", "O:c"), log("M:b")),
                "O:a", "M:b", "O:c");

        // Cas 4 : aucun recouvrement, on ajoute a la suite.
        check("aucun recouvrement",
                merge(log("O:a"), log("M:z")),
                "O:a", "M:z");

        // PIEGE : deux « ok » successifs legitimes ne doivent pas fusionner.
        check("PIEGE deux ok successifs",
                merge(log("O:ok", "O:ok"), log("O:ok", "O:ok", "M:mdr")),
                "O:ok", "O:ok", "M:mdr");

        // PIEGE : le meme mot de chaque cote n'est pas le meme message.
        check("PIEGE meme texte expediteurs differents",
                merge(log("O:ok"), log("M:ok")),
                "O:ok", "M:ok");

        // PIEGE : recouvrement partiel long, un seul nouveau message.
        check("recouvrement long",
                merge(log("O:1", "M:2", "O:3", "M:4"), log("M:2", "O:3", "M:4", "O:5")),
                "O:1", "M:2", "O:3", "M:4", "O:5");

        // Journal vide.
        check("journal vide", merge(log(), log("O:a")), "O:a");

        // Fenetre vide.
        check("fenetre vide", merge(log("O:a"), log()), "O:a");

        // Les blancs sont ignores a l'absorption.
        ConversationLog cl = new ConversationLog();
        cl.absorb(Arrays.asList(ChatMessage.other("a"), ChatMessage.mine("   "), ChatMessage.other("b")));
        check("blancs ignores", cl.items(), "O:a", "O:b");

        // Aller-retour par serialisation.
        ConversationLog src = new ConversationLog();
        src.absorb(log("O:multi\nligne", "M:ok"));
        ConversationLog back = ConversationLog.parse(src.serialize());
        check("serialisation", back.items(), "O:multi\nligne", "M:ok");

        // Dernier message de l'autre.
        ConversationLog d = new ConversationLog();
        d.absorb(log("O:vieux", "M:moi", "O:recent", "M:encore moi"));
        ChatMessage lo = d.lastFromOther();
        if (lo != null && "recent".equals(lo.text)) {
            pass("dernier recu");
        } else {
            fail("dernier recu", String.valueOf(lo));
        }

        System.out.println();
        System.out.println("reussis=" + ok + " echoues=" + ko);
        if (ko > 0) {
            System.exit(1);
        }
    }

    static List<ChatMessage> merge(List<ChatMessage> a, List<ChatMessage> b) {
        return ConversationLog.merge(a, b);
    }

    /** "O:texte" ou "M:texte" */
    static List<ChatMessage> log(String... specs) {
        List<ChatMessage> out = new ArrayList<ChatMessage>();
        for (String s : specs) {
            String text = s.substring(2);
            out.add(s.charAt(0) == 'M' ? ChatMessage.mine(text) : ChatMessage.other(text));
        }
        return out;
    }

    static void check(String name, List<ChatMessage> got, String... expected) {
        if (got.size() != expected.length) {
            fail(name, "taille " + got.size() + " attendu " + expected.length + " -> " + got);
            return;
        }
        for (int i = 0; i < expected.length; i++) {
            ChatMessage m = got.get(i);
            String actual = (m.sender == ChatMessage.Sender.ME ? "M:" : "O:") + m.text;
            if (!expected[i].equals(actual)) {
                fail(name, "[" + i + "] obtenu <" + actual + "> attendu <" + expected[i] + ">");
                return;
            }
        }
        pass(name);
    }

    static void pass(String name) {
        ok++;
        System.out.println("  ok   " + name);
    }

    static void fail(String name, String why) {
        ko++;
        System.out.println("  KO   " + name + " : " + why);
    }
}
