/*
 * Copyright (C) 2025 Jerome Blanchard <jayblanc@gmail.com>
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
package fr.jayblanc.mbyte.store.data;

import fr.jayblanc.mbyte.store.data.backend.StorageBackend;
import fr.jayblanc.mbyte.store.data.backend.StorageBackendException;
import fr.jayblanc.mbyte.store.data.backend.StorageService;
import fr.jayblanc.mbyte.store.data.backend.UserAwareStorageService;
import fr.jayblanc.mbyte.store.data.cipher.CipherException;
import fr.jayblanc.mbyte.store.data.cipher.CipherService;
import fr.jayblanc.mbyte.store.data.exception.DataNotFoundException;
import fr.jayblanc.mbyte.store.data.exception.DataStoreException;
import fr.jayblanc.mbyte.store.data.hash.HashedFilterInputStream;
import io.quarkus.runtime.Startup;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.core.MediaType;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Jerome Blanchard
 */
@Singleton
public class DataStoreBean implements DataStore {

    private static final Logger LOGGER = Logger.getLogger(DataStore.class.getName());

    @Inject
    DataStoreConfig config;

    @Inject
    StorageService storageService;

    @Inject
    UserAwareStorageService userAwareStorageService;

    @Inject
    CipherService cipherService;

    @ConfigProperty(name = "mbyte.store.id", defaultValue = "default")
    String storeId;

    private Path base;
    private Tika tika;

    public DataStoreBean() {
    }

    @Startup
    public void init() {
        this.base = Paths.get(config.home());
        LOGGER.log(Level.FINEST, "Initializing service with base folder: " + base);
        try {
            Files.createDirectories(base);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "unable to initialize data store", e);
        }
        this.tika = new Tika();
        
        // Log storage backend info
        if (storageService != null && storageService.isAvailable()) {
            LOGGER.log(Level.INFO, "Storage backend: " + storageService.getBackendName());
        } else {
            LOGGER.log(Level.INFO, "Storage backend: LOCAL (fallback)");
        }
        
