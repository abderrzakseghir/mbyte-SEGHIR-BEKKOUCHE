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
package fr.jayblanc.mbyte.store.data.backend.multi;

import fr.jayblanc.mbyte.store.data.backend.StorageBackend;
import fr.jayblanc.mbyte.store.data.backend.StorageBackendException;
import fr.jayblanc.mbyte.store.data.backend.StorageBackendType;
import fr.jayblanc.mbyte.store.data.backend.dropbox.DropboxStorageBackend;
import fr.jayblanc.mbyte.store.data.backend.googledrive.GoogleDriveStorageBackend;
import fr.jayblanc.mbyte.store.data.backend.local.LocalStorageBackend;
import fr.jayblanc.mbyte.store.data.backend.onedrive.OneDriveStorageBackend;
import fr.jayblanc.mbyte.store.data.backend.s3.S3StorageBackend;
import fr.jayblanc.mbyte.store.data.backend.webdav.WebDAVStorageBackend;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Multi-backend storage implementation with redundancy, load balancing,
 * and automatic file placement capabilities.
 * 
 * @author Abderrazak SEGHIR
 */
@ApplicationScoped
public class MultiBackendStorageBackend implements StorageBackend {

    private static final Logger LOG = Logger.getLogger(MultiBackendStorageBackend.class);
    private static final String BACKEND_NAME = "MULTI";

    @Inject
    MultiBackendConfig config;

    @Inject
    LocalStorageBackend localBackend;

    @Inject
    S3StorageBackend s3Backend;

    @Inject
    WebDAVStorageBackend webdavBackend;

    @Inject
    GoogleDriveStorageBackend googleDriveBackend;

    @Inject
    DropboxStorageBackend dropboxBackend;

    @Inject
    OneDriveStorageBackend oneDriveBackend;

    private List<StorageBackend> availableBackends;
    private Map<String, StorageBackend> backendMap;
    private AtomicInteger roundRobinCounter;
    private Map<String, Long> backendLatencies;
    private Map<String, List<String>> fileLocationCache;
    private boolean available = false;

    @PostConstruct
    void init() {
        if (!config.enabled()) {
            LOG.info("Multi-backend storage is disabled");
            return;
        }

        this.backendMap = new HashMap<>();
        this.availableBackends = new ArrayList<>();
        this.roundRobinCounter = new AtomicInteger(0);
        this.backendLatencies = new ConcurrentHashMap<>();
        this.fileLocationCache = new ConcurrentHashMap<>();

        initializeBackends();

        if (!availableBackends.isEmpty()) {
            this.available = true;
            LOG.infof("Multi-backend storage initialized with %d backends: %s",
                    availableBackends.size(),
                    availableBackends.stream().map(StorageBackend::getName).collect(Collectors.joining(", ")));
            LOG.infof("Redundancy level: %d, Load balancing: %s, Auto-placement: %s",
                    config.redundancyLevel(),
                    config.loadBalancingStrategy(),
                    config.autoPlacementEnabled());
        } else {
            LOG.warn("No backends available for multi-backend storage");
        }
    }

    private void initializeBackends() {
        // Map all backend types to their implementations
        backendMap.put("LOCAL", localBackend);
        backendMap.put("S3", s3Backend);
        backendMap.put("WEBDAV", webdavBackend);
        backendMap.put("GOOGLE_DRIVE", googleDriveBackend);
        backendMap.put("DROPBOX", dropboxBackend);
        backendMap.put("ONEDRIVE", oneDriveBackend);

        // Filter to only available backends from config
        for (String backendName : config.backends()) {
            StorageBackend backend = backendMap.get(backendName.toUpperCase());
            if (backend != null && backend.isAvailable()) {
                availableBackends.add(backend);
                LOG.debugf("Added backend to multi-backend: %s", backend.getName());
            } else {
                LOG.warnf("Backend '%s' is not available or not configured", backendName);
            }
        }
    }

    @Override
    public String getName() {
        return BACKEND_NAME;
    }

    @Override
    public boolean isAvailable() {
        return available && config.enabled() && !availableBackends.isEmpty();
    }

