package com.lvx.rpkeyboard.ime;

/** Dispositions du clavier et variantes accentuees. */
public final class Keys {

    public static final String SHIFT = "⇧";
    public static final String BACKSPACE = "⌫";
    public static final String LAYER = "?123";
    public static final String LAYER_BACK = "ABC";
    public static final String ENTER = "⏎";
    public static final String SPACE = "espace";

    public static final String[][] LETTERS = {
            {"a", "z", "e", "r", "t", "y", "u", "i", "o", "p"},
            {"q", "s", "d", "f", "g", "h", "j", "k", "l", "m"},
            {SHIFT, "w", "x", "c", "v", "b", "n", "'", BACKSPACE},
    };

    public static final String[][] SYMBOLS = {
            {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"},
            {"&", "#", "@", "(", ")", "-", "+", "=", "*", "/"},
            {SHIFT, "\"", "%", ":", ";", "!", "?", ",", BACKSPACE},
    };

    private Keys() {
    }

    public static boolean isSpecial(String label) {
        return SHIFT.equals(label) || BACKSPACE.equals(label) || LAYER.equals(label)
                || LAYER_BACK.equals(label) || ENTER.equals(label);
    }

    /**
     * Variantes proposees en appui long, comme sur Gboard.
     *
     * @return tableau vide si la touche n'a pas de variante
     */
    public static String[] accents(String label) {
        if (label == null || label.length() != 1) {
            return new String[0];
        }
        switch (label.charAt(0)) {
            case 'a': return new String[]{"à", "â", "ä", "á", "æ"};
            case 'e': return new String[]{"é", "è", "ê", "ë"};
            case 'i': return new String[]{"î", "ï", "í"};
            case 'o': return new String[]{"ô", "ö", "ó", "œ"};
            case 'u': return new String[]{"ù", "û", "ü", "ú"};
            case 'c': return new String[]{"ç"};
            case 'y': return new String[]{"ÿ"};
            case 'n': return new String[]{"ñ"};
            case '\'': return new String[]{"’", "«", "»"};
            case '-': return new String[]{"—", "_"};
            case '.': return new String[]{"…", "·"};
            case '!': return new String[]{"¡"};
            case '?': return new String[]{"¿"};
            default: return new String[0];
        }
    }
}
