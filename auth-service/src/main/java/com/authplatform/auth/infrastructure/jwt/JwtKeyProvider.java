package com.authplatform.auth.infrastructure.jwt;

import com.authplatform.auth.infrastructure.config.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;
import org.springframework.stereotype.Component;

import java.io.StringReader;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

@Component
@RequiredArgsConstructor
public class JwtKeyProvider {

    private final JwtProperties jwtProperties;

    private volatile PrivateKey privateKey;
    private volatile RSAPublicKey publicKey;
    private volatile String keyId;

    public PrivateKey getPrivateKey() {
        if (privateKey == null) {
            synchronized (this) {
                if (privateKey == null) {
                    String pem = jwtProperties.getPrivateKey();
                    if (pem == null || pem.isBlank()) {
                        throw new IllegalStateException("JWT private key not configured - check AWS Secrets Manager or JWT_PRIVATE_KEY env var");
                    }
                    try {
                        this.privateKey = loadPrivateKey(pem);
                    } catch (Exception e) {
                        throw new IllegalStateException("Failed to load JWT private key", e);
                    }
                }
            }
        }
        return privateKey;
    }

    public RSAPublicKey getPublicKey() {
        if (publicKey == null) {
            synchronized (this) {
                if (publicKey == null) {
                    String pem = jwtProperties.getPublicKey();
                    if (pem == null || pem.isBlank()) {
                        throw new IllegalStateException("JWT public key not configured - check AWS Secrets Manager or JWT_PUBLIC_KEY env var");
                    }
                    try {
                        this.publicKey = loadPublicKey(pem);
                    } catch (Exception e) {
                        throw new IllegalStateException("Failed to load JWT public key", e);
                    }
                }
            }
        }
        return publicKey;
    }

    public String getKeyId() {
        if (keyId == null) {
            synchronized (this) {
                if (keyId == null) {
                    try {
                        byte[] digest = MessageDigest.getInstance("SHA-256")
                            .digest(getPublicKey().getEncoded());
                        keyId = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
                    } catch (Exception e) {
                        throw new IllegalStateException("Failed to derive JWT key id", e);
                    }
                }
            }
        }
        return keyId;
    }

    public String getModulus() {
        return toBase64Url(getPublicKey().getModulus());
    }

    public String getExponent() {
        return toBase64Url(getPublicKey().getPublicExponent());
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

    private RSAPublicKey loadPublicKey(String pem) throws Exception {
        try (PemReader pemReader = new PemReader(new StringReader(pem))) {
            PemObject pemObject = pemReader.readPemObject();
            byte[] keyBytes = pemObject.getContent();
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return (RSAPublicKey) keyFactory.generatePublic(keySpec);
        }
    }

    private String toBase64Url(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
