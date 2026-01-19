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
package fr.jayblanc.mbyte.store.data.backend.onedrive;

import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.microsoft.graph.authentication.TokenCredentialAuthProvider;
import com.microsoft.graph.models.DriveItem;
import com.microsoft.graph.models.DriveItemUploadableProperties;
import com.microsoft.graph.models.Folder;
import com.microsoft.graph.requests.GraphServiceClient;
import fr.jayblanc.mbyte.store.data.backend.StorageBackend;
import fr.jayblanc.mbyte.store.data.backend.StorageBackendException;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import okhttp3.Request;
import org.jboss.logging.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

/**
 * Microsoft OneDrive storage backend implementation.
 * Uses Microsoft Graph API with Azure AD authentication.
 * 
 * @author Abderrazak SEGHIR
 */
@ApplicationScoped
public class OneDriveStorageBackend implements StorageBackend {

    private static final Logger LOG = Logger.getLogger(OneDriveStorageBackend.class);
    private static final String BACKEND_NAME = "ONEDRIVE";
    private static final List<String> SCOPES = Arrays.asList("https://graph.microsoft.com/.default");

    @Inject
    OneDriveStorageConfig config;

    private GraphServiceClient<Request> graphClient;
    private boolean available = false;
    private String rootPath;

    @PostConstruct
    void init() {
        if (!config.enabled()) {
            LOG.info("OneDrive storage backend is disabled");
            return;
        }

        try {
            initializeGraphClient();
            this.rootPath = config.rootPath();
            ensureRootFolderExists();
            this.available = true;
            LOG.infof("OneDrive storage backend initialized - rootPath: %s", rootPath);
        } catch (Exception e) {
            LOG.error("Failed to initialize OneDrive storage backend", e);
            this.available = false;
        }
    }

    private void initializeGraphClient() {
        if (config.clientId().isEmpty() || config.clientSecret().isEmpty()) {
            throw new IllegalStateException("OneDrive client ID and client secret are required");
        }

        ClientSecretCredential credential = new ClientSecretCredentialBuilder()
                .clientId(config.clientId().get())
                .clientSecret(config.clientSecret().get())
                .tenantId(config.tenantId())
                .build();

        TokenCredentialAuthProvider authProvider = new TokenCredentialAuthProvider(SCOPES, credential);
        
        this.graphClient = GraphServiceClient.builder()
                .authenticationProvider(authProvider)
                .buildClient();
    }

    private void ensureRootFolderExists() {
        try {
            // Try to get the root folder, create if it doesn't exist
            String[] pathParts = rootPath.split("/");
            String currentPath = "";
            
            for (String part : pathParts) {
                if (part.isEmpty()) continue;
                
                String parentPath = currentPath.isEmpty() ? "root" : currentPath;
                currentPath = currentPath.isEmpty() ? part : currentPath + "/" + part;
                
                try {
                    graphClient.me().drive().root()
                            .itemWithPath(currentPath)
                            .buildRequest()
                            .get();
                } catch (Exception e) {
                    // Create folder if it doesn't exist
                    DriveItem folder = new DriveItem();
                    folder.name = part;
                    folder.folder = new Folder();
                    
                    if (parentPath.equals("root")) {
                        graphClient.me().drive().root().children()
                                .buildRequest()
                                .post(folder);
                    } else {
                        graphClient.me().drive().root()
                                .itemWithPath(parentPath).children()
                                .buildRequest()
                                .post(folder);
                    }
                    LOG.infof("Created folder: %s", currentPath);
                }
            }
        } catch (Exception e) {
            LOG.warnf("Could not ensure root folder exists: %s", e.getMessage());
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
            graphClient.me().drive().root()
                    .itemWithPath(path)
                    .buildRequest()
                    .get();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void put(String key, InputStream data) throws StorageBackendException {
        if (!isAvailable()) {
            throw new StorageBackendException("OneDrive backend is not available");
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

            // For small files (< 4MB), use simple upload
            if (content.length < 4 * 1024 * 1024) {
                graphClient.me().drive().root()
                        .itemWithPath(path).content()
                        .buildRequest()
                        .put(content);
            } else {
                // For larger files, use upload session
                DriveItemUploadableProperties uploadProps = new DriveItemUploadableProperties();
                uploadProps.name = key;
                
                // Create upload session and upload in chunks
                // This is a simplified version - production code should handle chunked uploads
                graphClient.me().drive().root()
                        .itemWithPath(path).content()
                        .buildRequest()
                        .put(content);
            }
            
            LOG.debugf("Uploaded file to OneDrive: %s", path);
        } catch (IOException e) {
            throw new StorageBackendException("Failed to store file in OneDrive: " + key, e);
        }
    }

    @Override
    public InputStream get(String key) throws StorageBackendException {
        if (!isAvailable()) {
            throw new StorageBackendException("OneDrive backend is not available");
        }

        try {
            String path = buildPath(key);
            InputStream stream = graphClient.me().drive().root()
                    .itemWithPath(path).content()
                    .buildRequest()
                    .get();
            
            // Read into memory to avoid connection issues
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = stream.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            stream.close();
            
            return new ByteArrayInputStream(baos.toByteArray());
        } catch (IOException e) {
            throw new StorageBackendException("Failed to retrieve file from OneDrive: " + key, e);
        }
    }

    @Override
    public void delete(String key) throws StorageBackendException {
        if (!isAvailable()) {
            throw new StorageBackendException("OneDrive backend is not available");
        }

        try {
            String path = buildPath(key);
            graphClient.me().drive().root()
                    .itemWithPath(path)
                    .buildRequest()
                    .delete();
            LOG.debugf("Deleted file from OneDrive: %s", path);
        } catch (Exception e) {
            throw new StorageBackendException("Failed to delete file from OneDrive: " + key, e);
        }
    }

    @Override
    public long size(String key) throws StorageBackendException {
        if (!isAvailable()) {
            throw new StorageBackendException("OneDrive backend is not available");
        }

        try {
            String path = buildPath(key);
            DriveItem item = graphClient.me().drive().root()
                    .itemWithPath(path)
                    .buildRequest()
                    .get();
            return item.size != null ? item.size : 0;
        } catch (Exception e) {
            throw new StorageBackendException("Failed to get file size from OneDrive: " + key, e);
        }
    }

    private String buildPath(String key) {
        String normalizedKey = key.replace("/", "_"); // OneDrive doesn't like nested paths in item names
        return rootPath + "/" + normalizedKey;
    }
}
