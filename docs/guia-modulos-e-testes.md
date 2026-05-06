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

> **Nota arquitetural:** neste momento a plataforma não utiliza API Gateway. As aplicações consumidoras chamam diretamente `auth-service` e `authorization-service`, mantendo validação JWT local por serviço.

---

### 1.1 `auth-service` — O Guardião de Identidade

**O que faz:** É o coração da plataforma. Tem uma responsabilidade única: **"quem é você?"**

Recebe usuário e senha, vai ao LDAP/AD validar as credenciais, busca os grupos do usuário e emite um par de tokens JWT assinados com a chave RSA privada.

**Fluxo interno detalhado:**

```
POST /api/v1/auth/login
{username: "john.doe", password: "senha123", applicationId: "portal-xpto-a1b2c3d4"}
         ↓
AuthController
         ↓ chama
AuthenticateUserUseCaseImpl
         ↓ chama LdapUserPort (porta de saída)
         ↓
LdapUserAdapter (implementação)
         ↓ LDAP bind obrigatório
         ↓
OpenLDAP / Active Directory
         ↓ senha válida
Redis LDAP User Cache
         ↓ hit: atributos/grupos cacheados
         ↓ miss: Spring LDAP search
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

**Cache LDAP/AD na Fase 2:** após o bind LDAP/AD confirmar a senha, atributos e grupos do usuário podem vir do Redis. Em cache miss, o `auth-service` consulta LDAP/AD e grava o resultado com TTL. Um job periódico reconsulta o diretório para usuários cacheados e atualiza grupos no Redis. Redis nunca armazena senha e nunca substitui o bind de autenticação.

**Múltiplos domínios LDAP/AD na Fase 2:** o login aceita `ldapDomain` opcional. Quando omitido, o serviço usa `auth.ldap.default-domain`; também é possível inferir o domínio com usuário no formato `DOMINIO\usuario`. O domínio resolvido entra no claim `ldapDomain` do JWT e é reutilizado no refresh token para buscar o usuário no mesmo diretório.

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
**Dependências:** MySQL (dados persistentes de RBAC), AWS Secrets Manager/LocalStack (chave pública RSA para validação de JWT)

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
| POST | `/api/v1/permissions` | Criar permissão |
| GET | `/api/v1/permissions` | Listar permissões |
| PUT | `/api/v1/permissions/{id}` | Atualizar permissão |
| DELETE | `/api/v1/permissions/{id}` | Remover permissão |
| POST | `/api/v1/roles/{roleId}/permissions/{permissionId}` | Associar permissão a role |
| DELETE | `/api/v1/roles/{roleId}/permissions/{permissionId}` | Remover permissão da role |
| GET | `/api/v1/authorization/check` | Verificar permissão |
| GET | `/api/v1/authorization/permissions` | Listar permissões do usuário |

> **Fase 2:** permissões e associação role-permission possuem API administrativa. Testes com `allowed:true` podem ser feitos sem seed manual, criando a permissão e vinculando-a à role.

> **Fase 2:** os serviços aplicam rate limiting distribuído via Redis por IP e por `applicationId`, configurado por `auth.rate-limit.*`.

**Build independente:**
```bash
cd authorization-service
mvn clean package -DskipTests
```

---

### 1.3 `audit-service` — A Trilha de Evidências

**O que faz:** Centraliza eventos relevantes da plataforma para investigação, conformidade e rastreabilidade operacional.

Ele recebe eventos publicados de forma fail-open por `auth-service` e `authorization-service`. Se o audit-service ficar indisponível, login e autorização continuam funcionando, mas os produtores registram warning para observabilidade.

**Eventos cobertos na Fase 2:**

- Login com sucesso/falha, refresh e logout
- Cadastro, alteração e desativação de aplicações
- Criação de roles e atribuição/revogação de roles para usuários
- CRUD de permissões e associação/revogação de permissões em roles

**Porta:** 8083

**Dependências:** MySQL (tabela `audit_events`)

**Endpoints:**
| Método | Path | Função |
|--------|------|--------|
| POST | `/api/v1/audit/events` | Registrar evento |
| GET | `/api/v1/audit/events` | Consultar eventos com filtros |
| GET | `/api/v1/audit/events/{id}` | Buscar evento por id |

**Build independente:**
```bash
cd audit-service
mvn clean package -DskipTests
```

---

## 2. Como os Serviços se Comunicam

```
EXTERNO                INTERNO (auth-network Docker)
─────────              ──────────────────────────────────────────
Aplicação Cliente
  │
  ├── HTTP :8081 (autenticação)
  │   ▼
  │  ┌────────────────────────────────┐
  │  │  auth-service (:8081)          │
  │  │                                │
  │  │  → JWT validado na requisição  │
  │  │  → Emite/valida tokens RS256   │
  │  │  ←→ LDAP/AD                   │
  │  │  ←→ Redis (tokens/cache/rate) │
  │  │  → audit-service              │
  │  │  ←→ AWS Secrets Manager       │
  │  └────────────────────────────────┘
  │
  └── HTTP :8082 (autorização RBAC)
      ▼
     ┌────────────────────────────────┐
     │  authorization-service (:8082) │
     │                                │
     │  → JWT validado na requisição  │
     │  → Verifica permissões RBAC    │
     │  ←→ MySQL (roles/perms)       │
     │  → audit-service              │
     │  ←→ AWS Secrets Manager       │
     └────────────────────────────────┘
  │
  └── HTTP :8083 (auditoria)
      ▼
     ┌────────────────────────────────┐
     │  audit-service (:8083)         │
     │                                │
     │  → Registra audit_events       │
     │  ←→ MySQL                     │
     └────────────────────────────────┘
