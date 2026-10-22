import com.lvx.rpkeyboard.ai.AiEngine;
import com.lvx.rpkeyboard.ai.ChatMessage;
import com.lvx.rpkeyboard.ai.Completer;
import com.lvx.rpkeyboard.ai.LlmClient;
import com.lvx.rpkeyboard.ai.PromptBuilder;
import com.lvx.rpkeyboard.ai.Retry;
import com.lvx.rpkeyboard.ai.Sanitizer;
import com.lvx.rpkeyboard.ai.WaveSpeed;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Banc de test de bout en bout, contre un serveur local qui parle le protocole
 * de WaveSpeed.
 *
 * Les autres bancs testent le nettoyage et la politique de reessai sur des
 * chaines deja en memoire. Celui-ci part de {@link LlmClient} et traverse la
 * vraie pile : construction du JSON, en-tetes, encodage base64 de la capture,
 * codes d'erreur, lecture du corps. C'est la couche qui n'avait jamais ete
 * exercee, et celle ou une requete mal formee se paie en reponse vide.
 */
public class HttpTest {

    static int ok = 0;
    static int ko = 0;

    /** Ce que le serveur a recu du dernier appel. */
    static final class Seen {
        String method = "";
        String auth = "";
        String contentType = "";
        String accept = "";
        String body = "";
        final AtomicInteger calls = new AtomicInteger();
    }

    /** Reponses jouees dans l'ordre ; la derniere est rejouee si besoin. */
    static final class Script implements HttpHandler {
        private final Seen seen;
        private final int[] codes;
        private final String[] bodies;

        Script(Seen seen, int[] codes, String[] bodies) {
            this.seen = seen;
            this.codes = codes;
            this.bodies = bodies;
        }

        @Override
        public void handle(HttpExchange ex) throws java.io.IOException {
            int n = seen.calls.getAndIncrement();
            seen.method = ex.getRequestMethod();
            seen.auth = header(ex, "Authorization");
            seen.contentType = header(ex, "Content-Type");
            seen.accept = header(ex, "Accept");
            seen.body = new String(readAll(ex.getRequestBody()), "UTF-8");

            int i = Math.min(n, codes.length - 1);
            byte[] out = bodies[Math.min(n, bodies.length - 1)].getBytes("UTF-8");
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(codes[i], out.length);
            OutputStream os = ex.getResponseBody();
            os.write(out);
            os.close();
        }

        private static String header(HttpExchange ex, String name) {
            String v = ex.getRequestHeaders().getFirst(name);
            return v == null ? "" : v;
        }
    }

    static HttpServer server;
    static String base;

