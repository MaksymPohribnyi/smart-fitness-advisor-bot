package com.ua.pohribnyi.fitadvisorbot.service.token;

import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class TokenEncryptionService {

	@Value("${security.encryption.key}")
	private String encryptionKeyString;

	private static final String ALGORITHM = "AES";
	private static final String CIPHER_ALGORITHM = "AES";

	public String encrypt(String plainText) {
		try {
			if (plainText == null || plainText.isEmpty()) {
				return plainText;
			}

			SecretKey key = getSecretKey();
			Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
			cipher.init(Cipher.ENCRYPT_MODE, key);

			byte[] encryptedBytes = cipher.doFinal(plainText.getBytes());
			return Base64.getEncoder().encodeToString(encryptedBytes);

		} catch (Exception e) {
			log.error("Error encrypting token: {}", e.getMessage(), e);
			throw new RuntimeException("Token encryption failed", e);
		}
	}

	public String decrypt(String encryptedText) {
		try {
			if (encryptedText == null || encryptedText.isEmpty()) {
				return encryptedText;
			}

			SecretKey key = getSecretKey();
			Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
			cipher.init(Cipher.DECRYPT_MODE, key);

			byte[] decodedBytes = Base64.getDecoder().decode(encryptedText);
			byte[] decryptedBytes = cipher.doFinal(decodedBytes);
			return new String(decryptedBytes);

		} catch (Exception e) {
			log.error("Error decrypting token: {}", e.getMessage(), e);
			throw new RuntimeException("Token decryption failed", e);
		}
	}

	private SecretKey getSecretKey() {
		byte[] decodedKey = Base64.getDecoder().decode(encryptionKeyString);
		return new SecretKeySpec(decodedKey, 0, decodedKey.length, ALGORITHM);
	}
}
