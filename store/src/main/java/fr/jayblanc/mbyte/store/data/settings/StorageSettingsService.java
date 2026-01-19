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
package fr.jayblanc.mbyte.store.data.settings;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.Optional;

/**
 * Service for managing user storage settings.
 * 
 * @author Abderrazak SEGHIR
 */
@ApplicationScoped
public class StorageSettingsService {

    private static final Logger LOG = Logger.getLogger(StorageSettingsService.class);

    @Inject
    EntityManager em;

    /**
     * Get storage settings for a user, creating default settings if none exist.
     */
    @Transactional
    public StorageSettings getOrCreateSettings(String owner) {
        Optional<StorageSettings> existing = findByOwner(owner);
        if (existing.isPresent()) {
            return existing.get();
        }

        // Create default settings
        StorageSettings settings = new StorageSettings();
        settings.setOwner(owner);
        settings.setMultiBackendEnabled(true);
        settings.setRedundancyLevel(2);
        settings.setEncryptionEnabled(true);
        settings.setS3Enabled(true); // S3/MinIO enabled by default
        
        em.persist(settings);
        LOG.infof("Created default storage settings for user: %s", owner);
        return settings;
    }

    /**
     * Find settings by owner.
     */
    public Optional<StorageSettings> findByOwner(String owner) {
        try {
            StorageSettings settings = em.createQuery(
                    "SELECT s FROM StorageSettings s WHERE s.owner = :owner", StorageSettings.class)
                    .setParameter("owner", owner)
                    .getSingleResult();
            return Optional.of(settings);
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    /**
     * Save or update storage settings.
     */
    @Transactional
    public StorageSettings save(StorageSettings settings) {
        if (settings.getId() == null) {
            em.persist(settings);
            LOG.infof("Created storage settings for user: %s", settings.getOwner());
        } else {
            settings = em.merge(settings);
            LOG.infof("Updated storage settings for user: %s", settings.getOwner());
        }
        return settings;
    }

    /**
     * Update specific backend settings.
     */
    @Transactional
    public StorageSettings updateBackendSettings(String owner, String backend, boolean enabled, 
                                                  java.util.Map<String, String> credentials) {
        StorageSettings settings = getOrCreateSettings(owner);
        
        switch (backend.toUpperCase()) {
            case "GOOGLE_DRIVE":
                settings.setGoogleDriveEnabled(enabled);
                if (credentials != null) {
                    if (credentials.containsKey("clientId")) 
                        settings.setGoogleDriveClientId(credentials.get("clientId"));
                    if (credentials.containsKey("clientSecret")) 
                        settings.setGoogleDriveClientSecret(credentials.get("clientSecret"));
                    if (credentials.containsKey("refreshToken")) 
                        settings.setGoogleDriveRefreshToken(credentials.get("refreshToken"));
                    if (credentials.containsKey("folderId")) 
                        settings.setGoogleDriveFolderId(credentials.get("folderId"));
                }
                break;
                
            case "DROPBOX":
                settings.setDropboxEnabled(enabled);
                if (credentials != null) {
                    if (credentials.containsKey("accessToken")) 
                        settings.setDropboxAccessToken(credentials.get("accessToken"));
                    if (credentials.containsKey("appKey")) 
                        settings.setDropboxAppKey(credentials.get("appKey"));
                    if (credentials.containsKey("appSecret")) 
                        settings.setDropboxAppSecret(credentials.get("appSecret"));
                    if (credentials.containsKey("refreshToken")) 
                        settings.setDropboxRefreshToken(credentials.get("refreshToken"));
                    if (credentials.containsKey("rootPath")) 
                        settings.setDropboxRootPath(credentials.get("rootPath"));
                }
                break;
                
            case "ONEDRIVE":
                settings.setOneDriveEnabled(enabled);
                if (credentials != null) {
                    if (credentials.containsKey("clientId")) 
                        settings.setOneDriveClientId(credentials.get("clientId"));
                    if (credentials.containsKey("clientSecret")) 
                        settings.setOneDriveClientSecret(credentials.get("clientSecret"));
                    if (credentials.containsKey("tenantId")) 
                        settings.setOneDriveTenantId(credentials.get("tenantId"));
                    if (credentials.containsKey("refreshToken")) 
                        settings.setOneDriveRefreshToken(credentials.get("refreshToken"));
                    if (credentials.containsKey("rootPath")) 
                        settings.setOneDriveRootPath(credentials.get("rootPath"));
                }
                break;
                
            case "WEBDAV":
                settings.setWebdavEnabled(enabled);
                if (credentials != null) {
                    if (credentials.containsKey("url")) 
                        settings.setWebdavUrl(credentials.get("url"));
                    if (credentials.containsKey("username")) 
                        settings.setWebdavUsername(credentials.get("username"));
                    if (credentials.containsKey("password")) 
                        settings.setWebdavPassword(credentials.get("password"));
                }
                break;
                
            case "S3":
                settings.setS3Enabled(enabled);
                if (credentials != null) {
                    if (credentials.containsKey("endpoint")) 
                        settings.setS3Endpoint(credentials.get("endpoint"));
                    if (credentials.containsKey("accessKey")) 
                        settings.setS3AccessKey(credentials.get("accessKey"));
                    if (credentials.containsKey("secretKey")) 
                        settings.setS3SecretKey(credentials.get("secretKey"));
                    if (credentials.containsKey("bucket")) 
                        settings.setS3Bucket(credentials.get("bucket"));
                    if (credentials.containsKey("region")) 
                        settings.setS3Region(credentials.get("region"));
                }
                break;
                
            default:
                LOG.warnf("Unknown backend type: %s", backend);
        }
        
        return em.merge(settings);
    }
}
