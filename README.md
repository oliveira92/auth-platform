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

> **Estado atual da solução:** o ambiente local não possui API Gateway nem service discovery. As aplicações cliente chamam diretamente `auth-service` na porta `8081` e `authorization-service` na porta `8082`. API Gateway/Kong/AWS API Gateway permanece no roadmap como ponto central opcional para uma fase posterior.

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
- `keys/private_key_pkcs8.pem` — chave privada (NUNCA commitar!)
- `keys/public_key.pem` — chave pública (pode ser compartilhada)

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
1. postgres, redis, openldap, localstack   (infraestrutura)
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
docker compose up -d postgres redis openldap ldap-init localstack phpldapadmin
```

2. Garanta que os segredos locais existem no LocalStack:

```bash
docker exec auth-localstack bash /etc/localstack/init/ready.d/init-aws.sh
```

3. Execute a aplicação com o profile `local`.

No profile `local`, os defaults usam `localhost` para Redis, PostgreSQL, OpenLDAP e LocalStack. Quando os serviços rodam dentro do Docker Compose, o `docker-compose.yml` sobrescreve esses valores com os nomes de serviço da rede Docker (`redis`, `postgres`, `openldap`, `localstack`).

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
│   └── auth.jwt.issuer                            = https://auth.empresa.com
└── authorization-service/
    ├── spring.datasource.url                      = jdbc:postgresql://db:5432/authplatform
    ├── auth.jwt.issuer                            = https://auth.empresa.com
    ├── auth.platform.issuer                       = https://auth.empresa.com
    ├── auth.platform.jwks-uri                     = https://auth.empresa.com/.well-known/jwks.json
    ├── auth.platform.introspection-url            = https://auth.empresa.com/api/v1/auth/validate
    └── auth.platform.token-algorithm              = RS256
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

# Opcional: informe antes para o script sugerir como default
export LDAP_URL="ldaps://ad.empresa.com:636"
export LDAP_BASE_DN="dc=empresa,dc=com"
export LDAP_USER_SEARCH_BASE="ou=Users"
export LDAP_USER_SEARCH_FILTER="(sAMAccountName={0})"
export LDAP_GROUP_SEARCH_BASE="ou=Groups"
export LDAP_GROUP_SEARCH_FILTER="(member={0})"
export DB_URL="jdbc:postgresql://db.empresa.com:5432/authplatform"
export JWT_ISSUER="https://auth.empresa.com"
export AUTH_JWKS_URI="https://auth.empresa.com/.well-known/jwks.json"
export AUTH_INTROSPECTION_URL="https://auth.empresa.com/api/v1/auth/validate"

# Configurar AWS (interativo)
./scripts/aws/setup-aws-prod.sh \
  --region us-east-1 \
  --env production \
  --keys-dir ./keys
```

O script cria ou atualiza os segredos no Secrets Manager usando JSON válido para chaves PEM multiline e grava os parâmetros LDAP/JWT/PostgreSQL e os metadados de onboarding (`issuer`, `jwksUri`, `introspectionUrl`) no Parameter Store. Durante a execução, confirme os parâmetros sugeridos e informe a conta de serviço LDAP.

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

---

#### `POST /api/v1/auth/login`

Autentica com credenciais LDAP/AD e retorna par de tokens JWT.

**Request:**
```json
{
  "username": "john.doe",
  "password": "senha123",
  "applicationId": "minha-aplicacao-a1b2c3d4"
}
```

Neste endpoint, `applicationId` representa o identificador público da aplicação cliente (`clientId`) usado no fluxo de login.

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
  -d '{"username":"john.doe","password":"password123","applicationId":"portal-xpto-a1b2c3d4"}'
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

O `authorization-service` já expõe o fluxo base de auto-serviço para registrar aplicações, criar roles, atribuir roles a usuários e consultar permissões. O modelo de dados também já possui `permissions` e `role_permissions`.

O que ainda não existe como API pública neste momento é o CRUD de permissões e a associação de permissões a roles. Por isso, `/api/v1/authorization/check` só retorna `allowed: true` quando as permissões já estiverem cadastradas e vinculadas à role por seed, migração, script administrativo ou implementação futura desses endpoints.

Para chamadas de RBAC, use o `id` retornado pelo `POST /api/v1/applications` como `applicationId`. O `clientId` retornado no cadastro é o identificador público da aplicação e pode ser usado no fluxo de autenticação/login.

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
  "issuer": "https://auth.empresa.com",
  "jwksUri": "https://auth.empresa.com/.well-known/jwks.json",
  "introspectionUrl": "https://auth.empresa.com/api/v1/auth/validate",
  "tokenAlgorithm": "RS256",
  ...
}
```

Os campos `issuer`, `jwksUri`, `introspectionUrl` e `tokenAlgorithm` fazem parte do onboarding auto-serviço da aplicação consumidora. Com eles, o time já sai do cadastro com tudo o que precisa para:

- autenticar usuários via `auth-service`
- validar JWT localmente via `jwksUri`
- usar introspection apenas quando precisar confirmar revogação imediata
- chamar o RBAC com o `id` da aplicação

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

> **Nota sobre API Gateway:** A adição de um API Gateway (AWS API Gateway, Kong, Nginx) na frente destes serviços está prevista para uma fase futura. Quando implementado, poderá centralizar a validação JWT e injetar headers de identidade.

---

## Segurança

### Tokens JWT RS256

- **Algoritmo:** RSA-2048 com SHA-256 (RS256)
- **Header:** cada JWT emitido inclui `kid`, derivado da chave pública ativa, para seleção da chave no JWKS
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

- [ ] Sincronização periódica de grupos LDAP → Redis
- [ ] Auditoria completa (audit-service)
- [ ] CRUD de permissões e associação de permissões a roles via API/admin
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
