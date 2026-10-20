package com.lvx.rpkeyboard;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Clavier AZERTY avec un bouton de lecture d'ecran a la demande. */
public class RpKeyboardService extends InputMethodService implements Bus.Listener {

    private static final String[][] LETTERS = {
            {"a", "z", "e", "r", "t", "y", "u", "i", "o", "p"},
            {"q", "s", "d", "f", "g", "h", "j", "k", "l", "m"},
            {"⇧", "w", "x", "c", "v", "b", "n", "'", "⌫"},
    };

    private static final String[][] SYMBOLS = {
            {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"},
            {"é", "è", "à", "ç", "ù", "ê", "â", "î", "ô", "û"},
            {"@", "#", "&", "(", ")", "-", "+", "=", "!", "⌫"},
    };

    private static final String KEY_SHIFT = "⇧";
    private static final String KEY_BACKSPACE = "⌫";
    private static final String KEY_LAYER = "?123";
    private static final String KEY_LAYER_BACK = "ABC";
    private static final String KEY_ENTER = "⏎";
    private static final String KEY_SPACE = "espace";

    /** Conservees pour survivre a la recreation de la vue apres la capture. */
    private static List<String> pending = new ArrayList<String>();

    private final Handler ui = new Handler(Looper.getMainLooper());

    private LinearLayout root;
    private LinearLayout keyRows;
    private HorizontalScrollView chipScroll;
    private LinearLayout chipRow;
    private TextView status;
    private boolean shifted = false;
    private boolean symbols = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Bus.setListener(this);
    }

    @Override
    public void onDestroy() {
        Bus.setListener(null);
        super.onDestroy();
    }

    @Override
    public View onCreateInputView() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#14121A"));
        int pad = dp(6);
        root.setPadding(pad, pad, pad, pad);

        root.addView(buildAssistBar());
        root.addView(buildChipRow());

        keyRows = new LinearLayout(this);
        keyRows.setOrientation(LinearLayout.VERTICAL);
        root.addView(keyRows);
        renderKeys();

