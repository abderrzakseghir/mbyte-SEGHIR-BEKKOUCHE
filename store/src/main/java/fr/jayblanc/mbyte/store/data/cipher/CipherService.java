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

import java.io.InputStream;

/**
 * Service interface for encrypting and decrypting data streams.
 * 
 * This service provides transparent encryption for data stored in external
 * storage backends. It uses AES-256-GCM for authenticated encryption,
 * ensuring both confidentiality and integrity of the stored data.
 * 
 * @author MByte Team
 */
public interface CipherService {

    /**
     * Encrypts an input stream using AES-256-GCM.
     * 
     * The encrypted output includes:
     * - 12-byte random IV (nonce)
     * - Encrypted data
     * - 16-byte authentication tag (appended by GCM mode)
     * 
     * @param plainStream the input stream containing plaintext data
     * @return an input stream containing the encrypted data
     * @throws CipherException if encryption fails
     */
    InputStream encrypt(InputStream plainStream) throws CipherException;

    /**
     * Decrypts an input stream encrypted with AES-256-GCM.
     * 
     * @param encryptedStream the input stream containing encrypted data
     * @return an input stream containing the decrypted plaintext
     * @throws CipherException if decryption fails or authentication tag verification fails
     */
    InputStream decrypt(InputStream encryptedStream) throws CipherException;

    /**
     * Encrypts data bytes using AES-256-GCM.
     * 
     * @param plainData the plaintext data to encrypt
     * @return the encrypted data including IV and authentication tag
     * @throws CipherException if encryption fails
     */
    byte[] encrypt(byte[] plainData) throws CipherException;

    /**
     * Decrypts data bytes encrypted with AES-256-GCM.
     * 
     * @param encryptedData the encrypted data including IV and authentication tag
     * @return the decrypted plaintext data
     * @throws CipherException if decryption fails or authentication tag verification fails
     */
    byte[] decrypt(byte[] encryptedData) throws CipherException;

    /**
     * Checks if encryption is enabled.
     * 
     * @return true if encryption is enabled, false otherwise
     */
    boolean isEnabled();

    /**
     * Gets the encryption algorithm being used.
     * 
     * @return the encryption algorithm name (e.g., "AES-256-GCM")
     */
    String getAlgorithm();
}
