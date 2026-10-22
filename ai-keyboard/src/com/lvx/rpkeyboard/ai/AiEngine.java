package com.lvx.rpkeyboard.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestration : contexte, prompt, appel, reessai, nettoyage.
 *
 * Rend toujours un {@link Result}. En cas d'echec, {@code variants} est vide et
 * {@code error} porte un message affichable — rien n'est jamais insere dans le
 * champ de saisie sur un echec.
 *
 * Les pannes passageres (500, 502, 429, delai depasse, reponse vide) sont
 * reessayees selon {@link Retry} ; les erreurs deterministes, comme une cle
 * refusee, echouent tout de suite plutot que de couter trois appels.
 */
public final class AiEngine {

    public static final class Result {
        public final List<String> variants;
        public final String error;
        /** Vrai si l'echec vient d'une reponse mal formee, pas du reseau. */
        public final boolean malformed;
        /** Nombre de tentatives consommees. */
        public final int attempts;
        /** Modele qui a repondu, ou dernier essaye en cas d'echec. */
        public final String model;
        /** Vrai si le modele principal a du etre abandonne. */
        public final boolean switched;

        private Result(List<String> variants, String error, boolean malformed,
                       int attempts, String model, boolean switched) {
            this.variants = variants;
            this.error = error;
            this.malformed = malformed;
            this.attempts = attempts;
            this.model = model;
            this.switched = switched;
        }

        static Result ok(List<String> variants, int attempts) {
            return new Result(variants, null, false, attempts, "", false);
        }

        static Result fail(String error, boolean malformed, int attempts) {
            return new Result(new ArrayList<String>(), error, malformed, attempts, "", false);
        }

        /** Meme resultat, en nommant le modele qui l'a produit. */
        Result on(String model, boolean switched) {
            return new Result(variants, error, malformed, attempts,
                    model == null ? "" : model, switched);
        }

        public boolean isOk() {
            return error == null && !variants.isEmpty();
        }
    }

    /** Avertit l'interface de ce qui se passe pendant l'appel. */
    public interface Progress {
        void onRetry(int attempt, int total, String why);

        /** Le modele precedent n'a rien donne : on passe au suivant. */
        void onModelSwitch(String model, String why);
    }

    /**
     * Dernier recours : des modeles decouverts a chaud dans le catalogue.
     *
     * Une liste de replis ecrite a la compilation vieillit — un identifiant
     * retire du catalogue rend 404, et l'ecran affiche une erreur alors que
     * vingt autres modeles auraient repondu. Quand toute la chaine a echoue
     * pour une raison qui tient au modele, le moteur demande ici de quoi
     * continuer. Appele au plus une fois par reponse, et seulement a ce
     * moment : lire le catalogue coute un aller-retour reseau.
     */
    public interface Rescue {
        /**
         * @param alreadyTried identifiants deja essayes, a ne pas reproposer
         * @return modeles supplementaires, ou liste vide
         */
        List<Completer> candidates(List<String> alreadyTried) throws Exception;
    }

    /** Attente entre deux tentatives ; remplacable pour les tests. */
    public interface Sleeper {
        void sleep(long ms);
    }

