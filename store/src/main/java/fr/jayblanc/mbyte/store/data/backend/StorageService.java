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

import fr.jayblanc.mbyte.store.data.backend.local.LocalStorageBackend;
import fr.jayblanc.mbyte.store.data.backend.s3.S3StorageBackend;
import fr.jayblanc.mbyte.store.data.backend.webdav.WebDAVStorageBackend;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.InputStream;
import java.util.Optional;

/**
 * Storage service that delegates to the configured storage backend.
 * Supports LOCAL, S3, and WebDAV backends.
 * 
 * @author MByte Team
 */
@ApplicationScoped
public class StorageService {

    private static final Logger LOG = Logger.getLogger(StorageService.class);

    @ConfigProperty(name = "mbyte.store.backend.type", defaultValue = "LOCAL")
    String backendType;

    @Inject
    LocalStorageBackend localBackend;

    @Inject
    S3StorageBackend s3Backend;

    @Inject
    WebDAVStorageBackend webdavBackend;

    private StorageBackend activeBackend;

    @PostConstruct
    void init() {
        selectBackend();
        LOG.infof("Storage service initialized with backend: %s (available: %s)",
                activeBackend.getName(), activeBackend.isAvailable());
    }

    private void selectBackend() {
        StorageBackendType type;
        try {
            type = StorageBackendType.valueOf(backendType.toUpperCase());
        } catch (IllegalArgumentException e) {
            LOG.warnf("Unknown backend type '%s', falling back to LOCAL", backendType);
            type = StorageBackendType.LOCAL;
        }

        switch (type) {
            case S3:
                if (s3Backend.isAvailable()) {
                    activeBackend = s3Backend;
                } else {
                    LOG.warn("S3 backend not available, falling back to LOCAL");
                    activeBackend = localBackend;
                }
                break;
            case WEBDAV:
                if (webdavBackend.isAvailable()) {
                    activeBackend = webdavBackend;
                } else {
                    LOG.warn("WebDAV backend not available, falling back to LOCAL");
                    activeBackend = localBackend;
                }
                break;
            case LOCAL:
            default:
                activeBackend = localBackend;
                break;
        }
    }

    /**
     * Get the currently active storage backend.
     */
    public StorageBackend getActiveBackend() {
        return activeBackend;
    }

    /**
     * Get the name of the active backend.
     */
    public String getBackendName() {
        return activeBackend.getName();
    }

    /**
     * Check if storage service is available.
     */
    public boolean isAvailable() {
        return activeBackend != null && activeBackend.isAvailable();
    }

    /**
     * Check if a file exists.
     * 
     * @param storeId the store identifier
     * @param path the file path within the store
     * @return true if the file exists
     */
    public boolean exists(String storeId, String path) {
        String key = buildKey(storeId, path);
        return activeBackend.exists(key);
    }

    /**
     * Store a file.
     * 
     * @param storeId the store identifier
     * @param path the file path within the store
     * @param data the file data
     * @throws StorageBackendException if storage fails
     */
    public void put(String storeId, String path, InputStream data) throws StorageBackendException {
        String key = buildKey(storeId, path);
        activeBackend.put(key, data);
    }

    /**
     * Retrieve a file.
     * 
     * @param storeId the store identifier
     * @param path the file path within the store
     * @return the file data as an input stream
     * @throws StorageBackendException if retrieval fails
     */
    public InputStream get(String storeId, String path) throws StorageBackendException {
        String key = buildKey(storeId, path);
        return activeBackend.get(key);
    }

    /**
     * Delete a file.
     * 
     * @param storeId the store identifier
     * @param path the file path within the store
     * @throws StorageBackendException if deletion fails
     */
    public void delete(String storeId, String path) throws StorageBackendException {
        String key = buildKey(storeId, path);
        activeBackend.delete(key);
    }

    /**
     * Get the size of a file.
     * 
     * @param storeId the store identifier
     * @param path the file path within the store
     * @return the file size in bytes
     * @throws StorageBackendException if size retrieval fails
     */
    public long size(String storeId, String path) throws StorageBackendException {
        String key = buildKey(storeId, path);
        return activeBackend.size(key);
    }

    /**
     * Build a storage key from store ID and path.
     * Format: {storeId}/{path}
     */
    private String buildKey(String storeId, String path) {
        String normalizedPath = path.startsWith("/") ? path.substring(1) : path;
        return storeId + "/" + normalizedPath;
    }

    /**
     * Enum for supported storage backend types.
     */
    public enum StorageBackendType {
        LOCAL,
        S3,
        WEBDAV
    }
}
