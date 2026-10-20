package com.lvx.rpkeyboard;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
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
import com.lvx.rpkeyboard.ai.LlmClient;
import com.lvx.rpkeyboard.ai.PromptBuilder;
import com.lvx.rpkeyboard.ai.Provider;

import java.util.ArrayList;
import java.util.List;

/** Ecran de reglages : fournisseur, modele, mode, personnages. */
public class SettingsActivity extends Activity {

    private static final String BG = "#14121A";
    private static final String FIELD = "#1E1A28";
    private static final String ACCENT = "#6C4BD6";
    private static final String SOFT = "#241F31";
    private static final String LABEL = "#C9C2DB";
    private static final String MUTED = "#8A83A0";

    private com.lvx.rpkeyboard.data.Settings cfg;

    private Spinner providerSpinner;
    private Spinner modeSpinner;
    private EditText keyField;
    private EditText modelField;
    private EditText urlField;
    private EditText instructionField;
    private EditText meField;
    private EditText partnerField;
    private EditText universeField;
    private EditText styleField;
    private EditText countField;
    private CheckBox roleplayBox;
    private TextView pingResult;
    private TextView keyHint;

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
        col.addView(hint("Les propositions sont ecrites dans le champ de saisie. "
                + "Tu relis et tu envoies toi-meme."));

        // --- activation -------------------------------------------------
        col.addView(spacer(dp(18)));
        col.addView(label("Activer le clavier"));
        col.addView(action("Ouvrir les reglages de clavier", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
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

        // --- fournisseur ------------------------------------------------
        col.addView(spacer(dp(22)));
        col.addView(label("Fournisseur"));
        providerSpinner = new Spinner(this);
        List<String> names = new ArrayList<String>();
        for (Provider p : Provider.values()) {
            names.add(p.label);
        }
        providerSpinner.setAdapter(adapter(names));
        providerSpinner.setSelection(cfg.provider().ordinal());
        providerSpinner.setBackground(box(FIELD));
        providerSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                Provider chosen = Provider.values()[pos];
                if (chosen != cfg.provider()) {
                    saveAll();
                    cfg.setProvider(chosen);
                    reloadProviderFields();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        col.addView(providerSpinner);

        col.addView(spacer(dp(14)));
        col.addView(label("Cle API"));
        keyField = field(cfg.usesBuiltinKey() ? "" : cfg.apiKey(), "colle ta cle ici", 1);
        keyField.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        col.addView(keyField);
        keyHint = hint("");
        col.addView(keyHint);

        col.addView(spacer(dp(14)));
        col.addView(label("Modele"));
        modelField = field(cfg.model(), cfg.provider().defaultModel, 1);
        col.addView(modelField);
        col.addView(hint("Pour lire l'ecran il faut un modele a vision. Pistes WaveSpeed : "
                + join(Provider.VISION_PRESETS)));

        col.addView(spacer(dp(14)));
        col.addView(label("URL personnalisee (facultatif)"));
        urlField = field(cfg.baseUrl(), cfg.provider().defaultUrl, 1);
        col.addView(urlField);

        // --- pilotage ---------------------------------------------------
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

        col.addView(spacer(dp(14)));
        col.addView(label("Consigne unique"));
        instructionField = field(cfg.instruction(),
                "appliquee telle quelle a chaque reponse", 2);
        col.addView(instructionField);

        // --- jeu de role ------------------------------------------------
        col.addView(spacer(dp(22)));
        roleplayBox = new CheckBox(this);
        roleplayBox.setText("Mode jeu de role");
        roleplayBox.setTextColor(Color.parseColor(LABEL));
        roleplayBox.setChecked(cfg.roleplay());
        col.addView(roleplayBox);
        col.addView(hint("Decoche pour une conversation ordinaire : l'IA ecrit alors "
                + "avec ton style habituel au lieu de jouer un personnage."));

        col.addView(spacer(dp(14)));
        col.addView(label("Mon personnage"));
        meField = field(cfg.me(), "nom, caractere, facon de parler...", 3);
        col.addView(meField);

        col.addView(spacer(dp(14)));
        col.addView(label("Son personnage"));
        partnerField = field(cfg.partner(), "qui elle joue dans le RP...", 3);
        col.addView(partnerField);

        col.addView(spacer(dp(14)));
        col.addView(label("Univers et ton"));
        universeField = field(cfg.universe(), "cadre, epoque, ambiance, rythme...", 3);
        col.addView(universeField);

        col.addView(spacer(dp(14)));
        col.addView(label("Mon style d'ecriture (hors jeu de role)"));
        styleField = field(cfg.styleProfile(),
                "comment j'ecris d'habitude : longueur, ponctuation, emoji...", 3);
        col.addView(styleField);

        col.addView(spacer(dp(14)));
        col.addView(label("Nombre de propositions"));
        countField = field(String.valueOf(cfg.count()), "3", 1);
        countField.setInputType(InputType.TYPE_CLASS_NUMBER);
        col.addView(countField);

        // --- actions ----------------------------------------------------
        col.addView(spacer(dp(22)));
        col.addView(primary("Enregistrer", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveAll();
                Toast.makeText(SettingsActivity.this, "Enregistre", Toast.LENGTH_SHORT).show();
            }
        }));

        col.addView(spacer(dp(10)));
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
        col.addView(hint("La capture d'ecran ne se declenche qu'a l'appui du bouton ✨ "
                + "et passe par l'autorisation Android. Le bouton ⚡ reprend ce que tu as "
                + "tape dans le champ comme consigne ponctuelle. Une notification reste "
                + "visible tant que la session de lecture est ouverte."));

        setContentView(scroll);
        reloadProviderFields();
    }

    private void reloadProviderFields() {
        Provider p = cfg.provider();
        modelField.setText(cfg.model());
        modelField.setHint(p.defaultModel);
        urlField.setText(cfg.baseUrl());
        urlField.setHint(p.defaultUrl);
        keyField.setText(cfg.usesBuiltinKey() ? "" : cfg.apiKey());
        keyHint.setText(cfg.usesBuiltinKey()
                ? "Une cle est deja incluse dans cette version : laisse vide pour l'utiliser."
                : (cfg.apiKey().isEmpty() ? "Aucune cle pour ce fournisseur." : ""));
    }

    private void saveAll() {
        cfg.setApiKey(keyField.getText().toString());
        cfg.setModel(modelField.getText().toString());
        cfg.setBaseUrl(urlField.getText().toString());
        cfg.setMode(modeSpinner.getSelectedItemPosition() == 0
                ? PromptBuilder.Mode.ADAPTATIF
                : PromptBuilder.Mode.INSTRUCTION_UNIQUE);
        cfg.setRoleplay(roleplayBox.isChecked());

        int count;
        try {
            count = Integer.parseInt(countField.getText().toString().trim());
        } catch (Exception e) {
            count = 3;
        }
        cfg.saveProfile(
                instructionField.getText().toString(),
                meField.getText().toString(),
                partnerField.getText().toString(),
                universeField.getText().toString(),
                styleField.getText().toString(),
                count);
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
                    LlmClient client = new LlmClient(
                            cfg.provider(), cfg.baseUrl(), cfg.apiKey(), cfg.model());
                    PromptBuilder.Config c = cfg.config("");
                    c.count = 1;
                    AiEngine.Result r = new AiEngine(client, c)
                            .fromHistory(new ArrayList<com.lvx.rpkeyboard.ai.ChatMessage>());
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

    // ------------------------------------------------------------- fabrique

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
