import com.lvx.rpkeyboard.ai.ChatMessage;
import com.lvx.rpkeyboard.ai.ContextCompactor;

import java.util.ArrayList;
import java.util.List;

/**
 * Banc de test du compactage : la garantie est que la sortie ne depasse
 * jamais le budget, quelle que soit l'entree.
 */
public class BudgetTest {

    static int ok = 0;
    static int ko = 0;

    public static void main(String[] args) {

        // --- la garantie, sur des entrees hostiles ---------------------
        {
            // Le trou de la version precedente : 14 messages enormes etaient
            // tous conserves « quoi qu'il arrive ».
            ContextCompactor.Result r = ContextCompactor.compact(
                    many(14, 10_000), 64_000, 14);
            expect("14 messages de 10k tiennent dans le budget", r.chars <= 64_000);
            // Sous le plafond par message (16k), ils sont ecartes et non coupes.
            expect("les plus anciens sont ecartes", r.kept.size() < 14);
            expect("un resume les remplace", r.hasSummary());
        }
        {
            // Au-dessus du plafond par message, le raccourcissement s'applique.
            ContextCompactor.Result r = ContextCompactor.compact(
                    many(3, 50_000), 64_000, 14);
            expect("message au-dela du plafond : tient", r.chars <= 64_000);
            expect("message au-dela du plafond : raccourci", r.truncated);
        }
        {
            ContextCompactor.Result r = ContextCompactor.compact(
                    many(400, 2_000), 64_000, 14);
            expect("400 messages tiennent", r.chars <= 64_000);
            expect("un resume est produit", r.hasSummary());
        }
        {
            ContextCompactor.Result r = ContextCompactor.compact(
                    many(1, 500_000), 64_000, 14);
            expect("un seul message demesure tient", r.chars <= 64_000);
            expect("il reste un message", r.kept.size() == 1);
        }
        {
            // Budget minuscule : la garantie doit tenir quand meme.
            ContextCompactor.Result r = ContextCompactor.compact(
                    many(50, 4_000), 600, 14);
            expect("budget plancher respecte", r.chars <= 600);
        }
        {
            ContextCompactor.Result r = ContextCompactor.compact(
                    many(30, 300), 5_000, 14);
            expect("budget moyen respecte", r.chars <= 5_000);
        }

        // --- balayage : la garantie sur beaucoup de combinaisons --------
        {
            boolean all = true;
            int[] budgets = {600, 2_000, 12_000, 64_000, 160_000};
            int[] counts = {1, 5, 14, 60, 300};
            int[] sizes = {10, 500, 5_000, 30_000};
            for (int b : budgets) {
                for (int c : counts) {
                    for (int sz : sizes) {
                        ContextCompactor.Result r =
                                ContextCompactor.compact(many(c, sz), b, 14);
                        if (r.chars > b) {
                            all = false;
                            System.out.println("       depassement : budget=" + b
                                    + " n=" + c + " taille=" + sz + " -> " + r.chars);
                        }
                    }
                }
            }
            expect("garantie tenue sur 100 combinaisons", all);
        }

        // --- comportement normal ---------------------------------------
        {
            ContextCompactor.Result r = ContextCompactor.compact(
                    many(6, 200), 64_000, 14);
            expect("conversation courte : tout est garde", r.kept.size() == 6);
            expect("conversation courte : pas de resume", !r.hasSummary());
            expect("conversation courte : rien de raccourci", !r.truncated);
        }
        {
            ContextCompactor.Result r = ContextCompactor.compact(
                    new ArrayList<ChatMessage>(), 64_000, 14);
            expect("historique vide", r.chars == 0 && r.kept.isEmpty());
        }
        {
            ContextCompactor.Result r = ContextCompactor.compact(null, 64_000, 14);
            expect("historique null", r.chars == 0 && r.kept.isEmpty());
        }
        {
            // Le dernier message recu doit survivre : c'est celui auquel repondre.
            List<ChatMessage> h = many(20, 1_000);
            h.add(ChatMessage.other("Le dernier, celui qui compte."));
            ContextCompactor.Result r = ContextCompactor.compact(h, 2_000, 14);
            ChatMessage last = r.kept.get(r.kept.size() - 1);
            expect("le dernier message est conserve",
                    last.text.startsWith("Le dernier"));
        }

        // --- reserve pour l'image --------------------------------------
        {
            int plain = 64_000;
            int withImg = ContextCompactor.budgetWithImage(plain);
            expect("l'image reduit le budget", withImg < plain);
            expect("reduction egale a la reserve",
                    withImg == plain - ContextCompactor.IMAGE_RESERVE_CHARS);
            expect("jamais sous le plancher",
                    ContextCompactor.budgetWithImage(100)
                            >= ContextCompactor.MIN_BUDGET);
        }

        // --- la fenetre de 200k ----------------------------------------
        {
            // Reglages par defaut : 200 000 tokens, 8 % pour l'historique.
            int budgetChars = 200_000 * 8 / 100 * 4;   // 64 000
            ContextCompactor.Result r = ContextCompactor.compact(
                    many(500, 3_000), ContextCompactor.budgetWithImage(budgetChars), 14);
            int tokens = r.chars / 4 + ContextCompactor.IMAGE_RESERVE_CHARS / 4;
            expect("historique + image restent loin des 200k tokens",
                    tokens < 20_000);
            System.out.println("       cout estime : " + tokens + " tokens sur 200000");
        }

        System.out.println();
        System.out.println("reussis=" + ok + " echoues=" + ko);
        if (ko > 0) {
            System.exit(1);
        }
    }

    /** n messages alternes de taille donnee. */
    static List<ChatMessage> many(int n, int size) {
        List<ChatMessage> out = new ArrayList<ChatMessage>();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < size; i++) {
            sb.append('x');
        }
        String body = sb.toString();
        for (int i = 0; i < n; i++) {
            String text = i + " " + body;
            out.add(i % 2 == 0 ? ChatMessage.other(text) : ChatMessage.mine(text));
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
