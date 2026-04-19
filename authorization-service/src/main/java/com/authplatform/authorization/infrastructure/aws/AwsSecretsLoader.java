package com.authplatform.authorization.infrastructure.aws;

import com.authplatform.authorization.infrastructure.config.JwtProperties;
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
    private final ObjectMapper objectMapper;

    @Value("${auth.aws.secrets.jwt-public-key-name:auth-platform/shared/jwt-public-key}")
    private String jwtPublicKeySecretName;

    @PostConstruct
    public void loadSecrets() {
        try {
            log.info("Loading JWT public key from AWS Secrets Manager: {}", jwtPublicKeySecretName);
            String secretValue = secretsManagerClient.getSecretValue(
                GetSecretValueRequest.builder().secretId(jwtPublicKeySecretName).build()
            ).secretString();
            Map<String, String> secrets = objectMapper.readValue(secretValue, Map.class);
            jwtProperties.setPublicKey(secrets.get("publicKey"));
            log.info("JWT public key loaded successfully");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load JWT public key from AWS Secrets Manager", e);
        }
    }
}
