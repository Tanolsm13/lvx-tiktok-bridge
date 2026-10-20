package com.lvx.rpkeyboard.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.lvx.rpkeyboard.ai.ConversationLog;
import com.lvx.rpkeyboard.ai.PromptBuilder;
import com.lvx.rpkeyboard.ai.Provider;

/**
 * Reglages de l'app.
 *
 * La cle WaveSpeed est injectee a la compilation ({@link #BUILTIN_KEY}) pour que
 * l'app fonctionne des l'installation, sans rien saisir. Toute cle saisie dans
 * les reglages prend le dessus.
 */
public final class Settings {

    /** Injectee par build.sh. Vaut le placeholder si le build n'a pas de cle. */
    private static final String BUILTIN_KEY = "@WAVESPEED_KEY@";

    private static final String FILE = "rp_v2";

    private final SharedPreferences sp;

    private static Settings instance;

    public static synchronized Settings get(Context ctx) {
        if (instance == null) {
            instance = new Settings(ctx.getApplicationContext()
                    .getSharedPreferences(FILE, Context.MODE_PRIVATE));
        }
        return instance;
    }

    private Settings(SharedPreferences sp) {
        this.sp = sp;
    }

    public static boolean hasBuiltinKey() {
        return !BUILTIN_KEY.startsWith("@");
    }

    // ---------------------------------------------------------- fournisseur

    public Provider provider() {
        return Provider.from(sp.getString("provider", Provider.WAVESPEED.name()));
    }

    public void setProvider(Provider p) {
        sp.edit().putString("provider", p.name()).apply();
    }

    public String model() {
        String m = sp.getString("model_" + provider().name(), "");
        return m.isEmpty() ? provider().defaultModel : m;
    }

    public void setModel(String model) {
        sp.edit().putString("model_" + provider().name(), model == null ? "" : model.trim()).apply();
    }

    /** URL personnalisee ; vide = URL par defaut du fournisseur. */
    public String baseUrl() {
        return sp.getString("url_" + provider().name(), "");
    }

    public void setBaseUrl(String url) {
        sp.edit().putString("url_" + provider().name(), url == null ? "" : url.trim()).apply();
    }

    /** Cle du fournisseur courant : celle saisie, sinon celle du build. */
    public String apiKey() {
        String saved = sp.getString("key_" + provider().name(), "");
        if (!saved.isEmpty()) {
            return saved;
        }
        return (provider() == Provider.WAVESPEED && hasBuiltinKey()) ? BUILTIN_KEY : "";
    }

    public void setApiKey(String key) {
        sp.edit().putString("key_" + provider().name(), key == null ? "" : key.trim()).apply();
    }

    public boolean usesBuiltinKey() {
        return sp.getString("key_" + provider().name(), "").isEmpty()
                && provider() == Provider.WAVESPEED && hasBuiltinKey();
    }

    // ----------------------------------------------------------------- mode

    public PromptBuilder.Mode mode() {
        return "INSTRUCTION_UNIQUE".equals(sp.getString("mode", "ADAPTATIF"))
                ? PromptBuilder.Mode.INSTRUCTION_UNIQUE
                : PromptBuilder.Mode.ADAPTATIF;
    }

    public void setMode(PromptBuilder.Mode m) {
        sp.edit().putString("mode", m.name()).apply();
    }

    public String instruction() {
        return sp.getString("instruction", "");
    }

    public boolean roleplay() {
        return sp.getBoolean("roleplay", true);
    }

    public void setRoleplay(boolean on) {
        sp.edit().putBoolean("roleplay", on).apply();
    }

    public String me() {
        return sp.getString("me", "");
    }

    public String partner() {
        return sp.getString("partner", "");
    }

    public String universe() {
        return sp.getString("universe", "");
    }

    public String styleProfile() {
        return sp.getString("style", "");
    }

    public int count() {
        return Math.max(1, Math.min(5, sp.getInt("count", 3)));
    }

    public void saveProfile(String instruction, String me, String partner,
                            String universe, String style, int count) {
        sp.edit()
                .putString("instruction", nz(instruction))
                .putString("me", nz(me))
                .putString("partner", nz(partner))
                .putString("universe", nz(universe))
                .putString("style", nz(style))
                .putInt("count", Math.max(1, Math.min(5, count)))
                .apply();
    }

    /** Assemble la configuration de prompt a partir des reglages. */
    public PromptBuilder.Config config(String oneShot) {
        PromptBuilder.Config c = new PromptBuilder.Config();
        c.mode = mode();
        c.instruction = instruction();
        c.oneShot = oneShot == null ? "" : oneShot;
        c.me = me();
        c.partner = partner();
        c.universe = universe();
        c.styleProfile = styleProfile();
        c.roleplay = roleplay();
        c.count = count();
        return c;
    }


    // ------------------------------------------------------------- apparence

    /** Chemin d'une image de fond du clavier, vide = fond uni. */
    public String background() {
        return sp.getString("bg", "");
    }