        // Log cipher info
        if (cipherService != null && cipherService.isEnabled()) {
            LOGGER.log(Level.INFO, "Encryption enabled: " + cipherService.getAlgorithm());
        } else {
            LOGGER.log(Level.INFO, "Encryption: DISABLED");
        }
    }

    private boolean useEncryption() {
        return cipherService != null && cipherService.isEnabled() && useExternalStorage();
    }

    private boolean useExternalStorage() {
        return storageService != null && storageService.isAvailable() 
               && !"LOCAL".equalsIgnoreCase(storageService.getBackendName());
    }

    @Override
    public boolean exists(String key) {
        if (useExternalStorage()) {
            // Use user-aware storage service to check in user's preferred backend
            return userAwareStorageService.exists(storeId, key);
        }
        Path file = Paths.get(base.toString(), key);
        return Files.exists(file);
    }

    @Override
    public String put(InputStream is) throws DataStoreException {
        String tmpkey = UUID.randomUUID().toString();
        
        try {
            // Read and hash the content
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            byte[] content = baos.toByteArray();
            
            // Calculate hash
            String key;
            try (HashedFilterInputStream his = HashedFilterInputStream.SHA256(new ByteArrayInputStream(content))) {
                byte[] hashBuffer = new byte[8192];
                while (his.read(hashBuffer) != -1) {
                    // Just read to compute hash
                }
                key = his.getHash();
            }
            
            // Check if already exists
            if (exists(key)) {
                return key;
            }
            
            // Store based on backend
            if (useExternalStorage()) {
                try {
                    // Encrypt content if encryption is enabled
                    byte[] dataToStore = content;
                    if (useEncryption()) {
                        try {
                            dataToStore = cipherService.encrypt(content);
                            LOGGER.log(Level.FINE, "Encrypted file before storage: " + key);
                        } catch (CipherException e) {
                            throw new DataStoreException("Failed to encrypt data", e);
                        }
                    }
                    // Use user-aware storage service to respect user's backend preferences
                    userAwareStorageService.put(storeId, key, new ByteArrayInputStream(dataToStore));
                    LOGGER.log(Level.INFO, "Stored file using user's preferred backend" + 
                               (useEncryption() ? " (encrypted)" : "") + ": " + key);
                } catch (StorageBackendException e) {
                    throw new DataStoreException("Failed to store to external backend", e);
                }
            } else {
                // Local storage (original behavior)
                Path tmpfile = Paths.get(base.toString(), tmpkey);
                Files.copy(new ByteArrayInputStream(content), tmpfile, StandardCopyOption.REPLACE_EXISTING);
                Path file = Paths.get(base.toString(), key);
                if (!Files.exists(file)) {
                    Files.move(tmpfile, file);
                } else {
                    Files.delete(tmpfile);
                }
            }
            
            return key;
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new DataStoreException("unexpected error during stream copy", e);
        }
    }

    @Override
    public InputStream get(String key) throws DataStoreException, DataNotFoundException {
        if (useExternalStorage()) {
            try {
                // Use user-aware storage service to respect user's backend preferences
                InputStream encryptedStream = userAwareStorageService.get(storeId, key);
                // Decrypt content if encryption is enabled
                if (useEncryption()) {
                    try {
                        InputStream decryptedStream = cipherService.decrypt(encryptedStream);
                        LOGGER.log(Level.FINE, "Decrypted file from storage: " + key);
                        return decryptedStream;
                    } catch (CipherException e) {
                        throw new DataStoreException("Failed to decrypt data", e);
                    }
                }
                return encryptedStream;
            } catch (StorageBackendException e) {
                if (e.getMessage().contains("not found")) {
                    throw new DataNotFoundException("file not found in storage for key: " + key);
                }
                throw new DataStoreException("unexpected error while retrieving from external backend", e);
            }
        }
        
        Path file = Paths.get(base.toString(), key);
        if (!Files.exists(file)) {
            throw new DataNotFoundException("file not found in storage for key: " + key);
        }
        try {
            return Files.newInputStream(file, StandardOpenOption.READ);
        } catch (IOException e) {
            throw new DataStoreException("unexpected error while opening stream", e);
        }
    }

    @Override
    public String type(String key, String name) throws DataNotFoundException, DataStoreException {
        LOGGER.log(Level.FINE, "Extract type for key: " + key);
        
        try (InputStream stream = get(key)) {
            return tika.detect(stream, name);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Unable to detect mimetype: " + e.getMessage(), e);
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    @Override
    public long size(String key) throws DataStoreException, DataNotFoundException {
        if (useExternalStorage()) {
            try {
                return storageService.size(storeId, key);
            } catch (StorageBackendException e) {
                if (e.getMessage().contains("not found")) {
                    throw new DataNotFoundException("file not found in storage for key: " + key);
                }
                throw new DataStoreException("unexpected error while getting size from external backend", e);
            }
        }
        
        Path file = Paths.get(base.toString(), key);
        if (!Files.exists(file)) {
            throw new DataNotFoundException("file not found in storage for key: " + key);
        }
        try {
            return Files.size(file);
        } catch (IOException e) {
            throw new DataStoreException("unexpected error while getting stream size", e);
        }
    }

    @Override
    public String extract(String key, String name, String type) throws DataStoreException, DataNotFoundException {
        LOGGER.log(Level.FINE, "Extract text for key: " + key);
        
        try (InputStream stream = get(key)) {
            BodyContentHandler handler = new BodyContentHandler();
            AutoDetectParser parser = new AutoDetectParser();
            Metadata metadata = new Metadata();
            metadata.set(Metadata.CONTENT_TYPE, type);
            parser.parse(stream, handler, metadata);
            return handler.toString();
        } catch (IOException | SAXException | TikaException e) {
            throw new DataStoreException("unexpected error while opening stream", e);
        }
    }

    @Override
    public void delete(String key) throws DataStoreException {
        if (useExternalStorage()) {
            try {
                // Use user-aware storage service to delete from user's preferred backend
                userAwareStorageService.delete(storeId, key);
                LOGGER.log(Level.INFO, "Deleted file from user's preferred backend: " + key);
            } catch (StorageBackendException e) {
                throw new DataStoreException("Failed to delete from external backend", e);
            }
        } else {
            Path file = Paths.get(base.toString(), key);
            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                throw new DataStoreException("Failed to delete local file", e);
            }
        }
    }
}
