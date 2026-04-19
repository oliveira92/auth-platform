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
- **Cloud-native** — configurações no AWS Parameter Store, segredos no Secrets Manager

### Fluxo de Autenticação

```
Usuário → Aplicação → Auth Service (8081)
                           ↓ LDAP BIND + search
                      LDAP / Active Directory
                           ↓ User + Groups
                      JWT Token (access + refresh) RS256
                           ↓
                      Redis (token store / blacklist)
```

O token JWT emitido é usado diretamente nas chamadas ao `authorization-service (8082)`, que valida o Bearer token de forma local usando a chave pública RSA.

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
| RBAC Database | Spring Data JPA + PostgreSQL | 3.x / 16 |
| Migrações | Flyway | 10.x |
| AWS Config | Spring Cloud AWS Parameter Store | 3.4.0 |
| AWS Secrets | Spring Cloud AWS Secrets Manager | 3.4.0 |
| Métricas | Micrometer + Prometheus | 1.15.x |
| Docs API | SpringDoc OpenAPI (Swagger UI) | 3.0.3 |
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
└── authorization-service/           ← Microsserviço: RBAC + Registro de Aplicações
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
- `keys/private_key_pkcs8.pem` — chave privada (NUNCA commitar!)
- `keys/public_key.pem` — chave pública (pode ser compartilhada)

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
1. postgres, redis, openldap, localstack   (infraestrutura)
2. auth-service, authorization-service     (serviços de negócio)
```

> O LocalStack inicializa automaticamente os segredos/parâmetros AWS via  
> `scripts/aws/localstack-init.sh` — incluindo as chaves RSA geradas!

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
│   ├── auth.jwt.access-token-expiration-seconds   = 900
│   └── auth.jwt.refresh-token-expiration-seconds  = 86400
└── authorization-service/
    └── spring.datasource.url                      = jdbc:postgresql://db:5432/authplatform
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
# Gerar chaves RSA
./scripts/keys/generate-rsa-keys.sh --output-dir ./keys

# Configurar AWS (interactive)
./scripts/aws/setup-aws-prod.sh \
  --region us-east-1 \
  --env production \
  --keys-dir ./keys
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

#### `POST /api/v1/auth/login`

Autentica com credenciais LDAP/AD e retorna par de tokens JWT.

**Request:**
```json
{
  "username": "john.doe",
  "password": "senha123",
  "applicationId": "minha-aplicacao"
}
```

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
  -d '{"username":"john.doe","password":"password123","applicationId":"test-app"}'
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
curl "http://localhost:8082/api/v1/authorization/check?applicationId=my-app&resource=orders&action=read" \
  -H "Authorization: Bearer <token>"
```

**Response:**
```json
{
  "allowed": true,
  "username": "john.doe",
  "applicationId": "my-app",
  "resource": "orders",
  "action": "read"
}
```

---

#### `GET /api/v1/authorization/permissions`

Lista todas as permissões e roles do usuário para uma aplicação.

```bash
curl "http://localhost:8082/api/v1/authorization/permissions?applicationId=my-app" \
  -H "Authorization: Bearer <token>"
```

---

#### `POST /api/v1/applications`

Registra uma nova aplicação (self-service).

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
  ...
}
```

---

## Integração de Aplicações

### Modelo de Auto-Serviço

```
┌─────────────────────────────────────────────┐
│           Sua Aplicação                      │
│                                             │
│  1. Registrar via POST /api/v1/applications │
│  2. Redirecionar login → Auth Platform      │
│  3. Validar token via GET /api/v1/auth/     │
│     validate (ou localmente com public key) │
│  4. Verificar permissão via               │
│     GET /api/v1/authorization/check        │
└─────────────────────────────────────────────┘
```

### Validação Local de Token (recomendado para performance)

Sua aplicação pode validar tokens **localmente** usando a chave pública RSA sem chamar o Auth Service:

```java
// 1. Obter public key do AWS Secrets Manager: auth-platform/shared/jwt-public-key
// 2. Validar com JJWT:
Claims claims = Jwts.parser()
    .verifyWith(publicKey)   // RSA public key
    .build()
    .parseSignedClaims(bearerToken)
    .getPayload();

String username = claims.getSubject();
List<String> roles = claims.get("roles", List.class);
```

### Validação de JWT por Serviço

Cada serviço valida o `Authorization: Bearer <token>` diretamente. O username é extraído do claim `sub` do JWT — não são necessários headers propagados.

| Serviço | Operação com JWT |
|---------|-----------------|
| `auth-service` | Emite tokens (chave privada RSA) + valida para introspection |
| `authorization-service` | Valida tokens (chave pública RSA) para autenticar requisições |

> **Nota sobre API Gateway:** A adição de um API Gateway (AWS API Gateway, Kong, Nginx) na frente destes serviços está prevista para uma fase futura. Quando implementado, poderá centralizar a validação JWT e injetar headers de identidade.

---

## Segurança

### Tokens JWT RS256

- **Algoritmo:** RSA-2048 com SHA-256 (RS256)
- **Access Token:** 15 minutos de validade (configurável)
- **Refresh Token:** 24 horas de validade, com **rotação** (cada uso emite novo par)
- **Revogação:** Tokens são blacklistados no Redis com TTL até a expiração

### Gerenciamento de Chaves

- Chaves RSA **nunca** são commitadas no repositório (`.gitignore`)
- Em produção, chaves ficam no **AWS Secrets Manager**
- Rotação de chaves: gere novas chaves, atualize o Secrets Manager e reinicie os serviços
- Chave pública pode ser distribuída para validação local (sem chamada ao Auth Service)

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
- [x] RBAC básico (roles e permissões por aplicação)
- [x] Auto-registro de aplicações
- [x] Validação JWT nativa em cada microsserviço (RS256)
- [x] AWS Parameter Store + Secrets Manager
- [x] Docker Compose para ambiente local
- [x] OpenAPI / Swagger UI
- [x] Microsserviços independentes (sem acoplamento de build)

### Fase 2 (v1.1.0)

- [ ] Sincronização periódica de grupos LDAP → Redis
- [ ] Auditoria completa (audit-service)
- [ ] API Gateway (AWS API Gateway / Kong) como ponto de entrada centralizado
- [ ] Rate limiting (por IP e por `applicationId`)
- [ ] Suporte a múltiplos domínios LDAP/AD
- [ ] Cache de usuários LDAP (reduzir round trips)

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
