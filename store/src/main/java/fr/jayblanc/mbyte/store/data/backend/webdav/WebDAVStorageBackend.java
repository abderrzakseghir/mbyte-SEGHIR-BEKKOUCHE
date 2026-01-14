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

import com.github.sardine.Sardine;
import com.github.sardine.SardineFactory;
import com.github.sardine.DavResource;
import fr.jayblanc.mbyte.store.data.backend.StorageBackend;
import fr.jayblanc.mbyte.store.data.backend.StorageBackendException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * WebDAV storage backend implementation using Sardine library.
 * 
 * @author MByte Team
 */
@ApplicationScoped
public class WebDAVStorageBackend implements StorageBackend {

    private static final Logger LOG = Logger.getLogger(WebDAVStorageBackend.class);
    private static final String BACKEND_NAME = "WebDAV";

    @Inject
    WebDAVStorageConfig config;

    private Sardine sardine;
    private boolean available = false;

    @PostConstruct
    void init() {
        if (!config.enabled()) {
            LOG.info("WebDAV storage backend is disabled");
            return;
        }

        try {
            initializeClient();
            testConnection();
            this.available = true;
            LOG.infof("WebDAV storage backend initialized - endpoint: %s", config.url().orElse(""));
        } catch (Exception e) {
            LOG.error("Failed to initialize WebDAV storage backend", e);
            this.available = false;
        }
    }

    private void initializeClient() {
        if (config.username().isPresent() && config.password().isPresent()) {
            this.sardine = SardineFactory.begin(
                    config.username().get(),
                    config.password().get()
            );
        } else {
            this.sardine = SardineFactory.begin();
        }
    }

    private void testConnection() throws IOException {
        String baseUrl = normalizeUrl(config.url().orElseThrow(() -> new IllegalStateException("WebDAV URL is required")));
        if (!sardine.exists(baseUrl)) {
            sardine.createDirectory(baseUrl);
        }
    }

    @PreDestroy
    void cleanup() {
        if (sardine != null) {
            try {
                sardine.shutdown();
            } catch (IOException e) {
                LOG.warn("Failed to shutdown Sardine client", e);
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
        if (!isAvailable()) return false;
        
        try {
            String url = buildUrl(key);
            return sardine.exists(url);
        } catch (IOException e) {
            LOG.warnf("Failed to check if resource exists: %s", key);
            return false;
        }
    }

    @Override
    public void put(String key, InputStream data) throws StorageBackendException {
        checkAvailable();
        
        try {
            String url = buildUrl(key);
            ensureParentDirectories(key);
            
            byte[] bytes = readAllBytes(data);
            sardine.put(url, bytes);
            
            LOG.debugf("Stored resource: %s (%d bytes)", key, bytes.length);
            
        } catch (IOException e) {
            throw new StorageBackendException("Failed to store resource: " + key, e);
        }
    }

    @Override
    public InputStream get(String key) throws StorageBackendException {
        checkAvailable();
        
        try {
            String url = buildUrl(key);
            
            if (!sardine.exists(url)) {
                throw new StorageBackendException("Resource not found: " + key);
            }
            
            InputStream is = sardine.get(url);
            byte[] bytes = readAllBytes(is);
            is.close();
            
            return new ByteArrayInputStream(bytes);
            
        } catch (IOException e) {
            throw new StorageBackendException("Failed to retrieve resource: " + key, e);
        }
    }

    @Override
    public void delete(String key) throws StorageBackendException {
        checkAvailable();
        
        try {
            String url = buildUrl(key);
            
            if (sardine.exists(url)) {
                sardine.delete(url);
                LOG.debugf("Deleted resource: %s", key);
            }
            
        } catch (IOException e) {
            throw new StorageBackendException("Failed to delete resource: " + key, e);
        }
    }

    @Override
    public long size(String key) throws StorageBackendException {
        checkAvailable();
        
        try {
            String url = buildUrl(key);
            List<DavResource> resources = sardine.list(url, 0);
            
            if (resources.isEmpty()) {
                throw new StorageBackendException("Resource not found: " + key);
            }
            
            Long contentLength = resources.get(0).getContentLength();
            return contentLength != null ? contentLength : 0;
            
        } catch (IOException e) {
            throw new StorageBackendException("Failed to get resource size: " + key, e);
        }
    }

    private void checkAvailable() throws StorageBackendException {
        if (!isAvailable()) {
            throw new StorageBackendException("WebDAV storage backend is not available");
        }
    }

    private String buildUrl(String key) {
        String baseUrl = normalizeUrl(config.url().orElse(""));
        String normalizedKey = key.startsWith("/") ? key.substring(1) : key;
        return baseUrl + normalizedKey;
    }

    private String normalizeUrl(String url) {
        return url.endsWith("/") ? url : url + "/";
    }

    private void ensureParentDirectories(String key) throws IOException {
        String baseUrl = normalizeUrl(config.url().orElse(""));
        String[] parts = key.split("/");
        StringBuilder currentPath = new StringBuilder(baseUrl);
        
        for (int i = 0; i < parts.length - 1; i++) {
            if (parts[i].isEmpty()) continue;
            
            currentPath.append(parts[i]).append("/");
            String dirUrl = currentPath.toString();
            
            if (!sardine.exists(dirUrl)) {
                sardine.createDirectory(dirUrl);
                LOG.debugf("Created directory: %s", dirUrl);
            }
        }
    }

    private byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = is.read(buffer)) != -1) {
            baos.write(buffer, 0, bytesRead);
        }
        return baos.toByteArray();
    }
}
