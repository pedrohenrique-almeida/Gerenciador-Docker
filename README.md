# Gerenciador Docker via WhatsApp

Projeto para controlar containers Docker por mensagens de WhatsApp, usando Evolution API + Spring Boot.

## O que este projeto faz

- `1` ou `docker`: lista containers
- `2 <nome-ou-id>`: inicia container
- `3 <nome-ou-id>`: para container
- `4 <nome-ou-id>`: mostra logs (ultimas 50 linhas)

Containers protegidos em `docker.blocked` nao podem ser iniciados/parados.

## Estrutura

```text
GerenciadorDockerWhatsApp/
|- api/                  # API Spring Boot (webhook + comandos Docker)
|- evolution-api/        # Docker Compose da Evolution + Postgres + Redis + pgAdmin
`- documentation/README  # este guia
```

## Pre-requisitos

- Docker Desktop
- Docker Compose
- Java 21 

## Configuracao de tokens

Existem dois nomes, eles devem ter o mesmo valor.

1. `AUTHENTICATION_API_KEY` Evolution API (tem que definir no docker-compose)
2. `EVOLUTION_TOKEN` (API Spring Boot)

Regra:

- `AUTHENTICATION_API_KEY` = `EVOLUTION_TOKEN`

Isso significa:

- a Evolution usa `AUTHENTICATION_API_KEY` para autenticar chamadas
- a API Spring envia mensagens para Evolution usando `EVOLUTION_TOKEN` no header `apikey`

## Ordem exata de configuracao (recomendado: subir tudo no mesmo compose)

### 1) Criar arquivo `.env` da Evolution

No diretorio `evolution-api`:

```powershell
cd evolution-api
Copy-Item .env.example .env
```

Abra `evolution-api/.env` e preencha:

- `AUTHENTICATION_API_KEY`: crie uma chave forte (ex: 32+ caracteres)
- `POSTGRES_PASSWORD`: senha do Postgres
- `PGADMIN_DEFAULT_PASSWORD`: senha
- `DATABASE_CONNECTION_URI` com a mesma senha do Postgres

Exemplo de `DATABASE_CONNECTION_URI`:

`postgresql://postgres:senha_do_postgres@postgres:5432/evolution?schema=public`

### 2) Descomentar a API Spring no compose

No arquivo `evolution-api/docker-compose.yaml`, descomente o bloco:

- `api-whatsapp`

No bloco `api-whatsapp`, configure:

- `EVOLUTION_TOKEN`: coloque o mesmo valor de `AUTHENTICATION_API_KEY`
- `WHATSAPP_ADMIN`: numero autorizado (DDI + DDD + numero, sem simbolos)

### 3) Subir tudo

Ainda em `evolution-api`:

```powershell
docker compose up -d
```

### 4) Acessar Evolution e criar instancia

Acesse painel/API da Evolution em:

- `http://localhost:8082`

Crie a instancia com o nome esperado pelo projeto:

- `Gerenciador_containeres`

Se mudar o nome da instancia, ajuste tambem:

- `EVOLUTION_INSTANCE`
- `EVOLUTION_URL`

### 5) Configurar webhook da instancia

Como tudo esta na mesma rede Docker, use:

- `http://api-whatsapp:8081/webhook/receber`

Nao use `localhost` nesse caso.

## Sobre `application.yaml`

Voce nao precisa colocar segredo fixo nele.

O arquivo ja esta pronto para variaveis de ambiente:

- `evolution.token: ${EVOLUTION_TOKEN:}`

Ou seja:

- o valor real vem do `docker-compose`/`.env`
- `application.yaml` so referencia a variavel

## Como descobrir se esta funcionando

1. `docker ps` deve mostrar: `evolution_api`, `postgres`, `redis`, `api_whatsapp`
2. Envie `1` no WhatsApp para o numero conectado
3. A resposta deve trazer lista de containers

## Erros comuns e correcao rapida

- Erro 401/403 na Evolution:
  - `EVOLUTION_TOKEN` diferente de `AUTHENTICATION_API_KEY`

- Sem resposta no WhatsApp:
  - numero nao esta em `WHATSAPP_ADMIN`
  - webhook da instancia nao aponta para `/webhook/receber`

- Webhook nao chega:
  - usando `localhost` em vez de `api-whatsapp` quando tudo esta em compose

## Fluxo alternativo (Spring fora do Docker)

Se quiser rodar Spring local e Evolution em container:

1. Suba apenas `evolution-api` via compose
2. Rode Spring local (`./mvnw spring-boot:run`)
3. Defina `EVOLUTION_TOKEN` no terminal local
4. Configure webhook para `http://host.docker.internal:8081/webhook/receber` (ou ngrok)