```

**Validação de JWT por serviço:** Cada serviço valida o token `Authorization: Bearer` diretamente usando a chave pública RSA. O `auth-service` usa a chave privada para emitir tokens; o `authorization-service` usa apenas a chave pública para validação.

**Service discovery:** os serviços são acessados diretamente por DNS/host e porta do ambiente. Neste momento não há camada de API Gateway no fluxo da solução.

**Rate limiting:** ambos os serviços aplicam limite distribuído em Redis por IP e por `applicationId` quando o identificador estiver disponível na query string ou no header `X-Application-Id`. Quando o limite é excedido, a resposta é `429 Too Many Requests`.

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
docker compose logs audit-service | grep "Started AuditServiceApplication"
```

### 3.2 Checklist de Homologação — Autenticação

Execute na ordem. Use a collection Insomnia ou curl.

```
□ HC-01: Auth Service responde /actuator/health com status UP  (http://localhost:8081/actuator/health)
□ HC-02: Authorization Service responde /actuator/health com status UP  (http://localhost:8082/actuator/health)
□ HC-03: Audit Service responde /actuator/health com status UP  (http://localhost:8083/actuator/health)

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

Antes de testar a autorização ponta a ponta, você pode carregar um cenário pronto no MySQL:

```bash
./scripts/mysql/load-authorization-xpto.sh
```

Esse comando executa `scripts/mysql/seed-authorization-xpto.sql` e cria a aplicação `portal-xpto`, roles, permissões e vínculos de usuários.

Dados principais:

| Item | Valor |
|------|-------|
| Application ID | Em banco novo: `11111111-1111-1111-1111-111111111111`; se a aplicação já existir, use o `id` exibido no output do script |
| Client ID para login | `portal-xpto-a1b2c3d4` |
| Usuário comum | `john.doe` → `XPTO_USER` |
| Usuário RH | `jane.smith` → `XPTO_RH` |
| Usuário admin | `admin.user` → `XPTO_ADMIN` |

Checks esperados após a carga:

```text
john.doe   + beneficios:read       => allowed:true
john.doe   + folha:read            => allowed:false
jane.smith + folha:read            => allowed:true
admin.user + configuracoes:write   => allowed:true
```

```
□ AUTHZ-01: Registrar aplicação retorna 201 com id e clientId únicos
□ AUTHZ-02: Criar role XPTO_USER usando o id da aplicação retorna 201
□ AUTHZ-03: Criar role XPTO_RH usando o id da aplicação retorna 201
□ AUTHZ-04: Atribuir XPTO_USER a john.doe retorna 201
□ AUTHZ-05: Listar roles da aplicação mostra XPTO_USER e XPTO_RH
□ AUTHZ-06: Sem permissão vinculada à role, verificar beneficios:read retorna {allowed: false}
□ AUTHZ-07: Após criar permission e vinculá-la à role, beneficios:read para john.doe retorna {allowed: true}
□ AUTHZ-08: Revogar role de john.doe retorna 204
□ AUTHZ-09: Verificar beneficios:read após revogação retorna {allowed: false}
```

### 3.4 Checklist de Segurança

```
□ SEC-01: Request sem Authorization header para endpoint protegido → 401
□ SEC-02: Request com token de outra aplicação (applicationId diferente) é aceito pela validação JWT
          mas o check de permissão retorna allowed:false
□ SEC-03: Manipular o payload do JWT (sem re-assinar) → serviço rejeita com 401
□ SEC-04: Usar refresh token como access token → validate retorna {active: false} (type=REFRESH)
□ SEC-05: Token após logout/revogação → JWT ainda válido estruturalmente,
          mas validate retorna {active: false}
          IMPORTANTE: Cada serviço valida apenas assinatura e expiração. Para blacklist,
          o serviço deve chamar /validate.
