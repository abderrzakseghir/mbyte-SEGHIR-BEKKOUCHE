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
package fr.jayblanc.mbyte.store.data.backend.dropbox;

import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.oauth.DbxCredential;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.GetMetadataErrorException;
import com.dropbox.core.v2.files.WriteMode;
import fr.jayblanc.mbyte.store.data.backend.StorageBackend;
import fr.jayblanc.mbyte.store.data.backend.StorageBackendException;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Dropbox storage backend implementation.
 * Uses Dropbox API v2 with OAuth2 authentication.
 * 
 * @author Abderrazak SEGHIR
 */
@ApplicationScoped
public class DropboxStorageBackend implements StorageBackend {

    private static final Logger LOG = Logger.getLogger(DropboxStorageBackend.class);
    private static final String BACKEND_NAME = "DROPBOX";

    @Inject
    DropboxStorageConfig config;

    private DbxClientV2 dropboxClient;
    private boolean available = false;
    private String rootPath;

    @PostConstruct
    void init() {
        if (!config.enabled()) {
            LOG.info("Dropbox storage backend is disabled");
            return;
        }

        try {
            initializeClient();
            this.rootPath = config.rootPath();
            ensureRootFolderExists();
            this.available = true;
            LOG.infof("Dropbox storage backend initialized - rootPath: %s", rootPath);
        } catch (Exception e) {
            LOG.error("Failed to initialize Dropbox storage backend", e);
            this.available = false;
        }
    }

    private void initializeClient() {
        DbxRequestConfig requestConfig = DbxRequestConfig.newBuilder("MByte-Store")
                .withAutoRetryEnabled()
                .build();

        // Try to use refresh token first, then fall back to access token
        if (config.refreshToken().isPresent() && config.appKey().isPresent() && config.appSecret().isPresent()) {
            DbxCredential credential = new DbxCredential(
                    config.accessToken().orElse(""),
                    -1L, // Expires at (not used with refresh token)
                    config.refreshToken().get(),
                    config.appKey().get(),
                    config.appSecret().get()
            );
            this.dropboxClient = new DbxClientV2(requestConfig, credential);
        } else if (config.accessToken().isPresent()) {
            this.dropboxClient = new DbxClientV2(requestConfig, config.accessToken().get());
        } else {
            throw new IllegalStateException("Dropbox access token or refresh token with app credentials required");
        }
    }

    private void ensureRootFolderExists() throws DbxException {
        try {
            dropboxClient.files().getMetadata(rootPath);
        } catch (GetMetadataErrorException e) {
            if (e.errorValue.isPath() && e.errorValue.getPathValue().isNotFound()) {
                dropboxClient.files().createFolderV2(rootPath);
                LOG.infof("Created root folder: %s", rootPath);
            } else {
                throw e;
            }
        }
    }

    @Override
    public String getName() {
        return BACKEND_NAME;
    }

    @Override
    public boolean isAvailable() {
        return available && config.enabled();
    }

    @Override
    public boolean exists(String key) {
        if (!isAvailable()) {
            return false;
        }

        try {
            String path = buildPath(key);
            dropboxClient.files().getMetadata(path);
            return true;
        } catch (GetMetadataErrorException e) {
            if (e.errorValue.isPath() && e.errorValue.getPathValue().isNotFound()) {
                return false;
            }
            LOG.debugf("Error checking existence for key '%s': %s", key, e.getMessage());
            return false;
        } catch (DbxException e) {
            LOG.debugf("Error checking existence for key '%s': %s", key, e.getMessage());
            return false;
        }
    }

    @Override
    public void put(String key, InputStream data) throws StorageBackendException {
        if (!isAvailable()) {
            throw new StorageBackendException("Dropbox backend is not available");
        }

        try {
            String path = buildPath(key);
            
            // Read all data
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = data.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            byte[] content = baos.toByteArray();

            dropboxClient.files().uploadBuilder(path)
                    .withMode(WriteMode.OVERWRITE)
                    .uploadAndFinish(new ByteArrayInputStream(content));
            
            LOG.debugf("Uploaded file to Dropbox: %s", path);
        } catch (DbxException | IOException e) {
            throw new StorageBackendException("Failed to store file in Dropbox: " + key, e);
        }
    }

    @Override
    public InputStream get(String key) throws StorageBackendException {
        if (!isAvailable()) {
            throw new StorageBackendException("Dropbox backend is not available");
        }

        try {
            String path = buildPath(key);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            dropboxClient.files().download(path).download(baos);
            return new ByteArrayInputStream(baos.toByteArray());
        } catch (DbxException | IOException e) {
            throw new StorageBackendException("Failed to retrieve file from Dropbox: " + key, e);
        }
    }

    @Override
    public void delete(String key) throws StorageBackendException {
        if (!isAvailable()) {
            throw new StorageBackendException("Dropbox backend is not available");
        }

        try {
            String path = buildPath(key);
            dropboxClient.files().deleteV2(path);
            LOG.debugf("Deleted file from Dropbox: %s", path);
        } catch (DbxException e) {
            throw new StorageBackendException("Failed to delete file from Dropbox: " + key, e);
        }
    }

    @Override
    public long size(String key) throws StorageBackendException {
        if (!isAvailable()) {
            throw new StorageBackendException("Dropbox backend is not available");
        }

        try {
            String path = buildPath(key);
            FileMetadata metadata = (FileMetadata) dropboxClient.files().getMetadata(path);
            return metadata.getSize();
        } catch (DbxException e) {
            throw new StorageBackendException("Failed to get file size from Dropbox: " + key, e);
        }
    }

    private String buildPath(String key) {
        String normalizedKey = key.startsWith("/") ? key : "/" + key;
        return rootPath + normalizedKey;
    }
}
