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
package fr.jayblanc.mbyte.store.data.backend.webdav;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Optional;

/**
 * Configuration for WebDAV storage backend.
 * 
 * Example configuration:
 * <pre>
 * mbyte.store.backend.webdav.enabled=true
 * mbyte.store.backend.webdav.url=https://webdav.example.com/mbyte
 * mbyte.store.backend.webdav.username=user
 * mbyte.store.backend.webdav.password=secret
 * </pre>
 * 
 * @author MByte Team
 */
@ConfigMapping(prefix = "mbyte.store.backend.webdav")
public interface WebDAVStorageConfig {

    /**
     * Whether WebDAV storage backend is enabled.
     * Default: false
     */
    @WithDefault("false")
    boolean enabled();

    /**
     * WebDAV server base URL.
     * Example: https://webdav.example.com/mbyte
     */
    Optional<String> url();

    /**
     * WebDAV username for authentication.
     */
    Optional<String> username();

    /**
     * WebDAV password for authentication.
     */
    Optional<String> password();

    /**
     * Connection timeout in milliseconds.
     * Default: 10000 (10 seconds)
     */
    @WithDefault("10000")
    int connectionTimeout();

    /**
     * Read timeout in milliseconds.
     * Default: 30000 (30 seconds)
     */
    @WithDefault("30000")
    int readTimeout();

    /**
     * Whether to auto-create directories if they don't exist.
     * Default: true
     */
    @WithDefault("true")
    boolean autoCreateDirectories();

    /**
     * Whether to verify SSL certificates.
     * Default: true
     * 
     * Set to false only for testing with self-signed certificates.
     */
    @WithDefault("true")
    boolean verifySsl();
}
