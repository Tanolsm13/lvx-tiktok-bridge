package com.lvx.rpkeyboard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.lvx.rpkeyboard.ai.AiEngine;
import com.lvx.rpkeyboard.ai.ChatMessage;
import com.lvx.rpkeyboard.ai.Completer;
import com.lvx.rpkeyboard.ai.LlmClient;
import com.lvx.rpkeyboard.ai.PromptBuilder;
import com.lvx.rpkeyboard.ai.WaveSpeed;
import com.lvx.rpkeyboard.ime.Theme;

import java.util.ArrayList;
import java.util.List;

/** Reglages. Un seul fournisseur : WaveSpeed porte le texte, l'image et le vocal. */
public class SettingsActivity extends Activity {

    private static final String BG = "#14121A";
    private static final String FIELD = "#1E1A28";
    private static final String ACCENT = "#6C4BD6";
    private static final String SOFT = "#241F31";
    private static final String LABEL = "#C9C2DB";
    private static final String MUTED = "#8A83A0";

    private static final int PICK_BG = 9001;

    private com.lvx.rpkeyboard.data.Settings cfg;

    private EditText keyField;
    private EditText modelField;
    private EditText maxTokensField;
    private EditText fallbackField;
    private CheckBox fallbackBox;
    private Spinner modeSpinner;
    private EditText instructionField;
    private CheckBox roleplayBox;
    private EditText meField;
    private EditText partnerField;
    private EditText universeField;
    private EditText styleField;
    private EditText countField;
    private EditText voiceModelField;
    private EditText voiceIdField;
    private EditText imageModelField;
    private Spinner themeSpinner;
    private CheckBox animBox;
    private CheckBox hapticsBox;
    private EditText scrimField;
    private EditText ctxWindowField;
    private EditText ctxShareField;
    private TextView keyHint;
    private TextView bgHint;
    private TextView ctxHint;
    private TextView pingResult;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        cfg = com.lvx.rpkeyboard.data.Settings.get(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor(BG));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        col.setPadding(pad, pad, pad, pad);
        scroll.addView(col);

        col.addView(title("Clavier RP"));
        col.addView(hint("Tout passe par WaveSpeed : le texte, les images et le vocal. "
                + "Une seule cle."));

