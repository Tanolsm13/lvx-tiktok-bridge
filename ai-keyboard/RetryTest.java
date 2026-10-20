import com.lvx.rpkeyboard.ai.AiEngine;
import com.lvx.rpkeyboard.ai.Completer;
import com.lvx.rpkeyboard.ai.LlmClient;
import com.lvx.rpkeyboard.ai.PromptBuilder;
import com.lvx.rpkeyboard.ai.Retry;
import com.lvx.rpkeyboard.ai.Sanitizer;

import java.net.SocketTimeoutException;
import java.util.ArrayList;

/** Banc de test de la politique de reessai : erreurs 500, reponses vides. */
public class RetryTest {

    static int ok = 0;
    static int ko = 0;

    /** Fournisseur simule : joue un scenario, sans reseau. */
    static final class Fake implements Completer {
        private final String[] script;
        int calls = 0;

        Fake(String... script) {
            this.script = script;
        }

        @Override
        public String complete(String system, String user, byte[] jpeg) throws Exception {
            String step = script[Math.min(calls, script.length - 1)];
            calls++;
            if (step.startsWith("http:")) {
                throw new LlmClient.HttpError(
                        Integer.parseInt(step.substring(5)), "panne simulee");
            }
            if ("timeout".equals(step)) {
                throw new SocketTimeoutException("delai depasse");
            }
            if ("empty".equals(step)) {
                return body("");
            }
            if ("json".equals(step)) {
                return body("{\\\"foo\\\":{\\\"bar\\\":1}}");
            }
            return body(step);
        }

        @Override
        public Sanitizer.Shape shape() {
            return Sanitizer.Shape.OPENAI;
        }

        private static String body(String content) {
            return "{\"choices\":[{\"message\":{\"content\":\"" + content + "\"}}]}";
        }
    }

    public static void main(String[] args) {

        // --- politique pure -------------------------------------------
        expect("500 reessayable", Retry.shouldRetry(500));
        expect("502 reessayable", Retry.shouldRetry(502));
        expect("503 reessayable", Retry.shouldRetry(503));
        expect("429 reessayable", Retry.shouldRetry(429));
        expect("408 reessayable", Retry.shouldRetry(408));
        expect("401 non reessayable", !Retry.shouldRetry(401));
        expect("404 non reessayable", !Retry.shouldRetry(404));
        expect("400 non reessayable", !Retry.shouldRetry(400));

        expect("timeout reessayable", Retry.isTransient(new SocketTimeoutException()));
        expect("exception quelconque non reessayable",
                !Retry.isTransient(new IllegalStateException()));

        expect("reponse vide reessayable", Retry.shouldRetry(Sanitizer.Kind.VIDE));
        expect("structure reessayable", Retry.shouldRetry(Sanitizer.Kind.STRUCTURE));
        expect("json irreductible non reessayable",
                !Retry.shouldRetry(Sanitizer.Kind.ENCORE_JSON));
        expect("erreur d'api non reessayable",
                !Retry.shouldRetry(Sanitizer.Kind.ERREUR_API));

        expect("attente croissante",
                Retry.backoffMs(1) < Retry.backoffMs(2));

        // --- le moteur ------------------------------------------------
        {
            Fake f = new Fake("http:500", "http:500", "Ca remarche.");
            AiEngine.Result r = run(f);
            expect("500 puis 500 puis succes", r.isOk() && r.attempts == 3
                    && "Ca remarche.".equals(r.variants.get(0)));
            expect("trois appels consommes", f.calls == 3);
        }
        {
            Fake f = new Fake("http:500");
            AiEngine.Result r = run(f);
            expect("500 persistant -> echec apres 3 essais",
                    !r.isOk() && r.attempts == 3 && f.calls == 3);
            expect("message de 500 explicite", r.error.contains("500"));
        }
        {
            Fake f = new Fake("http:401");
            AiEngine.Result r = run(f);
            expect("cle refusee -> echec immediat",
                    !r.isOk() && r.attempts == 1 && f.calls == 1);
            expect("message de cle explicite",
                    r.error.toLowerCase().contains("cle"));
        }
        {
            Fake f = new Fake("empty", "Enfin du texte.");
            AiEngine.Result r = run(f);
            expect("reponse vide puis succes", r.isOk() && r.attempts == 2);
        }
        {
            Fake f = new Fake("empty");
            AiEngine.Result r = run(f);
            expect("vide persistant -> echec signale mal forme",
                    !r.isOk() && r.malformed && r.attempts == 3);
        }
        {
            Fake f = new Fake("json");
            AiEngine.Result r = run(f);
            expect("json irreductible -> pas de reessai",
                    !r.isOk() && r.attempts == 1 && f.calls == 1);
        }
        {
            Fake f = new Fake("timeout", "timeout", "Troisieme fois.");
            AiEngine.Result r = run(f);
            expect("timeout puis succes", r.isOk() && r.attempts == 3);
        }
        {
            final int[] notified = {0};
            Fake f = new Fake("http:503", "Bon.");
            new AiEngine(f, cfg())
                    .withSleeper(new AiEngine.Sleeper() {
                        @Override
                        public void sleep(long ms) {
                        }
                    })
                    .withProgress(new AiEngine.Progress() {
                        @Override
                        public void onRetry(int attempt, int total, String why) {
                            notified[0]++;
                        }
                    })
                    .fromHistory(new ArrayList<com.lvx.rpkeyboard.ai.ChatMessage>());
            expect("l'interface est prevenue du reessai", notified[0] == 1);
        }

        System.out.println();
        System.out.println("reussis=" + ok + " echoues=" + ko);
        if (ko > 0) {
            System.exit(1);
        }
    }

    static AiEngine.Result run(Fake f) {
        return new AiEngine(f, cfg())
                .withSleeper(new AiEngine.Sleeper() {
                    @Override
                    public void sleep(long ms) {
                    }
                })
                .fromHistory(new ArrayList<com.lvx.rpkeyboard.ai.ChatMessage>());
    }

    static PromptBuilder.Config cfg() {
        PromptBuilder.Config c = new PromptBuilder.Config();
        c.count = 1;
        return c;
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