    @Override
    public boolean exists(String key) {
        if (!isAvailable()) {
            return false;
        }

        // Check cache first
        if (fileLocationCache.containsKey(key)) {
            return true;
        }

        // Check all backends
        for (StorageBackend backend : availableBackends) {
            try {
                if (backend.exists(key)) {
                    // Cache the location
                    fileLocationCache.computeIfAbsent(key, k -> new ArrayList<>()).add(backend.getName());
                    return true;
                }
            } catch (Exception e) {
                LOG.debugf("Error checking existence on %s for key '%s': %s",
                        backend.getName(), key, e.getMessage());
            }
        }
        return false;
    }

    @Override
    public void put(String key, InputStream data) throws StorageBackendException {
        if (!isAvailable()) {
            throw new StorageBackendException("Multi-backend storage is not available");
        }

        // Read data into memory for replication
        byte[] content;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = data.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            content = baos.toByteArray();
        } catch (IOException e) {
            throw new StorageBackendException("Failed to read input data", e);
        }

        // Determine which backends to use
        List<StorageBackend> targetBackends = selectBackendsForWrite(key, content.length);
        
        // Write to multiple backends for redundancy
        int successCount = 0;
        List<String> successfulBackends = new ArrayList<>();
        List<Exception> failures = new ArrayList<>();

        for (StorageBackend backend : targetBackends) {
            try {
                long startTime = System.currentTimeMillis();
                backend.put(key, new ByteArrayInputStream(content));
                long latency = System.currentTimeMillis() - startTime;
                backendLatencies.put(backend.getName(), latency);
                
                successCount++;
                successfulBackends.add(backend.getName());
                LOG.debugf("Written to %s in %dms: %s", backend.getName(), latency, key);
            } catch (Exception e) {
                failures.add(e);
                LOG.warnf("Failed to write to %s: %s", backend.getName(), e.getMessage());
            }
        }

        // Update location cache
        fileLocationCache.put(key, successfulBackends);

        // Check if we met our requirements
        if (successCount < config.minimumWrites()) {
            throw new StorageBackendException(
                    String.format("Only %d of %d minimum writes succeeded. Failures: %s",
                            successCount, config.minimumWrites(),
                            failures.stream().map(Exception::getMessage).collect(Collectors.joining(", "))));
        }

        if (config.failOnPartialWrite() && successCount < config.redundancyLevel()) {
            LOG.warnf("Partial write: only %d of %d backends succeeded for key: %s",
                    successCount, config.redundancyLevel(), key);
        }

