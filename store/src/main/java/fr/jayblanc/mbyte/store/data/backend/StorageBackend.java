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
package fr.jayblanc.mbyte.store.data.backend;

import java.io.InputStream;

/**
 * Interface defining a storage backend for persisting data.
 * Implementations can use local filesystem, S3, WebDAV, or any other storage solution.
 * 
 * @author Abderrazak SEGHIR
 */
public interface StorageBackend {

    /**
     * Returns the name of this storage backend.
     * @return the backend name (e.g., "local", "s3", "webdav")
     */
    String getName();

    /**
     * Checks if this backend is available and properly configured.
     * @return true if the backend is ready to use
     */
    boolean isAvailable();

    /**
     * Checks if a file exists in the storage.
     * @param key the unique identifier of the file
     * @return true if the file exists
     */
    boolean exists(String key);

    /**
     * Stores data in the backend.
     * @param key the unique identifier for the data
     * @param data the input stream containing the data to store
     * @throws StorageBackendException if storage fails
     */
    void put(String key, InputStream data) throws StorageBackendException;

    /**
     * Retrieves data from the backend.
     * @param key the unique identifier of the data
     * @return an input stream to read the data
     * @throws StorageBackendException if retrieval fails or data not found
     */
    InputStream get(String key) throws StorageBackendException;

    /**
     * Deletes data from the backend.
     * @param key the unique identifier of the data to delete
     * @throws StorageBackendException if deletion fails
     */
    void delete(String key) throws StorageBackendException;

    /**
     * Returns the size of the stored data.
     * @param key the unique identifier of the data
     * @return the size in bytes
     * @throws StorageBackendException if size cannot be determined
     */
    long size(String key) throws StorageBackendException;

}