    private static final Sleeper REAL_SLEEPER = new Sleeper() {
        @Override
        public void sleep(long ms) {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    };

    /** Modeles dans l'ordre d'essai : le principal, puis les replis. */
    private final List<Completer> chain = new ArrayList<Completer>();
    private final PromptBuilder.Config config;

    /** Budget de compactage en caracteres, deduit de la fenetre du modele. */
    private int budget = ContextCompactor.DEFAULT_BUDGET;
    private Progress progress;
    private Sleeper sleeper = REAL_SLEEPER;
    private Rescue rescue;
    private boolean rescued = false;

    public AiEngine(Completer client, PromptBuilder.Config config) {
        this.chain.add(client);
        this.config = config;
    }

    /**
     * Modeles de repli, essayes dans l'ordre quand le premier ne donne rien.
     *
     * C'est la reponse au defaut le plus penible de la 3.0 : un refus de
     * filtrage rendait une reponse vide, et il n'y avait plus qu'a aller
     * changer de modele a la main au milieu d'une conversation.
     */
    public AiEngine withFallback(List<Completer> others) {
        if (others != null) {
            for (int i = 0; i < others.size(); i++) {
                Completer c = others.get(i);
                if (c != null) {
                    chain.add(c);
                }
            }
        }
        return this;
    }

    public AiEngine withBudget(int chars) {
        if (chars > 0) {
            this.budget = chars;
        }
        return this;
    }

    /** Voir {@link Rescue}. Sans cela, la chaine se limite aux modeles connus. */
    public AiEngine withRescue(Rescue rescue) {
        this.rescue = rescue;
        return this;
    }

    public AiEngine withProgress(Progress progress) {
        this.progress = progress;
        return this;
    }

    /** Pour les tests : evite d'attendre reellement entre deux tentatives. */
    public AiEngine withSleeper(Sleeper sleeper) {
        if (sleeper != null) {
            this.sleeper = sleeper;
        }
        return this;
    }

    // ------------------------------------------------------------- entrees

    /** Depuis un historique lu en texte. */
    public Result fromHistory(List<ChatMessage> history) {
        ContextCompactor.Result ctx = ContextCompactor.compact(
                history, budget, ContextCompactor.KEEP_VERBATIM);
        return run(PromptBuilder.system(config), PromptBuilder.user(config, ctx), null);
    }

    /** Depuis une capture d'ecran, pour un modele a vision. */
    public Result fromScreenshot(byte[] jpeg) {
        return run(PromptBuilder.system(config), PromptBuilder.userVision(config), jpeg);
    }

    /**
     * Historique connu ET capture : le modele a les deux.
     *
     * L'image occupe elle aussi la fenetre de contexte, donc on retire sa part
     * du budget de l'historique plutot que de l'ignorer.
     */
    public Result fromBoth(List<ChatMessage> history, byte[] jpeg) {
        ContextCompactor.Result ctx = ContextCompactor.compact(
                history, ContextCompactor.budgetWithImage(budget),
                ContextCompactor.KEEP_VERBATIM);
        return run(PromptBuilder.system(config), PromptBuilder.user(config, ctx), jpeg);
    }

    // ------------------------------------------------------------- boucle

    /**
     * Essaie chaque modele de la chaine jusqu'a une reponse propre.
     *
     * Deux boucles imbriquees, et c'est voulu : la premiere change de modele
     * quand le defaut est de son fait (refus, JSON, vide), la seconde reessaie
     * le meme modele quand le defaut est passager (500, 429, reseau).
     */
    private Result run(String system, String user, byte[] jpeg) {
        Result last = Result.fail("echec inconnu", false, 0);

        for (int m = 0; m < chain.size(); m++) {
            Completer client = chain.get(m);
            Try t = attempt(client, system, user, jpeg);
            if (t.result.isOk()) {
                return t.result.on(client.label(), m > 0);
            }
            last = t.result.on(client.label(), m > 0);

            if (!t.otherModel) {
                return last;
            }
            if (m + 1 >= chain.size()) {
                enlarge();
                if (m + 1 >= chain.size()) {
                    return last;
                }
            }
            if (progress != null) {
                progress.onModelSwitch(chain.get(m + 1).label(), t.why);
            }
        }
        return last;
    }

    /**
     * Allonge la chaine avec des modeles du catalogue, une seule fois.
     *
     * Un echec ici ne doit pas remplacer le vrai motif a l'ecran : si le
     * catalogue est injoignable, l'utilisateur doit lire pourquoi son modele
     * n'a pas repondu, pas pourquoi la liste n'a pas pu etre relue.
     */
    private void enlarge() {
        if (rescue == null || rescued) {
            return;
        }
        rescued = true;
        List<String> tried = new ArrayList<String>();
        for (int i = 0; i < chain.size(); i++) {
            tried.add(chain.get(i).label());
        }
        try {
            List<Completer> found = rescue.candidates(tried);
            if (found == null) {
                return;
            }
            for (int i = 0; i < found.size(); i++) {
                if (found.get(i) != null) {
                    chain.add(found.get(i));
                }
            }
        } catch (Exception ignored) {
            // Pas de catalogue : on s'en tient au motif d'echec deja etabli.
        }
    }

    /** Resultat d'un modele, et faut-il en essayer un autre. */
    private static final class Try {
        final Result result;
        final boolean otherModel;
        final String why;

        Try(Result result, boolean otherModel, String why) {
            this.result = result;
            this.otherModel = otherModel;
            this.why = why;
        }
    }

    private Try attempt(Completer client, String system, String user, byte[] jpeg) {
        for (int tries = 1; tries <= Retry.MAX_ATTEMPTS; tries++) {
            String raw;

            // 1. l'appel
            try {
                raw = client.complete(system, user, jpeg);
            } catch (LlmClient.HttpError he) {
                String advice = Retry.advise(he.code, he.getMessage());
                if (Retry.shouldRetry(he.code) && tries < Retry.MAX_ATTEMPTS) {
                    pause(tries, "HTTP " + he.code);
                    continue;
                }
                return new Try(Result.fail(advice, false, tries),
                        Retry.worthAnotherModel(he.code), "HTTP " + he.code);
            } catch (Exception e) {
                String msg = message(e);
                if (Retry.isTransient(e) && tries < Retry.MAX_ATTEMPTS) {
                    pause(tries, "reseau");
                    continue;
                }
                // Un incident reseau frappera le modele suivant pareil.
                return new Try(Result.fail(msg, false, tries), false, "reseau");
            }

            // 2. le nettoyage
            try {
                return new Try(Result.ok(Sanitizer.variants(raw, client.shape()), tries),
                        false, "");
            } catch (Sanitizer.BadReply bad) {
                String advice = Retry.advise(bad.kind, detailOf(bad));
                if (Retry.shouldRetry(bad.kind) && tries < Retry.MAX_ATTEMPTS) {
                    pause(tries, bad.kind.label);
                    continue;
                }
                return new Try(Result.fail(advice, true, tries),
                        Retry.worthAnotherModel(bad.kind), bad.kind.label);
            } catch (Exception e) {
                return new Try(Result.fail(message(e), true, tries), false, "");
            }
        }
        return new Try(Result.fail("echec inconnu", false, Retry.MAX_ATTEMPTS), true, "");
    }

    /** Le detail porte par l'echec, sans repeter son libelle. */
    private static String detailOf(Sanitizer.BadReply bad) {
        String m = bad.getMessage();
        if (m == null) {
            return "";
        }
        int open = m.indexOf(" (");
        return (open > 0 && m.endsWith(")")) ? m.substring(open + 2, m.length() - 1) : "";
    }

    private void pause(int completedAttempts, String why) {
        if (progress != null) {
            progress.onRetry(completedAttempts + 1, Retry.MAX_ATTEMPTS, why);
        }
        sleeper.sleep(Retry.backoffMs(completedAttempts));
    }

    private static String message(Exception e) {
        String m = e.getMessage();
        return (m == null || m.trim().isEmpty()) ? e.getClass().getSimpleName() : m;
    }
}
