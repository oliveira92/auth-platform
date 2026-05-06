package com.authplatform.authorization.infrastructure.jwt;

import com.authplatform.authorization.infrastructure.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtTokenValidatorTest {

    @Test
    void shouldValidateTokenWithConfiguredAudience() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        JwtProperties properties = jwtProperties(keyPair, "auth-platform-api");
        JwtTokenValidator validator = new JwtTokenValidator(properties);

        Claims claims = validator.validateToken(generateToken(keyPair, "auth-platform-api"));

        assertEquals("john.doe", claims.getSubject());
        assertEquals("https://auth.empresa.com", claims.getIssuer());
    }

    @Test
    void shouldRejectTokenWithUnexpectedAudience() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        JwtProperties properties = jwtProperties(keyPair, "other-api");
        JwtTokenValidator validator = new JwtTokenValidator(properties);

        assertThrows(JwtException.class, () -> validator.validateToken(generateToken(keyPair, "auth-platform-api")));
    }

    private JwtProperties jwtProperties(KeyPair keyPair, String audience) {
        JwtProperties properties = new JwtProperties();
        properties.setPublicKey(toPem("PUBLIC KEY", keyPair.getPublic().getEncoded()));
        properties.setIssuer("https://auth.empresa.com");
        properties.setAudience(audience);
        return properties;
    }

    private String generateToken(KeyPair keyPair, String audience) {
        Instant issuedAt = Instant.now().minusSeconds(1);
        return Jwts.builder()
            .subject("john.doe")
            .issuer("https://auth.empresa.com")
            .audience().add(audience).and()
            .issuedAt(Date.from(issuedAt))
            .expiration(Date.from(issuedAt.plusSeconds(900)))
            .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
            .compact();
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
