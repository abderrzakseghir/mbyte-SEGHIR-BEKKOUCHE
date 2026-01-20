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

import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.oauth.DbxCredential;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.GetMetadataErrorException;
import com.dropbox.core.v2.files.WriteMode;
import fr.jayblanc.mbyte.store.auth.AuthenticationService;
import fr.jayblanc.mbyte.store.data.settings.StorageSettings;
import fr.jayblanc.mbyte.store.data.settings.StorageSettingsService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * User-aware storage service that uses the user's configured backends.
 * This service reads user settings from the database and dynamically
 * configures storage backends accordingly.
 * 
 * @author Abderrazak SEGHIR
 */
@RequestScoped
public class UserAwareStorageService {

    private static final Logger LOG = Logger.getLogger(UserAwareStorageService.class);

    @Inject
    AuthenticationService authService;

    @Inject
    StorageSettingsService settingsService;

    @Inject
    StorageService defaultStorageService;

    /**
     * Store a file using the user's configured backend.
     * 
     * @param storeId the store identifier
     * @param path the file path within the store
     * @param data the file data
     * @throws StorageBackendException if storage fails
     */
    public void put(String storeId, String path, InputStream data) throws StorageBackendException {
        String owner = getOwner();
        StorageSettings settings = settingsService.getOrCreateSettings(owner);
        
        // Determine which backend to use based on user settings
        if (settings.isDropboxEnabled() && hasDropboxCredentials(settings)) {
            LOG.infof("Using Dropbox backend for user: %s", owner);
            putToDropbox(settings, storeId, path, data);
        } else if (settings.isGoogleDriveEnabled() && hasGoogleDriveCredentials(settings)) {
            LOG.infof("Using Google Drive backend for user: %s", owner);
            putToGoogleDrive(settings, storeId, path, data);
        } else if (settings.isOneDriveEnabled() && hasOneDriveCredentials(settings)) {
            LOG.infof("Using OneDrive backend for user: %s", owner);
            putToOneDrive(settings, storeId, path, data);
        } else if (settings.isWebdavEnabled() && hasWebDavCredentials(settings)) {
            LOG.infof("Using WebDAV backend for user: %s", owner);
            putToWebDav(settings, storeId, path, data);
        } else if (settings.isS3Enabled()) {
            LOG.infof("Using default S3 backend for user: %s", owner);
            defaultStorageService.put(storeId, path, data);
        } else {
            LOG.infof("Using default storage backend for user: %s", owner);
            defaultStorageService.put(storeId, path, data);
        }
    }

    /**
     * Retrieve a file using the user's configured backend.
     */
    public InputStream get(String storeId, String path) throws StorageBackendException {
        String owner = getOwner();
        StorageSettings settings = settingsService.getOrCreateSettings(owner);
        
        // Try to get from the enabled backend
        if (settings.isDropboxEnabled() && hasDropboxCredentials(settings)) {
            try {
                return getFromDropbox(settings, storeId, path);
            } catch (StorageBackendException e) {
                LOG.warnf("Failed to get from Dropbox, falling back to default: %s", e.getMessage());
            }
        }
        
        // Fallback to default
        return defaultStorageService.get(storeId, path);
    }

    /**
     * Check if a file exists using the user's configured backend.
     */
    public boolean exists(String storeId, String path) {
        String owner = getOwner();
        StorageSettings settings = settingsService.getOrCreateSettings(owner);
        
        if (settings.isDropboxEnabled() && hasDropboxCredentials(settings)) {
            try {
                return existsInDropbox(settings, storeId, path);
            } catch (Exception e) {
                LOG.warnf("Failed to check existence in Dropbox: %s", e.getMessage());
            }
        }
        
        return defaultStorageService.exists(storeId, path);
    }

    /**
     * Delete a file using the user's configured backend.
     */
    public void delete(String storeId, String path) throws StorageBackendException {
        String owner = getOwner();
        StorageSettings settings = settingsService.getOrCreateSettings(owner);
        
        if (settings.isDropboxEnabled() && hasDropboxCredentials(settings)) {
            deleteFromDropbox(settings, storeId, path);
        } else {
            defaultStorageService.delete(storeId, path);
        }
    }

    private String getOwner() {
        try {
            return authService.getConnectedProfile().getUsername();
        } catch (Exception e) {
            LOG.warn("Could not get connected user, using default");
            return "default";
        }
    }

    // Dropbox operations

    private boolean hasDropboxCredentials(StorageSettings settings) {
        return settings.getDropboxAccessToken() != null && !settings.getDropboxAccessToken().isEmpty();
    }

    private DbxClientV2 createDropboxClient(StorageSettings settings) {
        DbxRequestConfig config = DbxRequestConfig.newBuilder("MByte-Store")
                .withAutoRetryEnabled()
                .build();

        if (settings.getDropboxRefreshToken() != null && !settings.getDropboxRefreshToken().isEmpty()
                && settings.getDropboxAppKey() != null && !settings.getDropboxAppKey().isEmpty()
                && settings.getDropboxAppSecret() != null && !settings.getDropboxAppSecret().isEmpty()) {
            DbxCredential credential = new DbxCredential(
                    settings.getDropboxAccessToken(),
                    -1L,
                    settings.getDropboxRefreshToken(),
                    settings.getDropboxAppKey(),
                    settings.getDropboxAppSecret()
            );
            return new DbxClientV2(config, credential);
        } else {
            return new DbxClientV2(config, settings.getDropboxAccessToken());
        }
    }

