package api.envio.mensagem.services;

import api.envio.mensagem.config.DockerConfig;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.core.command.LogContainerResultCallback;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class DockerService {

    private final DockerClient dockerClient;
    private final List<String> containersBloqueados;

    public DockerService(DockerClient dockerClient, DockerConfig dockerConfig) {
        this.dockerClient = dockerClient;
        this.containersBloqueados = dockerConfig.getBlocked();
    }

    private boolean isNomeProtegido(String nomeOuId) {
        if (containersBloqueados == null || nomeOuId == null) {
            return false;
        }
        String nomeLimpo = nomeOuId.replace("/", "");
        return containersBloqueados.stream().anyMatch(item -> item.equalsIgnoreCase(nomeLimpo));
    }

    private boolean isProtegido(String nomeOuId) {
        if (nomeOuId == null || nomeOuId.isBlank()) {
            return false;
        }
        if (isNomeProtegido(nomeOuId)) {
            return true;
        }

        List<Container> containers = dockerClient.listContainersCmd().withShowAll(true).exec();
        for (Container c : containers) {
            String nome = c.getNames()[0].replace("/", "");
            String idCompleto = c.getId();
            String idCurto = idCompleto.substring(0, 7);
            boolean alvoPorNome = nome.equalsIgnoreCase(nomeOuId);
            boolean alvoPorId = idCompleto.startsWith(nomeOuId) || idCurto.equalsIgnoreCase(nomeOuId);
            if ((alvoPorNome || alvoPorId) && isNomeProtegido(nome)) {
                return true;
            }
        }

        return false;
    }

    public String listarContainers() {
        try {
            List<Container> containers = dockerClient.listContainersCmd().withShowAll(true).exec();
            Set<String> protegidosMapeados = new HashSet<>();

            StringBuilder ativos = new StringBuilder();
            StringBuilder protegidos = new StringBuilder();
            StringBuilder parados = new StringBuilder();

            int totalAtivos = 0;
            int totalParados = 0;
            int totalProtegidos = 0;

            for (Container c : containers) {
                String nome = c.getNames()[0].replace("/", "");
                String id = c.getId().substring(0, 7);
                String status = c.getStatus();

                boolean containerProtegido = isNomeProtegido(nome);
                boolean estaEmExecucao = "running".equalsIgnoreCase(c.getState());

                if (containerProtegido) {
                    protegidos.append("• ").append(nome).append(" (").append(id).append(") - ").append(status).append("\n");
                    protegidosMapeados.add(nome.toLowerCase());
                    totalProtegidos++;
                } else if (estaEmExecucao) {
                    ativos.append("• ").append(nome).append(" (").append(id).append(") - ").append(status).append("\n");
                    totalAtivos++;
                } else {
                    parados.append("• ").append(nome).append(" (").append(id).append(") - ").append(status).append("\n");
                    totalParados++;
                }
            }

            if (containersBloqueados != null) {
                for (String protegidoConfig : containersBloqueados) {
                    if (!protegidosMapeados.contains(protegidoConfig.toLowerCase())) {
                        protegidos.append("• ").append(protegidoConfig).append(" (nao encontrado)").append("\n");
                        totalProtegidos++;
                    }
                }
            }

            if (ativos.isEmpty()) {
                ativos.append("• nenhum\n");
            }
            if (parados.isEmpty()) {
                parados.append("• nenhum\n");
            }
            if (protegidos.isEmpty()) {
                protegidos.append("• nenhum\n");
            }

            int totalContainers = totalAtivos + totalParados + totalProtegidos;

            StringBuilder resposta = new StringBuilder();
            resposta.append("🐳 *GERENCIADOR DOCKER*\n");
            resposta.append("━━━━━━━━━━━━━━━━━━\n");
            resposta.append("📊 *Resumo*\n");
            resposta.append("📦 Total: ").append(totalContainers).append("\n");
            resposta.append("🟢 Ativos: ").append(totalAtivos).append("\n");
            resposta.append("🔴 Parados: ").append(totalParados).append("\n");
            resposta.append("🔒 Protegidos: ").append(totalProtegidos).append("\n\n");

            resposta.append("🟢 *ATIVOS* \n").append(ativos).append("\n");
            resposta.append("🔴 *PARADOS* \n").append(parados).append("\n");
            resposta.append("🔒 *PROTEGIDOS* \n").append(protegidos).append("\n");

            resposta.append("🧭 *Comandos*\n");
            resposta.append("• `docker listar`\n");
            resposta.append("• `docker iniciar [nome-ou-id]`\n");
            resposta.append("• `docker parar [nome-ou-id]`\n");
            resposta.append("• `docker logs [nome-ou-id]`\n\n");
            resposta.append("Ex: `docker iniciar pgadmin` | `docker logs redis`");

            return resposta.toString();
        } catch (Exception e) {
            return "Erro ao conectar no Docker host (" + e.getMessage() + "). "
                    + "Verifique DOCKER_HOST no .env e se a API tem acesso ao daemon.";
        }
    }

    public String iniciarContainer(String nomeOuId) {
        if (isProtegido(nomeOuId)) {
            return "Erro: container protegido. Ação bloqueada.";
        }
        try {
            dockerClient.startContainerCmd(nomeOuId).exec();
            return "Container " + nomeOuId + " iniciado.";
        } catch (Exception e) {
            return "Erro: " + e.getMessage();
        }
    }

    public String pararContainer(String nomeOuId) {
        if (isProtegido(nomeOuId)) {
            return "Erro: container protegido. Acao bloqueada.";
        }
        try {
            dockerClient.stopContainerCmd(nomeOuId).exec();
            return "Container " + nomeOuId + " parado.";
        } catch (Exception e) {
            return "Erro: " + e.getMessage();
        }
    }

    public String verLogs(String nomeOuId) {
        StringBuilder logs = new StringBuilder();
        try {
            dockerClient.logContainerCmd(nomeOuId)
                    .withStdOut(true)
                    .withStdErr(true)
                    .withTail(50)
                    .exec(new LogContainerResultCallback() {
                        @Override
                        public void onNext(Frame frame) {
                            logs.append(new String(frame.getPayload()));
                        }
                    })
                    .awaitCompletion();
            return logs.toString();
        } catch (Exception e) {
            return "Erro ao buscar logs: " + e.getMessage();
        }
    }
}
