package com.smartvoucher.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.smartvoucher.config.JwtConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Date;

@Service
@RequiredArgsConstructor
@Slf4j
public class QrTokenService {

    private final JwtConfig jwtConfig;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    private static final long QR_TOKEN_TTL_MS = 24 * 60 * 60 * 1000L; // 24h
    private static final int QR_SIZE = 250;

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(
                Base64.getEncoder().encodeToString(jwtConfig.getSecret().getBytes())
        );
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateQrToken(String voucherCode, Long customerId, Long merchantId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + QR_TOKEN_TTL_MS);
        return Jwts.builder()
                .subject("qr")
                .claim("voucherCode", voucherCode)
                .claim("customerId", customerId)
                .claim("merchantId", merchantId)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey())
                .compact();
    }

    public Claims resolveQrToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new IllegalArgumentException("QR_TOKEN_EXPIRED");
        } catch (JwtException e) {
            throw new IllegalArgumentException("QR_TOKEN_INVALID");
        }
    }

    public String buildQrUrl(String token) {
        return baseUrl + "/api/v1/external/vouchers/qr/" + token;
    }

    public byte[] generateQrImage(String content) {
        try {
            BitMatrix matrix = new MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate QR image: {}", e.getMessage());
            throw new RuntimeException("QR generation failed", e);
        }
    }
}