    public static void main(String[] args) throws Exception {
        server = HttpServer.create(
                new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.setExecutor(null);
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();

        try {
            requestShape();
            visionShape();
            httpFailures();
            retryOverHttp();
            fallbackOverHttp();
            catalogueOverHttp();
            rescueOverHttp();
            bigImage();
        } finally {
            server.stop(0);
        }

        System.out.println();
        System.out.println("reussis=" + ok + " echoues=" + ko);
        if (ko > 0) {
            System.exit(1);
        }
    }

    // ------------------------------------------------------- forme envoyee

    /** La requete texte, telle qu'elle part vraiment sur le reseau. */
    static void requestShape() throws Exception {
        Seen seen = mount("/shape", new int[]{200},
                new String[]{reply("Je te reponds.")});

        AiEngine.Result r = engine(client("/shape", "google/gemini-3.6-flash"))
                .fromHistory(history());

        expect("appel texte : reponse rendue", r.isOk()
                && "Je te reponds.".equals(r.variants.get(0)));
        expect("methode POST", "POST".equals(seen.method));
        expect("cle envoyee en Bearer", "Bearer cle-de-test".equals(seen.auth));
        expect("content-type json", seen.contentType.contains("application/json"));
        expect("accept json", seen.accept.contains("application/json"));

        JSONObject body = new JSONObject(seen.body);
        expect("le modele part tel quel",
                "google/gemini-3.6-flash".equals(body.optString("model")));
        expect("flux desactive", !body.optBoolean("stream", true));
        expect("plafond de sortie transmis", body.optInt("max_tokens") == 4000);
        expect("temperature transmise", body.optDouble("temperature", 0) > 0);

        JSONArray messages = body.optJSONArray("messages");
        expect("deux messages", messages != null && messages.length() == 2);
        JSONObject system = messages.getJSONObject(0);
        expect("role systeme en premier", "system".equals(system.optString("role")));
        // Le separateur du contrat, pas un titre markdown : en 2.x c'etait "###",
        // que le modele rendait en titre et qui collait les trois propositions
        // en une seule. Le banc verifie donc le separateur reellement attendu.
        expect("le contrat de sortie est dans le systeme",
                system.optString("content").contains(Sanitizer.SEP));
        JSONObject user = messages.getJSONObject(1);
        expect("role utilisateur ensuite", "user".equals(user.optString("role")));
        JSONArray parts = user.optJSONArray("content");
        expect("contenu en blocs", parts != null && parts.length() == 1);
        expect("bloc de texte", "text".equals(parts.getJSONObject(0).optString("type")));
        expect("le dernier message recu est dans le prompt",
                parts.getJSONObject(0).optString("text").contains("Tu me manques"));
        expect("un seul appel", seen.calls.get() == 1);
    }

    /** La capture d'ecran, encodee et renvoyee a l'identique. */
    static void visionShape() throws Exception {
        Seen seen = mount("/vision", new int[]{200}, new String[]{reply("Je vois.")});

        byte[] jpeg = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x42,
                (byte) 0xFE, (byte) 0xD9};
        AiEngine.Result r = engine(client("/vision", "google/gemini-3.6-flash"))
                .fromScreenshot(jpeg);
        expect("appel vision : reponse rendue", r.isOk());

        JSONArray parts = new JSONObject(seen.body)
                .getJSONArray("messages").getJSONObject(1).getJSONArray("content");
        expect("deux blocs : texte puis image", parts.length() == 2);
        JSONObject img = parts.getJSONObject(1);
        expect("type image_url", "image_url".equals(img.optString("type")));
        String url = img.getJSONObject("image_url").optString("url");
        expect("prefixe data jpeg", url.startsWith("data:image/jpeg;base64,"));
        byte[] back = android.util.Base64.decode(
                url.substring("data:image/jpeg;base64,".length()), android.util.Base64.NO_WRAP);
        expect("l'image arrive intacte", Arrays.equals(jpeg, back));
        expect("pas de retour a la ligne dans le base64", !url.contains("\n"));
    }

    // ----------------------------------------------------------- echecs

    /** Les codes d'erreur donnent le bon conseil, et ne coutent pas 3 appels. */
    static void httpFailures() throws Exception {
        Seen k = mount("/cle", new int[]{401}, new String[]{"{\"error\":\"bad key\"}"});
        AiEngine.Result r = engine(client("/cle", "m")).fromHistory(history());
        expect("401 : echec immediat", !r.isOk() && k.calls.get() == 1);
        expect("401 : parle de la cle", r.error.contains("Cle refusee"));

        Seen n = mount("/inconnu", new int[]{404},
                new String[]{"{\"error\":{\"message\":\"model not found\"}}"});
        r = engine(client("/inconnu", "Google/Gemini")).fromHistory(history());
        expect("404 : echec immediat", !r.isOk() && n.calls.get() == 1);
        expect("404 : parle de la casse", r.error.contains("casse"));

        Seen v = mount("/sansvision", new int[]{400},
                new String[]{"{\"error\":{\"message\":\"image_url not supported\"}}"});
        r = engine(client("/sansvision", "m")).fromScreenshot(new byte[]{1, 2, 3});
        expect("400 image : renvoie vers un modele a vision",
                !r.isOk() && r.error.contains("Vision") && v.calls.get() == 1);

        Seen p = mount("/panne", new int[]{500}, new String[]{"boom"});
        r = engine(client("/panne", "m")).fromHistory(history());
        expect("500 : trois essais puis abandon",
                !r.isOk() && p.calls.get() == Retry.MAX_ATTEMPTS);
        expect("500 : parle d'une panne du fournisseur",
                r.error.contains("panne de leur cote"));
    }