        LOG.infof("Stored file with %d-way redundancy: %s -> [%s]",
                successCount, key, String.join(", ", successfulBackends));
    }

    @Override
    public InputStream get(String key) throws StorageBackendException {
        if (!isAvailable()) {
            throw new StorageBackendException("Multi-backend storage is not available");
        }

        // Use load balancing to select a backend
        StorageBackend selectedBackend = selectBackendForRead(key);
        
        if (selectedBackend == null) {
            throw new StorageBackendException("File not found on any backend: " + key);
        }

        try {
            long startTime = System.currentTimeMillis();
            InputStream result = selectedBackend.get(key);
            long latency = System.currentTimeMillis() - startTime;
            backendLatencies.put(selectedBackend.getName(), latency);
            
            LOG.debugf("Read from %s in %dms: %s", selectedBackend.getName(), latency, key);
            return result;
        } catch (Exception e) {
            LOG.warnf("Failed to read from %s, trying fallbacks: %s", selectedBackend.getName(), e.getMessage());
            
            // Try other backends
            for (StorageBackend backend : availableBackends) {
                if (backend == selectedBackend) continue;
                try {
                    if (backend.exists(key)) {
                        return backend.get(key);
                    }
                } catch (Exception fallbackError) {
                    LOG.debugf("Fallback %s also failed: %s", backend.getName(), fallbackError.getMessage());
                }
            }
            
            throw new StorageBackendException("Failed to read from all backends: " + key, e);
        }
    }

    @Override
    public void delete(String key) throws StorageBackendException {
        if (!isAvailable()) {
            throw new StorageBackendException("Multi-backend storage is not available");
        }

        int successCount = 0;
        
        // Delete from all backends where the file exists
        for (StorageBackend backend : availableBackends) {
            try {
                if (backend.exists(key)) {
                    backend.delete(key);
                    successCount++;
                    LOG.debugf("Deleted from %s: %s", backend.getName(), key);
                }
            } catch (Exception e) {
                LOG.warnf("Failed to delete from %s: %s", backend.getName(), e.getMessage());
            }
        }

        // Remove from cache
        fileLocationCache.remove(key);
        
        LOG.infof("Deleted file from %d backends: %s", successCount, key);
    }

    @Override
    public long size(String key) throws StorageBackendException {
        if (!isAvailable()) {
            throw new StorageBackendException("Multi-backend storage is not available");
        }

        // Get size from first available backend that has the file
        for (StorageBackend backend : availableBackends) {
            try {
                if (backend.exists(key)) {
                    return backend.size(key);
                }
            } catch (Exception e) {
                LOG.debugf("Error getting size from %s: %s", backend.getName(), e.getMessage());
            }
        }
        
        throw new StorageBackendException("File not found on any backend: " + key);
    }

    /**
     * Select backends for writing based on redundancy level and auto-placement settings.
     */
    private List<StorageBackend> selectBackendsForWrite(String key, long fileSize) {
        List<StorageBackend> selected = new ArrayList<>();
        int targetCount = Math.min(config.redundancyLevel(), availableBackends.size());

        if (config.autoPlacementEnabled()) {
            // Apply auto-placement rules
            StorageBackend preferredBackend = selectBackendByPlacementRules(fileSize);
            if (preferredBackend != null && preferredBackend.isAvailable()) {
                selected.add(preferredBackend);
            }
        }

        // Fill remaining slots with round-robin from available backends
        for (StorageBackend backend : availableBackends) {
            if (selected.size() >= targetCount) break;
            if (!selected.contains(backend)) {
                selected.add(backend);
            }
        }

        return selected;
    }

    /**
     * Select a backend based on file placement rules.
     */
    private StorageBackend selectBackendByPlacementRules(long fileSize) {
        // Large file placement
        if (fileSize > config.largeFileThreshold() && config.largeFileBackend().isPresent()) {
            StorageBackend backend = backendMap.get(config.largeFileBackend().get().toUpperCase());
            if (backend != null && backend.isAvailable()) {
                LOG.debugf("Using large file backend: %s", backend.getName());
                return backend;
            }
        }

        // Default to first available
        return availableBackends.isEmpty() ? null : availableBackends.get(0);
    }

    /**
     * Select a backend for reading using the configured load balancing strategy.
     */
    private StorageBackend selectBackendForRead(String key) {
        // Check cache for known locations
        List<String> locations = fileLocationCache.get(key);
        List<StorageBackend> candidates;
        
        if (locations != null && !locations.isEmpty()) {
            candidates = locations.stream()
                    .map(name -> backendMap.get(name))
                    .filter(Objects::nonNull)
                    .filter(StorageBackend::isAvailable)
                    .collect(Collectors.toList());
        } else {
            // Find which backends have the file
            candidates = availableBackends.stream()
                    .filter(b -> {
                        try {
                            return b.exists(key);
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .collect(Collectors.toList());
            
            // Update cache
            if (!candidates.isEmpty()) {
                fileLocationCache.put(key, candidates.stream()
                        .map(StorageBackend::getName)
                        .collect(Collectors.toList()));
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }

        // Apply load balancing strategy
        switch (config.loadBalancingStrategy()) {
            case ROUND_ROBIN:
                int index = roundRobinCounter.getAndIncrement() % candidates.size();
                return candidates.get(index);

            case RANDOM:
                return candidates.get(new Random().nextInt(candidates.size()));

            case FASTEST:
                return candidates.stream()
                        .min(Comparator.comparingLong(b -> backendLatencies.getOrDefault(b.getName(), Long.MAX_VALUE)))
                        .orElse(candidates.get(0));

            case PRIMARY_FIRST:
            default:
                return candidates.get(0);
        }
    }

    /**
     * Get statistics about the multi-backend storage.
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("availableBackends", availableBackends.stream()
                .map(StorageBackend::getName)
                .collect(Collectors.toList()));
        stats.put("redundancyLevel", config.redundancyLevel());
        stats.put("loadBalancingStrategy", config.loadBalancingStrategy().name());
        stats.put("autoPlacementEnabled", config.autoPlacementEnabled());
        stats.put("backendLatencies", new HashMap<>(backendLatencies));
        stats.put("cachedFileLocations", fileLocationCache.size());
        return stats;
    }
}