    public void setBackground(String path) {
        sp.edit().putString("bg", path == null ? "" : path).apply();
    }

    /** Opacite du voile pose sur l'image de fond, 0 a 100. */
    public int scrim() {
        return Math.max(0, Math.min(100, sp.getInt("scrim", 62)));
    }

    public void setScrim(int percent) {
        sp.edit().putInt("scrim", Math.max(0, Math.min(100, percent))).apply();
    }

    public boolean animations() {
        return sp.getBoolean("anim", true);
    }

    public void setAnimations(boolean on) {
        sp.edit().putBoolean("anim", on).apply();
    }

    // ----------------------------------------------------------------- vocal

    public String elevenKey() {
        return sp.getString("eleven_key", "");
    }

    public String elevenVoice() {
        return sp.getString("eleven_voice", "");
    }

    public String elevenModel() {
        return sp.getString("eleven_model", "");
    }

    public void saveVoice(String key, String voiceId, String model) {
        sp.edit()
                .putString("eleven_key", nz(key))
                .putString("eleven_voice", nz(voiceId))
                .putString("eleven_model", nz(model))
                .apply();
    }

    public boolean voiceReady() {
        return !elevenKey().isEmpty() && !elevenVoice().isEmpty();
    }

    // ----------------------------------------------------------------- image

    public String imageKey() {
        return sp.getString("img_key", "");
    }

    public String imageUrl() {
        return sp.getString("img_url", "");
    }

    public String imageModel() {
        return sp.getString("img_model", "");
    }

    public void saveImage(String key, String url, String model) {
        sp.edit()
                .putString("img_key", nz(key))
                .putString("img_url", nz(url))
                .putString("img_model", nz(model))
                .apply();
    }

    public boolean imageReady() {
        return !imageKey().isEmpty();
    }

    // ------------------------------------------------------------- mode auto

    public boolean autoAllowed() {
        return sp.getBoolean("auto_allowed", false);
    }

    public void setAutoAllowed(boolean on) {
        sp.edit().putBoolean("auto_allowed", on).apply();
    }

    public String autoDisclosure() {
        return sp.getString("auto_disclosure", "");
    }

    public int autoMaxMessages() {
        return Math.max(1, Math.min(50, sp.getInt("auto_max", 10)));
    }

    public int autoMinutes() {
        return Math.max(1, Math.min(120, sp.getInt("auto_minutes", 15)));
    }

    /** Seuil de secousse, en g. */
    public float shakeG() {
        return sp.getFloat("shake_g", 2.8f);
    }

    public void saveAuto(String disclosure, int maxMessages, int minutes, float shakeG) {
        sp.edit()
                .putString("auto_disclosure", nz(disclosure))
                .putInt("auto_max", Math.max(1, Math.min(50, maxMessages)))
                .putInt("auto_minutes", Math.max(1, Math.min(120, minutes)))
                .putFloat("shake_g", Math.max(1.5f, Math.min(6f, shakeG)))
                .apply();
    }

    // --------------------------------------------------------------- contexte

    /**
     * Fenetre de contexte du modele, en tokens. Sert a calculer le budget de
     * compactage pour ne jamais s'en approcher.
     */
    public int contextWindow() {
        return Math.max(8_000, sp.getInt("ctx_window", 200_000));
    }

    public void setContextWindow(int tokens) {
        sp.edit().putInt("ctx_window", Math.max(8_000, tokens)).apply();
    }

    /** Part de la fenetre qu'on s'autorise pour l'historique, en pourcent. */
    public int contextShare() {
        return Math.max(1, Math.min(50, sp.getInt("ctx_share", 8)));
    }

    public void setContextShare(int percent) {
        sp.edit().putInt("ctx_share", Math.max(1, Math.min(50, percent))).apply();
    }

    /**
     * Budget de compactage en caracteres, deduit de la fenetre du modele.
     * On compte ~4 caracteres par token, et on ne prend qu'une fraction de la
     * fenetre : l'historique n'est pas la seule chose a y tenir, et chaque
     * token envoye est facture.
     */
    public int contextBudgetChars() {
        long tokens = (long) contextWindow() * contextShare() / 100L;
        long chars = tokens * 4L;
        return (int) Math.max(2_000L, Math.min(400_000L, chars));
    }

    // ------------------------------------------------------------ historique

    /** Journal persiste par fil, pour survivre a la fermeture du clavier. */
    public ConversationLog loadLog(String threadKey) {
        return ConversationLog.parse(sp.getString("log_" + safe(threadKey), ""));
    }

    public void saveLog(String threadKey, ConversationLog log) {
        sp.edit().putString("log_" + safe(threadKey), log.serialize()).apply();
    }

    public void clearLog(String threadKey) {
        sp.edit().remove("log_" + safe(threadKey)).apply();
    }

    private static String safe(String key) {
        return key == null ? "default" : key.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }
}
