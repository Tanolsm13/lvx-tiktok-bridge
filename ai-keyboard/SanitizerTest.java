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

        // --- DECOUPAGE EN PLUSIEURS PROPOSITIONS -----------------------
        variants("separateur canonique",
                openai("Une.\\n<|sep|>\\nDeux.\\n<|sep|>\\nTrois."),
                Sanitizer.Shape.OPENAI,
                new String[]{"Une.", "Deux.", "Trois."});

        variants("separateur en ligne",
                openai("Une. <|sep|> Deux. <|sep|> Trois."),
                Sanitizer.Shape.OPENAI,
                new String[]{"Une.", "Deux.", "Trois."});

        // Le bug signale : ### devient un titre markdown et collait tout.
        variants("BUG titres markdown",
                openai("### Reponse 1\\nJe recule.\\n### Reponse 2\\nJe souris.\\n### Reponse 3\\nJe me tais."),
                Sanitizer.Shape.OPENAI,
                new String[]{"Je recule.", "Je souris.", "Je me tais."});

        variants("libelles en gras",
                openai("**Proposition 1**\\nViens la.\\n**Proposition 2**\\nReste ou tu es."),
                Sanitizer.Shape.OPENAI,
                new String[]{"Viens la.", "Reste ou tu es."});

        variants("liste numerotee",
                openai("1. Premiere.\\n2. Deuxieme.\\n3. Troisieme."),
                Sanitizer.Shape.OPENAI,
                new String[]{"Premiere.", "Deuxieme.", "Troisieme."});

        variants("regle horizontale",
                openai("Une.\\n---\\nDeux."),
                Sanitizer.Shape.OPENAI,
                new String[]{"Une.", "Deux."});

        // --- JSON NOYE DANS LA PROSE -----------------------------------
        variants("BUG json melange a du texte",
                openai("Voici mes propositions : {\\\"replies\\\":[\\\"Salut toi.\\\",\\\"Tu dors ?\\\"]} voila."),
                Sanitizer.Shape.OPENAI,
                new String[]{"Salut toi.", "Tu dors ?"});

        variants("json simple noye",
                openai("Reponse : {\\\"reply\\\":\\\"Je t'attends.\\\"}"),
                Sanitizer.Shape.OPENAI,
                new String[]{"Je t'attends."});

        // --- PIEGES du nouveau decoupage -------------------------------
        variants("PIEGE accolades sans cle connue",
                openai("Le panneau affichait {ACCES REFUSE} en rouge."),
                Sanitizer.Shape.OPENAI,
                new String[]{"Le panneau affichait {ACCES REFUSE} en rouge."});

        variants("PIEGE objet json sans cle de texte",
                openai("Il a tape {\\\"x\\\":1} sur le clavier."),
                Sanitizer.Shape.OPENAI,
                new String[]{"Il a tape {\"x\":1} sur le clavier."});

        variants("PIEGE un seul titre ne decoupe pas",
                openai("### Contexte\\nElle attend une reponse."),
                Sanitizer.Shape.OPENAI,
                new String[]{"Elle attend une reponse."});

        variants("PIEGE paragraphes d'une meme reponse",
                openai("Je m'approche.\\n\\nPuis je m'arrete net."),
                Sanitizer.Shape.OPENAI,
                new String[]{"Je m'approche.\n\nPuis je m'arrete net."});


        // --- diagnostic des reponses vides (le defaut de la 3.0) --------
        // Une 200 sans texte n'est pas une panne : c'est un refus, une coupe
        // ou un monologue. Les distinguer est tout l'interet du correctif.
        fails("refus explicite",
                "{\"choices\":[{\"message\":{\"content\":\"\",\"refusal\":"
                        + "\"I can't help with that\"}}]}",
                Sanitizer.Shape.OPENAI, Sanitizer.Kind.FILTRE);

        fails("arret pour filtrage de contenu",
                "{\"choices\":[{\"finish_reason\":\"content_filter\","
                        + "\"message\":{\"content\":\"\"}}]}",
                Sanitizer.Shape.OPENAI, Sanitizer.Kind.FILTRE);

        fails("filtrage sous le nom safety",
                "{\"choices\":[{\"native_finish_reason\":\"SAFETY\","
                        + "\"message\":{\"content\":\"\"}}]}",
                Sanitizer.Shape.OPENAI, Sanitizer.Kind.FILTRE);

        fails("plafond atteint avant le texte",
                "{\"choices\":[{\"finish_reason\":\"length\","
                        + "\"message\":{\"content\":\"\"}}]}",
                Sanitizer.Shape.OPENAI, Sanitizer.Kind.COUPE);

        fails("tout le budget parti en reflexion",
                "{\"choices\":[{\"finish_reason\":\"length\",\"message\":{"
                        + "\"content\":\"\",\"reasoning_content\":"
                        + "\"Bon, elle demande une reponse tendre, donc...\"}}]}",
                Sanitizer.Shape.OPENAI, Sanitizer.Kind.RAISONNEMENT);

        fails("reflexion dans reasoning_details",
                "{\"choices\":[{\"message\":{\"content\":\"\","
                        + "\"reasoning_details\":[{\"text\":\"je reflechis\"}]}}]}",
                Sanitizer.Shape.OPENAI, Sanitizer.Kind.RAISONNEMENT);

        fails("appel d'outil au lieu d'un texte",
                "{\"choices\":[{\"message\":{\"content\":\"\",\"tool_calls\":"
                        + "[{\"id\":\"c1\",\"type\":\"function\"}]}}]}",
                Sanitizer.Shape.OPENAI, Sanitizer.Kind.STRUCTURE);

        fails("vide sans explication",
                openai(""), Sanitizer.Shape.OPENAI, Sanitizer.Kind.VIDE);

        fails("bloc de reflexion jamais ferme",
                openai("<think>elle vient de dire qu'elle est fatiguee, donc"),
                Sanitizer.Shape.OPENAI, Sanitizer.Kind.RAISONNEMENT);

        fails("anthropic coupe net",
                "{\"stop_reason\":\"max_tokens\",\"content\":[{\"type\":\"text\","
                        + "\"text\":\"\"}]}",
                Sanitizer.Shape.ANTHROPIC, Sanitizer.Kind.COUPE);

        // Le monologue ne doit jamais atterrir dans la conversation, meme
        // quand le bloc n'est pas ferme : on garde ce qui precede, sinon rien.
        variants("texte avant un think non ferme",
                openai("Je te prends la main.\\n<think>est-ce trop ?"),
                Sanitizer.Shape.OPENAI,
                new String[]{"Je te prends la main."});

        variants("balise thinking fermee",
                openai("<thinking>plan</thinking>Je souris.") ,
                Sanitizer.Shape.OPENAI,
                new String[]{"Je souris."});

        // Le message existe mais le texte est en morceaux : forme courante
        // chez les passerelles compatibles OpenAI.
        variants("contenu en tableau de blocs",
                "{\"choices\":[{\"message\":{\"content\":[{\"type\":\"text\","
                        + "\"text\":\"Viens la.\"}]}}]}",
                Sanitizer.Shape.OPENAI,
                new String[]{"Viens la."});

        // Le detail doit nommer la cause : sans lui, l'app affiche « reponse
        // vide » et l'utilisateur n'a rien a corriger.
        detail("le detail nomme finish_reason",
                "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{"
                        + "\"content\":\"\"}}]}",
                "finish_reason");

        detail("le detail compte les caracteres de reflexion",
                "{\"choices\":[{\"message\":{\"content\":\"\","
                        + "\"reasoning\":\"abcdefghij\"}}]}",
                "10");

        System.out.println();
        System.out.println("reussis=" + ok + " echoues=" + ko);
        if (ko > 0) {
            System.exit(1);
        }
    }

    /** L'echec porte-t-il bien le detail qui permet de corriger ? */
    static void detail(String name, String body, String needle) {
        try {
            Sanitizer.variants(body, Sanitizer.Shape.OPENAI);
            fail(name, "aurait du echouer");
        } catch (Sanitizer.BadReply e) {
            String m = e.getMessage() == null ? "" : e.getMessage();
            if (m.contains(needle)) {
                pass(name);
            } else {
                fail(name, "detail <" + m + "> sans <" + needle + ">");
            }
        } catch (Exception e) {
            fail(name, "mauvaise exception " + e);
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