        // --- activation ---------------------------------------------------
        col.addView(spacer(dp(18)));
        col.addView(label("Activer le clavier"));
        col.addView(action("Ouvrir les reglages de clavier", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS));
            }
        }));
        col.addView(action("Choisir le clavier actif", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                InputMethodManager imm =
                        (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showInputMethodPicker();
                }
            }
        }));

        // --- cle ------------------------------------------------------------
        col.addView(spacer(dp(22)));
        col.addView(label("Cle WaveSpeed"));
        keyField = field(cfg.usesBuiltinKey() ? "" : cfg.apiKey(), "colle ta cle ici", 1);
        keyField.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        col.addView(keyField);
        keyHint = hint("");
        col.addView(keyHint);

        // --- modele texte ---------------------------------------------------
        col.addView(spacer(dp(22)));
        col.addView(label("Modele de texte"));
        modelField = field(cfg.model(), WaveSpeed.DEFAULT_LLM_MODEL, 1);
        col.addView(modelField);
        col.addView(hint("Pour lire l'ecran il faut un modele a vision. Les "
                + "identifiants sont sensibles a la casse et portent un prefixe "
                + "de vendeur. Pistes : " + join(WaveSpeed.VISION_MODELS)));
        col.addView(spacer(dp(8)));
        col.addView(action("Lister les modeles de mon compte", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveAll();
                loadCatalogue();
            }
        }));
        col.addView(hint("Va chercher le catalogue chez WaveSpeed : plus besoin de "
                + "deviner un identifiant."));

        col.addView(spacer(dp(12)));
        fallbackBox = new CheckBox(this);
        fallbackBox.setText("Repli automatique sur un autre modele");
        fallbackBox.setTextColor(Color.parseColor(LABEL));
        fallbackBox.setChecked(cfg.fallbackEnabled());
        col.addView(fallbackBox);
        col.addView(hint("Quand un modele refuse, se vide ou s'etrangle, l'app passe "
                + "au suivant de la liste au lieu de te laisser sans reponse."));
        fallbackField = field(commas(cfg.fallbackModels()),
                commas(WaveSpeed.VISION_MODELS), 2);
        col.addView(fallbackField);

        col.addView(spacer(dp(12)));
        col.addView(label("Longueur maximale de reponse (tokens)"));
        maxTokensField = field(String.valueOf(cfg.maxTokens()), "4000", 1);
        maxTokensField.setInputType(InputType.TYPE_CLASS_NUMBER);
        col.addView(maxTokensField);
        col.addView(hint("Monte si tes messages de jeu de role sont tronques."));

        // --- pilotage -------------------------------------------------------
        col.addView(spacer(dp(22)));
        col.addView(label("Pilotage de l'IA"));
        modeSpinner = new Spinner(this);
        List<String> modes = new ArrayList<String>();
        modes.add("Adaptatif - l'IA s'accorde au fil");
        modes.add("Instruction unique - ma consigne, toujours");
        modeSpinner.setAdapter(adapter(modes));
        modeSpinner.setSelection(cfg.mode() == PromptBuilder.Mode.ADAPTATIF ? 0 : 1);
        modeSpinner.setBackground(box(FIELD));
        col.addView(modeSpinner);

        col.addView(spacer(dp(12)));
        col.addView(label("Consigne unique"));
        instructionField = field(cfg.instruction(),
                "appliquee telle quelle a chaque reponse", 2);
        col.addView(instructionField);

        col.addView(spacer(dp(18)));
        roleplayBox = new CheckBox(this);
        roleplayBox.setText("Mode jeu de role");
        roleplayBox.setTextColor(Color.parseColor(LABEL));
        roleplayBox.setChecked(cfg.roleplay());
        col.addView(roleplayBox);
        col.addView(hint("Decoche pour une conversation ordinaire : l'IA ecrit alors "
                + "avec ton style habituel au lieu de jouer un personnage."));

        col.addView(spacer(dp(12)));
        col.addView(label("Mon personnage"));
        meField = field(cfg.me(), "nom, caractere, facon de parler...", 3);
        col.addView(meField);

        col.addView(spacer(dp(12)));
        col.addView(label("Son personnage"));
        partnerField = field(cfg.partner(), "qui elle joue dans le RP...", 3);
        col.addView(partnerField);

        col.addView(spacer(dp(12)));
        col.addView(label("Univers et ton"));
        universeField = field(cfg.universe(), "cadre, epoque, ambiance, rythme...", 3);
        col.addView(universeField);

        col.addView(spacer(dp(12)));
        col.addView(label("Mon style d'ecriture (hors jeu de role)"));
        styleField = field(cfg.styleProfile(),
                "longueur, ponctuation, emoji, tics de langage...", 3);
        col.addView(styleField);

        col.addView(spacer(dp(12)));
        col.addView(label("Nombre de propositions"));
        countField = field(String.valueOf(cfg.count()), "3", 1);
        countField.setInputType(InputType.TYPE_CLASS_NUMBER);
        col.addView(countField);

        // --- vocal ----------------------------------------------------------
        col.addView(spacer(dp(22)));
        col.addView(label("Vocal"));
        col.addView(hint("WaveSpeed heberge les modeles ElevenLabs : pas de cle "
                + "separee. Le bouton micro synthetise le texte du champ et ouvre "
                + "le partage du fichier audio."));
        voiceModelField = field(cfg.voiceModel(), WaveSpeed.DEFAULT_VOICE_MODEL, 1);
        col.addView(voiceModelField);
        col.addView(spacer(dp(8)));
        voiceIdField = field(cfg.voiceId(), "identifiant de la voix", 1);
        col.addView(voiceIdField);
        col.addView(hint("Les voix sont celles du catalogue ElevenLabs chez WaveSpeed, "
                + "pas une voix clonee sur un compte personnel."));

        // --- image ----------------------------------------------------------
        col.addView(spacer(dp(22)));
        col.addView(label("Modele d'image"));
        imageModelField = field(cfg.imageModel(), WaveSpeed.DEFAULT_IMAGE_MODEL, 1);
        col.addView(imageModelField);
        col.addView(hint("Pistes : " + join(WaveSpeed.IMAGE_MODELS)));

        // --- apparence --------------------------------------------------------
        col.addView(spacer(dp(22)));
        col.addView(label("Theme"));
        themeSpinner = new Spinner(this);
        List<String> themeNames = new ArrayList<String>();
        for (Theme t : Theme.values()) {
            themeNames.add(t.label);
        }
        themeSpinner.setAdapter(adapter(themeNames));
        themeSpinner.setSelection(cfg.theme().ordinal());
        themeSpinner.setBackground(box(FIELD));
        col.addView(themeSpinner);

        col.addView(spacer(dp(12)));
        col.addView(action("Choisir une image de fond", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .setType("image/*")
                        .addCategory(Intent.CATEGORY_OPENABLE), PICK_BG);
            }
        }));
        col.addView(action("Retirer l'image de fond", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cfg.setBackground("");
                refreshBgHint();
            }
        }));
        bgHint = hint("");
        col.addView(bgHint);

        col.addView(spacer(dp(10)));
        col.addView(label("Voile sur l'image (0 a 100)"));
        scrimField = field(String.valueOf(cfg.scrim()), "62", 1);
        scrimField.setInputType(InputType.TYPE_CLASS_NUMBER);
        col.addView(scrimField);

        col.addView(spacer(dp(10)));
        animBox = new CheckBox(this);
        animBox.setText("Animations des touches");
        animBox.setTextColor(Color.parseColor(LABEL));
        animBox.setChecked(cfg.animations());
        col.addView(animBox);

        hapticsBox = new CheckBox(this);
        hapticsBox.setText("Vibration a la frappe");
        hapticsBox.setTextColor(Color.parseColor(LABEL));
        hapticsBox.setChecked(cfg.haptics());
        col.addView(hapticsBox);

        // --- bulle ------------------------------------------------------------
        col.addView(spacer(dp(22)));
        col.addView(label("Bulle flottante"));
        col.addView(hint("Declenche la lecture d'ecran clavier ferme. Sans clavier "
                + "ouvert il n'y a pas de champ a remplir : la proposition choisie "
                + "part dans le presse-papiers."));
        col.addView(action("Autoriser l'affichage par-dessus les apps",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startActivity(BubbleService.overlayPermissionIntent(
                                SettingsActivity.this));
                    }
                }));
        col.addView(action("Afficher la bulle", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!BubbleService.canOverlay(SettingsActivity.this)) {
                    Toast.makeText(SettingsActivity.this,
                            "Autorise d'abord l'affichage par-dessus les apps",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                startForegroundService(new Intent(SettingsActivity.this, BubbleService.class)
                        .setAction(BubbleService.ACTION_SHOW));
            }
        }));
        col.addView(action("Masquer la bulle", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startService(new Intent(SettingsActivity.this, BubbleService.class)
                        .setAction(BubbleService.ACTION_HIDE));
            }
        }));

        // --- contexte ---------------------------------------------------------
        col.addView(spacer(dp(22)));
        col.addView(label("Fenetre de contexte du modele (tokens)"));
        ctxWindowField = field(String.valueOf(cfg.contextWindow()), "200000", 1);
        ctxWindowField.setInputType(InputType.TYPE_CLASS_NUMBER);
        col.addView(ctxWindowField);

        col.addView(spacer(dp(8)));
        col.addView(label("Part utilisee pour l'historique (%)"));
        ctxShareField = field(String.valueOf(cfg.contextShare()), "8", 1);
        ctxShareField.setInputType(InputType.TYPE_CLASS_NUMBER);
        col.addView(ctxShareField);
        ctxHint = hint("");
        col.addView(ctxHint);

        // --- actions -----------------------------------------------------------
        col.addView(spacer(dp(24)));
        col.addView(primary("Enregistrer", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveAll();
                Toast.makeText(SettingsActivity.this, "Enregistre", Toast.LENGTH_SHORT).show();
            }
        }));
        col.addView(spacer(dp(8)));
        col.addView(action("Tester la connexion", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveAll();
                ping();
            }
        }));
        pingResult = hint("");
        col.addView(pingResult);

        col.addView(spacer(dp(24)));
        col.addView(hint("La capture d'ecran ne se declenche qu'a l'appui du bouton "
                + "✨ et passe par l'autorisation Android. Le bouton ⚡ reprend "
                + "ce que tu as tape dans le champ comme consigne ponctuelle."));

        setContentView(scroll);
        refreshKeyHint();
        refreshBgHint();
        refreshCtxHint();
    }

    private void saveAll() {
        cfg.setApiKey(keyField.getText().toString());
        cfg.setModel(modelField.getText().toString());
        cfg.setFallbackEnabled(fallbackBox.isChecked());
        cfg.setFallbackModels(fallbackField.getText().toString());
        cfg.setMaxTokens(intOf(maxTokensField, 4000));
        cfg.setMode(modeSpinner.getSelectedItemPosition() == 0
                ? PromptBuilder.Mode.ADAPTATIF
                : PromptBuilder.Mode.INSTRUCTION_UNIQUE);
        cfg.setRoleplay(roleplayBox.isChecked());
        cfg.saveProfile(
                instructionField.getText().toString(),
                meField.getText().toString(),
                partnerField.getText().toString(),
                universeField.getText().toString(),
                styleField.getText().toString(),
                intOf(countField, 3));
        cfg.saveMedia(imageModelField.getText().toString(),
                voiceModelField.getText().toString(),
                voiceIdField.getText().toString());
        cfg.setTheme(Theme.values()[themeSpinner.getSelectedItemPosition()]);
        cfg.setAnimations(animBox.isChecked());
        cfg.setHaptics(hapticsBox.isChecked());
        cfg.setScrim(intOf(scrimField, 62));
        cfg.setContextWindow(intOf(ctxWindowField, 200_000));
        cfg.setContextShare(intOf(ctxShareField, 8));
        refreshKeyHint();
        refreshCtxHint();
    }

    /**
     * Diagnostic en deux temps, parce qu'un seul test ne dit pas ou ca casse.
     *
     * D'abord le texte : la cle, l'identifiant du modele et le reseau. Puis la
     * vision, avec une image fabriquee ici qui porte un mot connu. Si le modele
     * ne rend pas ce mot, il repond sans voir l'ecran — et c'est exactement ce
     * qui produisait des propositions vides ou hors sujet en 3.0.
     */
    private void ping() {
        pingResult.setTextColor(Color.parseColor(MUTED));
        pingResult.setText("Test du texte...");
        final Handler ui = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override
            public void run() {
                StringBuilder report = new StringBuilder();
                boolean ok = textStage(report, ui);
                if (ok) {
                    ok = visionStage(report, ui);
                }
                show(ui, report.toString(), ok);
            }
        }).start();
    }

    /** Premier temps : un aller-retour de texte, sans image. */
    private boolean textStage(StringBuilder report, Handler ui) {
        try {
            PromptBuilder.Config c = cfg.config(
                    "Ignore tout le reste et reponds uniquement par le mot : PRET");
            c.count = 1;
            AiEngine.Result r = engineFor(c).fromHistory(new ArrayList<ChatMessage>());
            if (!r.isOk()) {
                report.append("Texte : echec.\n").append(r.error);
                if (!r.model.isEmpty()) {
                    report.append("\nDernier modele essaye : ").append(r.model);
                }
                return false;
            }
            report.append("Texte : OK");
            if (r.switched) {
                report.append(" — mais c'est ").append(r.model)
                        .append(" qui a repondu, pas ").append(cfg.model());
            }
            report.append(".\n");
            return true;
        } catch (Exception e) {
            report.append("Texte : echec.\n").append(msg(e));
            return false;
        }
    }

    /** Second temps : une image fabriquee, pour verifier que le modele voit. */
    private boolean visionStage(StringBuilder report, Handler ui) {
        ui.post(new Runnable() {
            @Override
            public void run() {
                pingResult.setText("Test de la vision...");
            }
        });
        try {
            PromptBuilder.Config c = cfg.config(
                    "Ignore la consigne de jeu de role. Il n'y a pas de conversation "
                            + "dans cette image : un seul mot y est ecrit en gros. "
                            + "Reponds uniquement par ce mot, en majuscules.");
            c.count = 1;
            AiEngine.Result r = engineFor(c).fromScreenshot(testJpeg(VISION_WORD));
            if (!r.isOk()) {
                report.append("Vision : echec.\n").append(r.error);
                if (!r.model.isEmpty()) {
                    report.append("\nDernier modele essaye : ").append(r.model);
                }
                return false;
            }
            String answer = r.variants.get(0);
            boolean saw = answer.toUpperCase(java.util.Locale.ROOT).contains(VISION_WORD);
            if (saw) {
                report.append("Vision : OK, le modele lit bien l'image");
                if (r.switched) {
                    report.append(" (par ").append(r.model).append(')');
                }
                report.append('.');
                return true;
            }
            report.append("Vision : le modele repond mais ne lit pas l'image.\n")
                    .append("Attendu ").append(VISION_WORD).append(", recu : ")
                    .append(cut(answer, 90))
                    .append("\nPrends un modele marque Vision, par exemple ")
                    .append(WaveSpeed.VISION_MODELS[0]).append('.');
            return false;
        } catch (Exception e) {
            report.append("Vision : echec.\n").append(msg(e));
            return false;
        }
    }

    /** Mot temoin du test de vision : court, sans accent, improbable au hasard. */
    private static final String VISION_WORD = "MANGUE";

    /** Meme chaine de modeles que le clavier, pour tester ce qui sert vraiment. */
    private AiEngine engineFor(PromptBuilder.Config c) {
        List<Completer> others = new ArrayList<Completer>();
        if (cfg.fallbackEnabled()) {
            List<String> ids = cfg.fallbackModels();
            for (int i = 0; i < ids.size(); i++) {
                others.add(new LlmClient(cfg.apiKey(), ids.get(i)).withMaxTokens(900));
            }
        }
        return new AiEngine(new LlmClient(cfg.apiKey(), cfg.model()).withMaxTokens(900), c)
                .withBudget(cfg.contextBudgetChars())
                .withFallback(others)
                .withRescue(cfg.fallbackEnabled() ? rescue() : null);
    }

    /**
     * Le meme dernier recours que le clavier : un modele pris au catalogue.
     *
     * Le test doit dire ce qui se passera pour de vrai. S'il se limitait aux
     * modeles ecrits dans les reglages, il annoncerait un echec la ou le
     * clavier, lui, trouverait quand meme de quoi repondre.
     */
    private AiEngine.Rescue rescue() {
        return new AiEngine.Rescue() {
            @Override
            public List<Completer> candidates(List<String> tried) throws Exception {
                List<String> catalogue = LlmClient.listModels(cfg.apiKey());
                cfg.saveModelCatalogue(catalogue);
                List<Completer> out = new ArrayList<Completer>();
                List<String> ids = WaveSpeed.visionCandidates(catalogue, tried, 2);
                for (int i = 0; i < ids.size(); i++) {
                    out.add(new LlmClient(cfg.apiKey(), ids.get(i)).withMaxTokens(900));
                }
                return out;
            }
        };
    }

    /**
     * Une image JPEG portant un mot, fabriquee sur l'appareil.
     *
     * Elle remplace la capture d'ecran le temps du test : aucune autorisation
     * a demander, et le resultat attendu est connu d'avance.
     */
    private static byte[] testJpeg(String word) {
        Bitmap bmp = Bitmap.createBitmap(640, 320, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        canvas.drawColor(Color.WHITE);
        android.graphics.Paint paint = new android.graphics.Paint();
        paint.setAntiAlias(true);
        paint.setColor(Color.BLACK);
        paint.setTextSize(96);
        paint.setTextAlign(android.graphics.Paint.Align.CENTER);
        canvas.drawText(word, 320, 190, paint);
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG, 92, bos);
        bmp.recycle();
        return bos.toByteArray();
    }

    /**
     * Demande a WaveSpeed la liste des modeles du compte.
     *
     * Un identifiant invente rend une 404 et, pire, un identifiant proche mais
     * sans vision rend des reponses vides. Autant lire le catalogue.
     */
    private void loadCatalogue() {
        pingResult.setTextColor(Color.parseColor(MUTED));
        pingResult.setText("Lecture du catalogue...");
        final Handler ui = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<String> ids = LlmClient.listModels(cfg.apiKey());
                    if (ids.isEmpty()) {
                        show(ui, "Le compte ne renvoie aucun modele.", false);
                        return;
                    }
                    cfg.saveModelCatalogue(ids);
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            pingResult.setTextColor(Color.parseColor(MUTED));
                            pingResult.setText(ids.size() + " modeles disponibles.");
                            pickModel(ids);
                        }
                    });
                } catch (Exception e) {
                    List<String> cached = cfg.modelCatalogue();
                    if (cached.isEmpty()) {
                        show(ui, "Catalogue illisible : " + msg(e), false);
                        return;
                    }
                    final List<String> shown = cached;
                    final String why = msg(e);
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            pingResult.setTextColor(Color.parseColor("#E2777A"));
                            pingResult.setText("Catalogue hors ligne (" + why
                                    + ") : liste de la derniere fois.");
                            pickModel(shown);
                        }
                    });
                }
            }
        }).start();
    }

    /** Choix du modele principal dans la liste, avec marquage des pistes vision. */
    private void pickModel(final List<String> ids) {
        final String[] items = new String[ids.size()];
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            items[i] = looksVisual(id) ? id + "  (vision ?)" : id;
        }
        new AlertDialog.Builder(this)
                .setTitle("Modele de texte")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        modelField.setText(ids.get(which));
                        cfg.setModel(ids.get(which));
                        Toast.makeText(SettingsActivity.this,
                                ids.get(which), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    /**
     * Indice, pas verdict : le nom suggere un modele a vision.
     *
     * L'API ne dit pas quelles entrees lisent une image, donc on signale une
     * piste plutot que de laisser une liste nue. Le test de vision tranche.
     * Meme regle que celle du clavier quand il doit se rabattre tout seul sur
     * un modele du catalogue : un seul endroit, un seul comportement.
     */
    static boolean looksVisual(String id) {
        return WaveSpeed.looksVisual(id);
    }

    private void show(Handler ui, final String text, final boolean ok) {
        ui.post(new Runnable() {
            @Override
            public void run() {
                pingResult.setTextColor(Color.parseColor(ok ? "#7AD6A0" : "#E2777A"));
                pingResult.setText(text);
            }
        });
    }

    private static String msg(Exception e) {
        String m = e.getMessage();
        return (m == null || m.trim().isEmpty()) ? e.toString() : m;
    }

    private static String cut(String s, int max) {
        String one = s.replace("\n", " ").trim();
        return one.length() <= max ? one : one.substring(0, max) + "…";
    }

    @Override
    protected void onActivityResult(int req, int code, Intent data) {
        super.onActivityResult(req, code, data);
        if (req != PICK_BG || code != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        try {
            java.io.InputStream in = getContentResolver().openInputStream(data.getData());
            if (in == null) {
                throw new Exception("image illisible");
            }
            java.io.File out = new java.io.File(getFilesDir(), "fond.img");
            java.io.OutputStream os = new java.io.FileOutputStream(out);
            try {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    os.write(buf, 0, n);
                }
                os.flush();
            } finally {
                in.close();
                os.close();
            }
            cfg.setBackground(out.getAbsolutePath());
            refreshBgHint();
        } catch (Exception e) {
            Toast.makeText(this, "Echec : " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void refreshKeyHint() {
        keyHint.setText(cfg.usesBuiltinKey()
                ? "Une cle est deja incluse dans cette version : laisse vide pour l'utiliser."
                : (cfg.apiKey().isEmpty() ? "Aucune cle : rien ne fonctionnera." : ""));
    }

    private void refreshBgHint() {
        bgHint.setText(cfg.background().isEmpty()
                ? "Aucune image : fond uni du theme." : "Image active.");
    }

    private void refreshCtxHint() {
        int chars = cfg.contextBudgetChars();
        ctxHint.setText("Budget d'historique : environ " + (chars / 4) + " tokens ("
                + chars + " caracteres). Au-dela, les anciens messages sont resumes "
                + "localement, sans appel paye.");
    }

    // ------------------------------------------------------------- fabrique

    private static int intOf(EditText f, int fallback) {
        try {
            return Integer.parseInt(f.getText().toString().trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private ArrayAdapter<String> adapter(List<String> items) {
        ArrayAdapter<String> a = new ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_item, items) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView v = (TextView) super.getView(position, convertView, parent);
                v.setTextColor(Color.WHITE);
                v.setPadding(dp(12), dp(10), dp(12), dp(10));
                return v;
            }
        };
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return a;
    }

    private TextView title(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        return t;
    }

    private TextView label(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(Color.parseColor(LABEL));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setPadding(0, 0, 0, dp(6));
        return t;
    }

    private TextView hint(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(Color.parseColor(MUTED));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        t.setPadding(0, dp(6), 0, 0);
        return t;
    }

    private EditText field(String value, String hint, int lines) {
        EditText e = new EditText(this);
        e.setText(value);
        e.setHint(hint);
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(Color.parseColor("#6C657E"));
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        e.setBackground(box(FIELD));
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        e.setMinLines(lines);
        e.setGravity(Gravity.TOP | Gravity.START);
        if (lines > 1) {
            e.setInputType(InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        }
        e.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return e;
    }

    private Button primary(String text, View.OnClickListener listener) {
        Button b = baseButton(text, listener);
        b.setBackground(box(ACCENT));
        b.setTextColor(Color.WHITE);
        return b;
    }

    private Button action(String text, View.OnClickListener listener) {
        Button b = baseButton(text, listener);
        b.setBackground(box(SOFT));
        b.setTextColor(Color.parseColor(LABEL));
        return b;
    }

    private Button baseButton(String text, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        b.setPadding(dp(16), dp(12), dp(16), dp(12));
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        b.setLayoutParams(lp);
        return b;
    }

    private View spacer(int height) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, height));
        return v;
    }

    private GradientDrawable box(String color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.parseColor(color));
        d.setCornerRadius(dp(10));
        return d;
    }

    /** Pour un champ : virgules, c'est ce que le parseur attend en premier. */
    private static String commas(List<String> values) {
        return commas(values.toArray(new String[0]));
    }

    private static String commas(String[] values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(values[i]);
        }
        return sb.toString();
    }

    private static String join(String[] values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append("  ·  ");
            }
            sb.append(values[i]);
        }
        return sb.toString();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