        if (!pending.isEmpty()) {
            showChips(pending);
        }
        return root;
    }

    // ---------------------------------------------------------------- barre

    private View buildAssistBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(4), dp(2), dp(4), dp(6));

        Button read = new Button(this);
        read.setText("✨  Lire l'écran");
        read.setAllCaps(false);
        read.setTextColor(Color.WHITE);
        read.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        read.setBackground(pill("#6C4BD6"));
        read.setPadding(dp(14), dp(8), dp(14), dp(8));
        read.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestCapture();
            }
        });
        bar.addView(read, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        status = new TextView(this);
        status.setTextColor(Color.parseColor("#9A93AE"));
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        status.setPadding(dp(10), 0, dp(6), 0);
        status.setMaxLines(2);
        status.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        bar.addView(status, sp);

        Button settings = new Button(this);
        settings.setText("⚙");
        settings.setTextColor(Color.parseColor("#9A93AE"));
        settings.setBackground(pill("#221E2C"));
        settings.setPadding(dp(10), dp(8), dp(10), dp(8));
        settings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(RpKeyboardService.this, SettingsActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            }
        });
        bar.addView(settings, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        return bar;
    }

    private View buildChipRow() {
        chipScroll = new HorizontalScrollView(this);
        chipScroll.setVisibility(View.GONE);
        chipScroll.setPadding(dp(2), 0, dp(2), dp(6));

        chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        chipScroll.addView(chipRow);
        return chipScroll;
    }

    private void requestCapture() {
        pending.clear();
        hideChips();
        if (Prefs.API_KEY.startsWith("@")) {
            onError("Clé API absente de ce build");
            return;
        }
        if (CaptureService.isReady()) {
            onStatus("Lecture de l'écran...");
            startForegroundService(new Intent(this, CaptureService.class)
                    .setAction(CaptureService.ACTION_CAPTURE));
        } else {
            onStatus("Autorisation de capture...");
            Intent i = new Intent(this, CaptureActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
        }
    }

    // ------------------------------------------------------------ callbacks

    @Override
    public void onStatus(final String message) {
        ui.post(new Runnable() {
            @Override
            public void run() {
                if (status != null) {
                    status.setTextColor(Color.parseColor("#9A93AE"));
                    status.setText(message);
                }
            }
        });
    }

    @Override
    public void onError(final String message) {
        ui.post(new Runnable() {
            @Override
            public void run() {
                if (status != null) {
                    status.setTextColor(Color.parseColor("#E2777A"));
                    status.setText(message);
                }
            }
        });
    }

    @Override
    public void onSuggestions(final List<String> replies) {
        pending = new ArrayList<String>(replies);
        ui.post(new Runnable() {
            @Override
            public void run() {
                onStatus("");
                showChips(pending);
            }
        });
    }

    private void showChips(List<String> replies) {
        if (chipRow == null) {
            return;
        }
        chipRow.removeAllViews();
        for (int i = 0; i < replies.size(); i++) {
            chipRow.addView(chip(replies.get(i)));
        }

        Button clear = new Button(this);
        clear.setText("✕");
        clear.setTextColor(Color.parseColor("#9A93AE"));
        clear.setBackground(pill("#221E2C"));
        clear.setPadding(dp(12), dp(8), dp(12), dp(8));
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pending.clear();
                hideChips();
            }
        });
        chipRow.addView(clear);

        chipScroll.setVisibility(View.VISIBLE);
        chipScroll.scrollTo(0, 0);
    }

    private void hideChips() {
        if (chipScroll != null) {
            chipScroll.setVisibility(View.GONE);
        }
    }

    /** Un appui insere le texte dans le champ : l'envoi reste manuel. */
    private View chip(final String text) {
        Button b = new Button(this);
        b.setText(text.length() > 140 ? text.substring(0, 140) + "…" : text);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        b.setBackground(pill("#2C2740"));
        b.setPadding(dp(12), dp(8), dp(12), dp(8));
        b.setMaxWidth(dp(260));
        b.setMaxLines(3);
        b.setEllipsize(TextUtils.TruncateAt.END);
        b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(6);
        b.setLayoutParams(lp);

        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) {
                    ic.commitText(text, text.length());
                }
                pending.clear();
                hideChips();
                onStatus("Relis, modifie, puis envoie");
            }
        });
        return b;
    }

    // -------------------------------------------------------------- touches

    private void renderKeys() {
        keyRows.removeAllViews();
        String[][] layer = symbols ? SYMBOLS : LETTERS;

        for (int r = 0; r < layer.length; r++) {
            LinearLayout row = newRow();
            for (int c = 0; c < layer[r].length; c++) {
                String label = layer[r][c];
                float weight = KEY_BACKSPACE.equals(label) || KEY_SHIFT.equals(label) ? 1.5f : 1f;
                row.addView(key(label, weight, isSpecial(label)));
            }
            keyRows.addView(row);
        }

        LinearLayout bottom = newRow();
        bottom.addView(key(symbols ? KEY_LAYER_BACK : KEY_LAYER, 1.6f, true));
        bottom.addView(key(",", 1f, false));
        bottom.addView(key(KEY_SPACE, 4.4f, false));
        bottom.addView(key(".", 1f, false));
        bottom.addView(key(KEY_ENTER, 1.6f, true));
        keyRows.addView(bottom);
    }

    private static boolean isSpecial(String label) {
        return KEY_SHIFT.equals(label) || KEY_BACKSPACE.equals(label)
                || KEY_LAYER.equals(label) || KEY_LAYER_BACK.equals(label)
                || KEY_ENTER.equals(label);
    }

    private LinearLayout newRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(4);
        row.setLayoutParams(lp);
        return row;
    }

    private View key(final String label, float weight, boolean special) {
        Button b = new Button(this);
        boolean upper = shifted && !symbols && label.length() == 1;
        b.setText(upper ? label.toUpperCase() : label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, KEY_SPACE.equals(label) ? 13 : 16);
        b.setBackground(pill(special ? "#221E2C" : "#322C45"));
        b.setPadding(0, dp(12), 0, dp(12));

        if (KEY_SHIFT.equals(label) && shifted) {
            b.setBackground(pill("#6C4BD6"));
        }

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, weight);
        lp.rightMargin = dp(4);
        b.setLayoutParams(lp);

        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onKeyPress(label);
            }
        });
        return b;
    }

    private void onKeyPress(String label) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            return;
        }

        if (KEY_SHIFT.equals(label)) {
            shifted = !shifted;
            renderKeys();
            return;
        }
        if (KEY_LAYER.equals(label) || KEY_LAYER_BACK.equals(label)) {
            symbols = !symbols;
            renderKeys();
            return;
        }
        if (KEY_BACKSPACE.equals(label)) {
            if (TextUtils.isEmpty(ic.getSelectedText(0))) {
                ic.deleteSurroundingText(1, 0);
            } else {
                ic.commitText("", 1);
            }
            return;
        }
        if (KEY_ENTER.equals(label)) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
            return;
        }
        if (KEY_SPACE.equals(label)) {
            ic.commitText(" ", 1);
            return;
        }

        String out = (shifted && !symbols) ? label.toUpperCase() : label;
        ic.commitText(out, out.length());
        if (shifted) {
            shifted = false;
            renderKeys();
        }
    }

    // ---------------------------------------------------------------- utils

    private GradientDrawable pill(String color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.parseColor(color));
        d.setCornerRadius(dp(10));
        return d;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
