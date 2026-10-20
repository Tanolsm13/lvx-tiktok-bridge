package com.lvx.rpkeyboard.ai;

import java.util.List;

/**
 * Construit le prompt systeme et le message utilisateur.
 *
 * Le contrat de sortie est impose ici, en texte brut separe par
 * {@link Sanitizer#SEP} : c'est la premiere ligne de defense contre le defaut
 * de la v1. Le {@link Sanitizer} est la seconde, au cas ou le modele n'obeisse
 * pas.
 */
public final class PromptBuilder {

    /** Facon de piloter l'IA. */
    public enum Mode {
        /** L'IA lit le fil, deduit le ton et s'y accorde. */
        ADAPTATIF,
        /** Une seule consigne, ecrite par toi, appliquee telle quelle. */
        INSTRUCTION_UNIQUE
    }

    public static final class Config {
        public Mode mode = Mode.ADAPTATIF;
        /** Consigne unique, utilisee en mode INSTRUCTION_UNIQUE. */
        public String instruction = "";
        /** Consigne ponctuelle tapee dans le champ, prioritaire si presente. */
        public String oneShot = "";
        public String me = "";
        public String partner = "";
        public String universe = "";
        /** Profil de style, utilise quand le jeu de role est eteint. */
        public String styleProfile = "";
        public boolean roleplay = true;
        public int count = 3;
    }

    private PromptBuilder() {
    }

    public static String system(Config c) {
        StringBuilder sb = new StringBuilder();

        if (c.roleplay) {
            sb.append("Tu es l'assistant d'ecriture d'un joueur de jeu de role par messages.\n");
            sb.append("Tu rediges des messages qu'il pourrait envoyer, a la premiere personne.\n");
        } else {
            sb.append("Tu es l'assistant d'ecriture d'une personne dans une conversation ");
            sb.append("de messagerie ordinaire.\n");
            sb.append("Tu rediges des messages qu'elle pourrait envoyer, a la premiere personne.\n");
        }

        sb.append('\n');

        if (c.mode == Mode.INSTRUCTION_UNIQUE && !c.instruction.trim().isEmpty()) {
            sb.append("Consigne a appliquer telle quelle a chaque reponse :\n");
            sb.append(c.instruction.trim()).append('\n');
        } else {
            sb.append("Accorde-toi au fil : reprends son ton, son registre, sa longueur ");
            sb.append("de message et son rythme. Ne surjoue pas, ne rallonge pas sans raison.\n");
        }

        if (c.roleplay) {
            if (!c.me.trim().isEmpty()) {
                sb.append("\nPersonnage joue par l'utilisateur : ").append(c.me.trim());
            }
            if (!c.partner.trim().isEmpty()) {
                sb.append("\nPersonnage de l'interlocutrice : ").append(c.partner.trim());
            }
            if (!c.universe.trim().isEmpty()) {
                sb.append("\nUnivers et ton : ").append(c.universe.trim());
            }
        } else if (!c.styleProfile.trim().isEmpty()) {
            sb.append("\nEcris comme l'utilisateur ecrit d'habitude : ")
                    .append(c.styleProfile.trim());
        }

        int n = Math.max(1, c.count);
        sb.append("\n\nContrat de sortie, a respecter strictement :\n");
        sb.append("- Produis exactement ").append(n).append(" propositions differentes.\n");
        sb.append("- Separe chaque proposition par une ligne contenant exactement ")
                .append(Sanitizer.SEP).append("\n");
        sb.append("- Chaque proposition est le texte du message, rien d'autre.\n");
        sb.append("- Pas de JSON, pas de titre, pas de numerotation, pas de ");
        sb.append("guillemets autour, pas de commentaire, pas d'explication.\n");
        sb.append("- Varie : une courte, une plus developpee, une qui relance.\n");

        sb.append("\nFormat attendu, exactement :\n");
        for (int i = 1; i <= n; i++) {
            sb.append("texte de la proposition ").append(i).append('\n');
            if (i < n) {
                sb.append(Sanitizer.SEP).append('\n');
            }
        }
        return sb.toString();
    }

    /** Message utilisateur : le contexte lu a l'ecran, puis la demande. */
    public static String user(Config c, ContextCompactor.Result ctx) {
        StringBuilder sb = new StringBuilder();

        if (ctx != null && ctx.hasSummary()) {
            sb.append(ctx.summary).append("\n\n");
        }

        if (ctx != null && !ctx.kept.isEmpty()) {
            sb.append("Fil de la conversation, du plus ancien au plus recent :\n");
            for (ChatMessage m : ctx.kept) {
                sb.append(m.sender == ChatMessage.Sender.ME ? "moi : " : "elle : ")
                        .append(m.text.trim().replace("\n", " "))
                        .append('\n');
            }
            sb.append('\n');
        }

        ChatMessage target = lastFromOther(ctx);
        if (target != null) {
            sb.append("Dernier message recu, celui auquel repondre :\n")
                    .append(target.text.trim()).append("\n\n");
        }

        if (!c.oneShot.trim().isEmpty()) {
            sb.append("Consigne ponctuelle, prioritaire sur le reste :\n")
                    .append(c.oneShot.trim()).append("\n\n");
        }

        sb.append("Propose ").append(Math.max(1, c.count))
                .append(" reponses, separees par ").append(Sanitizer.SEP).append('.');
        return sb.toString();
    }

    private static ChatMessage lastFromOther(ContextCompactor.Result ctx) {
        if (ctx == null) {
            return null;
        }
        List<ChatMessage> kept = ctx.kept;
        for (int i = kept.size() - 1; i >= 0; i--) {
            if (kept.get(i).sender == ChatMessage.Sender.OTHER) {
                return kept.get(i);
            }
        }
        return null;
    }

    /** Demande sans historique : l'ecran est joint en image. */
    public static String userVision(Config c) {
        StringBuilder sb = new StringBuilder();
        sb.append("Voici la capture de ma conversation. Repere le dernier message ");
        sb.append("recu, celui de l'interlocutrice, et propose ");
        sb.append(Math.max(1, c.count)).append(" reponses.\n");
        if (!c.oneShot.trim().isEmpty()) {
            sb.append("\nConsigne ponctuelle, prioritaire :\n")
                    .append(c.oneShot.trim()).append('\n');
        }
        sb.append("\nSepare les propositions par ").append(Sanitizer.SEP).append('.');
        return sb.toString();
    }
}
