package com.authplatform.auth.infrastructure.jwt;

import com.authplatform.auth.infrastructure.config.JwtProperties;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JwtKeyProviderTest {

    @Test
    void shouldExposeStableJwkMetadataFromConfiguredPem() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();

        JwtProperties properties = new JwtProperties();
        properties.setPrivateKey(toPem("PRIVATE KEY", keyPair.getPrivate().getEncoded()));
        properties.setPublicKey(toPem("PUBLIC KEY", keyPair.getPublic().getEncoded()));

        JwtKeyProvider provider = new JwtKeyProvider(properties);
        RSAPublicKey rsaPublicKey = (RSAPublicKey) keyPair.getPublic();

        assertNotNull(provider.getPrivateKey());
        assertNotNull(provider.getPublicKey());
        assertFalse(provider.getKeyId().isBlank());
        assertEquals(provider.getKeyId(), provider.getKeyId());
        assertEquals(toBase64Url(rsaPublicKey.getModulus().toByteArray()), provider.getModulus());
        assertEquals(toBase64Url(rsaPublicKey.getPublicExponent().toByteArray()), provider.getExponent());
    }

    private String toPem(String type, byte[] content) {
        String body = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(content);
        return "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----";
    }

    private String toBase64Url(byte[] value) {
        byte[] normalized = value;
        if (normalized.length > 1 && normalized[0] == 0) {
            byte[] trimmed = new byte[normalized.length - 1];
            System.arraycopy(normalized, 1, trimmed, 0, trimmed.length);
            normalized = trimmed;
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(normalized);
    }
}
