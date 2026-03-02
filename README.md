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
Gerenciador-Docker/
|- api-springBoot/       # API Spring Boot (webhook + comandos Docker)
|- evolution-api/        # Docker Compose da Evolution + Postgres + Redis + pgAdmin + API Spring
`- README.md             # este guia
```

## Pre-requisitos

- Docker
- Docker Compose
- Java 21 (se for rodar a API fora de container)

## Regra de token (obrigatoria)

Esses dois valores devem ser iguais:

- `AUTHENTICATION_API_KEY` (Evolution API)
- `EVOLUTION_TOKEN` (API Spring Boot)

## Configuracao do `.env`

No diretorio `evolution-api`:

```bash
cd evolution-api
cp .env.example .env
```

Edite o arquivo `evolution-api/.env` e preencha os valores.

Principais campos:

- `AUTHENTICATION_API_KEY`
- `EVOLUTION_TOKEN` (mesmo valor da chave acima)
- `POSTGRES_PASSWORD`
- `PGADMIN_DEFAULT_PASSWORD`
- `DATABASE_CONNECTION_URI` (mesma senha do Postgres)
- `WHATSAPP_ADMIN` (DDI + DDD + numero, sem simbolos)

## DOCKER_HOST (valor correto por ambiente)

Esse campo define como a API Spring conversa com o Docker da maquina host.

Exemplos mais comuns para `DOCKER_HOST`:

- Docker Desktop (Windows/macOS): `tcp://host.docker.internal:2375`
- Linux com Docker socket montado no container: `unix:///var/run/docker.sock`
- Linux com Docker remoto via TCP (sem TLS, ambiente controlado): `tcp://172.17.0.1:2375`
- Docker remoto com TLS: `tcp://SEU_HOST:2376`

Valor padrao atual do projeto no `.env`:

- `DOCKER_HOST=tcp://host.docker.internal:2375`

Se esse valor nao funcionar no seu ambiente, ajuste apenas no `evolution-api/.env` e rode novamente `docker compose up -d`.

## Como subir

No diretorio `evolution-api`:

```bash
docker compose up -d
```

## Importante: como o compose usa o `.env`

Sim, o projeto esta configurado para ler valores do `.env`.

- O `docker compose` carrega automaticamente `evolution-api/.env`.
- O serviço `evolution-api` usa `env_file: .env`.
- O serviço `api-springBoot` tambem usa `env_file: .env` e variaveis `${...}`.

Na pratica: voce configura o `.env` e sobe com `docker compose up -d`.

## Criacao da instancia na Evolution

Acesse:

- `http://localhost:8082`

Crie a instancia com o nome:

- `Gerenciador_containeres`

Se mudar o nome da instancia, ajuste no `.env`:

- `EVOLUTION_INSTANCE`
- `EVOLUTION_URL`

## Webhook da instancia

Como tudo esta na mesma rede Docker, use:

- `http://api-springBoot:8081/webhook/receber`

Nao use `localhost` nesse caso.

## Validacao rapida

1. `docker compose ps`
2. Conferir se estao `Up`: `evolution_api`, `postgres`, `redis`, `api_whatsapp`
3. Enviar `1` no WhatsApp para o numero autorizado em `WHATSAPP_ADMIN`

## Erros comuns

- `401/403` na Evolution:
  - `AUTHENTICATION_API_KEY` e `EVOLUTION_TOKEN` diferentes.

- Sem resposta no WhatsApp:
  - numero nao esta em `WHATSAPP_ADMIN`.
  - webhook da instancia nao aponta para `/webhook/receber`.

- `docker compose` mostrando aviso de `version is obsolete`:
  - resolvido neste projeto (campo `version` removido do compose).

## Fluxo alternativo (Spring fora do Docker)

1. Suba apenas os servicos da pasta `evolution-api`.
2. Rode a API local em `api-springBoot`.
3. Defina `EVOLUTION_TOKEN` no ambiente local.
4. Configure webhook para `http://host.docker.internal:8081/webhook/receber` (ou ngrok).