    private void putToDropbox(StorageSettings settings, String storeId, String path, InputStream data) 
            throws StorageBackendException {
        try {
            DbxClientV2 client = createDropboxClient(settings);
            String rootPath = settings.getDropboxRootPath() != null ? settings.getDropboxRootPath() : "/mbyte-store";
            String fullPath = rootPath + "/" + storeId + "/" + path;
            
            // Ensure path starts with /
            if (!fullPath.startsWith("/")) {
                fullPath = "/" + fullPath;
            }
            
            // Read all data into memory for upload
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            data.transferTo(baos);
            byte[] bytes = baos.toByteArray();
            
            // Upload to Dropbox
            FileMetadata metadata = client.files()
                    .uploadBuilder(fullPath)
                    .withMode(WriteMode.OVERWRITE)
                    .uploadAndFinish(new ByteArrayInputStream(bytes));
            
            LOG.infof("File uploaded to Dropbox: %s (size: %d bytes)", fullPath, metadata.getSize());
        } catch (DbxException | IOException e) {
            throw new StorageBackendException("Failed to upload to Dropbox: " + e.getMessage(), e);
        }
    }

    private InputStream getFromDropbox(StorageSettings settings, String storeId, String path) 
            throws StorageBackendException {
        try {
            DbxClientV2 client = createDropboxClient(settings);
            String rootPath = settings.getDropboxRootPath() != null ? settings.getDropboxRootPath() : "/mbyte-store";
            String fullPath = rootPath + "/" + storeId + "/" + path;
            
            if (!fullPath.startsWith("/")) {
                fullPath = "/" + fullPath;
            }
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            client.files().download(fullPath).download(baos);
            return new ByteArrayInputStream(baos.toByteArray());
        } catch (DbxException | IOException e) {
            throw new StorageBackendException("Failed to download from Dropbox: " + e.getMessage(), e);
        }
    }

    private boolean existsInDropbox(StorageSettings settings, String storeId, String path) {
        try {
            DbxClientV2 client = createDropboxClient(settings);
            String rootPath = settings.getDropboxRootPath() != null ? settings.getDropboxRootPath() : "/mbyte-store";
            String fullPath = rootPath + "/" + storeId + "/" + path;
            
            if (!fullPath.startsWith("/")) {
                fullPath = "/" + fullPath;
            }
            
            client.files().getMetadata(fullPath);
            return true;
        } catch (GetMetadataErrorException e) {
            return false;
        } catch (DbxException e) {
            LOG.warnf("Error checking existence in Dropbox: %s", e.getMessage());
            return false;
        }
    }

    private void deleteFromDropbox(StorageSettings settings, String storeId, String path) 
            throws StorageBackendException {
        try {
            DbxClientV2 client = createDropboxClient(settings);
            String rootPath = settings.getDropboxRootPath() != null ? settings.getDropboxRootPath() : "/mbyte-store";
            String fullPath = rootPath + "/" + storeId + "/" + path;
            
            if (!fullPath.startsWith("/")) {
                fullPath = "/" + fullPath;
            }
            
            client.files().deleteV2(fullPath);
            LOG.infof("File deleted from Dropbox: %s", fullPath);
        } catch (DbxException e) {
            throw new StorageBackendException("Failed to delete from Dropbox: " + e.getMessage(), e);
        }
    }

    // Google Drive operations (placeholder - needs implementation)

    private boolean hasGoogleDriveCredentials(StorageSettings settings) {
        return settings.getGoogleDriveClientId() != null && !settings.getGoogleDriveClientId().isEmpty()
                && settings.getGoogleDriveRefreshToken() != null && !settings.getGoogleDriveRefreshToken().isEmpty();
    }

    private void putToGoogleDrive(StorageSettings settings, String storeId, String path, InputStream data) 
            throws StorageBackendException {
        // TODO: Implement Google Drive upload
        LOG.warn("Google Drive upload not yet fully implemented, falling back to default");
        defaultStorageService.put(storeId, path, data);
    }

    // OneDrive operations (placeholder - needs implementation)

    private boolean hasOneDriveCredentials(StorageSettings settings) {
        return settings.getOneDriveClientId() != null && !settings.getOneDriveClientId().isEmpty()
                && settings.getOneDriveRefreshToken() != null && !settings.getOneDriveRefreshToken().isEmpty();
    }

    private void putToOneDrive(StorageSettings settings, String storeId, String path, InputStream data) 
            throws StorageBackendException {
        // TODO: Implement OneDrive upload
        LOG.warn("OneDrive upload not yet fully implemented, falling back to default");
        defaultStorageService.put(storeId, path, data);
    }

    // WebDAV operations (placeholder - needs implementation)

    private boolean hasWebDavCredentials(StorageSettings settings) {
        return settings.getWebdavUrl() != null && !settings.getWebdavUrl().isEmpty();
    }

    private void putToWebDav(StorageSettings settings, String storeId, String path, InputStream data) 
            throws StorageBackendException {
        // TODO: Implement WebDAV upload
        LOG.warn("WebDAV upload not yet fully implemented, falling back to default");
        defaultStorageService.put(storeId, path, data);
    }
}
