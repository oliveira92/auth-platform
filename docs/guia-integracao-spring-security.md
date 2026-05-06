# Guia de Integração — Spring Security com Auth Platform

Este guia cobre como consumir o **auth-platform** em um backend Java com Spring Boot,
usando Spring Security para proteger endpoints com `@PreAuthorize`, `@PostAuthorize`,
`@Secured` e demais annotations de method security.

---

## Sumário

1. [Como funciona a integração](#1-como-funciona-a-integração)
2. [Dependências](#2-dependências)
3. [Configuração](#3-configuração)
4. [SecurityConfig](#4-securityconfig)
5. [AuthPlatformClient](#5-authplatformclient)
6. [AuthPlatformPermissionLoader](#6-authplatformpermissionloader)
7. [Usando as annotations](#7-usando-as-annotations)
8. [PermissionEvaluator customizado](#8-permissionevaluator-customizado)
9. [`/permissions` vs `/check` — qual usar](#9-permissions-vs-check--qual-usar)
10. [Revogação de tokens](#10-revogação-de-tokens)

---

## 1. Como funciona a integração

O backend da sua aplicação recebe requests com o JWT emitido pelo `auth-service`.
Em vez de validar permissões chamando o `authorization-service` a cada annotation,
as permissões são carregadas **uma única vez por request** e populadas no
`SecurityContext` do Spring Security. A partir daí, todas as annotations
funcionam com avaliação local, sem chamadas de rede adicionais.

```
Request (Bearer JWT)
       │
       ▼
[OAuth2 Resource Server]
 valida JWT via JWKS (:8081/.well-known/jwks.json)
       │
       ▼
[AuthPlatformPermissionLoader]  ──► GET :8082/api/v1/authorization/permissions
 converte Jwt → GrantedAuthority       retorna { roles, permissions }
       │
       ▼
[SecurityContext]
 Authentication com authorities:
   "beneficios:read", "folha:write",
   "ROLE_XPTO_RH", "ROLE_XPTO_ADMIN"
       │
       ▼
[@PreAuthorize] [@Secured] [@PostAuthorize]
 avaliados localmente — sem HTTP adicional
```

---

## 2. Dependências

```xml
<!-- Segurança -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- Resource Server: valida JWT via JWKS do auth-service -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<!-- Opcional: cache das permissões para reduzir chamadas ao authorization-service -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-cache</artifactId>
</dependency>
```

---

## 3. Configuração

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          # JWKS do auth-service — valida assinatura RS256 localmente
          jwk-set-uri: http://auth-service:8081/.well-known/jwks.json

auth-platform:
  # URL base do authorization-service
  authz-url: http://authorization-service:8082
  # ID retornado no onboarding da sua aplicação (não é o clientId)
  application-id: app-uuid-001
```

> **Atenção:** use o campo `id` (UUID) retornado no cadastro da aplicação como
> `application-id`, não o `clientId`. O `clientId` é usado apenas no fluxo de login
> no `auth-service`.

---

## 4. SecurityConfig

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity  // habilita @PreAuthorize, @PostAuthorize, @Secured, @PreFilter, @PostFilter
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtAuthenticationConverter jwtConverter) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter))
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .csrf(AbstractHttpConfigurer::disable);

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter(
            AuthPlatformPermissionLoader permissionLoader) {

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        // Substitui o conversor padrão (que leria claims "scope"/"roles" do JWT)
        // pelo loader que consulta o authorization-service
        converter.setJwtGrantedAuthoritiesConverter(permissionLoader);
        return converter;
    }
}
```

---

## 5. AuthPlatformClient

Cliente HTTP responsável por chamar o `authorization-service`.

```java
@Component
public class AuthPlatformClient {

    private final RestClient restClient;
    private final String authzUrl;

    public AuthPlatformClient(RestClient.Builder builder,
                               @Value("${auth-platform.authz-url}") String authzUrl) {
        this.restClient = builder.build();
        this.authzUrl = authzUrl;
    }

    // Cache com chave appId:username
    // TTL recomendado alinhado ao TTL do access token (padrão: 900s no auth-service)
    @Cacheable(value = "user-permissions", key = "#applicationId + ':' + #username")
    public UserPermissions getUserPermissions(String bearerToken,
                                              String applicationId,
                                              String username) {
        return restClient.get()
            .uri(authzUrl + "/api/v1/authorization/permissions?applicationId={appId}", applicationId)
            .header("Authorization", "Bearer " + bearerToken)
            .retrieve()
            .body(UserPermissions.class);
    }

    public record UserPermissions(
        String username,
        String applicationId,
        List<String> roles,        // ex: ["XPTO_RH", "XPTO_ADMIN"]
        List<String> permissions   // ex: ["beneficios:read", "folha:read", "folha:write"]
    ) {}
}
```

---

## 6. AuthPlatformPermissionLoader

Transforma o JWT em `GrantedAuthority` consultando as permissões reais do `authorization-service`.
É invocado uma vez por request, no momento da validação do JWT.

```java
@Component
@RequiredArgsConstructor
public class AuthPlatformPermissionLoader implements Converter<Jwt, Collection<GrantedAuthority>> {

    private final AuthPlatformClient authPlatformClient;

    @Value("${auth-platform.application-id}")
    private String applicationId;

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        String username = jwt.getSubject();
        String bearerToken = jwt.getTokenValue();

        AuthPlatformClient.UserPermissions perms =
            authPlatformClient.getUserPermissions(bearerToken, applicationId, username);

        List<GrantedAuthority> authorities = new ArrayList<>();

        // Permissões no formato "resource:action" — usadas com hasAuthority()
        // Ex: "beneficios:read", "folha:write", "configuracoes:read"
        perms.permissions().stream()
            .map(SimpleGrantedAuthority::new)
            .forEach(authorities::add);

        // Roles com prefixo ROLE_ — usadas com hasRole() e @Secured
        // Ex: "ROLE_XPTO_RH", "ROLE_XPTO_ADMIN"
        perms.roles().stream()
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
            .forEach(authorities::add);

        return authorities;
    }
}
```

---

## 7. Usando as annotations

### `@PreAuthorize` com `hasAuthority` — checa permissão `resource:action`

```java
@GetMapping("/beneficios")
@PreAuthorize("hasAuthority('beneficios:read')")
public List<Beneficio> getBeneficios() {
    return beneficioService.listarTodos();
}

@PostMapping("/folha")
@PreAuthorize("hasAuthority('folha:write')")
public ResponseEntity<Void> updateFolha(@RequestBody FolhaRequest req) {
    folhaService.atualizar(req);
    return ResponseEntity.ok().build();
}
```

### `@PreAuthorize` com `hasRole` — checa role (Spring remove o prefixo `ROLE_` automaticamente)

```java
@GetMapping("/configuracoes")
@PreAuthorize("hasRole('XPTO_ADMIN')")
public Configuracoes getConfiguracoes() {
    return configService.getAll();
}
```

### `@Secured` — checagem simples de role sem SpEL

```java
@DeleteMapping("/configuracoes/{id}")
@Secured("ROLE_XPTO_ADMIN")
public ResponseEntity<Void> deleteConfiguracao(@PathVariable String id) {
    configService.remover(id);
    return ResponseEntity.noContent().build();
}
```

### SpEL composto — `OR` entre permissões ou roles

```java
@PutMapping("/folha/{id}")
@PreAuthorize("hasAuthority('folha:write') or hasRole('XPTO_ADMIN')")
public ResponseEntity<Void> atualizarLancamento(@PathVariable String id,
                                                 @RequestBody LancamentoRequest req) {
    folhaService.atualizarLancamento(id, req);
    return ResponseEntity.ok().build();
}
```

### SpEL com parâmetro do método — usuário acessa apenas seus próprios dados

```java
@GetMapping("/folha/{username}")
@PreAuthorize("hasAuthority('folha:read') and #username == authentication.name " +
              "or hasRole('XPTO_ADMIN')")
public FolhaPagamento getFolhaPorUsuario(@PathVariable String username) {
    return folhaService.porUsuario(username);
}
```

### `@PostAuthorize` — valida o objeto retornado antes de entregar ao cliente

```java
@GetMapping("/documentos/{id}")
@PostAuthorize("returnObject.dono == authentication.name or hasRole('XPTO_ADMIN')")
public Documento getDocumento(@PathVariable String id) {
    return documentoService.buscarPorId(id);
}
```

### `@PostFilter` — filtra lista retornada sem lançar 403

```java
@GetMapping("/documentos")
@PreAuthorize("isAuthenticated()")
@PostFilter("filterObject.dono == authentication.name or hasRole('XPTO_ADMIN')")
public List<Documento> listarDocumentos() {
    return documentoService.listarTodos();
}
```

### Resumo das annotations

| Annotation | Quando usar |
|---|---|
| `@PreAuthorize` | Lógica complexa com SpEL antes do método |
| `@PostAuthorize` | Checar objeto retornado (ex: ownership) |
| `@Secured` | Checagem simples de role, sem SpEL |
| `@PreFilter` | Filtrar coleção de entrada antes do método |
| `@PostFilter` | Filtrar coleção de saída sem lançar 403 |

---

## 8. PermissionEvaluator customizado

Habilita a sintaxe `hasPermission(targetId, resource, action)` nas expressions SpEL,
útil quando a decisão envolve um recurso identificado por ID.

```java
@Component
public class AuthPlatformPermissionEvaluator implements PermissionEvaluator {

    // hasPermission(objeto, "folha:read")
    @Override
    public boolean hasPermission(Authentication auth, Object targetDomainObject, Object permission) {
        if (permission instanceof String scope) {
            return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(scope));
        }
        return false;
    }

    // hasPermission(#id, 'folha', 'read')
    // targetType = resource, permission = action
    @Override
    public boolean hasPermission(Authentication auth, Serializable targetId,
                                  String targetType, Object permission) {
        String scope = targetType + ":" + permission;
        return auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals(scope));
    }
}
```

Registrar no `SecurityConfig`:

```java
@Bean
public MethodSecurityExpressionHandler methodSecurityExpressionHandler(
        AuthPlatformPermissionEvaluator evaluator) {
    DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
    handler.setPermissionEvaluator(evaluator);
    return handler;
}
```

Uso no controller:

```java
@GetMapping("/folha/{id}")
@PreAuthorize("hasPermission(#id, 'folha', 'read')")
public FolhaPagamento getFolha(@PathVariable String id) { ... }
```

---

## 9. `/permissions` vs `/check` — qual usar

O `authorization-service` expõe dois endpoints de consulta:

| Endpoint | Pergunta respondida | Resposta |
|---|---|---|
| `GET /api/v1/authorization/permissions` | "o que esse usuário pode fazer?" | lista completa de roles e permissões |
| `GET /api/v1/authorization/check` | "pode fazer esta ação específica agora?" | `{ allowed: true/false }` |

### Por que a integração Spring Security usa `/permissions`

Com `/check`, cada annotation `@PreAuthorize` exigiria uma chamada HTTP ao `authorization-service`:

```
Request com 3 endpoints protegidos
  → @PreAuthorize → HTTP /check (1ª chamada)
  → @PreAuthorize → HTTP /check (2ª chamada)
  → @PostAuthorize → HTTP /check (3ª chamada)
```

Com `/permissions`, todas as permissões são carregadas **uma única vez** na entrada do request.
O Spring Security avalia `hasAuthority()` contra a lista em memória — sem chamadas adicionais:

```
Request entra
  → AuthPlatformPermissionLoader → HTTP /permissions (1 chamada total)
  → SecurityContext populado
  → @PreAuthorize → avaliado localmente
  → @PreAuthorize → avaliado localmente
  → @PostAuthorize → avaliado localmente
```

### Quando usar `/check` diretamente

O `/check` tem valor quando a decisão depende de **dados dinâmicos do objeto**,
não apenas da identidade do usuário — casos que as annotations padrão não cobrem:

```java
// "este usuário pode editar este documento específico?"
// a lógica depende do estado do documento, não só das permissões do usuário
boolean podeEditar = authClient.check(token, appId, "documento:" + doc.getId(), "write");
```

---

## 10. Revogação de tokens

O JWT é validado localmente via JWKS (sem round-trip ao `auth-service`).
Por padrão, um token revogado via `DELETE /api/v1/auth/logout` continua passando
na validação de assinatura até expirar — a blacklist fica no Redis do `auth-service`.

Se a sua aplicação exige detecção de revogação imediata (ex: logout de sessão ativa),
chame `POST /api/v1/auth/validate` antes de processar o request:

```java
// Antes de buscar permissões no AuthPlatformPermissionLoader:
if (!authServiceClient.isTokenActive(bearerToken)) {
    throw new BadCredentialsException("Token revogado");
}
```

Esta é uma troca consciente documentada na plataforma: validação local via JWKS tem
**latência zero**, mas não detecta revogação sem um round-trip ao `auth-service`.
Para a maioria das aplicações, o TTL curto do access token (15 minutos por padrão)
torna a verificação de revogação desnecessária no fluxo normal.
