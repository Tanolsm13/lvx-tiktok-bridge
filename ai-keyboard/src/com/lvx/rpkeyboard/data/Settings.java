package com.lvx.rpkeyboard.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.lvx.rpkeyboard.ai.ConversationLog;
import com.lvx.rpkeyboard.ai.PromptBuilder;
import com.lvx.rpkeyboard.ai.WaveSpeed;
import com.lvx.rpkeyboard.ime.Theme;

import java.util.ArrayList;
import java.util.List;

/**
 * Reglages de l'app.
 *
 * Un seul fournisseur, WaveSpeed, qui porte le texte, les images et le vocal :
 * il n'y a donc qu'une cle, injectee a la compilation pour que l'app marche des
 * l'installation. Une cle saisie dans les reglages prend le dessus.
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

    // ------------------------------------------------------------------ cle

    /** La cle saisie, sinon celle du build. */
    public String apiKey() {
        String saved = sp.getString("ws_key", "");
        return saved.isEmpty() ? (hasBuiltinKey() ? BUILTIN_KEY : "") : saved;
    }

    public void setApiKey(String key) {
        sp.edit().putString("ws_key", key == null ? "" : key.trim()).apply();
    }

    public boolean usesBuiltinKey() {
        return sp.getString("ws_key", "").isEmpty() && hasBuiltinKey();
    }

    // --------------------------------------------------------------- modeles

    public String model() {
        String m = sp.getString("llm_model", "");
        return m.isEmpty() ? WaveSpeed.DEFAULT_LLM_MODEL : m;
    }

    public void setModel(String model) {
        sp.edit().putString("llm_model", model == null ? "" : model.trim()).apply();
    }

    /**
     * Repli automatique sur un autre modele quand le premier ne rend rien.
     *
     * Actif par defaut : au milieu d'une conversation, voir arriver une
     * reponse d'un autre modele vaut mieux que de devoir ouvrir les reglages.
     */
    public boolean fallbackEnabled() {
        return sp.getBoolean("fallback", true);
    }

    public void setFallbackEnabled(boolean on) {
        sp.edit().putBoolean("fallback", on).apply();
    }

    /**
     * Modeles de repli, dans l'ordre, sans repeter le modele principal.
     *
     * La separation est volontairement permissive : virgule, point-virgule,
     * retour a la ligne, espace ou le point median de l'affichage. Un champ de
     * reglages se remplit souvent par copier-coller, et un identifiant perdu
     * parce que le separateur n'etait pas le bon se paie en reponse vide.
     */
    public List<String> fallbackModels() {
        String saved = sp.getString("fallback_models", "");
        String[] ids = saved.trim().isEmpty()
                ? WaveSpeed.VISION_MODELS : saved.split(SEPARATORS);
        String primary = model();
        List<String> out = new ArrayList<String>();
        for (int i = 0; i < ids.length; i++) {
            String id = ids[i].trim();
            if (!id.isEmpty() && !id.equals(primary) && !out.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }

    public void setFallbackModels(String separated) {
        sp.edit().putString("fallback_models", nz(separated)).apply();
    }

    /** Virgule, point-virgule, point median, retour a la ligne ou espaces. */
    static final String SEPARATORS = "[,;\u00b7\n\r\t ]+";

    /** Dernier catalogue lu chez WaveSpeed, pour choisir hors ligne ensuite. */
    public List<String> modelCatalogue() {
        List<String> out = new ArrayList<String>();
        String saved = sp.getString("catalogue", "");
        if (saved.trim().isEmpty()) {
            return out;
        }
        String[] ids = saved.split("\n");
        for (int i = 0; i < ids.length; i++) {
            String id = ids[i].trim();
            if (!id.isEmpty()) {
                out.add(id);
            }
        }
        return out;
    }

    public void saveModelCatalogue(List<String> ids) {
        StringBuilder sb = new StringBuilder();
        if (ids != null) {
            for (int i = 0; i < ids.size(); i++) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(ids.get(i));
            }
        }
        sp.edit().putString("catalogue", sb.toString()).apply();
    }

    public String imageModel() {
        String m = sp.getString("img_model", "");
        return m.isEmpty() ? WaveSpeed.DEFAULT_IMAGE_MODEL : m;
    }

    public String voiceModel() {
        String m = sp.getString("voice_model", "");
        return m.isEmpty() ? WaveSpeed.DEFAULT_VOICE_MODEL : m;
    }

    public String voiceId() {
        return sp.getString("voice_id", "");
    }

    public void saveMedia(String imageModel, String voiceModel, String voiceId) {
        sp.edit()
                .putString("img_model", nz(imageModel))
                .putString("voice_model", nz(voiceModel))
                .putString("voice_id", nz(voiceId))
                .apply();
    }

    public boolean voiceReady() {
        return !apiKey().isEmpty() && !voiceId().isEmpty();
    }

    public boolean imageReady() {
        return !apiKey().isEmpty();
    }

    /** Longueur maximale de la reponse du modele, en tokens. */
    public int maxTokens() {
        return Math.max(256, Math.min(16_000, sp.getInt("max_tokens", 4000)));
    }

    public void setMaxTokens(int tokens) {
        sp.edit().putInt("max_tokens", Math.max(256, Math.min(16_000, tokens))).apply();
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

    public Theme theme() {
        return Theme.from(sp.getString("theme", "NUIT"));
    }

    public void setTheme(Theme t) {
        sp.edit().putString("theme", t.name()).apply();
    }

    public boolean haptics() {
        return sp.getBoolean("haptics", true);
    }

    public void setHaptics(boolean on) {
        sp.edit().putBoolean("haptics", on).apply();
    }

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

    // --------------------------------------------------------------- contexte

    /** Fenetre de contexte du modele, en tokens. */
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
     * fenetre : l'historique n'est pas seul a y tenir, et chaque token se paie.
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
