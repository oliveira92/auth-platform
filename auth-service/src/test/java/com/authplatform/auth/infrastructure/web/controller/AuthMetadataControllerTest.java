package com.authplatform.auth.infrastructure.web.controller;

import com.authplatform.auth.infrastructure.config.JwtProperties;
import com.authplatform.auth.infrastructure.jwt.JwtKeyProvider;
import com.authplatform.auth.infrastructure.web.dto.OpenIdConfigurationResponse;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthMetadataControllerTest {

    @Test
    void shouldExposeOpenIdDiscoveryMetadataForJwtAuthorizers() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        JwtProperties properties = new JwtProperties();
        properties.setPrivateKey(toPem("PRIVATE KEY", keyPair.getPrivate().getEncoded()));
        properties.setPublicKey(toPem("PUBLIC KEY", keyPair.getPublic().getEncoded()));
        properties.setIssuer("https://auth.empresa.com");

        AuthMetadataController controller = new AuthMetadataController(
            new JwtKeyProvider(properties),
            properties
        );

        OpenIdConfigurationResponse response = controller.openIdConfiguration().getBody();

        assertEquals("https://auth.empresa.com", response.issuer());
        assertEquals("https://auth.empresa.com/.well-known/jwks.json", response.jwksUri());
        assertEquals("RS256", response.idTokenSigningAlgValuesSupported().get(0));
    }

    private KeyPair generateRsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private String toPem(String type, byte[] content) {
        String body = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(content);
        return "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----";
    }
}
