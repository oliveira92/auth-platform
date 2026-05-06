# Auth Platform — Plataforma Corporativa de Autenticação e Autorização

> **Golden Path** para autenticação e autorização corporativa via LDAP/Active Directory.  
> Modelo de **auto-serviço**: aplicações apenas se conectam a esta plataforma.

---

## Sumário

1. [Visão Geral](#visão-geral)
2. [Arquitetura](#arquitetura)
3. [Tecnologias](#tecnologias)
4. [Estrutura do Projeto](#estrutura-do-projeto)
5. [Configuração Local (Rancher / Docker Compose)](#configuração-local)
6. [Configuração AWS](#configuração-aws)
7. [Endpoints da API](#endpoints-da-api)
8. [Integração de Aplicações (Self-Service)](#integração-de-aplicações)
9. [Segurança](#segurança)
10. [Roadmap do Produto](#roadmap)
11. [Contribuição](#contribuição)

---

## Visão Geral

A **Auth Platform** é a solução corporativa de autenticação e autorização baseada nos princípios de:

- **LDAP/AD como fonte de verdade** — usuários e grupos gerenciados pelo diretório corporativo
- **Auto-serviço** — aplicações se registram e consomem a plataforma de forma independente
- **JWT RS256** — tokens assimétricos, sem estado, validados localmente por cada serviço
- **RBAC** — controle de acesso baseado em papéis configurável por aplicação
- **Auditoria** — trilha central de eventos de autenticação, RBAC e administração
- **Cloud-native** — configurações no AWS Parameter Store, segredos no Secrets Manager

### Fluxo de Autenticação

```
Usuário → Aplicação → Auth Service / Authorization Service
                           ↓ LDAP BIND + search
                      LDAP / Active Directory
                           ↓ User + Groups
                      JWT Token (access + refresh) RS256
                           ↓
                      Redis (token store / blacklist)
                           ↓
                      Audit Service (eventos)
```

No desenho atual, as aplicações consumidoras chamam diretamente os serviços da Auth Platform. O token JWT emitido é validado localmente pelos serviços internos e pelos backends consumidores usando a chave pública exposta via JWKS.

---

## Arquitetura

O projeto segue **Arquitetura Hexagonal (Ports & Adapters)** + **Domain Driven Design (DDD)** em uma estrutura de **microsserviços independentes**.

### Diagrama C4

O arquivo [`docs/c4-architecture.drawio`](docs/c4-architecture.drawio) contém 4 níveis:

| Nível | Diagrama |
|-------|----------|
| L1 | System Context — visão externa da plataforma |
| L2 | Container — microsserviços e infraestrutura |
| L3 | Component — componentes internos do auth-service |
| L4 | Deployment — ambiente local (Rancher/Docker) |

Abra no [draw.io](https://app.diagrams.net) ou instale a extensão VS Code **Draw.io Integration**.

> **Ambiente local e produtivo:** neste momento o produto não utiliza API Gateway. As aplicações cliente chamam diretamente `auth-service` e `authorization-service`, mantendo validação JWT local por serviço.

### Estrutura Hexagonal (auth-service)

```
auth-service/
└── src/main/java/com/authplatform/auth/
    ├── domain/                          ← Núcleo: sem dependências externas
    │   ├── model/                       ← Entidades e Value Objects (Records)
    │   │   ├── User.java
    │   │   ├── Token.java
    │   │   ├── TokenPair.java
    │   │   └── TokenType.java
    │   ├── port/
    │   │   ├── in/                      ← Portas de entrada (Use Cases)
    │   │   │   ├── AuthenticateUserUseCase.java
    │   │   │   ├── RefreshTokenUseCase.java
    │   │   │   ├── ValidateTokenUseCase.java
    │   │   │   └── RevokeTokenUseCase.java
    │   │   └── out/                     ← Portas de saída (Driven Ports)
    │   │       ├── LdapUserPort.java
    │   │       ├── TokenStoragePort.java
    │   │       └── JwtPort.java
    │   ├── service/
    │   │   └── AuthenticationDomainService.java
    │   └── exception/
    ├── application/                     ← Orquestra Use Cases
    │   └── usecase/
    │       ├── AuthenticateUserUseCaseImpl.java
    │       ├── RefreshTokenUseCaseImpl.java
    │       ├── ValidateTokenUseCaseImpl.java
    │       └── RevokeTokenUseCaseImpl.java
    └── infrastructure/                  ← Adaptadores (Spring, Redis, AWS, etc.)
        ├── config/
        ├── ldap/       → LdapUserAdapter (implements LdapUserPort)
        ├── jwt/        → JwtTokenAdapter (implements JwtPort)
        ├── redis/      → RedisTokenStorageAdapter (implements TokenStoragePort)
        ├── aws/        → AwsSecretsLoader
        └── web/        → AuthController, DTOs, Filters
```

---

## Tecnologias

| Camada | Tecnologia | Versão |
|--------|-----------|--------|
| Linguagem | Java | 25 (LTS) |
| Framework | Spring Boot | 3.5.13 |
| LDAP | Spring LDAP Core + Spring Security LDAP | 3.x |
| JWT | JJWT (jjwt-api) | 0.13.0 |
| Chaves RSA | BouncyCastle | 1.84 |
| Cache/Tokens | Spring Data Redis + Lettuce | 3.x |
| RBAC Database | Spring Data JPA + MySQL | 3.x / 8.4 |
| Migrações | Flyway | 11.x |
| AWS Config | Spring Cloud AWS Parameter Store | 3.4.0 |
| AWS Secrets | Spring Cloud AWS Secrets Manager | 3.4.0 |
| Métricas | Micrometer + Prometheus | 1.15.x |
| Docs API | SpringDoc OpenAPI (Swagger UI) | 2.8.4 |
| Threads | Virtual Threads (Java 21+) | — |
| Container | eclipse-temurin:25-jre-alpine | — |
| Local AWS | LocalStack | 3.4 |
| Local LDAP | OpenLDAP (osixia/openldap) | 1.5.0 |

---

## Estrutura do Projeto

Cada microsserviço é um projeto Spring Boot **independente**, com seu próprio `pom.xml` e ciclo de build autônomo. Eles coexistem no mesmo repositório (monorepo), mas não possuem dependência de POM pai compartilhado.

```
auth-platform/
├── docker-compose.yml               ← Ambiente local completo
├── Makefile                         ← Atalhos de build e execução
├── .gitignore
├── README.md
│
├── docs/
│   ├── c4-architecture.drawio       ← Diagrama C4 (4 níveis)
│   ├── guia-modulos-e-testes.md     ← Guia detalhado de módulos e testes
│   └── insomnia-auth-platform.json  ← Collection Insomnia parametrizada
│
├── scripts/
│   ├── aws/
│   │   ├── localstack-init.sh       ← Bootstrap do LocalStack
│   │   └── setup-aws-prod.sh        ← Setup de produção AWS
│   ├── keys/
│   │   └── generate-rsa-keys.sh     ← Gera par RSA para JWT
│   └── ldap/
│       └── bootstrap.ldif           ← Usuários/grupos de teste OpenLDAP
│
├── auth-service/                    ← Microsserviço: Autenticação LDAP + JWT
│   ├── pom.xml                      ← Spring Boot parent independente
│   ├── Dockerfile
│   └── src/
│
├── authorization-service/           ← Microsserviço: RBAC + Registro de Aplicações
│   ├── pom.xml                      ← Spring Boot parent independente
│   ├── Dockerfile
│   └── src/
│
└── audit-service/                   ← Microsserviço: trilha de auditoria
    ├── pom.xml                      ← Spring Boot parent independente
    ├── Dockerfile
    └── src/
```

---

## Configuração Local

### Pré-requisitos

- **Docker** 24+ e **Docker Compose** V2
- **Rancher Desktop** (ou Docker Desktop)
- **Java 25** (JDK — para build local)
- **Maven 3.9+**
- **OpenSSL** (para geração de chaves RSA)
- **AWS CLI v2** e **Python 3** para executar o setup AWS de produção

### Passo a Passo

#### 1. Clonar e configurar

```bash
git clone <repositorio>
cd auth-platform

# Tornar scripts executáveis
chmod +x scripts/**/*.sh
```

#### 2. Gerar par de chaves RSA

```bash
./scripts/keys/generate-rsa-keys.sh --output-dir ./keys
```

Isso cria:
- `keys/private_key_pkcs8.pem` — chave privada em PKCS#8 (NUNCA commitar!)
- `keys/public_key.pem` — chave pública publicada no JWKS
- `keys/jwt-keys.secret.json` — payload para o secret do `auth-service`
- `keys/jwt-public-key.secret.json` — payload para o secret compartilhado de validação
- `keys/key-metadata.json` — metadados não sensíveis, incluindo `kid` e fingerprint SHA-256

> Para o fluxo `docker compose up -d`, este passo é opcional: o LocalStack executa `scripts/aws/localstack-init.sh` e gera um par de chaves local dentro do container se ainda não existir. O diretório `./keys` é útil para setup manual e para o script de produção.

#### 3. Build dos serviços

Cada serviço é construído de forma independente a partir de seu próprio diretório:

```bash
# Build de todos os serviços (via Makefile)
make build

# Ou individualmente
cd auth-service && mvn clean package -DskipTests
cd authorization-service && mvn clean package -DskipTests
```

#### 4. Subir o ambiente

```bash
docker compose up -d
# ou via Makefile:
make up
```

**Ordem de inicialização** (automática via `depends_on`):

```
1. mysql, redis, openldap, localstack      (infraestrutura)
2. auth-service, authorization-service     (serviços de negócio)
```

> O LocalStack inicializa automaticamente os segredos/parâmetros AWS via  
> `scripts/aws/localstack-init.sh` — incluindo um par de chaves RSA local para desenvolvimento.

#### 5. Verificar saúde dos serviços

```bash
# Status de todos os containers
docker compose ps

# Health checks individuais
curl http://localhost:8081/actuator/health    # Auth Service
curl http://localhost:8082/actuator/health    # Authorization Service
```

#### 6. Acessar interfaces

| Serviço | URL | Credenciais |
|---------|-----|-------------|
| Auth Service Swagger | http://localhost:8081/swagger-ui.html | — |
| Authorization Swagger | http://localhost:8082/swagger-ui.html | — |
| phpLDAPadmin | http://localhost:8090 | cn=admin,dc=authplatform,dc=com / admin |

### Execução Local Pela IDE

Se preferir rodar `AuthServiceApplication` ou `AuthorizationServiceApplication` pelo IntelliJ:

1. Suba apenas a infraestrutura:

```bash
docker compose up -d mysql redis openldap ldap-init localstack phpldapadmin
```

2. Garanta que os segredos locais existem no LocalStack:

```bash
docker exec auth-localstack bash /etc/localstack/init/ready.d/init-aws.sh
```

3. Execute a aplicação com o profile `local`.

No profile `local`, os defaults usam `localhost` para Redis, MySQL, OpenLDAP e LocalStack. Quando os serviços rodam dentro do Docker Compose, o `docker-compose.yml` sobrescreve esses valores com os nomes de serviço da rede Docker (`redis`, `mysql`, `openldap`, `localstack`).

---

## Configuração AWS

### Estrutura no Parameter Store

```
/config/auth-platform/
├── auth-service/
│   ├── auth.ldap.url                              = ldap://ad.empresa.com:389
│   ├── auth.ldap.base-dn                          = dc=empresa,dc=com
│   ├── auth.ldap.user-search-base                 = ou=Users
│   ├── auth.ldap.user-search-filter               = (sAMAccountName={0})
│   ├── auth.ldap.group-search-base                = ou=Groups
│   ├── auth.ldap.group-search-filter              = (member={0})
│   ├── auth.jwt.access-token-expiration-seconds   = 900
│   ├── auth.jwt.refresh-token-expiration-seconds  = 86400
│   ├── auth.jwt.issuer                            = https://auth.empresa.com
│   └── auth.jwt.audience                          = auth-platform-api
└── authorization-service/
    ├── spring.datasource.url                      = jdbc:mysql://db:3306/authplatform
    ├── auth.jwt.issuer                            = https://auth.empresa.com
    ├── auth.jwt.audience                          = auth-platform-api
    ├── auth.platform.issuer                       = https://auth.empresa.com
    ├── auth.platform.jwks-uri                     = https://auth.empresa.com/.well-known/jwks.json
    ├── auth.platform.introspection-url            = https://auth.empresa.com/api/v1/auth/validate
    ├── auth.platform.token-algorithm              = RS256
    └── auth.platform.token-audience               = auth-platform-api
```

### Estrutura no Secrets Manager

```
auth-platform/auth-service/jwt-keys
{
  "privateKey": "-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----",
  "publicKey":  "-----BEGIN PUBLIC KEY-----\n...\n-----END PUBLIC KEY-----"
}

auth-platform/shared/jwt-public-key
{
  "publicKey": "-----BEGIN PUBLIC KEY-----\n...\n-----END PUBLIC KEY-----"
}

auth-platform/auth-service/ldap-credentials
{
  "username": "cn=svc-auth,ou=Service Accounts,dc=empresa,dc=com",
  "password": "senha-segura"
}
```

### Setup de Produção

```bash
# Gerar chaves RSA localmente, uma vez por ambiente
./scripts/keys/generate-rsa-keys.sh --env dev
./scripts/keys/generate-rsa-keys.sh --env hom
./scripts/keys/generate-rsa-keys.sh --env prod

# Exemplo de envio do ambiente dev para AWS
export LDAP_URL="ldaps://ad-dev.empresa.com:636"
export LDAP_BASE_DN="dc=empresa,dc=com"
export LDAP_USER_SEARCH_BASE="ou=Users"
export LDAP_USER_SEARCH_FILTER="(sAMAccountName={0})"
export LDAP_GROUP_SEARCH_BASE="ou=Groups"
export LDAP_GROUP_SEARCH_FILTER="(member={0})"
export DB_URL="jdbc:mysql://db-dev.empresa.com:3306/authplatform?useSSL=true&serverTimezone=UTC"
export JWT_ISSUER="https://auth-dev.empresa.com"
export JWT_AUDIENCE="auth-platform-api"
export AUTH_JWKS_URI="https://auth-dev.empresa.com/.well-known/jwks.json"
export AUTH_INTROSPECTION_URL="https://auth-dev.empresa.com/api/v1/auth/validate"
export LDAP_CACHE_ENABLED="true"
export LDAP_CACHE_USER_TTL_SECONDS="300"
export LDAP_GROUP_SYNC_ENABLED="true"
export LDAP_GROUP_SYNC_INTERVAL_MS="300000"

# Configurar AWS (interativo)
./scripts/aws/setup-aws-prod.sh \
  --region us-east-1 \
  --env dev \
  --keys-dir ./keys/dev
```

O script cria ou atualiza os segredos no Secrets Manager usando JSON válido para chaves PEM multiline e grava os parâmetros LDAP/JWT/MySQL, rate limiting, cache LDAP e os metadados de onboarding (`issuer`, `jwksUri`, `introspectionUrl`, `tokenAudience`) no Parameter Store. Durante a execução, confirme os parâmetros sugeridos e informe a conta de serviço LDAP.

O token emitido pelo `auth-service` inclui `aud=auth-platform-api` por padrão. Esse valor deve ser igual ao `auth.jwt.audience` configurado nos serviços que validam o token.

Em ambientes que já possuam tokens emitidos antes deste contrato, planeje a implantação considerando a expiração dos tokens antigos. Tokens sem `aud` passam a ser rejeitados pelos serviços internos.

Se `dev`, `hom` e `prod` estiverem na mesma conta AWS, use namespace por ambiente para evitar sobrescrita de secrets e parâmetros:

```bash
./scripts/aws/setup-aws-prod.sh \
  --region us-east-1 \
  --env dev \
  --keys-dir ./keys/dev \
  --namespace-by-env
```

Nesse modo, os paths ficam assim:

```text
Secrets Manager:
  auth-platform/dev/auth-service/jwt-keys
  auth-platform/dev/shared/jwt-public-key

Parameter Store:
  /config/auth-platform/dev/auth-service/
  /config/auth-platform/dev/authorization-service/
```

Configure cada serviço do ambiente com o import correspondente:

```bash
# auth-service dev
SPRING_CONFIG_IMPORT=optional:aws-parameterstore:/config/auth-platform/dev/auth-service/

# authorization-service dev
SPRING_CONFIG_IMPORT=optional:aws-parameterstore:/config/auth-platform/dev/authorization-service/
```

### IAM Permissions necessárias

Os serviços precisam de permissões IAM (via EC2 instance profile, ECS Task Role ou IRSA):

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "secretsmanager:GetSecretValue",
        "secretsmanager:DescribeSecret"
      ],
      "Resource": "arn:aws:secretsmanager:*:*:secret:auth-platform/*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "ssm:GetParameter",
        "ssm:GetParameters",
        "ssm:GetParametersByPath"
      ],
      "Resource": "arn:aws:ssm:*:*:parameter/config/auth-platform/*"
    }
  ]
}
```

---

## Endpoints da API

Os endpoints de autenticação estão no `auth-service` (porta **8081**) e os de autorização no `authorization-service` (porta **8082**).

### Autenticação

#### `GET /.well-known/jwks.json`

Expõe a chave pública RSA ativa em formato JWKS para validação local de JWT por aplicações consumidoras.

**Response 200:**
```json
{
  "keys": [
    {
      "kty": "RSA",
      "use": "sig",
      "alg": "RS256",
      "kid": "uM4q...",
      "n": "base64url-modulus",
      "e": "AQAB"
    }
  ]
}
```

Use este endpoint como mecanismo padrão de auto-serviço para validação local. O acesso direto ao AWS Secrets Manager continua reservado aos serviços da plataforma e a integrações internas controladas.

#### `GET /.well-known/openid-configuration`

Expõe metadados de descoberta OIDC mínimos para clientes compatíveis encontrarem o `jwks_uri` a partir do `issuer`.

**Response 200:**
```json
{
  "issuer": "https://auth.empresa.com",
  "jwks_uri": "https://auth.empresa.com/.well-known/jwks.json",
  "id_token_signing_alg_values_supported": ["RS256"],
  "response_types_supported": ["token"],
  "subject_types_supported": ["public"],
  "claims_supported": ["sub", "iss", "aud", "exp", "iat", "jti", "roles", "groups", "applicationId", "ldapDomain", "type"]
}
```

---

#### `POST /api/v1/auth/login`

Autentica com credenciais LDAP/AD e retorna par de tokens JWT.

**Request:**
```json
{
  "username": "john.doe",
  "password": "senha123",
  "applicationId": "minha-aplicacao-a1b2c3d4",
  "ldapDomain": "default"
}
```

Neste endpoint, `applicationId` representa o identificador público da aplicação cliente (`clientId`) usado no fluxo de login. O campo `ldapDomain` é opcional; quando omitido, o `auth-service` usa `auth.ldap.default-domain`.

**Response 200:**
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiJ9...",
  "refresh_token": "eyJhbGciOiJSUzI1NiJ9...",
  "token_type": "Bearer",
  "expires_in": 900,
  "refresh_expires_in": 86400
}
```

**Exemplo curl:**
```bash
curl -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"john.doe","password":"password123","applicationId":"portal-xpto-a1b2c3d4","ldapDomain":"default"}'
```

---

#### `POST /api/v1/auth/refresh`

Renova o access token usando um refresh token válido.

**Request:**
```json
{
  "refresh_token": "eyJhbGciOiJSUzI1NiJ9...",
  "application_id": "minha-aplicacao"
}
```

**Response 200:** Mesmo formato do login.

---

#### `POST /api/v1/auth/validate` (Token Introspection — RFC 7662)

Valida um token e retorna suas informações. Utilizado por outros serviços.

**Request header:** `Authorization: Bearer <token>`

**Response 200 (token válido):**
```json
{
  "active": true,
  "sub": "john.doe",
  "iss": "auth-platform",
  "exp": 1745000000,
  "iat": 1744999100,
  "token_type": "ACCESS",
  "roles": ["ROLE_ENGINEERS", "ROLE_PLATFORM_TEAM"],
  "groups": ["engineers", "platform-team"],
  "application_id": "minha-aplicacao"
}
```

**Response 200 (token inválido):**
```json
{
  "active": false
}
```

---

#### `DELETE /api/v1/auth/logout`

Revoga o token atual.

**Request header:** `Authorization: Bearer <token>`

**Response:** `204 No Content`

---

### Autorização (RBAC)

#### `GET /api/v1/authorization/check`

Verifica se usuário tem permissão em um recurso.

**Headers:** `Authorization: Bearer <token>` (username extraído do JWT pelo serviço)

**Query params:** `applicationId`, `resource`, `action`

```bash
curl "http://localhost:8082/api/v1/authorization/check?applicationId=app-uuid-001&resource=orders&action=read" \
  -H "Authorization: Bearer <token>"
```

**Response:**
```json
{
  "allowed": true,
  "username": "john.doe",
  "applicationId": "app-uuid-001",
  "resource": "orders",
  "action": "read"
}
```

---

#### `GET /api/v1/authorization/permissions`

Lista todas as permissões e roles do usuário para uma aplicação.

```bash
curl "http://localhost:8082/api/v1/authorization/permissions?applicationId=app-uuid-001" \
  -H "Authorization: Bearer <token>"
```

---

### Estado Atual do RBAC

O `authorization-service` expõe o fluxo base de auto-serviço para registrar aplicações, criar roles, atribuir roles a usuários, gerenciar permissões e consultar autorização.

Para chamadas de RBAC, use o `id` retornado pelo `POST /api/v1/applications` como `applicationId`. O `clientId` retornado no cadastro é o identificador público da aplicação e pode ser usado no fluxo de autenticação/login.

#### `POST /api/v1/applications`

Registra uma nova aplicação (self-service). Este endpoint é público para permitir o bootstrap de aplicações consumidoras. Alteração e desativação de aplicações continuam exigindo JWT válido.

```json
{
  "name": "meu-servico",
  "description": "Serviço de pedidos",
  "ownerTeam": "team-commerce",
  "allowedRoles": ["ROLE_ENGINEERS", "ROLE_PLATFORM_TEAM"]
}
```

**Response 201:**
```json
{
  "id": "uuid",
  "name": "meu-servico",
  "clientId": "meu-servico-a1b2c3d4",
  "status": "ACTIVE",
  "issuer": "https://auth.empresa.com",
  "jwksUri": "https://auth.empresa.com/.well-known/jwks.json",
  "introspectionUrl": "https://auth.empresa.com/api/v1/auth/validate",
  "tokenAlgorithm": "RS256",
  "tokenAudience": "auth-platform-api",
  ...
}
```

Os campos `issuer`, `jwksUri`, `introspectionUrl`, `tokenAlgorithm` e `tokenAudience` fazem parte do onboarding auto-serviço da aplicação consumidora. Com eles, o time já sai do cadastro com tudo o que precisa para:

- autenticar usuários via `auth-service`
- validar JWT localmente via `jwksUri`
- usar introspection apenas quando precisar confirmar revogação imediata
- chamar o RBAC com o `id` da aplicação

#### Permissões e Role-Permissions

Permissões são cadastradas no catálogo RBAC e depois associadas a roles. Usuários que possuem a role passam a receber essas permissões em `GET /api/v1/authorization/permissions` e podem obter `allowed:true` em `/api/v1/authorization/check`.

Para carregar um cenário completo de teste no MySQL local:

```bash
./scripts/mysql/load-authorization-xpto.sh
```

O script cria a aplicação `portal-xpto`, permissões, roles e atribuições para `john.doe`, `jane.smith` e `admin.user`.

```bash
curl -X POST http://localhost:8082/api/v1/permissions \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "beneficios:read",
    "description": "Ler beneficios",
    "resource": "beneficios",
    "action": "read"
  }'

curl -X POST http://localhost:8082/api/v1/roles/{roleId}/permissions/{permissionId} \
  -H "Authorization: Bearer <token>"
```

| Método | Path | Função |
|--------|------|--------|
| POST | `/api/v1/permissions` | Criar permissão |
| GET | `/api/v1/permissions` | Listar permissões |
| GET | `/api/v1/permissions/{id}` | Buscar permissão |
| PUT | `/api/v1/permissions/{id}` | Atualizar permissão |
| DELETE | `/api/v1/permissions/{id}` | Remover permissão |
| POST | `/api/v1/roles/{roleId}/permissions/{permissionId}` | Associar permissão à role |
| DELETE | `/api/v1/roles/{roleId}/permissions/{permissionId}` | Remover permissão da role |

### Rate Limiting

Os serviços aplicam rate limiting distribuído usando Redis, sem depender de API Gateway. Por padrão, cada serviço limita:

- `120` requisições por IP a cada `60` segundos
- `600` requisições por `applicationId` a cada `60` segundos, quando `applicationId` estiver na query string ou no header `X-Application-Id`

Configurações:

| Propriedade | Default |
|-------------|---------|
| `auth.rate-limit.enabled` | `true` |
| `auth.rate-limit.window-seconds` | `60` |
| `auth.rate-limit.ip-limit` | `120` |
| `auth.rate-limit.application-limit` | `600` |

### Cache e Sincronização LDAP

O `auth-service` usa Redis para cachear atributos e grupos LDAP de usuários já resolvidos. A senha do usuário nunca é armazenada e a autenticação por senha continua sendo feita por LDAP bind a cada login.

Fluxo:

1. `POST /api/v1/auth/login` valida usuário/senha no LDAP/AD
2. Após bind válido, o serviço tenta buscar atributos e grupos no Redis
3. Em cache miss, consulta LDAP/AD, monta `groups`/`roles` e grava no Redis com TTL
4. Um job periódico reconsulta o LDAP/AD para usuários cacheados e atualiza grupos no Redis
5. Se Redis estiver indisponível, o serviço falha aberto para consulta LDAP direta

Configurações:

| Propriedade | Default |
|-------------|---------|
| `auth.ldap.cache.enabled` | `true` |
| `auth.ldap.cache.user-ttl-seconds` | `300` |
| `auth.ldap.cache.sync-enabled` | `true` |
| `auth.ldap.cache.sync-interval-ms` | `300000` |
| `auth.ldap.cache.sync-initial-delay-ms` | `60000` |

Esse desenho reduz round trips de leitura para LDAP/AD sem transformar Redis em fonte de autenticação. Mudanças críticas de grupo ainda dependem da combinação de TTL, sincronização periódica e expiração curta do access token.

### Múltiplos Domínios LDAP/AD

O `auth-service` suporta domínios LDAP/AD nomeados. O contrato é retrocompatível: se a aplicação consumidora não enviar `ldapDomain`, o domínio `auth.ldap.default-domain` é usado.

Exemplo de configuração:

```yaml
auth:
  ldap:
    default-domain: corp
    domains:
      corp:
        url: ldaps://ad-corp.empresa.com:636
        base-dn: dc=corp,dc=empresa,dc=com
        user-search-base: ou=Users
        username-attribute: sAMAccountName
        group-search-base: ou=Groups
        group-object-class: group
        group-member-attribute: member
        group-member-is-username: false
      partners:
        url: ldaps://ad-partners.empresa.com:636
        base-dn: dc=partners,dc=empresa,dc=com
        user-search-base: ou=Users
        username-attribute: sAMAccountName
```

As credenciais podem vir do mesmo secret LDAP:

```json
{
  "username": "cn=svc-auth,ou=Service Accounts,dc=corp,dc=empresa,dc=com",
  "password": "senha-default",
  "domains": {
    "corp": {
      "username": "cn=svc-auth,ou=Service Accounts,dc=corp,dc=empresa,dc=com",
      "password": "senha-corp"
    },
    "partners": {
      "username": "cn=svc-auth,ou=Service Accounts,dc=partners,dc=empresa,dc=com",
      "password": "senha-partners"
    }
  }
}
```

A aplicação pode escolher o domínio de duas formas:

- Enviar `ldapDomain` no `POST /api/v1/auth/login`
- Usar o formato `DOMINIO\usuario` no campo `username`

O domínio resolvido é persistido no claim `ldapDomain` do JWT e usado também no refresh token para buscar o usuário no mesmo diretório.

### Auditoria

O `audit-service` centraliza eventos relevantes da plataforma e expõe consulta paginada para investigação operacional, trilha de administração e evidências de segurança.

Eventos publicados:

- `auth-service`: login com sucesso/falha, refresh e logout
- `authorization-service`: cadastro/alteração/desativação de aplicações, criação/atribuição/revogação de roles, CRUD de permissões e associação role-permission

Endpoints:

| Método | Path | Função |
|--------|------|--------|
| POST | `/api/v1/audit/events` | Registrar evento de auditoria |
| GET | `/api/v1/audit/events` | Consultar eventos com filtros |
| GET | `/api/v1/audit/events/{id}` | Buscar evento por id |

Configurações dos serviços produtores:

| Propriedade | Default |
|-------------|---------|
| `auth.audit.enabled` | `false` |
| `auth.audit.base-url` | `http://localhost:8083` |
| `auth.audit.connect-timeout-ms` | `500` |
| `auth.audit.read-timeout-ms` | `1000` |

A publicação é **fail-open**: indisponibilidade do `audit-service` não bloqueia autenticação nem autorização. Em produção, habilite `AUDIT_ENABLED=true`, aponte `AUDIT_SERVICE_URL` para a URL interna do serviço e monitore falhas de publicação nos logs.

---

## Integração de Aplicações

### Modelo de Auto-Serviço

```
┌─────────────────────────────────────────────┐
│           Sua Aplicação                      │
│                                             │
│  1. Registrar via POST /api/v1/applications │
│  2. Receber clientId + issuer + jwksUri     │
│  3. Redirecionar login → Auth Platform      │
│  4. Validar token localmente via JWKS       │
│     ou via POST /api/v1/auth/validate       │
│  5. Verificar permissão via                 │
│     GET /api/v1/authorization/check         │
└─────────────────────────────────────────────┘
```

### Validação Local de Token (recomendado para performance)

Sua aplicação pode validar tokens **localmente** usando a chave pública RSA sem chamar o Auth Service. O fluxo recomendado é:

1. Registrar a aplicação via `POST /api/v1/applications`
2. Guardar o `jwksUri` retornado no onboarding
3. Buscar o JWKS público em `GET /.well-known/jwks.json`
4. Escolher a chave pelo `kid` presente no header do JWT
5. Validar assinatura, `iss`, `exp` e claims necessárias localmente

Exemplo simplificado:

```java
// 1. Buscar o JWKS do onboarding (jwksUri)
// 2. Resolver a RSA public key correspondente ao kid do token
// 3. Validar com JJWT:
Claims claims = Jwts.parser()
    .verifyWith(publicKey)   // RSA public key
    .requireIssuer("https://auth.empresa.com")
    .build()
    .parseSignedClaims(bearerToken)
    .getPayload();

String username = claims.getSubject();
List<String> roles = claims.get("roles", List.class);
```

> **Serviços internos da plataforma:** podem continuar lendo a chave pública do AWS Secrets Manager conforme o padrão já existente. O JWKS público foi adicionado para eliminar dependência de acesso direto a segredos no onboarding das aplicações consumidoras.

### Validação de JWT por Serviço

Cada serviço valida o `Authorization: Bearer <token>` diretamente. O username é extraído do claim `sub` do JWT — não são necessários headers propagados.

| Serviço | Operação com JWT |
|---------|-----------------|
| `auth-service` | Emite tokens (chave privada RSA) + valida para introspection |
| `authorization-service` | Valida tokens (chave pública RSA) para autenticar requisições |
| `audit-service` | Recebe eventos internos; não participa da validação JWT do fluxo de usuário |

> **Nota sobre gateway:** neste momento o produto não depende de API Gateway. Caso essa camada seja retomada no futuro, ela deve preservar a validação JWT local dos serviços como defesa em profundidade.

---

## Segurança

### Tokens JWT RS256

- **Algoritmo:** RSA-2048 com SHA-256 (RS256)
- **Header:** cada JWT emitido inclui `kid`, derivado da chave pública ativa, para seleção da chave no JWKS
- **Audience:** cada JWT emitido inclui `aud`, usado pelos serviços consumidores para rejeitar tokens emitidos para outra audiência
- **Access Token:** 15 minutos de validade (configurável)
- **Refresh Token:** 24 horas de validade, com **rotação** (cada uso emite novo par)
- **Revogação:** Tokens são blacklistados no Redis com TTL até a expiração

### Gerenciamento de Chaves

- Chaves RSA **nunca** são commitadas no repositório (`.gitignore`)
- Em produção, chaves ficam no **AWS Secrets Manager**
- O `auth-service` expõe a chave pública ativa em `GET /.well-known/jwks.json`
- Aplicações consumidoras devem preferir `jwksUri` do onboarding em vez de acesso direto ao Secrets Manager
- Rotação de chaves: gere novas chaves, atualize o Secrets Manager e reinicie os serviços
- **Estado atual da rotação:** o JWKS expõe a chave ativa com `kid`, mas ainda não há suporte a múltiplas chaves públicas simultâneas para rotação suave sem janela de transição. Essa evolução permanece no roadmap.

### Dados Sensíveis

| Dado | Onde Fica |
|------|-----------|
| Chave RSA privada | AWS Secrets Manager |
| Credenciais LDAP | AWS Secrets Manager |
| Configurações gerais | AWS Parameter Store |
| Senhas de infra (Redis, etc.) | Variáveis de ambiente |
| Passwords LDAP dos usuários | NUNCA armazenadas |

### TLS / LDAPS

Em produção, configure `auth.ldap.url=ldaps://ad.empresa.com:636` para conexão segura com o AD.

---

## Usuários de Teste (Ambiente Local)

| Username | Password | Grupos |
|----------|----------|--------|
| `john.doe` | `password123` | engineers |
| `jane.smith` | `password123` | engineers, platform-team |
| `admin.user` | `admin123` | administrators |

---

## Rancher Desktop — Dicas

Para usar com **Rancher Desktop** (Kubernetes local):

```bash
# Verificar contexto
kubectl config current-context

# O docker-compose funciona nativamente com Rancher Desktop
# Habilitando via: Preferences > Container Engine > dockerd (moby)

# Ou use nerdctl (Rancher Desktop default):
nerdctl compose up -d
```

Para deploy em Kubernetes local (Rancher):

```bash
# Converter docker-compose para manifests Kubernetes
kompose convert -f docker-compose.yml -o k8s/
kubectl apply -f k8s/
```

---

## Roadmap

### MVP — v1.0.0 (29/04/2026)

- [x] Autenticação LDAP/AD com emissão de JWT RS256
- [x] Refresh token com rotação
- [x] Token validation / introspection (RFC 7662)
- [x] Token revocation / logout
- [x] RBAC básico (registro de aplicações, roles e atribuições por usuário)
- [x] Auto-registro de aplicações
- [x] Validação JWT nativa em cada microsserviço (RS256)
- [x] AWS Parameter Store + Secrets Manager
- [x] Docker Compose para ambiente local
- [x] OpenAPI / Swagger UI
- [x] Microsserviços independentes (sem acoplamento de build)

### Fase 2 (v1.1.0)

- [x] Sincronização periódica de grupos LDAP → Redis
- [x] Auditoria completa (audit-service)
- [x] CRUD de permissões e associação de permissões a roles via API/admin
- [x] Rate limiting (por IP e por `applicationId`)
- [x] Suporte a múltiplos domínios LDAP/AD
- [x] Cache de usuários LDAP (reduzir round trips)

### Fase 3 (v2.0.0)

- [ ] Suporte OAuth2 / OpenID Connect (OIDC)
- [ ] Dashboard de administração (React)
- [ ] SSO entre aplicações
- [ ] API Keys para serviços M2M (machine-to-machine)
- [ ] Helm Chart para deploy em Kubernetes/Rancher
- [ ] Multi-tenant (múltiplas organizações)

---

## Contribuição

1. Clone o repositório
2. Crie uma feature branch: `git checkout -b feature/nome-da-feature`
3. Implemente seguindo a **arquitetura hexagonal**
4. Adicione testes unitários e de integração
5. Abra um Pull Request

### Convenções

- **Domain objects**: `record` (imutáveis)
- **Ports**: interfaces em `domain/port/{in,out}`
- **Adapters**: implementações em `infrastructure/`
- **Use Cases**: orquestração em `application/usecase`
- **Sem lógica de negócio na infra**: infra só adapta
- **Build independente**: cada serviço se constrói a partir de seu próprio diretório

---

## Suporte

Em caso de dúvidas ou problemas, abra uma issue no repositório.
