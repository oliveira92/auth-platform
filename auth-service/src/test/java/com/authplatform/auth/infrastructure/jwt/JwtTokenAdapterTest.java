package com.authplatform.auth.infrastructure.jwt;

import com.authplatform.auth.domain.model.Token;
import com.authplatform.auth.domain.model.TokenType;
import com.authplatform.auth.infrastructure.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JwtTokenAdapterTest {

    @Test
    void shouldGenerateTokenWithKidHeaderAndConfiguredIssuer() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        JwtProperties properties = new JwtProperties();
        properties.setPrivateKey(toPem("PRIVATE KEY", keyPair.getPrivate().getEncoded()));
        properties.setPublicKey(toPem("PUBLIC KEY", keyPair.getPublic().getEncoded()));
        properties.setIssuer("https://auth.empresa.com");

        JwtKeyProvider keyProvider = new JwtKeyProvider(properties);
        JwtTokenAdapter adapter = new JwtTokenAdapter(properties, keyProvider);
        Instant issuedAt = Instant.now().minusSeconds(1);
        Token token = new Token(
            "token-123",
            "john.doe",
            TokenType.ACCESS,
            issuedAt,
            issuedAt.plusSeconds(900),
            List.of("ROLE_ENGINEERS"),
            List.of("engineers"),
            "portal-xpto-a1b2c3d4",
            "127.0.0.1"
        );

        String rawToken = adapter.generateToken(token);
        Jws<Claims> parsed = Jwts.parser()
            .verifyWith(keyProvider.getPublicKey())
            .requireIssuer(properties.getIssuer())
            .build()
            .parseSignedClaims(rawToken);

        assertEquals(keyProvider.getKeyId(), parsed.getHeader().getKeyId());
        assertEquals("token-123", parsed.getPayload().getId());
        assertEquals("john.doe", parsed.getPayload().getSubject());
        assertEquals("https://auth.empresa.com", parsed.getPayload().getIssuer());

        Token parsedToken = adapter.parseToken(rawToken);
        assertEquals(token.tokenId(), parsedToken.tokenId());
        assertEquals(token.username(), parsedToken.username());
        assertEquals(token.applicationId(), parsedToken.applicationId());
        assertEquals(token.roles(), parsedToken.roles());
        assertEquals(token.groups(), parsedToken.groups());
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
