package com.lvx.rpkeyboard.screen;

import com.lvx.rpkeyboard.ai.ChatMessage;

import java.util.List;

/**
 * Machine a etats du mode automatique <b>annonce</b>.
 *
 * Regle fondatrice : la personne en face sait qu'elle ecrit a un assistant. Le
 * mode ne peut donc pas demarrer tant que le message d'annonce n'est pas
 * reellement parti ({@link #confirmDisclosureSent()}) ; si l'annonce echoue, on
 * retombe a l'arret et rien n'est envoye automatiquement.
 *
 * Garde-fous, tous actifs en meme temps :
 * <ul>
 *   <li>annonce obligatoire avant le premier message automatique ;</li>
 *   <li>nouvelle annonce si la conversation change ;</li>
 *   <li>plafond de messages ;</li>
 *   <li>expiration dans le temps ;</li>
 *   <li>arret immediat si l'utilisateur tape au clavier ;</li>
 *   <li>arret immediat sur secousse franche du telephone ;</li>
 *   <li>texte uniquement : jamais de vocal, qui contredirait l'annonce.</li>
 * </ul>
 */
public final class AutoReplyController {

    public enum State {
        /** Inactif. */
        ARRET,
        /** L'utilisateur a demande l'activation, l'annonce reste a envoyer. */
        ANNONCE_REQUISE,
        /** Annonce partie : les reponses automatiques sont autorisees. */
        ACTIF
    }

    public enum StopReason {
        UTILISATEUR, SECOUSSE, PLAFOND_MESSAGES, EXPIRATION,
        CHANGEMENT_DE_FIL, ECHEC_ANNONCE
    }

    /** Marqueur ajoute a chaque reponse automatique. */
    public static final String MARKER = "🤖";

    public static final String DEFAULT_DISCLOSURE =
            "🤖 Message automatique : a partir de maintenant, c'est un "
                    + "assistant IA qui repond a ma place dans cette conversation. "
                    + "Dis-le moi si tu preferes attendre que je revienne.";

    /** Source de temps, remplacable pour les tests. */
    public interface Clock {
        long now();
    }

    private final Clock clock;

    private State state = State.ARRET;
    private String threadKey;
    private int sentCount;
    private StopReason lastStopReason;
    private long startedAt;
    private String lastHandledFingerprint;

    /** Plafond de messages envoyes automatiquement avant extinction. */
    public int maxMessages = 10;

    /** Duree de vie du mode, quoi qu'il arrive. */
    public long timeoutMs = 15 * 60 * 1000L;

    public AutoReplyController() {
        this(new Clock() {
            @Override
            public long now() {
                return System.currentTimeMillis();
            }
        });
    }

    public AutoReplyController(Clock clock) {
        this.clock = clock;
    }

    // --------------------------------------------------------------- lecture

    public State state() {
        return state;
    }

    public String threadKey() {
        return threadKey;
    }

    public int sentCount() {
        return sentCount;
    }

    public StopReason lastStopReason() {
        return lastStopReason;
    }

    public boolean isActive() {
        return state == State.ACTIF;
    }

    // ----------------------------------------------------------- transitions

    /**
     * L'utilisateur demande l'activation. Le mode n'est pas encore actif : il
     * faut d'abord que l'annonce parte.
     */
    public void request(String threadKey, List<ChatMessage> lastKnown) {
        this.threadKey = threadKey;
        this.state = State.ANNONCE_REQUISE;
        this.sentCount = 0;
        this.startedAt = clock.now();
        this.lastStopReason = null;
        // Tout ce qui precede l'activation est considere comme deja traite,
        // pour ne pas repondre retroactivement a un vieux message.
        this.lastHandledFingerprint = null;
        if (lastKnown != null) {
            for (int i = lastKnown.size() - 1; i >= 0; i--) {
                if (lastKnown.get(i).sender == ChatMessage.Sender.OTHER) {
                    this.lastHandledFingerprint = lastKnown.get(i).fingerprint;
                    break;
                }
            }
        }
    }

    /** L'annonce est bien partie : le mode devient actif. */
    public void confirmDisclosureSent() {
        if (state == State.ANNONCE_REQUISE) {
            state = State.ACTIF;
            startedAt = clock.now();
        }
    }

    /** L'annonce n'a pas pu etre envoyee : on n'active rien. */
    public void failDisclosure() {
        stop(StopReason.ECHEC_ANNONCE);
    }

    public void stop(StopReason reason) {
        if (state != State.ARRET) {
            lastStopReason = reason;
        }
        state = State.ARRET;
        threadKey = null;
        sentCount = 0;
        lastHandledFingerprint = null;
    }

    /** Toute frappe manuelle reprend la main. */
    public void onUserTyped() {
        if (state != State.ARRET) {
            stop(StopReason.UTILISATEUR);
        }
    }

    /** Secousse franche : coupe-circuit. */
    public void onShake() {
        if (state != State.ARRET) {
            stop(StopReason.SECOUSSE);
        }
    }

    // -------------------------------------------------------------- decision

    /**
     * Decide s'il faut repondre automatiquement a l'etat actuel du fil.
     *
     * @return le dernier message recu auquel repondre, ou null s'il ne faut
     *         rien faire
     */
    public ChatMessage shouldReplyTo(String currentThreadKey, List<ChatMessage> history) {
        if (state != State.ACTIF) {
            return null;
        }
        if (threadKey == null || !threadKey.equals(currentThreadKey)) {
            // Changement d'interlocuteur : on coupe. Une nouvelle annonce sera
            // necessaire pour l'autre conversation.
            stop(StopReason.CHANGEMENT_DE_FIL);
            return null;
        }
        if (clock.now() - startedAt > timeoutMs) {
            stop(StopReason.EXPIRATION);
            return null;
        }
        if (sentCount >= maxMessages) {
            stop(StopReason.PLAFOND_MESSAGES);
            return null;
        }
        if (history == null || history.isEmpty()) {
            return null;
        }

        ChatMessage last = history.get(history.size() - 1);
        if (last.sender != ChatMessage.Sender.OTHER) {
            return null; // c'est a moi de parler
        }
        if (last.fingerprint.equals(lastHandledFingerprint)) {
            return null; // deja traite
        }
        return last;
    }

    /** A appeler une fois la reponse automatique reellement envoyee. */
    public void markReplied(ChatMessage to) {
        lastHandledFingerprint = to.fingerprint;
        sentCount++;
        if (sentCount >= maxMessages) {
            stop(StopReason.PLAFOND_MESSAGES);
        }
    }

    // ---------------------------------------------------------------- textes

    public String disclosureText(String custom) {
        return (custom == null || custom.trim().isEmpty())
                ? DEFAULT_DISCLOSURE
                : custom.trim();
    }

    /** Le marqueur rend chaque reponse automatique reconnaissable. */
    public String decorate(String reply) {
        return MARKER + " " + reply;
    }

    public String stopReasonLabel() {
        if (lastStopReason == null) {
            return "";
        }
        switch (lastStopReason) {
            case UTILISATEUR:
                return "Mode auto coupe : tu as repris la main.";
            case SECOUSSE:
                return "Mode auto coupe (secousse).";
            case PLAFOND_MESSAGES:
                return "Mode auto termine : plafond de messages atteint.";
            case EXPIRATION:
                return "Mode auto termine : delai ecoule.";
            case CHANGEMENT_DE_FIL:
                return "Mode auto coupe : conversation differente.";
            case ECHEC_ANNONCE:
                return "Mode auto non active : l'annonce n'a pas pu etre envoyee.";
            default:
                return "";
        }
    }
}
