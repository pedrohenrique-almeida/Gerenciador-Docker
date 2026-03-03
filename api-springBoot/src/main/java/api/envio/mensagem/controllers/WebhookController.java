package api.envio.mensagem.controllers;

import api.envio.mensagem.config.EvolutionConfig;
import api.envio.mensagem.config.WhatsProperties;
import api.envio.mensagem.services.DockerService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import kong.unirest.Unirest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhook")
public class WebhookController {

    private final DockerService dockerService;
    private final WhatsProperties whatsProperties;
    private final EvolutionConfig evolutionProperties;

    public WebhookController(DockerService dockerService, WhatsProperties whatsProperties, EvolutionConfig evolutionProperties) {
        this.dockerService = dockerService;
        this.whatsProperties = whatsProperties;
        this.evolutionProperties = evolutionProperties;
    }

    @PostMapping("/receber")
    public void receberMensagem(@RequestBody Map<String, Object> body) {
        try {
            String event = (String) body.get("event");
            if (!"messages.upsert".equals(event)) {
                return;
            }

            Map<String, Object> data = castMap(body.get("data"));
            Map<String, Object> key = castMap(data.get("key"));
            if (key == null || Boolean.TRUE.equals(key.get("fromMe"))) {
                return;
            }

            String remoteJid = (String) key.get("remoteJid");
            if (remoteJid == null || remoteJid.endsWith("@g.us")) {
                return;
            }

            String numeroValidacao = resolverNumeroContato(body, data, key, remoteJid);
            if (numeroValidacao == null || numeroValidacao.isBlank()) {
                System.out.println("[WEBHOOK] Ignorado: numero nao resolvido para remoteJid=" + remoteJid);
                return;
            }
            if (!whatsProperties.getWhitelist().contains(numeroValidacao)) {
                System.out.println("[WEBHOOK] Ignorado: numero fora da whitelist=" + numeroValidacao + " remoteJid=" + remoteJid);
                return;
            }

            String texto = extrairTexto(data);
            if (texto == null || texto.isBlank()) {
                return;
            }

            String resposta = processarComando(texto.trim());

            if (resposta != null && !resposta.isBlank()) {
                enviarResposta(numeroValidacao, resposta);
            }

        } catch (Exception e) {
            System.err.println("âŒ Erro no fluxo: " + e.getMessage());
        }
    }

    private String processarComando(String comandoOriginal) {
        String cmd = comandoOriginal.toLowerCase().trim();

        if (cmd.equals("docker") || cmd.equals("1")) {
            return dockerService.listarContainers();
        }

        String[] partes = cmd.split("\\s+", 2);
        String opcao = partes[0];
        String alvo = partes.length > 1 ? partes[1].trim() : null;

        if (alvo == null) {
            return null;
        }

        return switch (opcao) {
            case "2" ->
                dockerService.iniciarContainer(alvo);
            case "3" ->
                dockerService.pararContainer(alvo);
            case "4" ->
                dockerService.verLogs(alvo);
            default ->
                null;
        };
    }

    private void enviarResposta(String numeroDestino, String texto) {
        try {
            Map<String, Object> payload = Map.of(
                    "number", numeroDestino,
                    "text", texto
            );

            Unirest.post(evolutionProperties.getUrl())
                    .header("apikey", evolutionProperties.getToken())
                    .header("Content-Type", "application/json")
                    .body(payload)
                    .asJsonAsync();
        } catch (Exception e) {
            System.err.println("âŒ Erro envio: " + e.getMessage());
        }
    }

    private String resolverNumeroContato(Map<String, Object> body, Map<String, Object> data, Map<String, Object> key, String remoteJid) {
        boolean isLid = remoteJid != null && remoteJid.endsWith("@lid");

        if (isLid) {
            String numeroPorLid = resolverNumeroPorLid(remoteJid, data);
            if (numeroPorLid != null && !numeroPorLid.isBlank()) {
                return numeroPorLid;
            }

            String participantLid = key == null ? null : (String) key.get("participant");
            String numeroParticipantLid = normalizarNumero(participantLid);
            if (numeroParticipantLid != null && !numeroParticipantLid.isBlank()) {
                return numeroParticipantLid;
            }

            // Nunca usar o numero bruto do LID (ex.: 975...@lid) para whitelist.
            return null;
        }

        String numero = normalizarNumero(remoteJid);
        if (numero != null && !numero.isBlank()) {
            return numero;
        }

        String participant = key == null ? null : (String) key.get("participant");
        String numeroParticipant = normalizarNumero(participant);
        if (numeroParticipant != null && !numeroParticipant.isBlank()) {
            return numeroParticipant;
        }

        String sender = (String) body.get("sender");
        return normalizarNumero(sender);
    }

