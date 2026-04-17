# Auth Platform — Guia de Microsserviços, Testes e Integração

---

## Sumário

1. [Função de Cada Microsserviço](#1-função-de-cada-microsserviço)
2. [Como os Serviços se Comunicam](#2-como-os-serviços-se-comunicam)
3. [Como Testar e Homologar](#3-como-testar-e-homologar)
4. [Exemplo Prático: Portal XPTO](#4-exemplo-prático-portal-xpto)
5. [Collection Insomnia](#5-collection-insomnia)

---

## 1. Função de Cada Microsserviço

A plataforma é composta por **3 microsserviços independentes**. Cada um possui seu próprio `pom.xml`, ciclo de build, Dockerfile e pode ser desenvolvido, testado e implantado de forma autônoma.

---

### 1.1 `auth-service` — O Guardião de Identidade

**O que faz:** É o coração da plataforma. Tem uma responsabilidade única: **"quem é você?"**

Recebe usuário e senha, vai ao LDAP/AD validar as credenciais, busca os grupos do usuário e emite um par de tokens JWT assinados com a chave RSA privada.

**Fluxo interno detalhado:**

```
POST /api/v1/auth/login
{username: "john.doe", password: "senha123", applicationId: "portal-xpto"}
         ↓
AuthController
         ↓ chama
AuthenticateUserUseCaseImpl
         ↓ chama LdapUserPort (porta de saída)
         ↓
LdapUserAdapter (implementação)
         ↓ Spring LDAP
         ↓
OpenLDAP / Active Directory
         ↓ retorna User{username, email, groups: ["engineers"], roles: ["ROLE_ENGINEERS"]}
         ↓
AuthenticationDomainService
         ↓ cria Token{id=uuid, type=ACCESS, expires=+15min, roles=[...]}
         ↓ cria Token{id=uuid, type=REFRESH, expires=+24h, roles=[...]}
         ↓
RedisTokenStorageAdapter
         ↓ salva ambos no Redis com TTL
         ↓
JwtTokenAdapter (JJWT RS256)
         ↓ assina os tokens com chave RSA privada
         ↓
Retorna TokenPair{accessToken, refreshToken, expiresIn: 900}
```

**Porta:** 8081  
**Dependências:** LDAP/AD, Redis, AWS Secrets Manager (chaves RSA)

**Endpoints:**
| Método | Path | Função |
|--------|------|---------|
| POST | `/api/v1/auth/login` | Autenticar usuário |
| POST | `/api/v1/auth/refresh` | Renovar tokens |
| POST | `/api/v1/auth/validate` | Introspectar token |
| DELETE | `/api/v1/auth/logout` | Revogar token atual |
| DELETE | `/api/v1/auth/logout/all` | Revogar todos os tokens do usuário |

**Build independente:**
```bash
cd auth-service
mvn clean package -DskipTests
```

---

### 1.2 `authorization-service` — O Fiscal de Permissões

**O que faz:** Responde à pergunta **"o que você pode fazer?"**

Enquanto o auth-service sabe *quem* o usuário é (vem do LDAP), o authorization-service sabe *o que* esse usuário pode fazer em *qual* aplicação. Ele gerencia:

- **Aplicações**: registros das aplicações corporativas que usam a plataforma
- **Roles**: papéis dentro de cada aplicação (ex: `XPTO_ADMIN`, `XPTO_RH`)
- **Permissões**: ações específicas em recursos (ex: `folha:read`, `config:write`)
- **Atribuições**: qual usuário tem qual role em qual aplicação

**Exemplo de modelo de dados:**

```
Aplicação: portal-xpto
  └── Role: XPTO_USER
        └── Permission: beneficios:read
        └── Permission: perfil:read
  └── Role: XPTO_RH
        └── Permission: beneficios:read
        └── Permission: folha:read
        └── Permission: folha:write
  └── Role: XPTO_ADMIN
        └── Permission: (todas acima)
        └── Permission: configuracoes:read
        └── Permission: configuracoes:write

Atribuições:
  john.doe   → XPTO_USER  em portal-xpto
  jane.smith → XPTO_RH    em portal-xpto
  admin.user → XPTO_ADMIN em portal-xpto
```

**Porta:** 8082  
**Dependências:** PostgreSQL (dados persistentes de RBAC)

**Endpoints:**
| Método | Path | Função |
|--------|------|---------|
| POST | `/api/v1/applications` | Registrar aplicação (self-service) |
| PUT | `/api/v1/applications/{id}` | Atualizar aplicação |
| DELETE | `/api/v1/applications/{id}` | Desativar aplicação |
| POST | `/api/v1/roles` | Criar role |
| GET | `/api/v1/roles?applicationId=` | Listar roles da aplicação |
| POST | `/api/v1/roles/{id}/assignments` | Atribuir role a usuário |
| DELETE | `/api/v1/roles/{id}/assignments` | Revogar role de usuário |
| GET | `/api/v1/authorization/check` | Verificar permissão |
| GET | `/api/v1/authorization/permissions` | Listar permissões do usuário |

**Build independente:**
```bash
cd authorization-service
mvn clean package -DskipTests
```

---

### 1.3 `api-gateway` — O Portão de Entrada

**O que faz:** É o **único ponto de contato** das aplicações externas com a plataforma. Nenhum serviço downstream deve ser chamado diretamente pelas aplicações clientes.

Suas três funções:

**1. Validação de JWT (sem round-trip)**
```
Requisição chega com "Authorization: Bearer eyJ..."
   ↓
JwtAuthenticationFilter
   ↓ verifica assinatura RSA com a chave pública (em memória)
   ↓ verifica expiração
   ↓ se inválido → 401 Unauthorized (sem chamar nenhum serviço)
   ↓ se válido → extrai claims (username, roles, groups)
```

**2. Propagação de Identidade**
Após validar o token, o Gateway **injeta headers** na requisição antes de encaminhar:
```
X-Username: john.doe
X-User-Roles: ROLE_ENGINEERS,ROLE_PLATFORM_TEAM
X-User-Groups: engineers,platform-team
X-Application-Id: portal-xpto
X-Correlation-ID: uuid-rastreabilidade
```
Os serviços downstream recebem esses headers e *não precisam* saber de JWT.

**3. Roteamento por URL direta**
```
/api/v1/auth/**              → http://auth-service:8081
/api/v1/authorization/**     → http://authorization-service:8082
/api/v1/applications/**      → http://authorization-service:8082
/api/v1/roles/**             → http://authorization-service:8082
```

Os endereços dos serviços são configurados via variáveis de ambiente (`AUTH_SERVICE_HOST`, `AUTHZ_SERVICE_HOST`), com padrão baseado nos nomes dos containers Docker.

**Porta:** 8080 (único ponto de entrada)

**Build independente:**
```bash
cd api-gateway
mvn clean package -DskipTests
```

---

## 2. Como os Serviços se Comunicam

```
EXTERNO                INTERNO (auth-network Docker)
─────────              ──────────────────────────────────────────
Aplicação Cliente
  │
  │ HTTP :8080
  ▼
┌──────────────────────────────────────────┐
│  API Gateway (:8080)                     │
│                                          │
│  → JWT validado localmente (RSA pub key) │
│  → Injeta X-Username, X-User-Roles, etc  │
│  → Roteia por hostname Docker            │
│  → LocalStack/AWS (Parameter Store)      │
└──────┬──────────────────────┬────────────┘
       │                      │
       │ http://auth-service:8081
       │                      │ http://authorization-service:8082
       ▼                      ▼
┌────────────┐        ┌───────────────────┐
│auth-service│        │authorization-svc  │
│  :8081     │        │    :8082          │
│            │        │                   │
│ ←LDAP→    │        │ ←→ PostgreSQL     │
│ ←Redis→   │        │ ←→ AWS SM (chave) │
│ ←AWS SM→  │        │                   │
└────────────┘        └───────────────────┘
```

**Regra de ouro:** Aplicações clientes *só* falam com o Gateway na porta 8080. As portas 8081 e 8082 são internas à rede Docker e não devem ser expostas em produção.

**Ausência de service discovery:** Os serviços se comunicam diretamente por nome de container Docker (resolvido pelo DNS interno da `auth-network`). Não há necessidade de um registro de serviços centralizado — a infraestrutura Docker resolve os endereços de forma nativa.

---

## 3. Como Testar e Homologar

### 3.1 Verificação do Ambiente (Antes de Tudo)

```bash
# 1. Subir o ambiente
docker compose up -d

# 2. Aguardar inicialização (~60-90 segundos)
docker compose ps

# 3. Verificar que todos os serviços estão "healthy"
# Todos devem mostrar "(healthy)" ou "running"

# 4. Verificar logs de inicialização
docker compose logs auth-service | grep "Started AuthServiceApplication"
docker compose logs authorization-service | grep "Started AuthorizationServiceApplication"
docker compose logs api-gateway | grep "Started ApiGatewayApplication"
```

### 3.2 Checklist de Homologação — Autenticação

Execute na ordem. Use a collection Insomnia ou curl.

```
□ HC-01: Gateway responde /actuator/health com status UP
□ HC-02: Auth Service responde /actuator/health com status UP
□ HC-03: Authorization Service responde /actuator/health com status UP

□ AUTH-01: Login com credenciais válidas (john.doe) retorna 200 com access_token e refresh_token
□ AUTH-02: Login com senha errada retorna 401 com mensagem "Authentication Failed"
□ AUTH-03: Login com campos vazios retorna 400 com lista de erros de validação
□ AUTH-04: Validar token recém-emitido retorna {active: true, sub: "john.doe"}
□ AUTH-05: Validar token com string inválida retorna {active: false}
□ AUTH-06: Refresh token retorna NOVO par de tokens
□ AUTH-07: Usar o refresh token ANTERIOR após rotate retorna 401 (token revogado)
□ AUTH-08: Logout retorna 204
□ AUTH-09: Validar token após logout retorna {active: false}
□ AUTH-10: Access token expirado retorna {active: false} (aguardar 15min ou reduzir config para teste)
```

### 3.3 Checklist de Homologação — Autorização

```
□ AUTHZ-01: Registrar aplicação retorna 201 com clientId único
□ AUTHZ-02: Criar role XPTO_USER para a aplicação retorna 201
□ AUTHZ-03: Criar role XPTO_RH para a aplicação retorna 201
□ AUTHZ-04: Atribuir XPTO_USER a john.doe retorna 201
□ AUTHZ-05: Verificar beneficios:read para john.doe retorna {allowed: true}
□ AUTHZ-06: Verificar folha:read para john.doe retorna {allowed: false}
□ AUTHZ-07: Listar permissões de john.doe mostra [beneficios:read, perfil:read]
□ AUTHZ-08: Revogar role de john.doe retorna 204
□ AUTHZ-09: Verificar beneficios:read após revogação retorna {allowed: false}
```

### 3.4 Checklist de Segurança

```
□ SEC-01: Request sem Authorization header para endpoint protegido → 401
□ SEC-02: Request com token de outra aplicação (applicationId diferente) é aceito pelo Gateway
          mas o check de permissão retorna allowed:false
□ SEC-03: Manipular o payload do JWT (sem re-assinar) → Gateway rejeita (401)
□ SEC-04: Usar refresh token como access token → validate retorna {active: false} (type=REFRESH)
□ SEC-05: Token após logout/revogação → Gateway deixa passar (JWT é válido estruturalmente),
          mas validate retorna {active: false}
          IMPORTANTE: O Gateway valida apenas assinatura e expiração. Para blacklist,
          o serviço backend deve chamar /validate.
```

> **Nota sobre SEC-05:** O Gateway não chama o Redis para verificar blacklist a cada requisição — isso seria um gargalo. A validação de revogação deve ser feita pelos serviços que precisam dessa garantia via `/api/v1/auth/validate`. Esta é uma troca consciente: latência zero no Gateway versus verificação de revogação onde necessário.

### 3.5 Teste de Carga (Pré-Produção)

```bash
# Instalar k6 (https://k6.io)
brew install k6

# Executar teste básico de carga no endpoint de login
# (100 usuários simultâneos por 30 segundos)
k6 run - <<'EOF'
import http from 'k6/http';
import { check } from 'k6';

export const options = {
  vus: 100,
  duration: '30s',
};

export default function () {
  const payload = JSON.stringify({
    username: 'john.doe',
    password: 'password123',
    applicationId: 'load-test'
  });

  const params = { headers: { 'Content-Type': 'application/json' } };
  const res = http.post('http://localhost:8080/api/v1/auth/login', payload, params);

  check(res, {
    'status is 200': (r) => r.status === 200,
    'has access_token': (r) => JSON.parse(r.body).access_token !== undefined,
    'response time < 500ms': (r) => r.timings.duration < 500,
  });
}
EOF
```

**Métricas esperadas para MVP:**
- p95 de tempo de resposta do login: < 500ms
- Taxa de erro: < 1%
- Throughput mínimo: 50 req/s por instância

### 3.6 Verificação dos Logs

```bash
# Monitorar logs em tempo real
docker compose logs -f auth-service

# Filtrar apenas erros
docker compose logs auth-service | grep -E "ERROR|WARN"

# Verificar se o correlationId aparece nos logs
docker compose logs auth-service | grep "correlationId"

# Verificar se LDAP está sendo consultado
docker compose logs auth-service | grep "LDAP"
```

---

## 4. Exemplo Prático: Portal XPTO

### Cenário

O **Portal XPTO** é um sistema interno corporativo com três áreas:

| Área | Recurso | Ação | Quem Acessa |
|------|---------|------|-------------|
| Meus Benefícios | `beneficios` | `read` | Todos os funcionários |
| Folha de Pagamento | `folha` | `read`, `write` | Equipe de RH |
| Configurações | `configuracoes` | `read`, `write` | Administradores |

Usuários de teste mapeados:
- **john.doe** (engenheiro) → acessa só Benefícios
- **jane.smith** (time de plataforma) → acessa Benefícios
- **admin.user** (administrador) → acessa tudo

---

### 4.1 Onboarding do Portal XPTO (Uma Única Vez)

O time responsável pelo Portal XPTO faz o onboarding por conta própria, sem precisar de nenhuma equipe de plataforma.

**Passo 1 — Registrar a aplicação:**
```bash
curl -X POST http://localhost:8080/api/v1/applications \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{
    "name": "portal-xpto",
    "description": "Portal corporativo de RH e benefícios",
    "ownerTeam": "time-rh-xpto",
    "allowedRoles": ["ROLE_ENGINEERS", "ROLE_ADMINISTRATORS"]
  }'

# Resposta:
# { "id": "app-uuid-001", "clientId": "portal-xpto-a1b2c3d4", "status": "ACTIVE" }
```

**Passo 2 — Criar as roles da aplicação:**
```bash
# Role para usuário comum
curl -X POST http://localhost:8080/api/v1/roles \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "XPTO_USER",
    "description": "Acesso básico ao portal",
    "applicationId": "app-uuid-001"
  }'

# Role para equipe de RH
curl -X POST http://localhost:8080/api/v1/roles \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{ "name": "XPTO_RH", "description": "Acesso RH", "applicationId": "app-uuid-001" }'
```

**Passo 3 — Atribuir roles aos usuários:**
```bash
curl -X POST http://localhost:8080/api/v1/roles/role-uuid-xpto-user/assignments \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "john.doe",
    "applicationId": "app-uuid-001"
  }'
```

---

### 4.2 Fluxo de Login do Usuário

```
NAVEGADOR                PORTAL XPTO (Backend)          AUTH PLATFORM
    │                           │                              │
    │  Clica em "Entrar"        │                              │
    ├──────────────────────────►│                              │
    │                           │                              │
    │  Form: usuário + senha    │                              │
    ├──────────────────────────►│                              │
    │                           │  POST /api/v1/auth/login     │
    │                           │  {username, password,        │
    │                           │   applicationId:"xpto"}      │
    │                           ├─────────────────────────────►│
    │                           │                              │ LDAP bind
    │                           │                              │ busca grupos
    │                           │                              │ emite JWT RS256
    │                           │  200 {access_token,          │
    │                           │       refresh_token}         │
    │                           │◄─────────────────────────────┤
    │                           │                              │
    │                           │ (armazena tokens)            │
    │  Redireciona: /dashboard  │                              │
    │◄──────────────────────────┤                              │
```

**Código de exemplo (JavaScript/Node.js):**

```javascript
// portal-xpto/auth.service.js
async function login(username, password) {
  const response = await fetch('https://auth-platform.empresa.com/api/v1/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      username,
      password,
      applicationId: 'portal-xpto'
    })
  });

  if (!response.ok) {
    throw new Error('Credenciais inválidas');
  }

  const { access_token, refresh_token, expires_in } = await response.json();

  // Armazena em memória (access_token) e cookie httpOnly (refresh_token)
  tokenStore.setAccessToken(access_token);
  tokenStore.setRefreshToken(refresh_token);
  tokenStore.setExpiresAt(Date.now() + (expires_in * 1000));

  return true;
}
```

---

### 4.3 Fluxo de Acesso a Recurso Protegido

Quando o usuário acessa uma página que chama uma API do Portal XPTO:

```
NAVEGADOR               PORTAL XPTO API         AUTH PLATFORM
    │                        │                        │
    │  GET /api/folha         │                        │
    │  Authorization: Bearer  │                        │
    │  <access_token>         │                        │
    ├───────────────────────►│                        │
    │                        │                        │
    │                        │ → API Gateway valida JWT (local, sem chamada HTTP)
    │                        │ → Gateway injeta headers:
    │                        │   X-Username: john.doe
    │                        │   X-User-Roles: ROLE_ENGINEERS
    │                        │                        │
    │                        │ (Gateway encaminha para o backend do Portal)
    │                        │                        │
    │                        │ Backend verifica:      │
    │                        │ X-User-Roles contém    │
    │                        │ XPTO_RH?               │
    │                        │ → NÃO → 403 Forbidden  │
    │  403 Forbidden          │                        │
    │◄───────────────────────┤                        │
```

**Código de exemplo (Java/Spring — backend do Portal XPTO):**

```java
// portal-xpto/FolhaController.java
@RestController
@RequestMapping("/api/folha")
public class FolhaController {

    @GetMapping
    public ResponseEntity<?> getFolha(
            @RequestHeader("X-Username") String username,
            @RequestHeader("X-User-Roles") String roles) {

        List<String> userRoles = Arrays.asList(roles.split(","));

        if (!userRoles.contains("ROLE_XPTO_RH") && !userRoles.contains("ROLE_ADMINISTRATORS")) {
            return ResponseEntity.status(403)
                .body(Map.of("error", "Acesso negado: role XPTO_RH necessária"));
        }

        return ResponseEntity.ok(folhaService.getFolha(username));
    }
}
```

**Alternativa: usar o authorization-service para verificação granular:**

```java
@GetMapping
public ResponseEntity<?> getFolha(@RequestHeader("X-Username") String username) {
    
    boolean podeAcessar = authorizationClient.checkPermission(
        username, "portal-xpto", "folha", "read"
    );

    if (!podeAcessar) {
        return ResponseEntity.status(403).build();
    }

    return ResponseEntity.ok(folhaService.getFolha(username));
}
```

---

### 4.4 Renovação Automática de Token

```javascript
// portal-xpto/token-refresh.interceptor.js
async function requestWithAutoRefresh(url, options) {
  if (tokenStore.expiresAt - Date.now() < 60_000) {
    await refreshToken();
  }

  return fetch(url, {
    ...options,
    headers: {
      ...options.headers,
      'Authorization': `Bearer ${tokenStore.accessToken}`
    }
  });
}

async function refreshToken() {
  const response = await fetch('/api/v1/auth/refresh', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      refresh_token: tokenStore.refreshToken,
      application_id: 'portal-xpto'
    })
  });

  if (!response.ok) {
    redirectToLogin();
    return;
  }

  const { access_token, refresh_token, expires_in } = await response.json();
  tokenStore.setAccessToken(access_token);
  tokenStore.setRefreshToken(refresh_token);
  tokenStore.setExpiresAt(Date.now() + (expires_in * 1000));
}
```

---

### 4.5 Menu Dinâmico Baseado em Permissões

```javascript
// portal-xpto/menu.component.js
async function buildMenu(accessToken) {
  const response = await fetch(
    '/api/v1/authorization/permissions?applicationId=portal-xpto',
    { headers: { 'Authorization': `Bearer ${accessToken}` } }
  );

  const { permissions } = await response.json();

  return [
    {
      label: 'Meus Benefícios',
      path: '/beneficios',
      visible: permissions.includes('beneficios:read')
    },
    {
      label: 'Folha de Pagamento',
      path: '/folha',
      visible: permissions.includes('folha:read')
    },
    {
      label: 'Configurações',
      path: '/config',
      visible: permissions.includes('configuracoes:read')
    }
  ].filter(item => item.visible);
}
```

---

### 4.6 Fluxo Completo do Portal XPTO — Diagrama

```
┌─────────────────────────────────────────────────────────────┐
│                      PORTAL XPTO                            │
│                                                             │
│  Tela de Login                                              │
│  ┌─────────────┐                                            │
│  │ Usuário: __ │  → POST /api/v1/auth/login                 │
│  │ Senha:   __ │    (LDAP valida, JWT emitido)              │
│  └─────────────┘                                            │
│         ↓ tokens recebidos                                  │
│                                                             │
│  Dashboard                                                  │
│  ┌──────────────────────────────────────────┐               │
│  │ Menu (montado com permissões do usuário) │               │
│  │                                          │               │
│  │ [✓] Meus Benefícios                      │               │
│  │ [✗] Folha de Pagamento (oculto p/ user)  │ ← GET /authorization/permissions
│  │ [✗] Configurações (oculto p/ user)       │               │
│  └──────────────────────────────────────────┘               │
│         ↓ usuário clica em Benefícios                       │
│                                                             │
│  API Request                                                │
│  GET /api/beneficios                                        │
│  Authorization: Bearer <access_token>                       │
│         ↓                                                   │
│  Gateway valida JWT → injeta X-Username, X-User-Roles       │
│         ↓                                                   │
│  Backend do Portal recebe headers e retorna dados           │
│                                                             │
│  Antes do token expirar (< 60s):                           │
│  POST /api/v1/auth/refresh → novo par de tokens (silencioso)│
│                                                             │
│  Usuário clica em Sair:                                     │
│  DELETE /api/v1/auth/logout → token revogado                │
│  → Redireciona para tela de login                           │
└─────────────────────────────────────────────────────────────┘
```

---

## 5. Collection Insomnia

O arquivo [`docs/insomnia-auth-platform.json`](insomnia-auth-platform.json) contém a collection completa.

### Como importar no Insomnia

1. Abra o **Insomnia** (versão 2023.x ou superior)
2. Clique em **Import** (ícone de pasta ou menu File → Import)
3. Selecione o arquivo `docs/insomnia-auth-platform.json`
4. Selecione **Import to Collection**
5. A collection "Auth Platform - Corporate Auth" aparecerá

### Como configurar os ambientes

1. Clique no dropdown de ambiente (canto superior esquerdo)
2. Selecione **"Local - Docker Compose"** para testes locais
3. As variáveis `base_url`, `username`, `password` já estão preenchidas

### Como usar a collection

**Fluxo rápido de validação:**
1. Execute `Health Checks / API Gateway` — deve retornar `{"status": "UP"}`
2. Execute `Autenticacao / Login (LDAP/AD)` — copie o `access_token` para a variável de ambiente
3. Execute `Autenticacao / Validar Token` — deve retornar `{"active": true}`
4. Execute o fluxo completo `Portal XPTO - Fluxo Completo` na ordem dos passos XPTO-01 a XPTO-09

### Pastas da Collection

| Pasta | Conteúdo |
|-------|----------|
| `Health Checks` | 3 requests de verificação de saúde (Gateway, Auth, Authorization) |
| `Autenticacao` | Login, Refresh, Validate, Logout (+ casos negativos) |
| `Autorizacao (RBAC)` | Check permission, listar permissões |
| `Gestao de Aplicacoes` | Registrar, atualizar, desativar aplicações |
| `Gestao de Roles` | Criar roles, atribuir/revogar para usuários |
| `Portal XPTO - Fluxo Completo` | 9 passos end-to-end do caso de uso real |
| `Documentacao (Swagger)` | Links para as UIs de documentação |

### Variáveis de Ambiente

| Variável | Descrição | Exemplo |
|----------|-----------|---------|
| `base_url` | URL do API Gateway | `http://localhost:8080` |
| `auth_url` | URL direta do Auth Service | `http://localhost:8081` |
| `authz_url` | URL direta do Authorization Service | `http://localhost:8082` |
| `access_token` | JWT access token (atualizar após login) | `eyJ...` |
| `refresh_token` | JWT refresh token (atualizar após login/refresh) | `eyJ...` |
| `username` | Usuário LDAP para testes | `john.doe` |
| `password` | Senha do usuário LDAP | `password123` |
| `app_id` | ID da aplicação registrada | `portal-xpto` |
| `role_id` | ID de uma role (após criação) | `uuid` |
