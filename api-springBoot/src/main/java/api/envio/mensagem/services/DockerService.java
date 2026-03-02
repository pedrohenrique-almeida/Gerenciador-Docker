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
        List<Container> containers = dockerClient.listContainersCmd().withShowAll(true).exec();
        Set<String> protegidosMapeados = new HashSet<>();

        StringBuilder ativos = new StringBuilder("ATIVOS (GERENCIAVEIS)\n");
        StringBuilder protegidos = new StringBuilder("\nSISTEMA (PROTEGIDOS)\n");
        StringBuilder parados = new StringBuilder("\nPARADOS\n");

        boolean existeContainerParado = false;

        for (Container c : containers) {
            String nome = c.getNames()[0].replace("/", "");
            String id = c.getId().substring(0, 7);
            String status = c.getStatus();

            boolean containerProtegido = isNomeProtegido(nome);
            boolean estaEmExecucao = "running".equalsIgnoreCase(c.getState());

            if (containerProtegido) {
                protegidos.append("- ").append(nome).append(" (`").append(id).append("`) - ").append(status).append("\n");
                protegidosMapeados.add(nome.toLowerCase());
            } else if (estaEmExecucao) {
                ativos.append("- ").append(nome).append(" (`").append(id).append("`)\n");
            } else {
                parados.append("- ").append(nome).append(" (`").append(id).append("`) - ").append(status).append("\n");
                existeContainerParado = true;
            }
        }

        if (containersBloqueados != null) {
            for (String protegidoConfig : containersBloqueados) {
                if (!protegidosMapeados.contains(protegidoConfig.toLowerCase())) {
                    protegidos.append("- ").append(protegidoConfig).append(" (`nao encontrado`)").append("\n");
                }
            }
        }

        StringBuilder resposta = new StringBuilder("GERENCIADOR DE DOCKER\n\n");
        resposta.append("Containers:\n");
        resposta.append("------------------------------\n\n");
        resposta.append(ativos);
        resposta.append(protegidos);

        if (existeContainerParado) {
            resposta.append(parados);
        }

        resposta.append("\n------------------------------\n");
        resposta.append("Comandos\n");
        resposta.append("1 Listar | 2 Iniciar | 3 Parar | 4 Logs\n");
        resposta.append("------------------------------\n");
        resposta.append("Comando: [Acao] [Nome ou ID]\n");
        resposta.append("Ex: 3 pgadmin ou 2 b52bda0 ou 4 redis");

        return resposta.toString();
    }

    public String iniciarContainer(String nomeOuId) {
        if (isProtegido(nomeOuId)) {
            return "Erro: container protegido. Acao bloqueada.";
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
