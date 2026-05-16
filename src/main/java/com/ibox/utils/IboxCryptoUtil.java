package com.ibox.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;

/**
 * Utility methods for iBox request and response encryption.
 */
@Component
public class IboxCryptoUtil {

    /** Default private key resource path. */
    public static final String DEFAULT_PRIVATE_KEY_PATH = "keys/private_key.txt";

    /** Default public key resource path. */
    public static final String DEFAULT_PUBLIC_KEY_PATH = "keys/public_key.txt";

    /** RSA transformation used by iBox. */
    private static final String RSA_TRANSFORMATION = "RSA/ECB/PKCS1Padding";
    /** AES transformation used by iBox. */
    private static final String AES_TRANSFORMATION = "AES/ECB/PKCS5Padding";
    /** Shared JSON mapper. */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** Cached default private key. */
    private static volatile PrivateKey cachedPrivateKey;
    /** Cached default public key. */
    private static volatile PublicKey cachedPublicKey;

    /**
     * Decrypts a response by using the default private key.
     */
    public static String decryptResponse(String data, String encryptKey) {
        try {
            String aesKey = rsaDecryptEncryptKey(encryptKey, getDefaultPrivateKey());
            byte[] plainBytes = aesDecrypt(data, aesKey);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("iBox响应解密失败", e);
        }
    }

    /**
     * Decrypts a response by using a provided private key.
     */
    public static String decryptResponse(String data, String encryptKey, String privateKeyText) {
        try {
            String aesKey = rsaDecryptEncryptKey(encryptKey, buildPrivateKey(privateKeyText));
            byte[] plainBytes = aesDecrypt(data, aesKey);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("iBox响应解密失败", e);
        }
    }

    /**
     * Decrypts a response and parses it into a map.
     */
    public static Map<String, Object> decryptResponseToMap(String data, String encryptKey) {
        String plainText = decryptResponse(data, encryptKey);
        try {
            return OBJECT_MAPPER.readValue(plainText, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("解密结果不是合法JSON", e);
        }
    }

    /**
     * Encrypts an AES key by using the default public key.
     */
    public static String rsaEncryptAesKey(String aesKeyAscii) {
        try {
            return rsaEncryptAesKey(aesKeyAscii, getDefaultPublicKey());
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("AES密钥加密失败", e);
        }
    }

    /**
     * Encrypts an AES key by using a provided public key.
     */
    public static String rsaEncryptAesKey(String aesKeyAscii, String publicKeyText) {
        try {
            return rsaEncryptAesKey(aesKeyAscii, buildPublicKey(publicKeyText));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES密钥加密失败", e);
        }
    }

    /**
     * Encrypts plain text by using AES.
     */
    public static String aesEncrypt(String plainText, String aesKeyAscii) {
        try {
            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            SecretKeySpec secretKeySpec = new SecretKeySpec(aesKeyAscii.getBytes(StandardCharsets.US_ASCII), "AES");
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES数据加密失败", e);
        }
    }

    /**
     * Builds the encrypted request payload required by iBox.
     */
    public static Map<String, String> encryptPayload(String plainText, String aesKeyAscii) {
        String data = aesEncrypt(plainText, aesKeyAscii);
        String encryptKey = rsaEncryptAesKey(aesKeyAscii);
        return Map.of("data", data, "encryptKey", encryptKey);
    }

    private static PrivateKey getDefaultPrivateKey() throws IOException, GeneralSecurityException {
        PrivateKey local = cachedPrivateKey;
        if (local != null) {
            return local;
        }
        synchronized (IboxCryptoUtil.class) {
            if (cachedPrivateKey == null) {
                cachedPrivateKey = buildPrivateKey(loadClasspathKey(DEFAULT_PRIVATE_KEY_PATH));
            }
            return cachedPrivateKey;
        }
    }

    private static PublicKey getDefaultPublicKey() throws IOException, GeneralSecurityException {
        PublicKey local = cachedPublicKey;
        if (local != null) {
            return local;
        }
        synchronized (IboxCryptoUtil.class) {
            if (cachedPublicKey == null) {
                cachedPublicKey = buildPublicKey(loadClasspathKey(DEFAULT_PUBLIC_KEY_PATH));
            }
            return cachedPublicKey;
        }
    }

    private static String rsaEncryptAesKey(String aesKeyAscii, PublicKey publicKey) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(RSA_TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        byte[] encrypted = cipher.doFinal(aesKeyAscii.getBytes(StandardCharsets.US_ASCII));
        return Base64.getEncoder().encodeToString(encrypted);
    }

    private static String rsaDecryptEncryptKey(String encryptKey, PrivateKey privateKey) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(RSA_TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        byte[] encrypted = decodeBase64Auto(encryptKey);
        byte[] aesKeyBytes = cipher.doFinal(encrypted);
        return new String(aesKeyBytes, StandardCharsets.US_ASCII);
    }

    private static byte[] aesDecrypt(String data, String aesKeyAscii) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
        SecretKeySpec secretKeySpec = new SecretKeySpec(aesKeyAscii.getBytes(StandardCharsets.US_ASCII), "AES");
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);
        byte[] encrypted = decodeBase64Auto(data);
        return cipher.doFinal(encrypted);
    }

    private static PrivateKey buildPrivateKey(String privateKeyText) throws GeneralSecurityException {
        String compactKey = compactKeyText(privateKeyText);
        byte[] keyBytes = Base64.getDecoder().decode(compactKey);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(keySpec);
    }

    private static PublicKey buildPublicKey(String publicKeyText) throws GeneralSecurityException {
        String compactKey = compactKeyText(publicKeyText);
        byte[] keyBytes = Base64.getDecoder().decode(compactKey);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(keySpec);
    }

    /**
     * Removes PEM headers, footers, and whitespace from key text.
     */
    private static String compactKeyText(String keyText) {
        return keyText
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "")
                .trim();
    }

    /**
     * Decodes Base64 text and auto-fills missing padding.
     */
    private static byte[] decodeBase64Auto(String value) {
        String text = value == null ? "" : value.trim();
        int pad = (4 - text.length() % 4) % 4;
        if (pad > 0) {
            text = text + "=".repeat(pad);
        }
        return Base64.getDecoder().decode(text);
    }

    /**
     * Reads key text from a classpath resource.
     */
    private static String loadClasspathKey(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
