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

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Optional;

/**
 * Configuration for the cipher service.
 * 
 * Example configuration in application.properties:
 * 
 * <pre>
 * mbyte.store.cipher.enabled=true
 * mbyte.store.cipher.algorithm=AES/GCM/NoPadding
 * mbyte.store.cipher.key-size=256
 * mbyte.store.cipher.secret-key=base64EncodedKey
 * </pre>
 * 
 * @author MByte Team
 */
@ConfigMapping(prefix = "mbyte.store.cipher")
public interface CipherConfig {

    /**
     * Whether encryption is enabled.
     * Default: false
     */
    @WithDefault("false")
    boolean enabled();

    /**
     * The encryption algorithm to use.
     * Default: AES/GCM/NoPadding
     */
    @WithDefault("AES/GCM/NoPadding")
    String algorithm();

    /**
     * The key size in bits.
     * Default: 256
     */
    @WithDefault("256")
    int keySize();

    /**
     * The IV size in bytes for GCM mode.
     * Default: 12 (96 bits, recommended for GCM)
     */
    @WithDefault("12")
    int ivSize();

    /**
     * The authentication tag length in bits for GCM mode.
     * Default: 128
     */
    @WithDefault("128")
    int tagLength();

    /**
     * The secret key encoded in Base64.
     * If not provided, a random key will be generated (not recommended for production).
     * 
     * IMPORTANT: For production, always provide a stable key to ensure
     * data can be decrypted after restarts.
     */
    Optional<String> secretKey();
}
