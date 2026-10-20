package com.lvx.rpkeyboard.ai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Transforme la reponse brute du fournisseur en texte propre, pret a etre
 * insere dans le champ de saisie.
 *
 * Regle fondatrice : <b>aucun repli sur le corps brut</b>. C'etait la cause du
 * defaut de la v1 — quand l'extraction echouait, le JSON finissait colle dans
 * le message. Ici, soit on produit du texte propre, soit on leve {@link BadReply},
 * affiche dans la barre d'etat du clavier et jamais dans le champ.
 */
public final class Sanitizer {

    /**
     * Separateur impose au modele par le prompt : pas de JSON en sortie.
     *
     * Volontairement hors syntaxe markdown. Un {@code ###} serait un titre de
     * niveau 3 : le modele ecrit alors « ### Reponse 1 » au lieu du separateur
     * seul sur sa ligne, et les propositions arrivent collees en une seule.
     */
    public static final String SEP = "<|sep|>";

    public enum Kind {
        VIDE("reponse vide du modele"),
        ENCORE_JSON("le modele a renvoye du JSON au lieu de texte"),
        STRUCTURE("reponse de forme inattendue"),
        ERREUR_API("erreur renvoyee par l'API");

        public final String label;

        Kind(String label) {
            this.label = label;
        }
    }

    /** Echec typé : on sait quoi afficher, et on n'insere rien. */
    public static final class BadReply extends Exception {
        public final Kind kind;

        public BadReply(Kind kind, String detail) {
            super(detail == null || detail.isEmpty() ? kind.label : kind.label + " (" + detail + ")");
            this.kind = kind;
        }
    }

    /** Forme de la reponse selon le fournisseur. */
    public enum Shape {
        /** OpenAI et compatibles : choices[0].message.content */
        OPENAI,
        /** Anthropic : content[0].text */
        ANTHROPIC
    }

    private static final String[] TEXT_KEYS = {
            "reply", "text", "content", "message", "output", "response", "answer"
    };

    private static final String[] ROLE_PREFIXES = {
            "assistant:", "assistant :", "ia:", "ia :", "reponse:", "reponse :",
            "réponse:", "réponse :", "moi:", "moi :"
    };

    private Sanitizer() {
    }

    // ------------------------------------------------------------------ API

    /**
     * Chaine complete : corps HTTP brut vers la liste des propositions.
     *
     * @param body  corps de la reponse, eventuellement un flux SSE
     * @param shape forme attendue selon le fournisseur
     */
    public static List<String> variants(String body, Shape shape) throws BadReply {
        String content = extractContent(body, shape);
        String clean = clean(content);

        List<String> out = split(clean);
        if (out.isEmpty()) {
            throw new BadReply(Kind.VIDE, null);
        }
        return out;
    }

    /**
     * Decoupe en propositions, en essayant plusieurs conventions.
     *
     * Les modeles n'obeissent pas toujours au separateur demande : certains le
     * remplacent par un titre markdown, une regle horizontale ou une liste
     * numerotee. On essaie dans cet ordre et on s'arrete des qu'un decoupage
     * donne au moins deux morceaux.
     */
    static List<String> split(String text) {
        List<String> out = bySeparator(text, java.util.regex.Pattern.quote(SEP));
        if (out.size() >= 2) {
            return out;
        }
        // separateur seul sur sa ligne : ###, ---, ===, ***
        out = bySeparator(text, "(?m)^[ \\t]*(?:#{3,}|-{3,}|={3,}|\\*{3,})[ \\t]*$");
        if (out.size() >= 2) {
            return out;
        }
        out = byHeadings(text);
        if (out.size() >= 2) {
            return out;
        }
        out = byNumberedList(text);
        if (out.size() >= 2) {
            return out;
        }
        return cleanAll(new String[]{text});
    }

    private static List<String> bySeparator(String text, String regex) {
        return cleanAll(text.split(regex));
    }

