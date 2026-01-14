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
package fr.jayblanc.mbyte.store.data.backend.local;

import fr.jayblanc.mbyte.store.data.backend.StorageBackend;
import fr.jayblanc.mbyte.store.data.backend.StorageBackendException;
import io.quarkus.arc.lookup.LookupIfProperty;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Local filesystem storage backend implementation.
 * Stores files directly on the local filesystem.
 * 
 * @author Abderrazak SEGHIR
 */
@ApplicationScoped
@LookupIfProperty(name = "store.data.backend.type", stringValue = "local", lookupIfMissing = true)
public class LocalStorageBackend implements StorageBackend {

    private static final Logger LOGGER = Logger.getLogger(LocalStorageBackend.class.getName());
    private static final String BACKEND_NAME = "local";

    @Inject
    LocalStorageConfig config;

    private Path basePath;
    private boolean available = false;

    @PostConstruct
    void init() {
        try {
            this.basePath = Paths.get(config.basePath());
            Files.createDirectories(basePath);
            this.available = true;
            LOGGER.log(Level.INFO, "LocalStorageBackend initialized with base path: " + basePath);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize LocalStorageBackend", e);
            this.available = false;
        }
    }

    @Override
    public String getName() {
        return BACKEND_NAME;
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public boolean exists(String key) {
        Path file = basePath.resolve(key);
        return Files.exists(file);
    }

    @Override
    public void put(String key, InputStream data) throws StorageBackendException {
        Path file = basePath.resolve(key);
        try {
            Files.copy(data, file, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.log(Level.FINE, "File stored locally: " + key);
        } catch (IOException e) {
            throw new StorageBackendException("Failed to store file locally: " + key, e);
        }
    }

    @Override
    public InputStream get(String key) throws StorageBackendException {
        Path file = basePath.resolve(key);
        if (!Files.exists(file)) {
            throw new StorageBackendException("File not found in local storage: " + key);
        }
        try {
            return Files.newInputStream(file, StandardOpenOption.READ);
        } catch (IOException e) {
            throw new StorageBackendException("Failed to read file from local storage: " + key, e);
        }
    }

    @Override
    public void delete(String key) throws StorageBackendException {
        Path file = basePath.resolve(key);
        try {
            Files.deleteIfExists(file);
            LOGGER.log(Level.FINE, "File deleted from local storage: " + key);
        } catch (IOException e) {
            throw new StorageBackendException("Failed to delete file from local storage: " + key, e);
        }
    }

    @Override
    public long size(String key) throws StorageBackendException {
        Path file = basePath.resolve(key);
        if (!Files.exists(file)) {
            throw new StorageBackendException("File not found in local storage: " + key);
        }
        try {
            return Files.size(file);
        } catch (IOException e) {
            throw new StorageBackendException("Failed to get file size from local storage: " + key, e);
        }
    }

}
