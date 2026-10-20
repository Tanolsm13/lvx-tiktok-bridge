package com.lvx.rpkeyboard.ai;

/**
 * Fournisseurs d'IA disponibles.
 *
 * WaveSpeed est le defaut : c'est la cle dont on dispose, et sa passerelle
 * expose un catalogue multi-modeles au format OpenAI. Les autres entrees
 * servent si une autre cle est saisie dans les reglages.
 */
public enum Provider {

    WAVESPEED("WaveSpeed",
            "https://llm.wavespeed.ai/v1/chat/completions",
            Sanitizer.Shape.OPENAI, Auth.BEARER,
            "google/gemini-3.6-flash", true),

    OPENAI("OpenAI",
            "https://api.openai.com/v1/chat/completions",
            Sanitizer.Shape.OPENAI, Auth.BEARER,
            "gpt-4o", true),

    ANTHROPIC("Anthropic",
            "https://api.anthropic.com/v1/messages",
            Sanitizer.Shape.ANTHROPIC, Auth.X_API_KEY,
            "claude-sonnet-4-5", true),

    MISTRAL("Mistral",
            "https://api.mistral.ai/v1/chat/completions",
            Sanitizer.Shape.OPENAI, Auth.BEARER,
            "mistral-large-latest", false),

    GROQ("Groq",
            "https://api.groq.com/openai/v1/chat/completions",
            Sanitizer.Shape.OPENAI, Auth.BEARER,
            "llama-3.3-70b-versatile", false),

    DEEPSEEK("DeepSeek",
            "https://api.deepseek.com/v1/chat/completions",
            Sanitizer.Shape.OPENAI, Auth.BEARER,
            "deepseek-chat", false),

    /** Compatible OpenAI : URL libre, pour toute autre passerelle. */
    CUSTOM("Compatible OpenAI",
            "",
            Sanitizer.Shape.OPENAI, Auth.BEARER,
            "", false);

    public enum Auth {
        /** Authorization: Bearer <cle> */
        BEARER,
        /** x-api-key: <cle> + anthropic-version */
        X_API_KEY
    }

    public final String label;
    public final String defaultUrl;
    public final Sanitizer.Shape shape;
    public final Auth auth;
    public final String defaultModel;
    /** Le modele par defaut accepte-t-il une image en entree. */
    public final boolean visionByDefault;

    Provider(String label, String defaultUrl, Sanitizer.Shape shape, Auth auth,
             String defaultModel, boolean visionByDefault) {
        this.label = label;
        this.defaultUrl = defaultUrl;
        this.shape = shape;
        this.auth = auth;
        this.defaultModel = defaultModel;
        this.visionByDefault = visionByDefault;
    }

    public static Provider from(String name) {
        if (name != null) {
            for (Provider p : values()) {
                if (p.name().equals(name)) {
                    return p;
                }
            }
        }
        return WAVESPEED;
    }

    /** Quelques identifiants de modeles a vision, proposes dans les reglages. */
    public static final String[] VISION_PRESETS = {
            "google/gemini-3.6-flash",
            "google/gemini-3.5-flash",
            "x-ai/grok-4.3",
            "qwen/qwen3.6-35b-a3b",
    };
}
