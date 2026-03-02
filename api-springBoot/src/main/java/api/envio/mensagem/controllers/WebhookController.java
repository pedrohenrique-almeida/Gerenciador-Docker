package api.envio.mensagem.controllers;

import api.envio.mensagem.config.EvolutionConfig;
import api.envio.mensagem.config.WhatsProperties;
import api.envio.mensagem.services.DockerService;
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

            String numeroValidacao = normalizarNumero(remoteJid);
            if (!whatsProperties.getWhitelist().contains(numeroValidacao)) {
                return;
            }

            String texto = extrairTexto(data);
            if (texto == null || texto.isBlank()) {
                return;
            }

            String resposta = processarComando(texto.trim());

            if (resposta != null && !resposta.isBlank()) {
                enviarResposta(remoteJid, resposta);
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

    private void enviarResposta(String destinoJid, String texto) {
        try {
            Map<String, Object> payload = Map.of(
                    "number", normalizarNumero(destinoJid),
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
        return (jid == null) ? null : jid.split("@")[0].replaceAll("\\D", "");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object obj) {
        return (obj instanceof Map) ? (Map<String, Object>) obj : null;
    }
}


