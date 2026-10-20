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
