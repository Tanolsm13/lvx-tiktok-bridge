package com.lvx.rpkeyboard;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.inputmethodservice.InputMethodService;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.lvx.rpkeyboard.ai.ChatMessage;
import com.lvx.rpkeyboard.ai.ConversationLog;
import com.lvx.rpkeyboard.data.Settings;
import com.lvx.rpkeyboard.ime.Keys;
import com.lvx.rpkeyboard.imagegen.ImageClient;
import com.lvx.rpkeyboard.voice.ElevenLabsClient;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Clavier AZERTY : lecture d'ecran a la demande, consigne, vocal, image. */
public class RpKeyboardService extends InputMethodService implements Bus.Listener {

    private static final String BG = "#14121A";
    private static final String KEY_BG = "#322C45";
    private static final String KEY_BG_DOWN = "#473E63";
    private static final String KEY_BG_SPECIAL = "#221E2C";
    private static final String ACCENT = "#6C4BD6";
    private static final String CHIP_BG = "#2C2740";
    private static final String MUTED = "#9A93AE";
    private static final String DANGER = "#E2777A";
    private static final String GOLD = "#E8C86A";

    private static final long LONG_PRESS_MS = 320;

    /** Conservees pour survivre a la recreation de la vue apres la capture. */
    private static List<String> pending = new ArrayList<String>();

    private final Handler ui = new Handler(Looper.getMainLooper());

    private LinearLayout root;
    private LinearLayout keyRows;
    private HorizontalScrollView chipScroll;
    private LinearLayout chipRow;
    private TextView status;
    private PopupWindow preview;
    private PopupWindow accentPopup;
    private boolean shifted = false;
    private boolean symbols = false;
    private boolean animate = true;

    @Override
    public void onCreate() {
        super.onCreate();
        Bus.setListener(this);
    }

    @Override
    public void onDestroy() {
        dismissPopups();
        Bus.setListener(null);
        super.onDestroy();
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        dismissPopups();
        super.onFinishInputView(finishingInput);
    }

