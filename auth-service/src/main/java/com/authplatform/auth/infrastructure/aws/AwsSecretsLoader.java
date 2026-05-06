package com.authplatform.auth.infrastructure.aws;

import com.authplatform.auth.infrastructure.config.JwtProperties;
import com.authplatform.auth.infrastructure.config.LdapProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AwsSecretsLoader {

    private final SecretsManagerClient secretsManagerClient;
    private final JwtProperties jwtProperties;
    private final LdapProperties ldapProperties;
    private final ObjectMapper objectMapper;

    @Value("${auth.aws.secrets.jwt-secret-name:auth-platform/auth-service/jwt-keys}")
    private String jwtSecretName;

    @Value("${auth.aws.secrets.ldap-secret-name:auth-platform/auth-service/ldap-credentials}")
    private String ldapSecretName;

    @PostConstruct
    public void loadSecrets() {
        loadJwtSecrets();
        loadLdapSecrets();
    }

    private void loadJwtSecrets() {
        try {
            log.info("Loading JWT secrets from AWS Secrets Manager: {}", jwtSecretName);
            String secretValue = getSecret(jwtSecretName);
            Map<String, String> secrets = objectMapper.readValue(secretValue, Map.class);
            jwtProperties.setPrivateKey(secrets.get("privateKey"));
            jwtProperties.setPublicKey(secrets.get("publicKey"));
            log.info("JWT secrets loaded successfully");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load JWT secrets from AWS", e);
        }
    }

    private void loadLdapSecrets() {
        try {
            log.info("Loading LDAP credentials from AWS Secrets Manager: {}", ldapSecretName);
            String secretValue = getSecret(ldapSecretName);
            Map<String, Object> secrets = objectMapper.readValue(secretValue, Map.class);
            ldapProperties.setUserDn(asString(secrets.get("username")));
            ldapProperties.setPassword(asString(secrets.get("password")));
            loadDomainLdapSecrets(secrets);
            log.info("LDAP credentials loaded successfully");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load LDAP credentials from AWS", e);
        }
    }

    private void loadDomainLdapSecrets(Map<String, Object> secrets) {
        Object domainSecrets = secrets.get("domains");
        if (!(domainSecrets instanceof Map<?, ?> domains)) {
            return;
        }

        domains.forEach((domainKey, credentials) -> {
            if (!(credentials instanceof Map<?, ?> domainCredentials)) {
                return;
            }

            LdapProperties.Domain domain = ldapProperties.getDomains()
                .computeIfAbsent(domainKey.toString(), ignored -> new LdapProperties.Domain());
            domain.setUserDn(asString(domainCredentials.get("username")));
            domain.setPassword(asString(domainCredentials.get("password")));
        });
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private String getSecret(String secretName) {
        return secretsManagerClient.getSecretValue(
            GetSecretValueRequest.builder().secretId(secretName).build()
        ).secretString();
    }
}
