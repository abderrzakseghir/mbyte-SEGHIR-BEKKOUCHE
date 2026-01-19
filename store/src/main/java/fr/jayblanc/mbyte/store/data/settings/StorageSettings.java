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

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity for storing user's external storage configuration.
 * Each user can configure their own storage backends with credentials.
 * 
 * @author Abderrazak SEGHIR
 */
@Entity
@Table(name = "storage_settings")
public class StorageSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner", nullable = false, unique = true)
    private String owner;

    // === General Settings ===
    
    @Column(name = "multi_backend_enabled")
    private boolean multiBackendEnabled = true;

    @Column(name = "redundancy_level")
    private int redundancyLevel = 2;

    @Column(name = "load_balancing_strategy")
    private String loadBalancingStrategy = "ROUND_ROBIN";

    @Column(name = "encryption_enabled")
    private boolean encryptionEnabled = true;

    // === S3/MinIO Settings ===
    
    @Column(name = "s3_enabled")
    private boolean s3Enabled = true;

    @Column(name = "s3_endpoint")
    private String s3Endpoint;

    @Column(name = "s3_access_key")
    private String s3AccessKey;

    @Column(name = "s3_secret_key")
    private String s3SecretKey;

    @Column(name = "s3_bucket")
    private String s3Bucket;

    @Column(name = "s3_region")
    private String s3Region = "us-east-1";

    // === Google Drive Settings ===
    
    @Column(name = "google_drive_enabled")
    private boolean googleDriveEnabled = false;

    @Column(name = "google_drive_client_id")
    private String googleDriveClientId;

    @Column(name = "google_drive_client_secret")
    private String googleDriveClientSecret;

    @Column(name = "google_drive_refresh_token", length = 2048)
    private String googleDriveRefreshToken;

    @Column(name = "google_drive_folder_id")
    private String googleDriveFolderId;

    // === Dropbox Settings ===
    
    @Column(name = "dropbox_enabled")
    private boolean dropboxEnabled = false;

    @Column(name = "dropbox_access_token", length = 2048)
    private String dropboxAccessToken;

    @Column(name = "dropbox_app_key")
    private String dropboxAppKey;

    @Column(name = "dropbox_app_secret")
    private String dropboxAppSecret;

    @Column(name = "dropbox_refresh_token", length = 2048)
    private String dropboxRefreshToken;

    @Column(name = "dropbox_root_path")
    private String dropboxRootPath = "/mbyte-store";

    // === OneDrive Settings ===
    
    @Column(name = "onedrive_enabled")
    private boolean oneDriveEnabled = false;

    @Column(name = "onedrive_client_id")
    private String oneDriveClientId;

    @Column(name = "onedrive_client_secret")
    private String oneDriveClientSecret;

    @Column(name = "onedrive_tenant_id")
    private String oneDriveTenantId = "common";

    @Column(name = "onedrive_refresh_token", length = 2048)
    private String oneDriveRefreshToken;

    @Column(name = "onedrive_root_path")
    private String oneDriveRootPath = "/mbyte-store";

    // === WebDAV Settings ===
    
    @Column(name = "webdav_enabled")
    private boolean webdavEnabled = false;

    @Column(name = "webdav_url")
    private String webdavUrl;

    @Column(name = "webdav_username")
    private String webdavUsername;

    @Column(name = "webdav_password")
    private String webdavPassword;

    // === Metadata ===
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // === Getters and Setters ===

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public boolean isMultiBackendEnabled() {
        return multiBackendEnabled;
    }

    public void setMultiBackendEnabled(boolean multiBackendEnabled) {
        this.multiBackendEnabled = multiBackendEnabled;
    }

    public int getRedundancyLevel() {
        return redundancyLevel;
    }

    public void setRedundancyLevel(int redundancyLevel) {
        this.redundancyLevel = redundancyLevel;
    }

    public String getLoadBalancingStrategy() {
        return loadBalancingStrategy;
    }

    public void setLoadBalancingStrategy(String loadBalancingStrategy) {
        this.loadBalancingStrategy = loadBalancingStrategy;
    }

    public boolean isEncryptionEnabled() {
        return encryptionEnabled;
    }

    public void setEncryptionEnabled(boolean encryptionEnabled) {
        this.encryptionEnabled = encryptionEnabled;
    }

    public boolean isS3Enabled() {
        return s3Enabled;
    }

    public void setS3Enabled(boolean s3Enabled) {
        this.s3Enabled = s3Enabled;
    }

    public String getS3Endpoint() {
        return s3Endpoint;
    }

    public void setS3Endpoint(String s3Endpoint) {
        this.s3Endpoint = s3Endpoint;
    }

    public String getS3AccessKey() {
        return s3AccessKey;
    }

    public void setS3AccessKey(String s3AccessKey) {
        this.s3AccessKey = s3AccessKey;
    }

    public String getS3SecretKey() {
        return s3SecretKey;
    }

    public void setS3SecretKey(String s3SecretKey) {
        this.s3SecretKey = s3SecretKey;
    }

    public String getS3Bucket() {
        return s3Bucket;
    }

    public void setS3Bucket(String s3Bucket) {
        this.s3Bucket = s3Bucket;
    }

    public String getS3Region() {
        return s3Region;
    }

    public void setS3Region(String s3Region) {
        this.s3Region = s3Region;
    }

    public boolean isGoogleDriveEnabled() {
        return googleDriveEnabled;
    }

    public void setGoogleDriveEnabled(boolean googleDriveEnabled) {
        this.googleDriveEnabled = googleDriveEnabled;
    }

    public String getGoogleDriveClientId() {
        return googleDriveClientId;
    }

    public void setGoogleDriveClientId(String googleDriveClientId) {
        this.googleDriveClientId = googleDriveClientId;
    }

    public String getGoogleDriveClientSecret() {
        return googleDriveClientSecret;
    }

    public void setGoogleDriveClientSecret(String googleDriveClientSecret) {
        this.googleDriveClientSecret = googleDriveClientSecret;
    }

    public String getGoogleDriveRefreshToken() {
        return googleDriveRefreshToken;
    }

    public void setGoogleDriveRefreshToken(String googleDriveRefreshToken) {
        this.googleDriveRefreshToken = googleDriveRefreshToken;
    }

    public String getGoogleDriveFolderId() {
        return googleDriveFolderId;
    }

    public void setGoogleDriveFolderId(String googleDriveFolderId) {
        this.googleDriveFolderId = googleDriveFolderId;
    }

    public boolean isDropboxEnabled() {
        return dropboxEnabled;
    }

    public void setDropboxEnabled(boolean dropboxEnabled) {
        this.dropboxEnabled = dropboxEnabled;
    }

    public String getDropboxAccessToken() {
        return dropboxAccessToken;
    }

    public void setDropboxAccessToken(String dropboxAccessToken) {
        this.dropboxAccessToken = dropboxAccessToken;
    }

    public String getDropboxAppKey() {
        return dropboxAppKey;
    }

    public void setDropboxAppKey(String dropboxAppKey) {
        this.dropboxAppKey = dropboxAppKey;
    }

    public String getDropboxAppSecret() {
        return dropboxAppSecret;
    }

    public void setDropboxAppSecret(String dropboxAppSecret) {
        this.dropboxAppSecret = dropboxAppSecret;
    }

    public String getDropboxRefreshToken() {
        return dropboxRefreshToken;
    }

    public void setDropboxRefreshToken(String dropboxRefreshToken) {
        this.dropboxRefreshToken = dropboxRefreshToken;
    }

    public String getDropboxRootPath() {
        return dropboxRootPath;
    }

    public void setDropboxRootPath(String dropboxRootPath) {
        this.dropboxRootPath = dropboxRootPath;
    }

    public boolean isOneDriveEnabled() {
        return oneDriveEnabled;
    }

    public void setOneDriveEnabled(boolean oneDriveEnabled) {
        this.oneDriveEnabled = oneDriveEnabled;
    }

    public String getOneDriveClientId() {
        return oneDriveClientId;
    }

    public void setOneDriveClientId(String oneDriveClientId) {
        this.oneDriveClientId = oneDriveClientId;
    }

    public String getOneDriveClientSecret() {
        return oneDriveClientSecret;
    }

    public void setOneDriveClientSecret(String oneDriveClientSecret) {
        this.oneDriveClientSecret = oneDriveClientSecret;
    }

    public String getOneDriveTenantId() {
        return oneDriveTenantId;
    }

    public void setOneDriveTenantId(String oneDriveTenantId) {
        this.oneDriveTenantId = oneDriveTenantId;
    }

    public String getOneDriveRefreshToken() {
        return oneDriveRefreshToken;
    }

    public void setOneDriveRefreshToken(String oneDriveRefreshToken) {
        this.oneDriveRefreshToken = oneDriveRefreshToken;
    }

    public String getOneDriveRootPath() {
        return oneDriveRootPath;
    }

    public void setOneDriveRootPath(String oneDriveRootPath) {
        this.oneDriveRootPath = oneDriveRootPath;
    }

    public boolean isWebdavEnabled() {
        return webdavEnabled;
    }

    public void setWebdavEnabled(boolean webdavEnabled) {
        this.webdavEnabled = webdavEnabled;
    }

    public String getWebdavUrl() {
        return webdavUrl;
    }

    public void setWebdavUrl(String webdavUrl) {
        this.webdavUrl = webdavUrl;
    }

    public String getWebdavUsername() {
        return webdavUsername;
    }

    public void setWebdavUsername(String webdavUsername) {
        this.webdavUsername = webdavUsername;
    }

    public String getWebdavPassword() {
        return webdavPassword;
    }

    public void setWebdavPassword(String webdavPassword) {
        this.webdavPassword = webdavPassword;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Get the list of enabled backends as a comma-separated string.
     */
    public String getEnabledBackends() {
        StringBuilder sb = new StringBuilder();
        if (s3Enabled) sb.append("S3,");
        if (googleDriveEnabled) sb.append("GOOGLE_DRIVE,");
        if (dropboxEnabled) sb.append("DROPBOX,");
        if (oneDriveEnabled) sb.append("ONEDRIVE,");
        if (webdavEnabled) sb.append("WEBDAV,");
        sb.append("LOCAL"); // Always include local as fallback
        return sb.toString();
    }

    /**
     * Count how many external backends are enabled.
     */
    public int getEnabledBackendCount() {
        int count = 1; // LOCAL always enabled
        if (s3Enabled) count++;
        if (googleDriveEnabled) count++;
        if (dropboxEnabled) count++;
        if (oneDriveEnabled) count++;
        if (webdavEnabled) count++;
        return count;
    }
}
