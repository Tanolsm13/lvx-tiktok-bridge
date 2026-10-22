import com.lvx.rpkeyboard.ai.AiEngine;
import com.lvx.rpkeyboard.ai.ChatMessage;
import com.lvx.rpkeyboard.ai.Completer;
import com.lvx.rpkeyboard.ai.LlmClient;
import com.lvx.rpkeyboard.ai.PromptBuilder;
import com.lvx.rpkeyboard.ai.Retry;
import com.lvx.rpkeyboard.ai.Sanitizer;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Banc de test de la chaine de modeles.
 *
 * Le defaut que ces tests verrouillent : en 3.0, un refus de filtrage rendait
 * une reponse vide, l'app reessayait trois fois le meme modele, et l'ecran
 * affichait « reponse vide » sans rien proposer. Ici on verifie qu'un autre
 * modele est essaye quand cela a un sens, et seulement alors — un incident
 * reseau ou une cle refusee frapperaient le suivant pareil.
 */
public class ChainTest {

    static int ok = 0;
    static int ko = 0;

    /** Modele simule : un scenario, un nom, aucun reseau. */
    static final class Fake implements Completer {
        private final String name;
        private final String[] script;
        int calls = 0;

        Fake(String name, String... script) {
            this.name = name;
            this.script = script;
        }

        @Override
        public String label() {
            return name;
        }

        @Override
        public Sanitizer.Shape shape() {
            return Sanitizer.Shape.OPENAI;
        }

        @Override
        public String complete(String system, String user, byte[] jpeg) throws Exception {
            String step = script[Math.min(calls, script.length - 1)];
            calls++;
            if (step.startsWith("http:")) {
                throw new LlmClient.HttpError(
                        Integer.parseInt(step.substring(5)), "panne simulee");
            }
            if (step.startsWith("http400image:")) {
                throw new LlmClient.HttpError(400, "image_url is not supported");
            }
            if ("timeout".equals(step)) {
                throw new SocketTimeoutException("delai depasse");
            }
            if ("filtre".equals(step)) {
                return "{\"choices\":[{\"finish_reason\":\"content_filter\","
                        + "\"message\":{\"content\":\"\"}}]}";
            }
            if ("coupe".equals(step)) {
                return "{\"choices\":[{\"finish_reason\":\"length\","
                        + "\"message\":{\"content\":\"\"}}]}";
            }
            if ("reflexion".equals(step)) {
                return "{\"choices\":[{\"finish_reason\":\"length\",\"message\":{"
                        + "\"content\":\"\",\"reasoning_content\":\"je reflechis...\"}}]}";
            }
            if ("vide".equals(step)) {
                return "{\"choices\":[{\"message\":{\"content\":\"\"}}]}";
            }
            return "{\"choices\":[{\"message\":{\"content\":\"" + step + "\"}}]}";
        }
    }

