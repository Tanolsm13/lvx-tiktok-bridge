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

        private Result(List<String> variants, String error, boolean malformed, int attempts) {
            this.variants = variants;
            this.error = error;
            this.malformed = malformed;
            this.attempts = attempts;
        }

        static Result ok(List<String> variants, int attempts) {
            return new Result(variants, null, false, attempts);
        }

        static Result fail(String error, boolean malformed, int attempts) {
            return new Result(new ArrayList<String>(), error, malformed, attempts);
        }

        public boolean isOk() {
            return error == null && !variants.isEmpty();
        }
    }

    /** Avertit l'interface qu'une nouvelle tentative commence. */
    public interface Progress {
        void onRetry(int attempt, int total, String why);
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

    private final Completer client;
    private final PromptBuilder.Config config;

    /** Budget de compactage en caracteres, deduit de la fenetre du modele. */
    private int budget = ContextCompactor.DEFAULT_BUDGET;
    private Progress progress;
    private Sleeper sleeper = REAL_SLEEPER;

    public AiEngine(Completer client, PromptBuilder.Config config) {
        this.client = client;
        this.config = config;
    }

    public AiEngine withBudget(int chars) {
        if (chars > 0) {
            this.budget = chars;
        }
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

    /** Historique connu ET capture : le modele a les deux. */
    public Result fromBoth(List<ChatMessage> history, byte[] jpeg) {
        ContextCompactor.Result ctx = ContextCompactor.compact(
                history, budget, ContextCompactor.KEEP_VERBATIM);
        return run(PromptBuilder.system(config), PromptBuilder.user(config, ctx), jpeg);
    }

    // ------------------------------------------------------------- boucle

    private Result run(String system, String user, byte[] jpeg) {
        String lastError = "echec inconnu";
        boolean lastMalformed = false;

        for (int attempt = 1; attempt <= Retry.MAX_ATTEMPTS; attempt++) {
            String raw;

            // 1. l'appel
            try {
                raw = client.complete(system, user, jpeg);
            } catch (LlmClient.HttpError he) {
                lastError = Retry.advise(he.code, he.getMessage());
                lastMalformed = false;
                if (Retry.shouldRetry(he.code) && attempt < Retry.MAX_ATTEMPTS) {
                    pause(attempt, "HTTP " + he.code);
                    continue;
                }
                return Result.fail(lastError, false, attempt);
            } catch (Exception e) {
                lastError = message(e);
                lastMalformed = false;
                if (Retry.isTransient(e) && attempt < Retry.MAX_ATTEMPTS) {
                    pause(attempt, "reseau");
                    continue;
                }
                return Result.fail(lastError, false, attempt);
            }

            // 2. le nettoyage
            try {
                return Result.ok(Sanitizer.variants(raw, client.shape()), attempt);
            } catch (Sanitizer.BadReply bad) {
                lastError = bad.getMessage();
                lastMalformed = true;
                if (Retry.shouldRetry(bad.kind) && attempt < Retry.MAX_ATTEMPTS) {
                    pause(attempt, bad.kind.label);
                    continue;
                }
                return Result.fail(lastError, true, attempt);
            } catch (Exception e) {
                return Result.fail(message(e), true, attempt);
            }
        }
        return Result.fail(lastError, lastMalformed, Retry.MAX_ATTEMPTS);
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
