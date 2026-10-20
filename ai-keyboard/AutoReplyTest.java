import com.lvx.rpkeyboard.ai.ChatMessage;
import com.lvx.rpkeyboard.screen.AutoReplyController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Banc de test des garde-fous du mode automatique annonce. */
public class AutoReplyTest {

    static int ok = 0;
    static int ko = 0;

    /** Horloge pilotee, pour tester l'expiration sans attendre. */
    static final class FakeClock implements AutoReplyController.Clock {
        long t = 1_000_000L;

        @Override
        public long now() {
            return t;
        }
    }

    public static void main(String[] args) {

        // --- l'annonce commande tout -----------------------------------
        {
            AutoReplyController a = fresh();
            a.request("whatsapp", history());
            expect("pas actif avant l'annonce",
                    a.state() == AutoReplyController.State.ANNONCE_REQUISE && !a.isActive());
            expect("rien a envoyer avant l'annonce",
                    a.shouldReplyTo("whatsapp", history("O:coucou")) == null);
        }
        {
            AutoReplyController a = fresh();
            a.request("whatsapp", history());
            a.failDisclosure();
            expect("annonce echouee -> arret",
                    a.state() == AutoReplyController.State.ARRET
                            && a.lastStopReason() == AutoReplyController.StopReason.ECHEC_ANNONCE);
            expect("annonce echouee -> rien n'est envoye",
                    a.shouldReplyTo("whatsapp", history("O:coucou")) == null);
        }
        {
            AutoReplyController a = fresh();
            a.confirmDisclosureSent();
            expect("confirmation sans demande ne change rien",
                    a.state() == AutoReplyController.State.ARRET);
        }

        // --- fonctionnement normal -------------------------------------
        {
            AutoReplyController a = active("whatsapp");
            ChatMessage target = a.shouldReplyTo("whatsapp", history("O:tu es la ?"));
            expect("repond au message recu", target != null && "tu es la ?".equals(target.text));
        }
        {
            AutoReplyController a = active("whatsapp");
            expect("ne repond pas a mon propre message",
                    a.shouldReplyTo("whatsapp", history("M:je suis la")) == null);
        }
        {
            AutoReplyController a = active("whatsapp");
            List<ChatMessage> h = history("O:ça va ?");
            ChatMessage t = a.shouldReplyTo("whatsapp", h);
            a.markReplied(t);
            expect("ne repond pas deux fois au meme message",
                    a.shouldReplyTo("whatsapp", h) == null);
            expect("compteur incremente", a.sentCount() == 1);
        }
        {
            // Pas de reponse retroactive a ce qui precede l'activation.
            AutoReplyController a = fresh();
            a.request("whatsapp", history("O:vieux message"));
            a.confirmDisclosureSent();
            expect("pas de reponse retroactive",
                    a.shouldReplyTo("whatsapp", history("O:vieux message")) == null);
        }

        // --- coupures ---------------------------------------------------
        {
            AutoReplyController a = active("whatsapp");
            expect("changement de fil -> coupe",
                    a.shouldReplyTo("instagram", history("O:salut")) == null
                            && a.lastStopReason()
                            == AutoReplyController.StopReason.CHANGEMENT_DE_FIL);
        }
        {
            FakeClock c = new FakeClock();
            AutoReplyController a = new AutoReplyController(c);
            a.request("whatsapp", history());
            a.confirmDisclosureSent();
            c.t += a.timeoutMs + 1;
            expect("expiration -> coupe",
                    a.shouldReplyTo("whatsapp", history("O:encore la ?")) == null
                            && a.lastStopReason()
                            == AutoReplyController.StopReason.EXPIRATION);
        }
        {
            AutoReplyController a = active("whatsapp");
            a.maxMessages = 2;
            a.markReplied(ChatMessage.other("un"));
            a.markReplied(ChatMessage.other("deux"));
            expect("plafond -> coupe",
                    !a.isActive() && a.lastStopReason()
                            == AutoReplyController.StopReason.PLAFOND_MESSAGES);
        }
        {
            AutoReplyController a = active("whatsapp");
            a.onUserTyped();
            expect("frappe manuelle -> coupe",
                    !a.isActive() && a.lastStopReason()
                            == AutoReplyController.StopReason.UTILISATEUR);
        }
        {
            AutoReplyController a = active("whatsapp");
            a.onShake();
            expect("secousse -> coupe",
                    !a.isActive() && a.lastStopReason()
                            == AutoReplyController.StopReason.SECOUSSE);
        }
        {
            AutoReplyController a = fresh();
            a.onShake();
            expect("secousse a l'arret ne note rien", a.lastStopReason() == null);
        }

        // --- textes -----------------------------------------------------
        {
            AutoReplyController a = fresh();
            expect("annonce par defaut non vide",
                    a.disclosureText("").contains("assistant IA"));
            expect("annonce personnalisee respectee",
                    "coucou c'est un bot".equals(a.disclosureText("  coucou c'est un bot  ")));
            expect("marqueur ajoute",
                    a.decorate("salut").startsWith(AutoReplyController.MARKER));
        }

        System.out.println();
        System.out.println("reussis=" + ok + " echoues=" + ko);
        if (ko > 0) {
            System.exit(1);
        }
    }

    static AutoReplyController fresh() {
        return new AutoReplyController(new FakeClock());
    }

    static AutoReplyController active(String thread) {
        AutoReplyController a = fresh();
        a.request(thread, history());
        a.confirmDisclosureSent();
        return a;
    }

    static List<ChatMessage> history(String... specs) {
        List<ChatMessage> out = new ArrayList<ChatMessage>();
        for (String s : specs) {
            String text = s.substring(2);
            out.add(s.charAt(0) == 'M' ? ChatMessage.mine(text) : ChatMessage.other(text));
        }
        return out;
    }

    static void expect(String name, boolean condition) {
        if (condition) {
            ok++;
            System.out.println("  ok   " + name);
        } else {
            ko++;
            System.out.println("  KO   " + name);
        }
    }
}