    public static void main(String[] args) {

        // --- la decision pure ------------------------------------------
        expect("un filtre merite un autre modele",
                Retry.worthAnotherModel(Sanitizer.Kind.FILTRE));
        expect("une coupe merite un autre modele",
                Retry.worthAnotherModel(Sanitizer.Kind.COUPE));
        expect("un monologue merite un autre modele",
                Retry.worthAnotherModel(Sanitizer.Kind.RAISONNEMENT));
        expect("une erreur d'API ne merite pas un autre modele",
                !Retry.worthAnotherModel(Sanitizer.Kind.ERREUR_API));
        expect("404 merite un autre modele", Retry.worthAnotherModel(404));
        expect("400 merite un autre modele", Retry.worthAnotherModel(400));
        expect("401 ne merite pas un autre modele", !Retry.worthAnotherModel(401));
        expect("500 ne change pas de modele, il se reessaie",
                !Retry.worthAnotherModel(500));

        expect("conseil de filtrage parle de permissivite",
                Retry.advise(Sanitizer.Kind.FILTRE, "").contains("permissif"));
        expect("conseil de coupe parle de longueur",
                Retry.advise(Sanitizer.Kind.COUPE, "").contains("longueur"));
        expect("conseil de monologue parle de reflexion",
                Retry.advise(Sanitizer.Kind.RAISONNEMENT, "").contains("reflexion"));
        expect("404 rappelle la casse et le prefixe",
                Retry.advise(404, "").contains("casse")
                        && Retry.advise(404, "").contains("prefixe"));
        expect("400 sur une image renvoie vers un modele a vision",
                Retry.advise(400, "image_url is not supported").contains("Vision"));
        expect("400 ordinaire ne parle pas de vision",
                !Retry.advise(400, "bad temperature").contains("Vision"));
        expect("402 parle de credit", Retry.advise(402, "").contains("Credit"));
        expect("le detail est repris dans le conseil",
                Retry.advise(Sanitizer.Kind.FILTRE, "refus explicite")
                        .contains("refus explicite"));

        // --- la marche de la chaine ------------------------------------
        {
            Fake a = new Fake("alpha", "filtre");
            Fake b = new Fake("beta", "Je te reponds enfin.");
            AiEngine.Result r = run(Arrays.asList((Completer) a, (Completer) b));
            expect("un refus fait passer au modele suivant", r.isOk());
            expect("le repli n'a pas ete reessaye trois fois", a.calls == 1);
            expect("le second modele a repondu", b.calls == 1);
            expect("le nom du repondeur est remonte", "beta".equals(r.model));
            expect("le changement de modele est signale", r.switched);
        }
        {
            Fake a = new Fake("alpha", "Du premier coup.");
            Fake b = new Fake("beta", "jamais appele");
            AiEngine.Result r = run(Arrays.asList((Completer) a, (Completer) b));
            expect("le modele principal garde la main", r.isOk() && !r.switched);
            expect("le repli reste au repos", b.calls == 0);
            expect("le principal est nomme", "alpha".equals(r.model));
        }
        {
            Fake a = new Fake("alpha", "coupe");
            Fake b = new Fake("beta", "reflexion");
            Fake c = new Fake("gamma", "Troisieme, enfin.");
            AiEngine.Result r = run(Arrays.asList(
                    (Completer) a, (Completer) b, (Completer) c));
            expect("la chaine va jusqu'au troisieme",
                    r.isOk() && "gamma".equals(r.model));
        }
        {
            Fake a = new Fake("alpha", "filtre");
            Fake b = new Fake("beta", "filtre");
            AiEngine.Result r = run(Arrays.asList((Completer) a, (Completer) b));
            expect("tous refusent : echec, pas de texte", !r.isOk());
            expect("l'echec nomme le dernier essaye", "beta".equals(r.model));
            expect("l'echec explique quoi faire", r.error.contains("permissif"));
        }
        {
            // Un incident reseau frappera le suivant pareil : inutile de
            // depenser un appel de plus pour la meme panne.
            Fake a = new Fake("alpha", "timeout", "timeout", "timeout");
            Fake b = new Fake("beta", "jamais appele");
            AiEngine.Result r = run(Arrays.asList((Completer) a, (Completer) b));
            expect("le reseau ne fait pas changer de modele",
                    !r.isOk() && b.calls == 0);
            expect("le reseau a bien ete reessaye", a.calls == Retry.MAX_ATTEMPTS);
        }
        {
            Fake a = new Fake("alpha", "http:401");
            Fake b = new Fake("beta", "jamais appele");
            AiEngine.Result r = run(Arrays.asList((Completer) a, (Completer) b));
            expect("une cle refusee arrete tout de suite",
                    !r.isOk() && a.calls == 1 && b.calls == 0);
        }
        {
            Fake a = new Fake("alpha", "http:404");
            Fake b = new Fake("beta", "Bonjour toi.");
            AiEngine.Result r = run(Arrays.asList((Completer) a, (Completer) b));
            expect("un identifiant inconnu fait passer au suivant",
                    r.isOk() && "beta".equals(r.model));
        }
        {
            Fake a = new Fake("alpha", "http400image:");
            Fake b = new Fake("beta", "Je vois l'ecran.");
            AiEngine.Result r = run(Arrays.asList((Completer) a, (Completer) b));
            expect("un modele sans vision cede la place",
                    r.isOk() && "beta".equals(r.model));
        }
        {
            // Une reponse vide peut etre un flux coupe : on retente le meme
            // modele avant d'en changer, mais on finit par en changer.
            Fake a = new Fake("alpha", "vide");
            Fake b = new Fake("beta", "Enfin du texte.");
            AiEngine.Result r = run(Arrays.asList((Completer) a, (Completer) b));
            expect("le vide est reessaye puis delegue",
                    r.isOk() && a.calls == Retry.MAX_ATTEMPTS && b.calls == 1);
        }
        {
            Fake a = new Fake("alpha", "filtre");
            AiEngine.Result r = run(Arrays.asList((Completer) a));
            expect("sans repli configure, echec propre",
                    !r.isOk() && !r.switched && "alpha".equals(r.model));
        }
        {
            final List<String> told = new ArrayList<String>();
            Fake a = new Fake("alpha", "filtre");
            Fake b = new Fake("beta", "Voila.");
            new AiEngine(a, cfg())
                    .withSleeper(noSleep())
                    .withFallback(Arrays.asList((Completer) b))
                    .withProgress(new AiEngine.Progress() {
                        @Override
                        public void onRetry(int attempt, int total, String why) {
                            told.add("retry");
                        }

                        @Override
                        public void onModelSwitch(String model, String why) {
                            told.add("switch:" + model + ":" + why);
                        }
                    })
                    .fromHistory(new ArrayList<ChatMessage>());
            expect("l'interface est prevenue du changement de modele",
                    told.size() == 1 && told.get(0).startsWith("switch:beta:"));
            expect("le motif accompagne le changement",
                    told.get(0).contains("filtre"));
        }
        {
            // La vision passe par le meme chemin : le repli doit marcher aussi
            // quand c'est une capture d'ecran qui part.
            Fake a = new Fake("alpha", "filtre");
            Fake b = new Fake("beta", "Je lis l'ecran.");
            AiEngine.Result r = new AiEngine(a, cfg())
                    .withSleeper(noSleep())
                    .withFallback(Arrays.asList((Completer) b))
                    .fromScreenshot(new byte[]{1, 2, 3});
            expect("le repli marche aussi sur une capture",
                    r.isOk() && "beta".equals(r.model));
        }

        // --- dernier recours : un modele pris au catalogue ---------------

        {
            // Le cas qui rendait « reponse vide » : tous les identifiants
            // ecrits dans les reglages ont disparu du catalogue.
            Fake a = new Fake("google/perime", "http:404");
            Fake b = new Fake("x-ai/perime-aussi", "http:404");
            final Fake found = new Fake("google/gemini-3.6-flash", "Je suis la.");
            final List<String> seen = new ArrayList<String>();
            AiEngine.Result r = new AiEngine(a, cfg())
                    .withSleeper(noSleep())
                    .withFallback(Arrays.asList((Completer) b))
                    .withRescue(new AiEngine.Rescue() {
                        @Override
                        public List<Completer> candidates(List<String> tried) {
                            seen.addAll(tried);
                            return Arrays.asList((Completer) found);
                        }
                    })
                    .fromHistory(new ArrayList<ChatMessage>());
            expect("le catalogue sauve la reponse", r.isOk());
            expect("c'est le modele decouvert qui repond",
                    "google/gemini-3.6-flash".equals(r.model) && r.switched);
            expect("le dernier recours sait ce qui a deja echoue",
                    seen.contains("google/perime") && seen.contains("x-ai/perime-aussi"));
        }

        {
            // Un seul aller-retour vers le catalogue, meme si les modeles
            // decouverts echouent a leur tour : sinon on boucle sur le reseau.
            final int[] asked = {0};
            Fake a = new Fake("alpha", "http:404");
            AiEngine.Result r = new AiEngine(a, cfg())
                    .withSleeper(noSleep())
                    .withRescue(new AiEngine.Rescue() {
                        @Override
                        public List<Completer> candidates(List<String> tried) {
                            asked[0]++;
                            return Arrays.asList((Completer) new Fake("secours", "http:404"));
                        }
                    })
                    .fromHistory(new ArrayList<ChatMessage>());
            expect("le catalogue n'est lu qu'une fois", asked[0] == 1);
            expect("l'echec reste un echec si rien ne repond", !r.isOk());
        }

        {
            // Le catalogue est un bonus : injoignable, il ne doit pas masquer
            // le vrai motif d'echec a l'ecran.
            Fake a = new Fake("alpha", "filtre");
            AiEngine.Result r = new AiEngine(a, cfg())
                    .withSleeper(noSleep())
                    .withRescue(new AiEngine.Rescue() {
                        @Override
                        public List<Completer> candidates(List<String> tried)
                                throws Exception {
                            throw new java.io.IOException("catalogue injoignable");
                        }
                    })
                    .fromHistory(new ArrayList<ChatMessage>());
            expect("un catalogue injoignable ne remplace pas le motif",
                    !r.isOk() && r.error.contains("permissif"));
        }

        {
            // Une cle refusee ne se repare pas en changeant de modele : inutile
            // d'aller lire le catalogue pour ca.
            final int[] asked = {0};
            Fake a = new Fake("alpha", "http:401");
            new AiEngine(a, cfg())
                    .withSleeper(noSleep())
                    .withRescue(new AiEngine.Rescue() {
                        @Override
                        public List<Completer> candidates(List<String> tried) {
                            asked[0]++;
                            return new ArrayList<Completer>();
                        }
                    })
                    .fromHistory(new ArrayList<ChatMessage>());
            expect("une cle refusee n'appelle pas le catalogue", asked[0] == 0);
        }

        // --- choix dans le catalogue -------------------------------------

        {
            List<String> catalogue = Arrays.asList(
                    "deepseek/deepseek-v4-flash",
                    "google/gemini-3.6-flash",
                    "anthropic/claude-opus-5.5",
                    "openai/gpt-4.1-mini",
                    "mistralai/mistral-small");
            List<String> picked = com.lvx.rpkeyboard.ai.WaveSpeed
                    .visionCandidates(catalogue, new ArrayList<String>(), 2);
            expect("deux candidats proposes", picked.size() == 2);
            expect("le modele a vision le plus sur passe devant",
                    "google/gemini-3.6-flash".equals(picked.get(0)));
            expect("un modele sans vision est ecarte",
                    !picked.contains("mistralai/mistral-small"));

            List<String> again = com.lvx.rpkeyboard.ai.WaveSpeed.visionCandidates(
                    catalogue, Arrays.asList("google/gemini-3.6-flash"), 2);
            expect("un modele deja essaye n'est pas repropose",
                    !again.contains("google/gemini-3.6-flash")
                            && again.contains("anthropic/claude-opus-5.5"));

            expect("un catalogue sans vision ne force rien",
                    com.lvx.rpkeyboard.ai.WaveSpeed.visionCandidates(
                            Arrays.asList("mistralai/mistral-small"),
                            new ArrayList<String>(), 2).isEmpty());
        }

        System.out.println();
        System.out.println("reussis=" + ok + " echoues=" + ko);
        if (ko > 0) {
            System.exit(1);
        }
    }

    static AiEngine.Result run(List<Completer> chain) {
        List<Completer> rest = new ArrayList<Completer>(chain.subList(1, chain.size()));
        return new AiEngine(chain.get(0), cfg())
                .withSleeper(noSleep())
                .withFallback(rest)
                .fromHistory(new ArrayList<ChatMessage>());
    }

    static AiEngine.Sleeper noSleep() {
        return new AiEngine.Sleeper() {
            @Override
            public void sleep(long ms) {
            }
        };
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
