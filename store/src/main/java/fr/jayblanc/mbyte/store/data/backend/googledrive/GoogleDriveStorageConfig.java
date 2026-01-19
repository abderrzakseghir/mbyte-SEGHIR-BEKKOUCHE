/*
 * Copyright (C) 2025 Jerome Blanchard <jayblanc@gmail.com>
 * Copyright (C) 2026 Abderrazak SEGHIR <abderrazakseghir1@gmail.com>
 *
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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package fr.jayblanc.mbyte.store.data.backend.googledrive;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Optional;

/**
 * Configuration for Google Drive storage backend.
 * 
 * @author Abderrazak SEGHIR
 */
@ConfigMapping(prefix = "mbyte.store.backend.googledrive")
public interface GoogleDriveStorageConfig {

    /**
     * Whether Google Drive backend is enabled.
     */
    @WithDefault("false")
    boolean enabled();

    /**
     * OAuth2 client ID for Google API.
     */
    Optional<String> clientId();

    /**
     * OAuth2 client secret for Google API.
     */
    Optional<String> clientSecret();

    /**
     * OAuth2 refresh token for Google API.
     */
    Optional<String> refreshToken();

    /**
     * Application name for Google API.
     */
    @WithDefault("MByte-Store")
    String applicationName();

    /**
     * Root folder ID in Google Drive where files will be stored.
     * If not specified, files will be stored in the root of My Drive.
     */
    Optional<String> rootFolderId();

    /**
     * Connection timeout in milliseconds.
     */
    @WithDefault("30000")
    int connectionTimeout();

    /**
     * Read timeout in milliseconds.
     */
    @WithDefault("60000")
    int readTimeout();

}