    /** Un 429 passager se rattrape tout seul. */
    static void retryOverHttp() throws Exception {
        Seen seen = mount("/quota", new int[]{429, 200},
                new String[]{"{\"error\":\"slow down\"}", reply("Ca va mieux.")});
        AiEngine.Result r = engine(client("/quota", "m")).fromHistory(history());
        expect("429 puis succes", r.isOk() && seen.calls.get() == 2);
        expect("le nombre de tentatives est remonte", r.attempts == 2);
    }

    /** Un refus de filtrage bascule vers un autre modele, sur le reseau reel. */
    static void fallbackOverHttp() throws Exception {
        Seen refus = mount("/refus", new int[]{200},
                new String[]{"{\"choices\":[{\"finish_reason\":\"content_filter\","
                        + "\"message\":{\"content\":\"\"}}]}"});
        Seen secours = mount("/secours", new int[]{200},
                new String[]{reply("Voila ta reponse.")});

        List<Completer> others = new ArrayList<Completer>();
        others.add(client("/secours", "modele-permissif"));
        AiEngine.Result r = new AiEngine(client("/refus", "modele-strict"), cfg())
                .withSleeper(noSleep())
                .withFallback(others)
                .fromHistory(history());

        expect("un refus bascule sur le modele suivant", r.isOk()
                && "Voila ta reponse.".equals(r.variants.get(0)));
        expect("le modele strict n'a ete appele qu'une fois", refus.calls.get() == 1);
        expect("le modele de secours a bien recu la demande", secours.calls.get() == 1);
        expect("le repondeur est nomme", "modele-permissif".equals(r.model) && r.switched);
    }

    /** Le catalogue, lu depuis la vraie forme OpenAI. */
    static void catalogueOverHttp() throws Exception {
        mount("/models", new int[]{200}, new String[]{
                "{\"object\":\"list\",\"data\":["
                        + "{\"id\":\"x-ai/grok-4.3\"},"
                        + "{\"id\":\"google/gemini-3.6-flash\"},"
                        + "{\"id\":\"google/gemini-3.6-flash\"}]}"});
        List<String> ids = LlmClient.listModels("cle-de-test", base + "/models");
        expect("catalogue lu", ids.size() == 2);
        expect("catalogue trie", "google/gemini-3.6-flash".equals(ids.get(0)));
        expect("doublons ecartes", !ids.get(1).equals(ids.get(0)));

        mount("/models-ko", new int[]{401}, new String[]{"{\"error\":\"nope\"}"});
        try {
            LlmClient.listModels("cle-de-test", base + "/models-ko");
            fail("catalogue refuse", "aurait du echouer");
        } catch (Exception e) {
            expect("catalogue refuse : erreur claire",
                    String.valueOf(e.getMessage()).contains("401"));
        }
    }

