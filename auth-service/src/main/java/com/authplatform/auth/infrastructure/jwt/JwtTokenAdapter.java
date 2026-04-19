package com.authplatform.auth.infrastructure.jwt;

import com.authplatform.auth.domain.exception.InvalidTokenException;
import com.authplatform.auth.domain.model.Token;
import com.authplatform.auth.domain.model.TokenType;
import com.authplatform.auth.domain.port.out.JwtPort;
import com.authplatform.auth.infrastructure.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;
import org.springframework.stereotype.Component;

import java.io.StringReader;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Date;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenAdapter implements JwtPort {

    private final JwtProperties jwtProperties;

    private volatile PrivateKey privateKey;
    private volatile PublicKey publicKey;

    @Override
    public String generateToken(Token token) {
        return Jwts.builder()
            .id(token.tokenId())
            .subject(token.username())
            .issuer(jwtProperties.getIssuer())
            .issuedAt(Date.from(token.issuedAt()))
            .expiration(Date.from(token.expiresAt()))
            .claim("type", token.type().name())
            .claim("roles", token.roles())
            .claim("groups", token.groups())
            .claim("applicationId", token.applicationId())
            .signWith(getPrivateKey(), Jwts.SIG.RS256)
            .compact();
    }

    @Override
    public Token parseToken(String rawToken) {
        try {
            Claims claims = Jwts.parser()
                .verifyWith(getPublicKey())
                .build()
                .parseSignedClaims(rawToken)
                .getPayload();

            return new Token(
                claims.getId(),
                claims.getSubject(),
                TokenType.valueOf(claims.get("type", String.class)),
                claims.getIssuedAt().toInstant(),
                claims.getExpiration().toInstant(),
                claims.get("roles", List.class),
                claims.get("groups", List.class),
                claims.get("applicationId", String.class),
                null
            );
        } catch (ExpiredJwtException e) {
            throw new InvalidTokenException("Token has expired", e);
        } catch (JwtException e) {
            throw new InvalidTokenException("Invalid token: " + e.getMessage(), e);
        }
    }

    private PrivateKey getPrivateKey() {
        if (privateKey == null) {
            synchronized (this) {
                if (privateKey == null) {
                    String pem = jwtProperties.getPrivateKey();
                    if (pem == null || pem.isBlank()) {
                        throw new IllegalStateException("JWT private key not configured — check AWS Secrets Manager or JWT_PRIVATE_KEY env var");
                    }
                    try {
                        this.privateKey = loadPrivateKey(pem);
                        log.info("JWT private key loaded successfully");
                    } catch (Exception e) {
                        throw new IllegalStateException("Failed to load JWT private key", e);
                    }
                }
            }
        }
        return privateKey;
    }

    private PublicKey getPublicKey() {
        if (publicKey == null) {
            synchronized (this) {
                if (publicKey == null) {
                    String pem = jwtProperties.getPublicKey();
                    if (pem == null || pem.isBlank()) {
                        throw new IllegalStateException("JWT public key not configured — check AWS Secrets Manager or JWT_PUBLIC_KEY env var");
                    }
                    try {
                        this.publicKey = loadPublicKey(pem);
                        log.info("JWT public key loaded successfully");
                    } catch (Exception e) {
                        throw new IllegalStateException("Failed to load JWT public key", e);
                    }
                }
            }
        }
        return publicKey;
    }

    private PrivateKey loadPrivateKey(String pem) throws Exception {
        try (PemReader pemReader = new PemReader(new StringReader(pem))) {
            PemObject pemObject = pemReader.readPemObject();
            byte[] keyBytes = pemObject.getContent();
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePrivate(keySpec);
        }
    }

    private PublicKey loadPublicKey(String pem) throws Exception {
        try (PemReader pemReader = new PemReader(new StringReader(pem))) {
            PemObject pemObject = pemReader.readPemObject();
            byte[] keyBytes = pemObject.getContent();
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePublic(keySpec);
        }
    }
}