    /**
     * Titres markdown ou libelles en gras utilises comme separateurs :
     * « ### Reponse 1 », « **Proposition 2** ». La ligne de titre est jetee.
     */
    private static List<String> byHeadings(String text) {
        String[] lines = text.split("\n");
        List<String> pieces = new ArrayList<String>();
        StringBuilder current = new StringBuilder();
        int headings = 0;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String t = line.trim();
            boolean isHeading = t.matches("#{1,6}\\s+\\S.*")
                    || t.matches("\\*\\*[^*]{1,40}\\*\\*:?");
            if (isHeading) {
                headings++;
                if (current.length() > 0) {
                    pieces.add(current.toString());
                    current.setLength(0);
                }
            } else {
                if (current.length() > 0) {
                    current.append('\n');
                }
                current.append(line);
            }
        }
        if (current.length() > 0) {
            pieces.add(current.toString());
        }
        // Un titre en tete a deja ete retire par clean(), donc un seul titre
        // restant separe bien deux propositions. C'est split() qui tranche, en
        // n'acceptant ce decoupage que s'il donne au moins deux morceaux.
        if (headings < 1) {
            return new ArrayList<String>();
        }
        return cleanAll(pieces.toArray(new String[0]));
    }

    /** Liste numerotee : « 1. ... » / « 2) ... » en debut de ligne. */
    private static List<String> byNumberedList(String text) {
        if (!text.trim().matches("(?s)^\\s*1[.)]\\s+\\S.*")) {
            return new ArrayList<String>();
        }
        return cleanAll(text.split("(?m)^[ \\t]*\\d{1,2}[.)]\\s+"));
    }

    private static List<String> cleanAll(String[] parts) {
        List<String> out = new ArrayList<String>();
        for (int i = 0; i < parts.length; i++) {
            try {
                String piece = clean(parts[i]);
                if (!piece.isEmpty()) {
                    out.add(piece);
                }
            } catch (BadReply ignored) {
                // un morceau illisible ne doit pas condamner les autres
            }
        }
        return out;
    }

    // ----------------------------------------------------------- extraction

    /** Extraction stricte du champ de contenu, SSE recolle au besoin. */
    static String extractContent(String body, Shape shape) throws BadReply {
        if (body == null || body.trim().isEmpty()) {
            throw new BadReply(Kind.VIDE, null);
        }
        String trimmed = body.trim();

        if (isSse(trimmed)) {
            return joinSse(trimmed, shape);
        }

        JSONObject root;
        try {
            root = new JSONObject(trimmed);
        } catch (Exception e) {
            throw new BadReply(Kind.STRUCTURE, "corps illisible");
        }

        // Une erreur d'API est explicite : on la remonte telle quelle.
        Object err = root.opt("error");
        if (err != null) {
            throw new BadReply(Kind.ERREUR_API, errorMessage(err));
        }

        if (shape == Shape.ANTHROPIC) {
            JSONArray blocks = root.optJSONArray("content");
            if (blocks == null) {
                throw new BadReply(Kind.STRUCTURE, "content absent");
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < blocks.length(); i++) {
                JSONObject b = blocks.optJSONObject(i);
                if (b != null && "text".equals(b.optString("type", "text"))) {
                    sb.append(b.optString("text", ""));
                }
            }
            return sb.toString();
        }

        JSONArray choices = root.optJSONArray("choices");
        if (choices == null || choices.length() == 0) {
            throw new BadReply(Kind.STRUCTURE, "choices absent");
        }
        JSONObject first = choices.optJSONObject(0);
        if (first == null) {
            throw new BadReply(Kind.STRUCTURE, "choice vide");
        }
        JSONObject message = first.optJSONObject("message");
        if (message == null) {
            // Certaines passerelles renvoient directement text.
            String direct = first.optString("text", "");
            if (!direct.isEmpty()) {
                return direct;
            }
            throw new BadReply(Kind.STRUCTURE, "message absent");
        }
        return contentToText(message.opt("content"));
    }

    /** Le contenu peut etre une chaine ou une liste de blocs. */
    private static String contentToText(Object content) throws BadReply {
        if (content == null) {
            throw new BadReply(Kind.VIDE, null);
        }
        if (content instanceof JSONArray) {
            StringBuilder sb = new StringBuilder();
            JSONArray parts = (JSONArray) content;
            for (int i = 0; i < parts.length(); i++) {
                JSONObject part = parts.optJSONObject(i);
                if (part != null) {
                    sb.append(part.optString("text", ""));
                }
            }
            return sb.toString();
        }
        return content.toString();
    }

    private static String errorMessage(Object err) {
        if (err instanceof JSONObject) {
            JSONObject o = (JSONObject) err;
            String m = o.optString("message", "");
            return m.isEmpty() ? o.toString() : m;
        }
        return String.valueOf(err);
    }

    // ------------------------------------------------------------------ SSE

    static boolean isSse(String s) {
        return s.startsWith("data:") || s.contains("\ndata:");
    }

    /** Recolle les fragments d'un flux qui aurait fuite jusqu'ici. */
    static String joinSse(String body, Shape shape) throws BadReply {
        StringBuilder sb = new StringBuilder();
        for (String line : body.split("\n")) {
            String l = line.trim();
            if (!l.startsWith("data:")) {
                continue;
            }
            String payload = l.substring(5).trim();
            if (payload.isEmpty() || "[DONE]".equals(payload)) {
                continue;
            }
            try {
                JSONObject chunk = new JSONObject(payload);
                if (shape == Shape.ANTHROPIC) {
                    JSONObject delta = chunk.optJSONObject("delta");
                    if (delta != null) {
                        sb.append(delta.optString("text", ""));
                    }
                } else {
                    JSONArray choices = chunk.optJSONArray("choices");
                    if (choices != null && choices.length() > 0) {
                        JSONObject c = choices.optJSONObject(0);
                        if (c != null) {
                            JSONObject delta = c.optJSONObject("delta");
                            if (delta != null) {
                                sb.append(delta.optString("content", ""));
                            } else {
                                JSONObject msg = c.optJSONObject("message");
                                if (msg != null) {
                                    sb.append(msg.optString("content", ""));
                                }
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
                // fragment illisible : on l'ignore plutot que de tout perdre
            }
        }
        if (sb.length() == 0) {
            throw new BadReply(Kind.STRUCTURE, "flux vide");
        }
        return sb.toString();
    }

    // ------------------------------------------------------------ deballage

    /**
     * Deballe jusqu'a point fixe, puis valide. Leve si le resultat ressemble
     * encore a du JSON : on prefere une erreur visible a du bruit dans le champ.
     */
    public static String clean(String input) throws BadReply {
        String s = input == null ? "" : input;
        for (int i = 0; i < 8; i++) {
            String before = s;
            s = stripThink(s);
            s = stripFence(s);
            s = stripLeadingHeading(s);
            s = stripRolePrefix(s);
            s = unwrapJson(s);
            s = unwrapEmbeddedJson(s);
            s = unescape(s);
            s = stripQuotes(s);
            s = s.trim();
            if (s.equals(before)) {
                break;
            }
        }
        if (looksLikeJson(s)) {
            throw new BadReply(Kind.ENCORE_JSON, null);
        }
        return s.trim();
    }


    /**
     * Extrait un objet JSON <b>noye dans de la prose</b>.
     *
     * {@link #unwrapJson} ne traite que les chaines entierement JSON. Un modele
     * bavard peut repondre « Voici mes propositions : {"replies":[...]} », et la
     * chaine complete n'etant pas du JSON, le garde-fou final ne la refuse pas :
     * le bloc finissait dans le message.
     *
     * On ne touche qu'a un bloc equilibre qui s'analyse <b>et</b> porte une des
     * cles connues. Un texte legitime contenant {@code {ACCES REFUSE}} n'a
     * aucune de ces cles et n'est pas du JSON valide : il passe intact.
     */
    static String unwrapEmbeddedJson(String s) {
        int open = s.indexOf('{');
        if (open < 0) {
            return s;
        }
        for (int start = open; start >= 0; start = s.indexOf('{', start + 1)) {
            int end = matchingBrace(s, start);
            if (end < 0) {
                continue;
            }
            String candidate = s.substring(start, end + 1);
            JSONObject o = asObject(candidate);
            if (o == null) {
                continue;
            }
            for (String key : new String[]{"replies", "variants", "propositions"}) {
                JSONArray arr = o.optJSONArray(key);
                if (arr != null) {
                    return joinArray(arr);
                }
            }
            for (String key : TEXT_KEYS) {
                Object v = o.opt(key);
                if (v instanceof String && !((String) v).trim().isEmpty()) {
                    return (String) v;
                }
            }
        }
        return s;
    }

    /** Position de l'accolade fermante correspondante, en ignorant les chaines. */
    private static int matchingBrace(String s, int start) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    static String stripThink(String s) {
        String out = s;
        for (String tag : new String[]{"think", "thinking", "reasoning"}) {
            int open = indexOfIgnoreCase(out, "<" + tag + ">");
            int close = indexOfIgnoreCase(out, "</" + tag + ">");
            if (open >= 0 && close > open) {
                out = out.substring(0, open) + out.substring(close + tag.length() + 3);
            }
        }
        return out.trim();
    }

    /** Retire un bloc ```...``` enveloppant, pas un bloc de code interne. */
    static String stripFence(String s) {
        String t = s.trim();
        if (!t.startsWith("```")) {
            return s;
        }
        int firstBreak = t.indexOf('\n');
        if (firstBreak < 0) {
            return s;
        }
        int end = t.lastIndexOf("```");
        if (end <= firstBreak) {
            return s;
        }
        return t.substring(firstBreak + 1, end).trim();
    }


    /**
     * Retire un titre markdown en tete, du bavardage de modele du type
     * « ### Contexte » suivi du vrai message.
     *
     * On n'y touche que s'il reste du contenu apres : une reponse d'une seule
     * ligne entierement en gras est un message legitime, pas un titre.
     */
    static String stripLeadingHeading(String s) {
        String t = s.trim();
        int nl = t.indexOf('\n');
        if (nl < 0) {
            return s;
        }
        String first = t.substring(0, nl).trim();
        String rest = t.substring(nl + 1).trim();
        if (rest.isEmpty()) {
            return s;
        }
        boolean heading = first.matches("#{1,6}\\s+\\S.*")
                || first.matches("\\*\\*[^*]{1,40}\\*\\*:?");
        return heading ? rest : s;
    }

    static String stripRolePrefix(String s) {
        String t = s.trim();
        String lower = t.toLowerCase();
        for (String p : ROLE_PREFIXES) {
            if (lower.startsWith(p)) {
                return t.substring(p.length()).trim();
            }
        }
        return s;
    }

    /**
     * Si la chaine entiere est un objet ou un tableau JSON, on en extrait le
     * texte. Une chaine qui contient seulement des accolades n'est pas touchee.
     */
    static String unwrapJson(String s) {
        String t = s.trim();
        if (t.length() < 2) {
            return s;
        }
        char c = t.charAt(0);
        if (c != '{' && c != '[') {
            return s;
        }

        if (c == '{') {
            JSONObject o = asObject(t);
            if (o == null) {
                return s;
            }
            // liste de variantes : on les recolle avec le separateur
            for (String key : new String[]{"replies", "variants", "propositions", "choices"}) {
                JSONArray arr = o.optJSONArray(key);
                if (arr != null) {
                    return joinArray(arr);
                }
            }
            for (String key : TEXT_KEYS) {
                Object v = o.opt(key);
                if (v instanceof String && !((String) v).trim().isEmpty()) {
                    return (String) v;
                }
            }
            return s;
        }

        JSONArray arr = asArray(t);
        return arr == null ? s : joinArray(arr);
    }

    private static String joinArray(JSONArray arr) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length(); i++) {
            String piece;
            Object v = arr.opt(i);
            if (v instanceof String) {
                piece = (String) v;
            } else if (v instanceof JSONObject) {
                piece = firstTextField((JSONObject) v);
            } else {
                piece = v == null ? "" : String.valueOf(v);
            }
            if (piece != null && !piece.trim().isEmpty()) {
                if (sb.length() > 0) {
                    sb.append("\n").append(SEP).append("\n");
                }
                sb.append(piece.trim());
            }
        }
        return sb.toString();
    }

    private static String firstTextField(JSONObject o) {
        for (String key : TEXT_KEYS) {
            Object v = o.opt(key);
            if (v instanceof String && !((String) v).trim().isEmpty()) {
                return (String) v;
            }
        }
        return "";
    }

    /** Dé-échappe seulement si la chaine est visiblement echappee. */
    static String unescape(String s) {
        boolean escaped = (s.contains("\\n") || s.contains("\\\"") || s.contains("\\u"))
                && !s.contains("\n");
        if (!escaped) {
            return s;
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch != '\\' || i + 1 >= s.length()) {
                sb.append(ch);
                continue;
            }
            char next = s.charAt(++i);
            switch (next) {
                case 'n': sb.append('\n'); break;
                case 't': sb.append('\t'); break;
                case 'r': break;
                case '"': sb.append('"'); break;
                case '\\': sb.append('\\'); break;
                case 'u':
                    if (i + 4 < s.length()) {
                        try {
                            sb.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16));
                            i += 4;
                        } catch (Exception e) {
                            sb.append("\\u");
                        }
                    } else {
                        sb.append("\\u");
                    }
                    break;
                default:
                    sb.append('\\').append(next);
            }
        }
        return sb.toString();
    }

    /**
     * Retire des guillemets droits enveloppants. On ne touche pas si le texte
     * en contient d'autres a l'interieur : ce serait du dialogue legitime.
     */
    static String stripQuotes(String s) {
        String t = s.trim();
        if (t.length() < 2) {
            return s;
        }
        char a = t.charAt(0);
        char b = t.charAt(t.length() - 1);
        if (a != '"' || b != '"') {
            return s;
        }
        String inner = t.substring(1, t.length() - 1);
        return inner.indexOf('"') >= 0 ? s : inner;
    }

    /** Vrai seulement si la chaine ENTIERE est un objet ou tableau JSON. */
    static boolean looksLikeJson(String s) {
        String t = s.trim();
        if (t.length() < 2) {
            return false;
        }
        char c = t.charAt(0);
        if (c == '{' && t.endsWith("}")) {
            return asObject(t) != null;
        }
        if (c == '[' && t.endsWith("]")) {
            return asArray(t) != null;
        }
        return false;
    }

    private static JSONObject asObject(String s) {
        try {
            return new JSONObject(s);
        } catch (Exception e) {
            return null;
        }
    }

    private static JSONArray asArray(String s) {
        try {
            return new JSONArray(s);
        } catch (Exception e) {
            return null;
        }
    }

    private static int indexOfIgnoreCase(String haystack, String needle) {
        return haystack.toLowerCase().indexOf(needle.toLowerCase());
    }
}
