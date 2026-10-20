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
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Ecran de reglages : persos, univers, modele. */
public class SettingsActivity extends Activity {

    private Prefs prefs;
    private EditText meField;
    private EditText partnerField;
    private EditText universeField;
    private EditText modelField;
    private EditText countField;
    private TextView pingResult;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = new Prefs(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#14121A"));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        col.setPadding(pad, pad, pad, pad);
        scroll.addView(col);

        col.addView(title("Clavier RP"));
        col.addView(hint("Les propositions sont ecrites dans le champ de saisie. "
                + "Tu relis et tu envoies toi-meme."));

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

        col.addView(spacer(dp(22)));
        col.addView(label("Mon personnage"));
        meField = field(prefs.me(), "Nom, caractere, facon de parler...", 3);
        col.addView(meField);

        col.addView(spacer(dp(14)));
        col.addView(label("Son personnage"));
        partnerField = field(prefs.partner(), "Qui elle joue dans le RP...", 3);
        col.addView(partnerField);

        col.addView(spacer(dp(14)));
        col.addView(label("Univers et ton"));
        universeField = field(prefs.universe(), "Cadre, epoque, ambiance, rythme...", 3);
        col.addView(universeField);

        col.addView(spacer(dp(14)));
        col.addView(label("Modele (vision requise)"));
        modelField = field(prefs.model(), Prefs.DEFAULT_MODEL, 1);
        modelField.setInputType(InputType.TYPE_CLASS_TEXT);
        col.addView(modelField);
        col.addView(hint("Presets : " + join(Prefs.MODEL_PRESETS)));

        col.addView(spacer(dp(14)));
        col.addView(label("Nombre de propositions"));
        countField = field(String.valueOf(prefs.count()), "3", 1);
        countField.setInputType(InputType.TYPE_CLASS_NUMBER);
        col.addView(countField);

        col.addView(spacer(dp(22)));
        col.addView(primary("Enregistrer", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
                Toast.makeText(SettingsActivity.this, "Enregistre", Toast.LENGTH_SHORT).show();
            }
        }));

        col.addView(spacer(dp(10)));
        col.addView(action("Tester la connexion", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
                ping();
            }
        }));

        pingResult = hint("");
        col.addView(pingResult);

        col.addView(spacer(dp(24)));
        col.addView(hint("La capture d'ecran ne se declenche qu'a l'appui du bouton ✨ "
                + "et passe par l'autorisation Android. Une notification reste visible "
                + "tant que la session de lecture est ouverte ; le bouton Arreter la ferme."));

        setContentView(scroll);
    }

    private void save() {
        int count;
        try {
            count = Integer.parseInt(countField.getText().toString().trim());
        } catch (Exception e) {
            count = 3;
        }
        count = Math.max(1, Math.min(5, count));

        String model = modelField.getText().toString().trim();
        if (model.isEmpty()) {
            model = Prefs.DEFAULT_MODEL;
        }

        prefs.save(model,
                meField.getText().toString().trim(),
                partnerField.getText().toString().trim(),
                universeField.getText().toString().trim(),
                count);
    }

    private void ping() {
        pingResult.setTextColor(Color.parseColor("#9A93AE"));
        pingResult.setText("Test en cours...");
        final Handler ui = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override
            public void run() {
                String message;
                boolean success = false;
                try {
                    String answer = new WaveSpeedClient(new Prefs(SettingsActivity.this)).ping();
                    message = "OK — reponse : " + answer.trim();
                    success = true;
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
        t.setTextColor(Color.parseColor("#C9C2DB"));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setPadding(0, 0, 0, dp(6));
        return t;
    }

    private TextView hint(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(Color.parseColor("#8A83A0"));
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
        e.setBackground(box("#1E1A28"));
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
        b.setBackground(box("#6C4BD6"));
        b.setTextColor(Color.WHITE);
        return b;
    }

    private Button action(String text, View.OnClickListener listener) {
        Button b = baseButton(text, listener);
        b.setBackground(box("#241F31"));
        b.setTextColor(Color.parseColor("#C9C2DB"));
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
