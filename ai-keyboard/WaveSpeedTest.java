import com.lvx.rpkeyboard.ai.WaveSpeed;

/** Banc de test de l'analyse des taches WaveSpeed (image et vocal). */
public class WaveSpeedTest {

    static int ok = 0;
    static int ko = 0;

    public static void main(String[] args) throws Exception {

        // --- soumission : les deux enveloppes ---------------------------
        expect("id enveloppe dans data",
                "abc123".equals(WaveSpeed.parseSubmitId(
                        "{\"code\":200,\"data\":{\"id\":\"abc123\",\"status\":\"created\"}}")));
        expect("id a plat",
                "xyz789".equals(WaveSpeed.parseSubmitId("{\"id\":\"xyz789\"}")));
        expect("id sous request_id",
                "r-1".equals(WaveSpeed.parseSubmitId("{\"data\":{\"request_id\":\"r-1\"}}")));
        failsSubmit("sans identifiant", "{\"code\":400,\"message\":\"cle invalide\"}");

        // --- sondage : etats --------------------------------------------
        {
            WaveSpeed.TaskState s = WaveSpeed.parseResult(
                    "{\"data\":{\"status\":\"processing\",\"outputs\":[]}}");
            expect("en cours : ni fini ni echoue", !s.isDone() && !s.isFailed());
        }
        {
            WaveSpeed.TaskState s = WaveSpeed.parseResult(
                    "{\"data\":{\"status\":\"completed\","
                            + "\"outputs\":[\"https://cdn.example/a.mp3\"]}}");
            expect("termine", s.isDone());
            expect("sortie lue", "https://cdn.example/a.mp3".equals(s.outputs.get(0)));
        }
        {
            WaveSpeed.TaskState s = WaveSpeed.parseResult(
                    "{\"status\":\"succeeded\",\"outputs\":[\"https://x/y.png\"]}");
            expect("succeeded compte comme termine", s.isDone());
        }
        {
            WaveSpeed.TaskState s = WaveSpeed.parseResult(
                    "{\"data\":{\"status\":\"failed\",\"error\":\"modele inconnu\"}}");
            expect("echec detecte", s.isFailed());
            expect("message d'echec lu", "modele inconnu".equals(s.error));
        }
        for (String bad : new String[]{"cancelled", "canceled", "timeout", "deleted", "error"}) {
            WaveSpeed.TaskState s = WaveSpeed.parseResult(
                    "{\"data\":{\"status\":\"" + bad + "\"}}");
            expect("etat terminal : " + bad, s.isFailed());
        }

        // --- formes de sortie -------------------------------------------
        {
            WaveSpeed.TaskState s = WaveSpeed.parseResult(
                    "{\"data\":{\"status\":\"completed\","
                            + "\"outputs\":[{\"url\":\"https://x/z.png\"}]}}");
            expect("sortie en objet", "https://x/z.png".equals(s.outputs.get(0)));
        }
        {
            WaveSpeed.TaskState s = WaveSpeed.parseResult(
                    "{\"data\":{\"status\":\"completed\",\"url\":\"https://x/seul.mp3\"}}");
            expect("sortie sous une cle unique",
                    "https://x/seul.mp3".equals(s.outputs.get(0)));
        }
        {
            WaveSpeed.TaskState s = WaveSpeed.parseResult(
                    "{\"data\":{\"status\":\"completed\",\"outputs\":[]}}");
            expect("termine sans fichier : liste vide", s.isDone() && s.outputs.isEmpty());
        }

        // --- valeurs par defaut -----------------------------------------
        expect("modele vocal par defaut",
                "elevenlabs/turbo-v2.5".equals(WaveSpeed.DEFAULT_VOICE_MODEL));
        expect("base d'API",
                "https://api.wavespeed.ai/api/v3/".equals(WaveSpeed.API_BASE));
        expect("endpoint texte",
                "https://llm.wavespeed.ai/v1/chat/completions".equals(WaveSpeed.LLM_URL));

        System.out.println();
        System.out.println("reussis=" + ok + " echoues=" + ko);
        if (ko > 0) {
            System.exit(1);
        }
    }

    static void failsSubmit(String name, String json) {
        try {
            WaveSpeed.parseSubmitId(json);
            fail(name + " : aurait du echouer");
        } catch (Exception e) {
            pass(name);
        }
    }

    static void expect(String name, boolean condition) {
        if (condition) {
            pass(name);
        } else {
            fail(name);
        }
    }

    static void pass(String name) {
        ok++;
        System.out.println("  ok   " + name);
    }

    static void fail(String name) {
        ko++;
        System.out.println("  KO   " + name);
    }
}