    @Override
    public View onCreateInputView() {
        animate = Settings.get(this).animations();

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        applyBackground(root);
        int pad = dp(6);
        root.setPadding(pad, pad, pad, pad);

        root.addView(buildToolbar());
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

    /** Fond uni, ou image choisie dans les reglages sous un voile sombre. */
    private void applyBackground(View target) {
        Settings s = Settings.get(this);
        String path = s.background();
        if (!path.isEmpty()) {
            try {
                File f = new File(path);
                if (f.exists()) {
                    Bitmap bmp = BitmapFactory.decodeFile(path);
                    if (bmp != null) {
                        BitmapDrawable image = new BitmapDrawable(getResources(), bmp);
                        image.setGravity(Gravity.FILL);
                        int alpha = Math.round(255f * s.scrim() / 100f);
                        ColorDrawable scrim =
                                new ColorDrawable(Color.argb(alpha, 0x14, 0x12, 0x1A));
                        target.setBackground(new LayerDrawable(
                                new Drawable[]{image, scrim}));
                        return;
                    }
                }
            } catch (Exception ignored) {
                // image illisible : on retombe sur le fond uni
            }
        }
        target.setBackgroundColor(Color.parseColor(BG));
    }

    // -------------------------------------------------------------- toolbar

    private View buildToolbar() {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(2), dp(2), dp(2), dp(6));

        bar.addView(toolButton("✨  Lire l'écran", ACCENT, Color.WHITE,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        requestCapture("");
                    }
                }));

        bar.addView(toolButton("⚡", KEY_BG_SPECIAL, Color.parseColor(GOLD),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        useFieldAsInstruction();
                    }
                }));

        bar.addView(toolButton("🎤", KEY_BG_SPECIAL, Color.parseColor("#8FD0E8"),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        makeVoice();
                    }
                }));

        bar.addView(toolButton("🌄", KEY_BG_SPECIAL, Color.parseColor("#A6E0B0"),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        makeImage();
                    }
                }));

        status = new TextView(this);
        status.setTextColor(Color.parseColor(MUTED));
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        status.setPadding(dp(8), 0, dp(8), 0);
        status.setMaxLines(2);
        status.setMinWidth(dp(90));
        status.setEllipsize(TextUtils.TruncateAt.END);
        bar.addView(status);

        bar.addView(toolButton("⚙", KEY_BG_SPECIAL, Color.parseColor(MUTED),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent i = new Intent(RpKeyboardService.this, SettingsActivity.class);
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(i);
                    }
                }));

        scroll.addView(bar);
        return scroll;
    }

    private Button toolButton(String label, String bg, int fg, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(fg);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        b.setBackground(pill(bg));
        b.setElevation(dp(3));
        b.setPadding(dp(12), dp(8), dp(12), dp(8));
        b.setOnClickListener(l);
        attachPressFeedback(b, bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(5);
        b.setLayoutParams(lp);
        return b;
    }

    private View buildChipRow() {
        chipScroll = new HorizontalScrollView(this);
        chipScroll.setVisibility(View.GONE);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipScroll.setPadding(dp(2), 0, dp(2), dp(6));

        chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        chipScroll.addView(chipRow);
        return chipScroll;
    }

    // -------------------------------------------------------------- actions

    private String threadKey() {
        EditorInfo info = getCurrentInputEditorInfo();
        return (info == null || info.packageName == null) ? "default" : info.packageName;
    }

    private String fieldText() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            return "";
        }
        ExtractedText et = ic.getExtractedText(new ExtractedTextRequest(), 0);
        return (et == null || et.text == null) ? "" : et.text.toString().trim();
    }

    private void clearField(int length) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.deleteSurroundingText(length + 100, length + 100);
        }
    }

    /** Le texte deja tape devient la consigne, et le champ est vide. */
    private void useFieldAsInstruction() {
        String typed = fieldText();
        if (typed.isEmpty()) {
            onError("Tape d'abord ta consigne dans le champ");
            return;
        }
        clearField(typed.length());
        requestCapture(typed);
    }

    private void requestCapture(String oneShot) {
        pending.clear();
        hideChips();

        if (Settings.get(this).apiKey().isEmpty()) {
            onError("Aucune clé API : ouvre les réglages");
            return;
        }

        Intent base = new Intent(this, CaptureService.class)
                .putExtra(CaptureService.EXTRA_ONE_SHOT, oneShot)
                .putExtra(CaptureService.EXTRA_THREAD, threadKey());

        if (CaptureService.isReady()) {
            onStatus(oneShot.isEmpty() ? "Lecture de l'écran..." : "Consigne prise en compte...");
            startForegroundService(base.setAction(CaptureService.ACTION_CAPTURE));
        } else {
            onStatus("Autorisation de capture...");
            CaptureActivity.pendingOneShot = oneShot;
            CaptureActivity.pendingThread = threadKey();
            Intent i = new Intent(this, CaptureActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
        }
    }

    /** Synthetise le texte du champ et ouvre le partage du fichier audio. */
    private void makeVoice() {
        final Settings s = Settings.get(this);
        if (!s.voiceReady()) {
            onError("Clé et voix ElevenLabs à renseigner dans les réglages");
            return;
        }
        final String text = fieldText();
        if (text.isEmpty()) {
            onError("Écris ou choisis d'abord le texte à dire");
            return;
        }
        onStatus("Génération du vocal...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File mp3 = new ElevenLabsClient(s.elevenKey(), s.elevenVoice(),
                            s.elevenModel())
                            .synthesize(text, Share.outDir(RpKeyboardService.this));
                    Uri uri = Share.publishAudio(RpKeyboardService.this, mp3);
                    onStatus("Vocal prêt : choisis l'app");
                    Share.send(RpKeyboardService.this, uri, "audio/mpeg", "Envoyer le vocal");
                } catch (Exception e) {
                    onError(msg(e));
                }
            }
        }).start();
    }

    /** Le texte du champ sert de description d'image. */
    private void makeImage() {
        final Settings s = Settings.get(this);
        if (!s.imageReady()) {
            onError("Clé image à renseigner dans les réglages");
            return;
        }
        final String prompt = fieldText();
        if (prompt.isEmpty()) {
            onError("Décris l'image dans le champ, puis réappuie");
            return;
        }
        onStatus("Génération de l'image...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File png = new ImageClient(s.imageUrl(), s.imageKey(), s.imageModel())
                            .generate(prompt, "1024x1024", Share.outDir(RpKeyboardService.this));
                    Uri uri = Share.publishImage(RpKeyboardService.this, png);
                    onStatus("Image prête : choisis l'app");
                    Share.send(RpKeyboardService.this, uri, "image/png", "Envoyer l'image");
                } catch (Exception e) {
                    onError(msg(e));
                }
            }
        }).start();
    }

    private static String msg(Exception e) {
        return e.getMessage() == null ? e.toString() : e.getMessage();
    }

    // ------------------------------------------------------------ callbacks

    @Override
    public void onStatus(final String message) {
        ui.post(new Runnable() {
            @Override
            public void run() {
                if (status != null) {
                    status.setTextColor(Color.parseColor(MUTED));
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
                    status.setTextColor(Color.parseColor(DANGER));
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
        chipRow.addView(toolButton("✕", KEY_BG_SPECIAL, Color.parseColor(MUTED),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        pending.clear();
                        hideChips();
                    }
                }));

        chipScroll.setVisibility(View.VISIBLE);
        chipScroll.scrollTo(0, 0);

        if (animate) {
            chipScroll.setAlpha(0f);
            chipScroll.setTranslationY(dp(10));
            chipScroll.animate().alpha(1f).translationY(0f).setDuration(190).start();
        } else {
            chipScroll.setAlpha(1f);
            chipScroll.setTranslationY(0f);
        }
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
        b.setBackground(pill(CHIP_BG));
        b.setElevation(dp(3));
        b.setPadding(dp(12), dp(9), dp(12), dp(9));
        b.setMaxWidth(dp(260));
        b.setMaxLines(3);
        b.setEllipsize(TextUtils.TruncateAt.END);
        b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        attachPressFeedback(b, CHIP_BG);

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
                rememberSent(text);
                pending.clear();
                hideChips();
                onStatus("Relis, modifie, puis envoie");
            }
        });
        return b;
    }

    /** Le message choisi entre dans le journal : le contexte s'enrichit. */
    private void rememberSent(String text) {
        try {
            Settings s = Settings.get(this);
            String key = threadKey();
            ConversationLog log = s.loadLog(key);
            List<ChatMessage> one = new ArrayList<ChatMessage>();
            one.add(ChatMessage.mine(text));
            log.absorb(one);
            s.saveLog(key, log);
        } catch (Exception ignored) {
            // le journal est un confort, pas une condition de fonctionnement
        }
    }

    // -------------------------------------------------------------- touches

    private void renderKeys() {
        keyRows.removeAllViews();
        String[][] layer = symbols ? Keys.SYMBOLS : Keys.LETTERS;

        for (int r = 0; r < layer.length; r++) {
            LinearLayout row = newRow();
            for (int c = 0; c < layer[r].length; c++) {
                String label = layer[r][c];
                float weight = Keys.BACKSPACE.equals(label) || Keys.SHIFT.equals(label)
                        ? 1.5f : 1f;
                row.addView(key(label, weight, Keys.isSpecial(label)));
            }
            keyRows.addView(row);
        }

        LinearLayout bottom = newRow();
        bottom.addView(key(symbols ? Keys.LAYER_BACK : Keys.LAYER, 1.6f, true));
        bottom.addView(key(",", 1f, false));
        bottom.addView(key(Keys.SPACE, 4.4f, false));
        bottom.addView(key(".", 1f, false));
        bottom.addView(key(Keys.ENTER, 1.6f, true));
        keyRows.addView(bottom);

        if (animate) {
            keyRows.setAlpha(0.75f);
            keyRows.animate().alpha(1f).setDuration(120).start();
        }
    }

    private LinearLayout newRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(5);
        row.setLayoutParams(lp);
        return row;
    }

    private View key(final String label, float weight, boolean special) {
        final Button b = new Button(this);
        boolean upper = shifted && !symbols && label.length() == 1;
        b.setText(upper ? label.toUpperCase() : label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, Keys.SPACE.equals(label) ? 13 : 17);
        b.setPadding(0, dp(13), 0, dp(13));
        b.setElevation(dp(2));

        final String base = (Keys.SHIFT.equals(label) && shifted)
                ? ACCENT : (special ? KEY_BG_SPECIAL : KEY_BG);
        b.setBackground(pill(base));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, weight);
        lp.rightMargin = dp(5);
        b.setLayoutParams(lp);

        attachKeyTouch(b, label, base);
        return b;
    }

    /** Animation d'appui, apercu au-dessus de la touche, et accents en appui long. */
    private void attachKeyTouch(final Button b, final String label, final String base) {
        final String[] variants = Keys.accents(label);
        final Runnable longPress = new Runnable() {
            @Override
            public void run() {
                if (variants.length > 0) {
                    dismissPreview();
                    showAccents(b, variants);
                }
            }
        };

        b.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        b.setBackground(pill(KEY_BG_DOWN));
                        if (animate) {
                            b.animate().scaleX(0.94f).scaleY(0.94f).setDuration(55).start();
                        }
                        if (!Keys.isSpecial(label) && !Keys.SPACE.equals(label)) {
                            showPreview(b, b.getText().toString());
                        }
                        if (variants.length > 0) {
                            b.postDelayed(longPress, LONG_PRESS_MS);
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        b.removeCallbacks(longPress);
                        restoreKey(b, base);
                        dismissPreview();
                        if (accentPopup == null || !accentPopup.isShowing()) {
                            onKeyPress(label);
                        }
                        return true;

                    case MotionEvent.ACTION_CANCEL:
                        b.removeCallbacks(longPress);
                        restoreKey(b, base);
                        dismissPreview();
                        return true;

                    default:
                        return false;
                }
            }
        });
    }

    private void restoreKey(Button b, String base) {
        b.setBackground(pill(base));
        if (animate) {
            b.animate().scaleX(1f).scaleY(1f).setDuration(90).start();
        }
    }

    /** Petit aperçu flottant, comme sur Gboard. */
    private void showPreview(View anchor, String text) {
        dismissPreview();
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(16), dp(8), dp(16), dp(8));
        tv.setBackground(pill(ACCENT));
        tv.setElevation(dp(8));

        preview = new PopupWindow(tv,
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        preview.setTouchable(false);
        preview.setOutsideTouchable(true);
        showAbove(preview, anchor, dp(6));
    }

    private void dismissPreview() {
        if (preview != null) {
            try {
                preview.dismiss();
            } catch (Exception ignored) {
                // fenetre deja partie
            }
            preview = null;
        }
    }

    /** Rangee de variantes accentuees, choisies au doigt. */
    private void showAccents(View anchor, String[] variants) {
        dismissAccents();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setBackground(pill("#1E1A28"));
        box.setElevation(dp(10));
        box.setPadding(dp(6), dp(6), dp(6), dp(6));

        for (int i = 0; i < variants.length; i++) {
            final String v = shifted ? variants[i].toUpperCase() : variants[i];
            Button ab = new Button(this);
            ab.setText(v);
            ab.setAllCaps(false);
            ab.setTextColor(Color.WHITE);
            ab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            ab.setBackground(pill(KEY_BG));
            ab.setPadding(dp(14), dp(10), dp(14), dp(10));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(4);
            ab.setLayoutParams(lp);
            ab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    InputConnection ic = getCurrentInputConnection();
                    if (ic != null) {
                        ic.commitText(v, v.length());
                    }
                    dismissAccents();
                }
            });
            box.addView(ab);
        }

        accentPopup = new PopupWindow(box,
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        accentPopup.setTouchable(true);
        accentPopup.setOutsideTouchable(true);
        accentPopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        showAbove(accentPopup, anchor, dp(6));
    }

    private void dismissAccents() {
        if (accentPopup != null) {
            try {
                accentPopup.dismiss();
            } catch (Exception ignored) {
                // fenetre deja partie
            }
            accentPopup = null;
        }
    }

    private void dismissPopups() {
        dismissPreview();
        dismissAccents();
    }

    /** Place une fenetre flottante juste au-dessus d'une touche. */
    private void showAbove(PopupWindow window, View anchor, int gap) {
        try {
            int[] pos = new int[2];
            anchor.getLocationInWindow(pos);
            window.showAtLocation(anchor, Gravity.NO_GRAVITY, 0, 0);
            window.update(pos[0], pos[1] - anchor.getHeight() - gap,
                    -1, -1);
        } catch (Exception ignored) {
            // si l'ancrage echoue, on se passe de l'apercu
        }
    }

    private void onKeyPress(String label) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            return;
        }

        if (Keys.SHIFT.equals(label)) {
            shifted = !shifted;
            renderKeys();
            return;
        }
        if (Keys.LAYER.equals(label) || Keys.LAYER_BACK.equals(label)) {
            symbols = !symbols;
            renderKeys();
            return;
        }
        if (Keys.BACKSPACE.equals(label)) {
            if (TextUtils.isEmpty(ic.getSelectedText(0))) {
                ic.deleteSurroundingText(1, 0);
            } else {
                ic.commitText("", 1);
            }
            return;
        }
        if (Keys.ENTER.equals(label)) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
            return;
        }
        if (Keys.SPACE.equals(label)) {
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


    /** Retour tactile des boutons de barre et des chips : enfoncement bref. */
    private void attachPressFeedback(final View v, final String base) {
        v.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setBackground(pill(KEY_BG_DOWN));
                        if (animate) {
                            v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(55).start();
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                        v.setBackground(pill(base));
                        if (animate) {
                            v.animate().scaleX(1f).scaleY(1f).setDuration(90).start();
                        }
                        v.performClick();
                        break;
                    case MotionEvent.ACTION_CANCEL:
                        v.setBackground(pill(base));
                        if (animate) {
                            v.animate().scaleX(1f).scaleY(1f).setDuration(90).start();
                        }
                        break;
                    default:
                        return false;
                }
                return true;
            }
        });
    }

    // ---------------------------------------------------------------- utils

    private GradientDrawable pill(String color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.parseColor(color));
        d.setCornerRadius(dp(11));
        return d;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
