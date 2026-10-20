package com.lvx.rpkeyboard.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestration : contexte, prompt, appel, nettoyage.
 *
 * Rend toujours un {@link Result}. En cas d'echec, {@code variants} est vide et
 * {@code error} porte un message affichable — rien n'est jamais insere dans le
 * champ de saisie sur un echec.
 */
public final class AiEngine {

    public static final class Result {
        public final List<String> variants;
        public final String error;
        /** Vrai si l'echec vient d'une reponse mal formee, pas du reseau. */
        public final boolean malformed;

        private Result(List<String> variants, String error, boolean malformed) {
            this.variants = variants;
            this.error = error;
            this.malformed = malformed;
        }

        static Result ok(List<String> variants) {
            return new Result(variants, null, false);
        }

        static Result fail(String error, boolean malformed) {
            return new Result(new ArrayList<String>(), error, malformed);
        }

        public boolean isOk() {
            return error == null && !variants.isEmpty();
        }
    }

    private final LlmClient client;
    private final PromptBuilder.Config config;

    /** Budget de compactage en caracteres, deduit de la fenetre du modele. */
    private int budget = ContextCompactor.DEFAULT_BUDGET;

    public AiEngine(LlmClient client, PromptBuilder.Config config) {
        this.client = client;
        this.config = config;
    }

    public AiEngine withBudget(int chars) {
        if (chars > 0) {
            this.budget = chars;
        }
        return this;
    }

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

    private Result run(String system, String user, byte[] jpeg) {
        String raw;
        try {
            raw = client.complete(system, user, jpeg);
        } catch (Exception e) {
            return Result.fail(message(e), false);
        }

        try {
            List<String> variants = Sanitizer.variants(raw, client.shape());
            return Result.ok(variants);
        } catch (Sanitizer.BadReply e) {
            return Result.fail(e.getMessage(), true);
        } catch (Exception e) {
            return Result.fail(message(e), true);
        }
    }

    private static String message(Exception e) {
        String m = e.getMessage();
        return (m == null || m.trim().isEmpty()) ? e.getClass().getSimpleName() : m;
    }
}
