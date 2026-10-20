package com.lvx.rpkeyboard;

import android.app.Activity;
import android.content.Intent;
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
        col.addView(hint("Pour lire l'ecran il faut un modele a vision. Pistes : "
                + join(WaveSpeed.VISION_MODELS)));

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

    private void ping() {
        pingResult.setTextColor(Color.parseColor(MUTED));
        pingResult.setText("Test en cours...");
        final Handler ui = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override
            public void run() {
                String message;
                boolean success = false;
                try {
                    PromptBuilder.Config c = cfg.config("");
                    c.count = 1;
                    AiEngine.Result r = new AiEngine(
                            new LlmClient(cfg.apiKey(), cfg.model()).withMaxTokens(256), c)
                            .fromHistory(new ArrayList<ChatMessage>());
                    if (r.isOk()) {
                        message = "OK — " + r.variants.get(0);
                        if (message.length() > 160) {
                            message = message.substring(0, 160) + "…";
                        }
                        success = true;
                    } else {
                        message = "Echec : " + r.error;
                    }
                } catch (Exception e) {
                    message = "Echec : " + (e.getMessage() == null ? e.toString() : e.getMessage());
                }
                final boolean ok = success;
                final String shown = message;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        pingResult.setTextColor(Color.parseColor(ok ? "#7AD6A0" : "#E2777A"));
                        pingResult.setText(shown);
                    }
                });
            }
        }).start();
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