```

> **Nota sobre SEC-05:** A validação de revogação via Redis blacklist deve ser feita chamando `/api/v1/auth/validate` quando a garantia de revogação imediata é necessária. Esta é uma troca consciente: validação local de JWT (latência zero, sem round-trip) versus verificação de revogação pontual onde necessário.

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
    applicationId: 'load-test',
    ldapDomain: 'default'
  });

  const params = { headers: { 'Content-Type': 'application/json' } };
  const res = http.post('http://localhost:8081/api/v1/auth/login', payload, params);

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
curl -X POST http://localhost:8082/api/v1/applications \
  -H "Content-Type: application/json" \
  -d '{
    "name": "portal-xpto",
    "description": "Portal corporativo de RH e benefícios",
    "ownerTeam": "time-rh-xpto",
    "allowedRoles": ["ROLE_ENGINEERS", "ROLE_ADMINISTRATORS"]
  }'

# Resposta:
# {
#   "id": "app-uuid-001",
#   "clientId": "portal-xpto-a1b2c3d4",
#   "issuer": "https://auth.empresa.com",
#   "jwksUri": "https://auth.empresa.com/.well-known/jwks.json",
#   "introspectionUrl": "https://auth.empresa.com/api/v1/auth/validate",
#   "tokenAlgorithm": "RS256",
#   "status": "ACTIVE"
# }
```

Use o `id` retornado (`app-uuid-001` no exemplo) como `applicationId` nas chamadas de RBAC. Use o `clientId` como identificador público da aplicação no fluxo de login. Guarde também `issuer`, `jwksUri` e `introspectionUrl` para o backend da aplicação consumidora.

**Passo 2 — Criar as roles da aplicação:**
```bash
# Role para usuário comum
curl -X POST http://localhost:8082/api/v1/roles \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "XPTO_USER",
    "description": "Acesso básico ao portal",
    "applicationId": "app-uuid-001"
  }'

# Role para equipe de RH
curl -X POST http://localhost:8082/api/v1/roles \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{ "name": "XPTO_RH", "description": "Acesso RH", "applicationId": "app-uuid-001" }'
```

**Passo 3 — Criar permissões e associar às roles:**

