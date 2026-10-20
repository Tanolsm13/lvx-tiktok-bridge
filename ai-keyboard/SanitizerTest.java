import com.lvx.rpkeyboard.ai.Sanitizer;

import java.util.List;

/** Banc de test du Sanitizer : cas normaux, formes degradees, et pieges. */
public class SanitizerTest {

    static int ok = 0;
    static int ko = 0;

    public static void main(String[] args) {
        // --- formes normales -------------------------------------------
        variants("texte simple",
                openai("Je recule d'un pas, sans la lacher du regard."),
                Sanitizer.Shape.OPENAI,
                new String[]{"Je recule d'un pas, sans la lacher du regard."});

        variants("trois variantes separees",
                openai("Premiere.\n###\nDeuxieme, plus longue.\n###\nTroisieme ?"),
                Sanitizer.Shape.OPENAI,
                new String[]{"Premiere.", "Deuxieme, plus longue.", "Troisieme ?"});

        variants("forme anthropic",
                "{\"content\":[{\"type\":\"text\",\"text\":\"Salut toi.\"}]}",
                Sanitizer.Shape.ANTHROPIC,
                new String[]{"Salut toi."});

        // --- formes degradees ------------------------------------------
        variants("json replies malgre la consigne",
                openai("{\\\"replies\\\":[\\\"A\\\",\\\"B\\\"]}"),
                Sanitizer.Shape.OPENAI,
                new String[]{"A", "B"});

        variants("bloc markdown enveloppant",
                openai("```\\nJe souris sans repondre.\\n```"),
                Sanitizer.Shape.OPENAI,
                new String[]{"Je souris sans repondre."});

        variants("prefixe de role",
                openai("Assistant: Tu crois vraiment ?"),
                Sanitizer.Shape.OPENAI,
                new String[]{"Tu crois vraiment ?"});

        variants("balise think",
                openai("<think>elle teste</think>Je hausse les epaules."),
                Sanitizer.Shape.OPENAI,
                new String[]{"Je hausse les epaules."});

        variants("guillemets enveloppants",
                openai("\\\"Viens plus pres.\\\""),
                Sanitizer.Shape.OPENAI,
                new String[]{"Viens plus pres."});

        variants("flux sse qui a fuite",
                "data: {\"choices\":[{\"delta\":{\"content\":\"Je \"}}]}\n"
                        + "data: {\"choices\":[{\"delta\":{\"content\":\"reste.\"}}]}\n"
                        + "data: [DONE]",
                Sanitizer.Shape.OPENAI,
                new String[]{"Je reste."});

        // --- PIEGES : doivent passer intacts ---------------------------
        variants("PIEGE accolades legitimes",
                openai("Le panneau affichait {ACCES REFUSE} en rouge."),
                Sanitizer.Shape.OPENAI,
                new String[]{"Le panneau affichait {ACCES REFUSE} en rouge."});

        variants("PIEGE apostrophe interne",
                openai("J'hesite, puis j'avance d'un pas."),
                Sanitizer.Shape.OPENAI,
                new String[]{"J'hesite, puis j'avance d'un pas."});

        variants("PIEGE guillemets internes conserves",
                openai("Elle a dit \\\"non\\\" et elle a raccroche."),
                Sanitizer.Shape.OPENAI,
                new String[]{"Elle a dit \"non\" et elle a raccroche."});

        // --- echecs typés : rien ne doit etre insere --------------------
        fails("json irreductible", openai("{\\\"foo\\\":{\\\"bar\\\":1}}"),
                Sanitizer.Shape.OPENAI, Sanitizer.Kind.ENCORE_JSON);

        fails("erreur d'api", "{\"error\":{\"message\":\"invalid api key\"}}",
                Sanitizer.Shape.OPENAI, Sanitizer.Kind.ERREUR_API);

        fails("corps illisible", "<html>502 Bad Gateway</html>",
                Sanitizer.Shape.OPENAI, Sanitizer.Kind.STRUCTURE);

        fails("contenu vide", openai(""),
                Sanitizer.Shape.OPENAI, Sanitizer.Kind.VIDE);

        fails("choices absent", "{\"id\":\"x\"}",
                Sanitizer.Shape.OPENAI, Sanitizer.Kind.STRUCTURE);

        System.out.println();
        System.out.println("reussis=" + ok + " echoues=" + ko);
        if (ko > 0) {
            System.exit(1);
        }
    }

    /** Enveloppe un contenu dans une reponse OpenAI. Le contenu est deja echappe. */
    static String openai(String escapedContent) {
        return "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\""
                + escapedContent + "\"}}]}";
    }

    static void variants(String name, String body, Sanitizer.Shape shape, String[] expected) {
        try {
            List<String> got = Sanitizer.variants(body, shape);
            if (got.size() != expected.length) {
                fail(name, "taille " + got.size() + " attendu " + expected.length + " -> " + got);
                return;
            }
            for (int i = 0; i < expected.length; i++) {
                if (!expected[i].equals(got.get(i))) {
                    fail(name, "[" + i + "] obtenu <" + got.get(i) + "> attendu <" + expected[i] + ">");
                    return;
                }
            }
            pass(name);
        } catch (Exception e) {
            fail(name, "exception " + e.getClass().getSimpleName() + " : " + e.getMessage());
        }
    }

    static void fails(String name, String body, Sanitizer.Shape shape, Sanitizer.Kind expected) {
        try {
            List<String> got = Sanitizer.variants(body, shape);
            fail(name, "aurait du echouer, a renvoye " + got);
        } catch (Sanitizer.BadReply e) {
            if (e.kind == expected) {
                pass(name);
            } else {
                fail(name, "kind " + e.kind + " attendu " + expected);
            }
        } catch (Exception e) {
            fail(name, "mauvaise exception " + e);
        }
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