    /**
     * Le scenario complet du « ca repond vide », de bout en bout.
     *
     * Le modele configure n'existe plus : la passerelle rend 404. L'app lit le
     * catalogue, y prend un modele a vision, et c'est lui qui repond — sans que
     * personne ait eu a ouvrir les reglages au milieu d'une conversation.
     */
    static void rescueOverHttp() throws Exception {
        Seen mort = mount("/disparu", new int[]{404},
                new String[]{"{\"error\":{\"message\":\"model not found\"}}"});
        final Seen vivant = mount("/trouve", new int[]{200},
                new String[]{reply("Je te lis, j'arrive.")});
        mount("/catalogue", new int[]{200}, new String[]{
                "{\"object\":\"list\",\"data\":["
                        + "{\"id\":\"mistralai/mistral-small\"},"
                        + "{\"id\":\"google/gemini-3.6-flash\"}]}"});

        final int[] lectures = {0};
        AiEngine.Result r = engine(client("/disparu", "google/modele-disparu"))
                .withRescue(new AiEngine.Rescue() {
                    @Override
                    public List<Completer> candidates(List<String> tried) throws Exception {
                        lectures[0]++;
                        List<String> ids = WaveSpeed.visionCandidates(
                                LlmClient.listModels("cle-de-test", base + "/catalogue"),
                                tried, 2);
                        List<Completer> out = new ArrayList<Completer>();
                        for (int i = 0; i < ids.size(); i++) {
                            out.add(client("/trouve", ids.get(i)));
                        }
                        return out;
                    }
                })
                .fromScreenshot(new byte[]{9, 8, 7});

        expect("modele disparu : l'app retombe sur ses pieds", r.isOk());
        expect("c'est un modele du catalogue qui repond",
                "google/gemini-3.6-flash".equals(r.model) && r.switched);
        expect("le modele disparu n'est appele qu'une fois", mort.calls.get() == 1);
        // La capture est relue apres decodage du JSON : org.json echappe les
        // barres obliques, donc "data:image/jpeg" ne se cherche pas dans le
        // corps brut — c'est la valeur decodee qui compte.
        String carried = vivant.calls.get() == 1
                ? new JSONObject(vivant.body).getJSONArray("messages").getJSONObject(1)
                        .getJSONArray("content").getJSONObject(1)
                        .getJSONObject("image_url").optString("url")
                : "";
        expect("le modele trouve recoit bien la capture",
                carried.startsWith("data:image/jpeg;base64,"));
        expect("le catalogue n'est lu qu'une fois", lectures[0] == 1);
        expect("le modele sans vision n'est pas essaye",
                !String.valueOf(vivant.body).contains("mistral"));
    }

    /**
     * Une vraie capture d'ecran fait quelques centaines de kilo-octets en
     * base64 : on verifie que le corps part en entier.
     */
    static void bigImage() throws Exception {
        Seen seen = mount("/grosse", new int[]{200}, new String[]{reply("Recu.")});
        byte[] jpeg = new byte[260_000];
        for (int i = 0; i < jpeg.length; i++) {
            jpeg[i] = (byte) (i * 31);
        }
        AiEngine.Result r = engine(client("/grosse", "m")).fromScreenshot(jpeg);
        expect("grosse capture : reponse rendue", r.isOk());
        String url = new JSONObject(seen.body).getJSONArray("messages").getJSONObject(1)
                .getJSONArray("content").getJSONObject(1)
                .getJSONObject("image_url").optString("url");
        byte[] back = android.util.Base64.decode(
                url.substring("data:image/jpeg;base64,".length()), android.util.Base64.NO_WRAP);
        expect("grosse capture : rien n'est tronque", Arrays.equals(jpeg, back));
    }

    // ---------------------------------------------------------- plomberie

    static Seen mount(String path, int[] codes, String[] bodies) {
        Seen seen = new Seen();
        server.createContext(path, new Script(seen, codes, bodies));
        return seen;
    }

    static LlmClient client(String path, String model) {
        return new LlmClient("cle-de-test", model).withEndpoint(base + path);
    }

    static AiEngine engine(LlmClient client) {
        return new AiEngine(client, cfg()).withSleeper(noSleep());
    }

    static String reply(String text) {
        return "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":"
                + "{\"role\":\"assistant\",\"content\":\"" + text + "\"}}]}";
    }

    static List<ChatMessage> history() {
        List<ChatMessage> h = new ArrayList<ChatMessage>();
        h.add(new ChatMessage(ChatMessage.Sender.ME, "Je rentre tard ce soir."));
        h.add(new ChatMessage(ChatMessage.Sender.OTHER, "Tu me manques deja."));
        return h;
    }

    static AiEngine.Sleeper noSleep() {
        return new AiEngine.Sleeper() {
            @Override
            public void sleep(long ms) {
            }
        };
    }

    static PromptBuilder.Config cfg() {
        PromptBuilder.Config c = new PromptBuilder.Config();
        c.count = 1;
        return c;
    }

    static byte[] readAll(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            bos.write(buf, 0, n);
        }
        in.close();
        return bos.toByteArray();
    }

    static void expect(String name, boolean condition) {
        if (condition) {
            ok++;
            System.out.println("  ok   " + name);
        } else {
            ko++;
            System.out.println("  KO   " + name);
        }
    }

    static void fail(String name, String why) {
        ko++;
        System.out.println("  KO   " + name + " : " + why);
    }
}
