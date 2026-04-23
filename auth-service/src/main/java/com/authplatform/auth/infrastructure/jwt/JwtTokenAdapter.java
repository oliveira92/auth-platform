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
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenAdapter implements JwtPort {

    private final JwtProperties jwtProperties;
    private final JwtKeyProvider jwtKeyProvider;

    @Override
    public String generateToken(Token token) {
        return Jwts.builder()
            .setHeaderParam("kid", jwtKeyProvider.getKeyId())
            .id(token.tokenId())
            .subject(token.username())
            .issuer(jwtProperties.getIssuer())
            .issuedAt(Date.from(token.issuedAt()))
            .expiration(Date.from(token.expiresAt()))
            .claim("type", token.type().name())
            .claim("roles", token.roles())
            .claim("groups", token.groups())
            .claim("applicationId", token.applicationId())
            .signWith(jwtKeyProvider.getPrivateKey(), Jwts.SIG.RS256)
            .compact();
    }

    @Override
    public Token parseToken(String rawToken) {
        try {
            Claims claims = Jwts.parser()
                .verifyWith(jwtKeyProvider.getPublicKey())
                .requireIssuer(jwtProperties.getIssuer())
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
}