    private String resolverNumeroPorLid(String remoteJid, Map<String, Object> data) {
        try {
            List<Map<String, Object>> contatos = buscarContatos();
            if (contatos.isEmpty()) {
                return null;
            }

            Map<String, Object> contatoLid = buscarContatoPorJid(contatos, remoteJid);
            String pushNameLid = contatoLid == null ? null : asString(contatoLid.get("pushName"));
            String profilePicLid = contatoLid == null ? null : asString(contatoLid.get("profilePicUrl"));

            if ((pushNameLid == null || pushNameLid.isBlank()) && data != null) {
                pushNameLid = asString(data.get("pushName"));
            }

            if (profilePicLid == null || profilePicLid.isBlank()) {
                profilePicLid = buscarFotoPerfil(remoteJid);
            }

            List<String> candidatos = new ArrayList<>();
            for (Map<String, Object> c : contatos) {
                String jid = asString(c.get("remoteJid"));
                if (jid == null || !jid.endsWith("@s.whatsapp.net")) {
                    continue;
                }

                String push = asString(c.get("pushName"));
                String pic = asString(c.get("profilePicUrl"));

                boolean matchPic = profilePicLid != null && !profilePicLid.isBlank() && profilePicLid.equals(pic);
                boolean matchPush = pushNameLid != null && !pushNameLid.isBlank() && pushNameLid.equalsIgnoreCase(push);

                if (matchPic || matchPush) {
                    String numero = normalizarNumero(jid);
                    if (numero != null && !numero.isBlank()) {
                        candidatos.add(numero);
                    }
                }
            }

            if (candidatos.size() == 1) {
                return candidatos.get(0);
            }
            if (candidatos.size() > 1) {
                for (String candidato : candidatos) {
                    if (whatsProperties.getWhitelist().contains(candidato)) {
                        return candidato;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private List<Map<String, Object>> buscarContatos() {
        try {
            String url = evolutionProperties.getBaseUrl() + "/chat/findContacts/" + evolutionProperties.getInstance();
            Map<String, Object> resposta = Unirest.post(url)
                    .header("apikey", evolutionProperties.getToken())
                    .header("Content-Type", "application/json")
                    .body("{}")
                    .asObject(Map.class)
                    .getBody();

            Object valor = resposta == null ? null : resposta.get("value");
            if (valor instanceof List) {
                return (List<Map<String, Object>>) valor;
            }
        } catch (Exception ignored) {
        }
        return new ArrayList<>();
    }

    private String buscarFotoPerfil(String remoteJid) {
        try {
            String url = evolutionProperties.getBaseUrl() + "/chat/fetchProfilePictureUrl/" + evolutionProperties.getInstance();
            Map<String, Object> payload = Map.of("number", remoteJid);
            Map<String, Object> resposta = Unirest.post(url)
                    .header("apikey", evolutionProperties.getToken())
                    .header("Content-Type", "application/json")
                    .body(payload)
                    .asObject(Map.class)
                    .getBody();

            return resposta == null ? null : asString(resposta.get("profilePictureUrl"));
        } catch (Exception ignored) {
            return null;
        }
    }

    private Map<String, Object> buscarContatoPorJid(List<Map<String, Object>> contatos, String remoteJid) {
        for (Map<String, Object> c : contatos) {
            if (remoteJid.equals(asString(c.get("remoteJid")))) {
                return c;
            }
        }
        return null;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
    // Extrai texto da mensagem nos formatos suportados pelo webhook da Evolution.
    private String extrairTexto(Map<String, Object> data) {
        try {
            Map<String, Object> msg = castMap(data.get("message"));
            if (msg == null) {
                return null;
            }
            if (msg.containsKey("conversation")) {
                return (String) msg.get("conversation");
            }
            if (msg.containsKey("extendedTextMessage")) {
                return (String) castMap(msg.get("extendedTextMessage")).get("text");
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String normalizarNumero(String jid) {
        if (jid == null) {
            return null;
        }
        String base = jid.split("@")[0];
        if (base.contains(":")) {
            base = base.split(":")[0];
        }
        return base.replaceAll("\\D", "");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object obj) {
        return (obj instanceof Map) ? (Map<String, Object>) obj : null;
    }
}


