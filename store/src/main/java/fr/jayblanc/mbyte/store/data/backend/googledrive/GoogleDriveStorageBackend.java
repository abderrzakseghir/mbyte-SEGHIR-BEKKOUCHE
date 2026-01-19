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

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
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
import java.security.GeneralSecurityException;
import java.util.Collections;

/**
 * Google Drive storage backend implementation.
 * Uses OAuth2 for authentication and Google Drive API v3.
 * 
 * @author Abderrazak SEGHIR
 */
@ApplicationScoped
public class GoogleDriveStorageBackend implements StorageBackend {

    private static final Logger LOG = Logger.getLogger(GoogleDriveStorageBackend.class);
    private static final String BACKEND_NAME = "GOOGLE_DRIVE";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    @Inject
    GoogleDriveStorageConfig config;

    private Drive driveService;
    private boolean available = false;
    private String rootFolderId;

    @PostConstruct
    void init() {
        if (!config.enabled()) {
            LOG.info("Google Drive storage backend is disabled");
            return;
        }

        try {
            initializeDriveService();
            this.rootFolderId = config.rootFolderId().orElse("root");
            this.available = true;
            LOG.infof("Google Drive storage backend initialized - app: %s, rootFolder: %s",
                    config.applicationName(), rootFolderId);
        } catch (Exception e) {
            LOG.error("Failed to initialize Google Drive storage backend", e);
            this.available = false;
        }
    }

    @SuppressWarnings("deprecation")
    private void initializeDriveService() throws GeneralSecurityException, IOException {
        if (config.clientId().isEmpty() || config.clientSecret().isEmpty() || config.refreshToken().isEmpty()) {
            throw new IllegalStateException("Google Drive client ID, client secret, and refresh token are required");
        }

        HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        
        // Create credential from refresh token
        GoogleCredential credential = new GoogleCredential.Builder()
                .setTransport(httpTransport)
                .setJsonFactory(JSON_FACTORY)
                .setClientSecrets(config.clientId().get(), config.clientSecret().get())
                .build()
                .setRefreshToken(config.refreshToken().get());

        this.driveService = new Drive.Builder(httpTransport, JSON_FACTORY, credential)
                .setApplicationName(config.applicationName())
                .build();
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
            String fileId = findFileId(key);
            return fileId != null;
        } catch (Exception e) {
            LOG.debugf("Error checking existence for key '%s': %s", key, e.getMessage());
            return false;
        }
    }

    @Override
    public void put(String key, InputStream data) throws StorageBackendException {
        if (!isAvailable()) {
            throw new StorageBackendException("Google Drive backend is not available");
        }

        try {
            // Read all data into memory
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = data.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            byte[] content = baos.toByteArray();

            // Check if file already exists
            String existingFileId = findFileId(key);
            
            if (existingFileId != null) {
                // Update existing file
                InputStreamContent mediaContent = new InputStreamContent(
                        "application/octet-stream",
                        new ByteArrayInputStream(content)
                );
                mediaContent.setLength(content.length);

                driveService.files().update(existingFileId, null, mediaContent).execute();
                LOG.debugf("Updated file in Google Drive: %s", key);
            } else {
                // Create new file
                File fileMetadata = new File();
                fileMetadata.setName(key);
                fileMetadata.setParents(Collections.singletonList(rootFolderId));

                InputStreamContent mediaContent = new InputStreamContent(
                        "application/octet-stream",
                        new ByteArrayInputStream(content)
                );
                mediaContent.setLength(content.length);

                driveService.files().create(fileMetadata, mediaContent)
                        .setFields("id")
                        .execute();
                LOG.debugf("Created file in Google Drive: %s", key);
            }
        } catch (IOException e) {
            throw new StorageBackendException("Failed to store file in Google Drive: " + key, e);
        }
    }

    @Override
    public InputStream get(String key) throws StorageBackendException {
        if (!isAvailable()) {
            throw new StorageBackendException("Google Drive backend is not available");
        }

        try {
            String fileId = findFileId(key);
            if (fileId == null) {
                throw new StorageBackendException("File not found in Google Drive: " + key);
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            driveService.files().get(fileId).executeMediaAndDownloadTo(outputStream);
            return new ByteArrayInputStream(outputStream.toByteArray());
        } catch (IOException e) {
            throw new StorageBackendException("Failed to retrieve file from Google Drive: " + key, e);
        }
    }

    @Override
    public void delete(String key) throws StorageBackendException {
        if (!isAvailable()) {
            throw new StorageBackendException("Google Drive backend is not available");
        }

        try {
            String fileId = findFileId(key);
            if (fileId != null) {
                driveService.files().delete(fileId).execute();
                LOG.debugf("Deleted file from Google Drive: %s", key);
            }
        } catch (IOException e) {
            throw new StorageBackendException("Failed to delete file from Google Drive: " + key, e);
        }
    }

    @Override
    public long size(String key) throws StorageBackendException {
        if (!isAvailable()) {
            throw new StorageBackendException("Google Drive backend is not available");
        }

        try {
            String fileId = findFileId(key);
            if (fileId == null) {
                throw new StorageBackendException("File not found in Google Drive: " + key);
            }

            File file = driveService.files().get(fileId).setFields("size").execute();
            return file.getSize() != null ? file.getSize() : 0;
        } catch (IOException e) {
            throw new StorageBackendException("Failed to get file size from Google Drive: " + key, e);
        }
    }

    /**
     * Find the Google Drive file ID for a given key.
     */
    private String findFileId(String key) throws IOException {
        String query = String.format("name = '%s' and '%s' in parents and trashed = false",
                key.replace("'", "\\'"), rootFolderId);
        
        FileList result = driveService.files().list()
                .setQ(query)
                .setFields("files(id)")
                .setPageSize(1)
                .execute();

        if (result.getFiles() != null && !result.getFiles().isEmpty()) {
            return result.getFiles().get(0).getId();
        }
        return null;
    }
}
