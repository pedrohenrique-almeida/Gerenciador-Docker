# Gerenciador Docker via WhatsApp

Projeto para controlar containers Docker por mensagens no WhatsApp, usando:

- Evolution API (recebe mensagens e envia respostas)
- API Spring Boot (processa comandos e conversa com Docker)
- Postgres, Redis e pgAdmin

## 1. Arquitetura

Servicos no `docker compose`:

- `evolution_api` (porta host `8082`)
- `api_whatsapp` (porta host `8081`)
- `postgres` (porta host `5432`)
- `redis` (porta host `6379`)
- `pgadmin` (porta host `4000`)

Fluxo:

1. WhatsApp -> Evolution API (`messages.upsert`)
2. Evolution chama webhook da Spring (`/webhook/receber`)
3. Spring executa comando Docker
4. Spring responde via endpoint `sendText` da Evolution

## 2. Estrutura

```text
Gerenciador-Docker-main/
|- api-springBoot/
|  `- src/main/resources/application.yaml
|- evolution-api/
|  |- docker-compose.yaml
|  `- .env.example         # modelo para novos ambientes
`- README.md
```

## 3. Requisitos

- Docker + Docker Compose
- Acesso ao Docker daemon no host
- Internet para conectar no WhatsApp pela Evolution

Opcional (somente se rodar Spring fora do container):

- Java 21
- Maven 3.9+

## 4. Configuracao do `.env`

Arquivo: `evolution-api/.env`

1. Copiar o exemplo:

```bash
cd evolution-api
cp .env.example .env
```

No Windows PowerShell:

```powershell
Copy-Item .env.example .env
```

2. Editar os valores obrigatorios:

- `AUTHENTICATION_API_KEY`
- `EVOLUTION_TOKEN` (deve ser igual ao `AUTHENTICATION_API_KEY`)
- `POSTGRES_PASSWORD`
- `PGADMIN_DEFAULT_PASSWORD`
- `DATABASE_CONNECTION_URI` (mesma senha do Postgres)
- `EVOLUTION_INSTANCE`
- `EVOLUTION_URL`
- `DOCKER_HOST`

## 5. Configuracao por ambiente (DOCKER_HOST)

### Linux (recomendado com socket)

- `DOCKER_HOST=unix:///var/run/docker.sock`
- Compose ja monta `/var/run/docker.sock:/var/run/docker.sock`

### Windows Docker Desktop

Opcoes:

1. Usar socket Linux da VM (WSL2):
- `DOCKER_HOST=unix:///var/run/docker.sock`

2. Usar TCP do Docker Desktop (se habilitado):
- `DOCKER_HOST=tcp://host.docker.internal:2375`

Se usar TCP 2375, habilitar essa opcao no Docker Desktop.

## 6. application.yaml (Spring)

Arquivo: `api-springBoot/src/main/resources/application.yaml`

Ja esta preparado para ler variaveis de ambiente. Normalmente nao precisa editar arquivo:

- `server.port <- SERVER_PORT`
- `evolution.base-url <- EVOLUTION_BASE_URL`
- `evolution.instance <- EVOLUTION_INSTANCE`
- `evolution.url <- EVOLUTION_URL`
- `evolution.token <- EVOLUTION_TOKEN`
- `docker.host <- DOCKER_HOST`
- `whatsapp.whitelist <- lista fixa no YAML`

Importante:

- A whitelist atual esta no `application.yaml`.
- Se outro numero precisar controlar os containers, adicionar na whitelist.

## 7. docker-compose

Arquivo: `evolution-api/docker-compose.yaml`

Pontos importantes:

- `evolution-api` exposta em `8082:8080`
- `api-springBoot` exposta em `8081:8081`
- Spring usa `env_file: .env`
- Spring monta o socket Docker do host
- Fallback de `DOCKER_HOST` esta para `unix:///var/run/docker.sock`

## 8. Subida do ambiente

```bash
cd evolution-api
docker compose up -d
```

Validar:

```bash
docker compose ps
```

Todos devem estar `Up`: `evolution_api`, `api_whatsapp`, `postgres`, `redis`, `pgadmin`.

## 9. Configuracao da instancia Evolution

1. Abrir `http://localhost:8082`
2. Criar/conectar instancia com nome igual a `EVOLUTION_INSTANCE`
3. Configurar webhook:

- URL: `http://api-springBoot:8081/webhook/receber`
- Enabled: `true`
- Eventos: `messages.upsert` (obrigatorio)

Sem webhook ativo a API responde uma vez e para.

## 10. Teste funcional

Enviar no WhatsApp (numero na whitelist):

- `docker` ou `1` -> lista containers
- `2 <nome-ou-id>` -> iniciar
- `3 <nome-ou-id>` -> parar
- `4 <nome-ou-id>` -> logs

## 11. Possíveis problemas

### Nao responde no WhatsApp

- Webhook da Evolution desativado
- Numero fora da whitelist (`application.yaml`)
- Instancia desconectada no painel Evolution

### Erro de Docker host

- `DOCKER_HOST` incorreto para o ambiente
- Socket nao montado no Spring
- Docker daemon indisponivel

### Erros `Bad MAC` / `No matching sessions found`

- Instabilidade de sessao WhatsApp na Evolution
- Reconectar instancia no painel e testar novamente