```bash
curl -X POST http://localhost:8082/api/v1/permissions \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "beneficios:read",
    "description": "Ler beneficios",
    "resource": "beneficios",
    "action": "read"
  }'

curl -X POST http://localhost:8082/api/v1/roles/role-uuid-xpto-user/permissions/perm-uuid-beneficios-read \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

**Passo 4 — Atribuir roles aos usuários:**
```bash
curl -X POST http://localhost:8082/api/v1/roles/role-uuid-xpto-user/assignments \
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
    │                           │   applicationId: clientId}   │
    │                           │  ex: portal-xpto-a1b2c3d4    │
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
  const response = await fetch('https://auth-service.empresa.com/api/v1/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      username,
      password,
      applicationId: 'portal-xpto-a1b2c3d4',
      ldapDomain: 'corp'
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

### 4.2.1 Auto-serviço da Chave Pública

Após o onboarding, o backend do Portal XPTO não precisa pedir acesso ao AWS Secrets Manager. Ele usa o `jwksUri` devolvido no cadastro da aplicação para obter a chave pública RSA ativa.

```javascript
async function loadSigningKeys(jwksUri) {
  const response = await fetch(jwksUri);
  if (!response.ok) {
    throw new Error('Nao foi possivel carregar o JWKS da Auth Platform');
  }

  const { keys } = await response.json();
  return keys;
}
```

O JWT emitido pelo `auth-service` carrega `kid` no header e `aud` no payload. O backend da aplicação usa esse `kid` para escolher a chave correta no JWKS e validar o token localmente. Os serviços internos usam o mesmo contrato para validar `iss`, `aud`, assinatura e expiração.

O `auth-service` também publica `GET /.well-known/openid-configuration`, usado por clientes compatíveis com descoberta OIDC para encontrar o `jwks_uri`.

Estado atual: o JWKS publica a chave pública ativa. A plataforma já prepara o contrato de `kid`, mas rotação suave com múltiplas chaves ativas simultâneas ainda deve ser evoluída antes de um processo de rotação sem janela de transição.

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
    │                        │ Backend valida assinatura via JWKS:
    │                        │ GET :8081/.well-known/jwks.json
    │                        │ kid do token -> chave RSA correta
    │                        │
    │                        │ Backend verifica permissão:
    │                        │ GET :8082/api/v1/authorization/check
    │                        │ Authorization: Bearer <access_token>
    │                        │ ?applicationId=app-uuid-001
    │                        │ &resource=folha&action=read
    │                        ├───────────────────────►│
    │                        │                        │ → valida JWT RS256
    │                        │                        │ → consulta MySQL
    │                        │  {allowed: false}       │
    │                        │◄───────────────────────┤
    │                        │                        │
    │                        │ → 403 Forbidden        │
    │  403 Forbidden          │                        │
    │◄───────────────────────┤                        │
```

**Código de exemplo (Java/Spring — backend do Portal XPTO):**

```java
// portal-xpto/FolhaController.java
@RestController
@RequestMapping("/api/folha")
public class FolhaController {

    private final AuthorizationClient authorizationClient;

    @GetMapping
    public ResponseEntity<?> getFolha(Authentication authentication) {
        String username = authentication.getName();

        boolean podeAcessar = authorizationClient.checkPermission(
            username, "app-uuid-001", "folha", "read"
        );

        if (!podeAcessar) {
            return ResponseEntity.status(403)
                .body(Map.of("error", "Acesso negado: permissão folha:read necessária"));
        }

        return ResponseEntity.ok(folhaService.getFolha(username));
    }
}
```

O backend primeiro valida o JWT localmente via `jwksUri` do onboarding e depois o `AuthorizationClient` chama `GET http://authorization-service:8082/api/v1/authorization/check` passando o Bearer token recebido da requisição original.
Use o `id` da aplicação registrada no parâmetro `applicationId`; o `clientId` fica restrito ao login/refresh no `auth-service`.

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
      application_id: 'portal-xpto-a1b2c3d4'
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
    '/api/v1/authorization/permissions?applicationId=app-uuid-001',
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
│  Backend valida JWT localmente ou chama authorization-check │
│         ↓                                                   │
│  Backend do Portal decide acesso e retorna dados            │
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
3. As variáveis `auth_url`, `authz_url`, `username`, `password` e `app_client_id` já estão preenchidas com defaults locais para smoke test. Após registrar uma aplicação, atualize `app_client_id` com o `clientId` retornado e preencha `app_id` com o `id` retornado para os requests de RBAC.

### Como usar a collection

**Fluxo rápido de validação:**
1. Execute `Health Checks / Auth Service` e `Health Checks / Authorization Service` — ambos devem retornar `{"status": "UP"}`
2. Execute `Autenticacao / Login (LDAP/AD)` — copie o `access_token` para a variável de ambiente
3. Execute `Autenticacao / Validar Token` — deve retornar `{"active": true}`
4. Execute `Auth Metadata / JWKS Publico` para confirmar o endpoint auto-serviço da chave pública.
5. Execute `Auth Metadata / OpenID Configuration` para confirmar os metadados públicos de descoberta.
6. Execute `Portal XPTO - Fluxo de Consumo` na ordem dos passos XPTO-01 a XPTO-09. Antes dos checks que esperam `allowed:true`, crie as permissões pela pasta `Gestao de Permissoes`, vincule-as às roles e atribua as roles pela pasta `Gestao de Roles`.

### Pastas da Collection

| Pasta | Conteúdo |
|-------|----------|
| `Health Checks` | 2 requests de verificação de saúde (Auth, Authorization) |
| `Autenticacao` | Login, Refresh, Validate, Logout (+ casos negativos) |
| `Auth Metadata` | Endpoints públicos JWKS e OpenID Configuration para validação local |
| `Autorizacao (RBAC)` | Check permission, listar permissões |
| `Gestao de Aplicacoes` | Registrar, atualizar, desativar aplicações |
| `Gestao de Permissoes` | Criar, listar, atualizar, remover e associar permissões a roles |
| `Gestao de Roles` | Criar roles, atribuir/revogar para usuários |
| `Portal XPTO - Fluxo de Consumo` | 9 passos do caso real após onboarding básico de aplicação, roles e permissões |
| `Documentacao (Swagger)` | Links para as UIs de documentação |

### Variáveis de Ambiente

| Variável | Descrição | Exemplo |
|----------|-----------|---------|
| `auth_url` | URL do Auth Service | `http://localhost:8081` |
| `authz_url` | URL do Authorization Service | `http://localhost:8082` |
| `access_token` | JWT access token (atualizar após login) | `eyJ...` |
| `refresh_token` | JWT refresh token (atualizar após login/refresh) | `eyJ...` |
| `username` | Usuário LDAP para testes | `john.doe` |
| `password` | Senha do usuário LDAP | `password123` |
| `app_client_id` | Identificador público usado no login | `portal-xpto-a1b2c3d4` |
| `app_id` | ID retornado pelo cadastro da aplicação, usado no RBAC | `uuid` |
| `role_id` | ID de uma role (após criação) | `uuid` |
