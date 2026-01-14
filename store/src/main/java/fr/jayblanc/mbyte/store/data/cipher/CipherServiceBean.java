/*
 * #%L
 * Musik Byte Store Service
 * %%
 * Copyright (C) 2024 Music Byte
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */
package fr.jayblanc.mbyte.store.data.cipher;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Implementation of CipherService using AES-256-GCM.
 * 
 * AES-256-GCM provides:
 * - 256-bit key strength (military grade encryption)
 * - Authenticated encryption (data integrity)
 * - Performance (hardware acceleration on modern CPUs)
 * 
 * Data format:
 * [12-byte IV][encrypted data with auth tag]
 * 
 * The authentication tag is automatically appended by GCM mode.
 * 
 * @author MByte Team
 */
@ApplicationScoped
public class CipherServiceBean implements CipherService {

    private static final Logger LOG = Logger.getLogger(CipherServiceBean.class);
    private static final String KEY_ALGORITHM = "AES";

    @Inject
    CipherConfig config;

    private SecretKey secretKey;
    private SecureRandom secureRandom;

    @PostConstruct
    void init() {
        this.secureRandom = new SecureRandom();
        
        if (config.enabled()) {
            initializeKey();
            LOG.infof("Cipher service initialized with algorithm: %s, key size: %d bits",
                    config.algorithm(), config.keySize());
        } else {
            LOG.info("Cipher service is disabled - data will not be encrypted");
        }
    }

    private void initializeKey() {
        if (config.secretKey().isPresent()) {
            // Use provided key
            byte[] keyBytes = Base64.getDecoder().decode(config.secretKey().get());
            if (keyBytes.length * 8 != config.keySize()) {
                throw new IllegalArgumentException(
                        String.format("Provided key size (%d bits) does not match configured key size (%d bits)",
                                keyBytes.length * 8, config.keySize()));
            }
            this.secretKey = new SecretKeySpec(keyBytes, KEY_ALGORITHM);
            LOG.debug("Using provided secret key");
        } else {
            // Generate random key (WARNING: not recommended for production)
            LOG.warn("No secret key provided - generating random key. " +
                    "This is NOT recommended for production as data will be unrecoverable after restart!");
            try {
                KeyGenerator keyGen = KeyGenerator.getInstance(KEY_ALGORITHM);
                keyGen.init(config.keySize(), secureRandom);
                this.secretKey = keyGen.generateKey();
                
                // Log the generated key so it can be saved (only in dev mode)
                String encodedKey = Base64.getEncoder().encodeToString(secretKey.getEncoded());
                LOG.warnf("Generated secret key (save this!): %s", encodedKey);
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("Failed to generate secret key", e);
            }
        }
    }

    @Override
    public InputStream encrypt(InputStream plainStream) throws CipherException {
        if (!config.enabled()) {
            return plainStream;
        }

        try {
            // Generate random IV
            byte[] iv = new byte[config.ivSize()];
            secureRandom.nextBytes(iv);

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(config.algorithm());
            GCMParameterSpec gcmSpec = new GCMParameterSpec(config.tagLength(), iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);

            // Create cipher input stream
            CipherInputStream cipherInputStream = new CipherInputStream(plainStream, cipher);

            // Prepend IV to the stream
            ByteArrayInputStream ivStream = new ByteArrayInputStream(iv);
            return new SequenceInputStream(ivStream, cipherInputStream);

        } catch (Exception e) {
            throw new CipherException("Failed to encrypt stream", e);
        }
    }

    @Override
    public InputStream decrypt(InputStream encryptedStream) throws CipherException {
        if (!config.enabled()) {
            return encryptedStream;
        }

        try {
            // Read IV from beginning of stream
            byte[] iv = new byte[config.ivSize()];
            int bytesRead = 0;
            while (bytesRead < iv.length) {
                int read = encryptedStream.read(iv, bytesRead, iv.length - bytesRead);
                if (read == -1) {
                    throw new CipherException("Encrypted stream is too short - could not read IV");
                }
                bytesRead += read;
            }

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(config.algorithm());
            GCMParameterSpec gcmSpec = new GCMParameterSpec(config.tagLength(), iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec);

            // Create cipher input stream for decryption
            return new CipherInputStream(encryptedStream, cipher);

        } catch (CipherException e) {
            throw e;
        } catch (Exception e) {
            throw new CipherException("Failed to decrypt stream", e);
        }
    }

    @Override
    public byte[] encrypt(byte[] plainData) throws CipherException {
        if (!config.enabled()) {
            return plainData;
        }

        try {
            // Generate random IV
            byte[] iv = new byte[config.ivSize()];
            secureRandom.nextBytes(iv);

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(config.algorithm());
            GCMParameterSpec gcmSpec = new GCMParameterSpec(config.tagLength(), iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);

            // Encrypt data
            byte[] encryptedData = cipher.doFinal(plainData);

            // Combine IV + encrypted data
            byte[] result = new byte[iv.length + encryptedData.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(encryptedData, 0, result, iv.length, encryptedData.length);

            return result;

        } catch (Exception e) {
            throw new CipherException("Failed to encrypt data", e);
        }
    }

    @Override
    public byte[] decrypt(byte[] encryptedData) throws CipherException {
        if (!config.enabled()) {
            return encryptedData;
        }

        try {
            if (encryptedData.length < config.ivSize()) {
                throw new CipherException("Encrypted data is too short - could not extract IV");
            }

            // Extract IV
            byte[] iv = new byte[config.ivSize()];
            System.arraycopy(encryptedData, 0, iv, 0, iv.length);

            // Extract encrypted content
            byte[] ciphertext = new byte[encryptedData.length - iv.length];
            System.arraycopy(encryptedData, iv.length, ciphertext, 0, ciphertext.length);

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(config.algorithm());
            GCMParameterSpec gcmSpec = new GCMParameterSpec(config.tagLength(), iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec);

            // Decrypt data
            return cipher.doFinal(ciphertext);

        } catch (CipherException e) {
            throw e;
        } catch (Exception e) {
            throw new CipherException("Failed to decrypt data", e);
        }
    }

    @Override
    public boolean isEnabled() {
        return config.enabled();
    }

    @Override
    public String getAlgorithm() {
        return "AES-" + config.keySize() + "-GCM";
    }

    /**
     * Utility method to generate a random key for configuration.
     * Call this once to generate a key, then save it in your configuration.
     * 
     * @return Base64-encoded secret key
     */
    public static String generateSecretKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance(KEY_ALGORITHM);
            keyGen.init(256, new SecureRandom());
            SecretKey key = keyGen.generateKey();
            return Base64.getEncoder().encodeToString(key.getEncoded());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to generate secret key", e);
        }
    }
}
