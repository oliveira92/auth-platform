package com.authplatform.authorization.infrastructure.jwt;

import com.authplatform.authorization.infrastructure.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenValidator {

    private final JwtProperties jwtProperties;

    private volatile RSAPublicKey publicKey;

    public Claims validateToken(String token) {
        return Jwts.parser()
            .verifyWith(getPublicKey())
            .requireIssuer(jwtProperties.getIssuer())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    private RSAPublicKey getPublicKey() {
        if (publicKey == null) {
            synchronized (this) {
                if (publicKey == null) {
                    String pem = jwtProperties.getPublicKey();
                    if (pem == null || pem.isBlank()) {
                        throw new IllegalStateException("JWT public key not configured");
                    }
                    try {
                        publicKey = parsePublicKey(pem);
                    } catch (Exception e) {
                        throw new IllegalStateException("Failed to parse JWT public key", e);
                    }
                }
            }
        }
        return publicKey;
    }

    private RSAPublicKey parsePublicKey(String pem) throws Exception {
        String cleaned = pem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replaceAll("\\s+", "");
        byte[] decoded = Base64.getDecoder().decode(cleaned);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return (RSAPublicKey) keyFactory.generatePublic(new X509EncodedKeySpec(decoded));
    }
}
