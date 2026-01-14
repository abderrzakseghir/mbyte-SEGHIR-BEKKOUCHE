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
package fr.jayblanc.mbyte.store.data.backend;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Configuration for selecting and managing storage backends.
 * 
 * This configuration determines which backend to use for storing data.
 * Available backends: LOCAL, S3, WEBDAV
 * 
 * Example configuration:
 * <pre>
 * mbyte.store.backend.type=S3
 * mbyte.store.backend.encrypt=true
 * </pre>
 * 
 * @author MByte Team
 */
@ConfigMapping(prefix = "mbyte.store.backend")
public interface StorageBackendConfig {

    /**
     * The type of storage backend to use.
     * Default: LOCAL
     * 
     * Supported values: LOCAL, S3, WEBDAV
     */
    @WithDefault("LOCAL")
    StorageBackendType type();

    /**
     * Whether to encrypt data before storing it in the backend.
     * Default: false
     * 
     * When enabled, data is encrypted using AES-256-GCM before being
     * stored in the backend. Configure the cipher service settings
     * in mbyte.store.cipher.* properties.
     */
    @WithDefault("false")
    boolean encrypt();

    /**
     * Whether to fallback to local storage if the configured backend is unavailable.
     * Default: false
     * 
     * WARNING: Enabling fallback may cause data consistency issues if
     * the primary backend becomes available again.
     */
    @WithDefault("false")
    boolean fallbackToLocal();
}
