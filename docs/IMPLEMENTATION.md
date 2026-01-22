# MByte - Technical Implementation Guide
## Issue #20: External Storage Integration

**Author:** Abderrazak SEGHIR  
**Date:** January 2026

---

## 🏗️ Architecture Overview

The external storage integration follows a **layered architecture** with clear separation of concerns:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           PRESENTATION LAYER                            │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────────┐ │
│  │ files.qute.html │  │settings.qute.html│  │ status.qute.html       │ │
│  └────────┬────────┘  └────────┬────────┘  └────────┬───────────────┘ │
│           │                    │                     │                  │
│  ┌────────▼────────┐  ┌────────▼────────┐  ┌────────▼───────────────┐ │
│  │  NodesResource  │  │SettingsResource │  │  StatusResource        │ │
│  └────────┬────────┘  └────────┬────────┘  └────────────────────────┘ │
└───────────┼────────────────────┼────────────────────────────────────────┘
            │                    │
┌───────────▼────────────────────▼────────────────────────────────────────┐
│                            SERVICE LAYER                                │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                      DataStoreBean                               │   │
│  │  - put(storeId, path, data)                                      │   │
│  │  - get(storeId, path)                                            │   │
│  │  - delete(storeId, path)                                         │   │
│  └───────────────────────────┬─────────────────────────────────────┘   │
│                              │                                          │
│  ┌───────────────────────────▼─────────────────────────────────────┐   │
│  │                 UserAwareStorageService                          │   │
│  │  @RequestScoped                                                  │   │
│  │  - Reads user's StorageSettings from database                    │   │
│  │  - Selects appropriate backend dynamically                       │   │
│  │  - Falls back to default if user backend fails                   │   │
│  └───────────────────────────┬─────────────────────────────────────┘   │
│                              │                                          │
│  ┌───────────────────────────▼─────────────────────────────────────┐   │
│  │                   StorageSettingsService                         │   │
│  │  - getOrCreateSettings(owner)                                    │   │
│  │  - updateSettings(settings)                                      │   │
│  └─────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────┘
            │
┌───────────▼─────────────────────────────────────────────────────────────┐
│                          BACKEND LAYER                                  │
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                      StorageService                              │   │
│  │  @ApplicationScoped                                              │   │
│  │  - Selects backend based on environment configuration            │   │
│  │  - Used as fallback for default S3 storage                       │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                         │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐      │
│  │   S3     │ │ WebDAV   │ │  Google  │ │ Dropbox  │ │ OneDrive │      │
│  │ Backend  │ │ Backend  │ │  Drive   │ │ Backend  │ │ Backend  │      │
│  │          │ │          │ │ Backend  │ │          │ │          │      │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘      │
│       │            │            │            │            │             │
└───────┼────────────┼────────────┼────────────┼────────────┼─────────────┘
        │            │            │            │            │
        ▼            ▼            ▼            ▼            ▼
   ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐
   │  MinIO  │  │  WebDAV │  │  Google │  │ Dropbox │  │Microsoft│
   │ Server  │  │  Server │  │  Cloud  │  │   API   │  │  Graph  │
   └─────────┘  └─────────┘  └─────────┘  └─────────┘  └─────────┘
```

---

## 📁 File Structure

```
store/src/main/java/fr/jayblanc/mbyte/store/
│
├── api/resources/
│   └── SettingsResource.java          # REST API for settings page
│
├── data/
│   ├── DataStoreBean.java             # Main data store (modified)
│   │
│   ├── settings/
│   │   ├── StorageSettings.java       # JPA Entity for user settings
│   │   └── StorageSettingsService.java # CRUD operations for settings
│   │
│   └── backend/
│       ├── StorageBackend.java        # Interface for all backends
│       ├── StorageBackendType.java    # Enum: S3, WEBDAV, DROPBOX, etc.
│       ├── StorageService.java        # Default backend selector
│       ├── UserAwareStorageService.java # User-specific backend selector (NEW)
│       │
│       ├── s3/
│       │   └── S3StorageBackend.java
│       ├── webdav/
│       │   └── WebDAVStorageBackend.java
│       ├── googledrive/
│       │   └── GoogleDriveStorageBackend.java
│       ├── dropbox/
│       │   └── DropboxStorageBackend.java
│       ├── onedrive/
│       │   └── OneDriveStorageBackend.java
│       └── multi/
│           └── MultiBackendStorageBackend.java

store/src/main/resources/
├── templates/
│   └── settings.qute.html             # Settings UI template (NEW)
└── db/
    └── changeLog.xml                  # Liquibase migrations
```

---

## 🔧 Implementation Details

### 1. StorageSettings Entity

The `StorageSettings` entity stores user-specific storage configuration in the database.

**File:** `store/src/main/java/fr/jayblanc/mbyte/store/data/settings/StorageSettings.java`

```java
@Entity
@Table(name = "storage_settings")
public class StorageSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner", nullable = false, unique = true)
    private String owner;

    // General Settings
    private boolean multiBackendEnabled = true;
    private int redundancyLevel = 2;
    private String loadBalancingStrategy = "ROUND_ROBIN";
    private boolean encryptionEnabled = true;

    // S3/MinIO Settings
    private boolean s3Enabled = true;
    private String s3Endpoint;
    private String s3AccessKey;
    private String s3SecretKey;
    private String s3Bucket;

    // Dropbox Settings
    private boolean dropboxEnabled = false;
    private String dropboxAccessToken;
    private String dropboxAppKey;
    private String dropboxAppSecret;
    private String dropboxRootPath = "/mbyte-store";

    // Google Drive, OneDrive, WebDAV settings...
    // (similar pattern)
}
```

**Key Points:**
- One record per user (`owner` is unique)
- Default values for all fields
- Credentials stored as strings (should be encrypted in production)

---

### 2. UserAwareStorageService

This is the **core component** that enables dynamic backend selection based on user settings.

**File:** `store/src/main/java/fr/jayblanc/mbyte/store/data/backend/UserAwareStorageService.java`

```java
@RequestScoped  // Important: creates new instance per request
public class UserAwareStorageService {

    @Inject
    AuthenticationService authService;

    @Inject
    StorageSettingsService settingsService;

    @Inject
    StorageService defaultStorageService;

    public void put(String storeId, String path, InputStream data) 
            throws StorageBackendException {
        
        String owner = authService.getConnectedProfile().getUsername();
        StorageSettings settings = settingsService.getOrCreateSettings(owner);
        
        // Priority-based backend selection
        if (settings.isDropboxEnabled() && hasDropboxCredentials(settings)) {
            LOG.infof("Using Dropbox backend for user: %s", owner);
            putToDropbox(settings, storeId, path, data);
        } 
        else if (settings.isGoogleDriveEnabled() && hasGoogleDriveCredentials(settings)) {
            LOG.infof("Using Google Drive backend for user: %s", owner);
            putToGoogleDrive(settings, storeId, path, data);
        }
        // ... other backends ...
        else {
            // Fallback to default S3
            defaultStorageService.put(storeId, path, data);
        }
    }

    private void putToDropbox(StorageSettings settings, String storeId, 
            String path, InputStream data) throws StorageBackendException {
        
        // Create Dropbox client dynamically from user settings
        DbxRequestConfig config = DbxRequestConfig.newBuilder("mbyte-store").build();
        DbxClientV2 client;
        
        if (settings.getDropboxAppKey() != null && settings.getDropboxAppSecret() != null) {
            // Use OAuth2 with app credentials
            DbxCredential credential = new DbxCredential(
                settings.getDropboxAccessToken(),
                -1L,
                null,
                settings.getDropboxAppKey(),
                settings.getDropboxAppSecret()
            );
            client = new DbxClientV2(config, credential);
        } else {
            // Use access token only
            client = new DbxClientV2(config, settings.getDropboxAccessToken());
        }
        
        // Build path and upload
        String dropboxPath = settings.getDropboxRootPath() + "/" + storeId + "/" + path;
        client.files().uploadBuilder(dropboxPath)
              .withMode(WriteMode.OVERWRITE)
              .uploadAndFinish(data);
    }
}
```

**Key Design Decisions:**
- `@RequestScoped` ensures user context is properly isolated
- Backend priority order: Dropbox → Google Drive → OneDrive → WebDAV → S3
- Graceful fallback to default if user backend fails
- Credentials read from database, not environment

---

### 3. DataStoreBean Modification

The main data store now delegates to `UserAwareStorageService`.

**File:** `store/src/main/java/fr/jayblanc/mbyte/store/data/DataStoreBean.java`

```java
@ApplicationScoped
public class DataStoreBean {

    @Inject
    StorageService storageService;  // Default backend
    
    @Inject
    UserAwareStorageService userAwareStorageService;  // NEW: User-aware backend

    @Inject
    CipherService cipherService;

    public void put(String storeId, String hash, InputStream data) 
            throws DataStoreException {
        try {
            InputStream processedData = cipherService.isEnabled() 
                ? cipherService.encrypt(data) 
                : data;
            
            // Changed: Use user-aware service instead of default
            userAwareStorageService.put(storeId, hash, processedData);
            
        } catch (StorageBackendException e) {
            throw new DataStoreException("Failed to store to external backend", e);
        }
    }
}
```

---

### 4. SettingsResource (REST API)

REST endpoints for the settings page.

**File:** `store/src/main/java/fr/jayblanc/mbyte/store/api/resources/SettingsResource.java`

```java
@Path("/settings")
public class SettingsResource {

    @Inject
    Template settings;  // Qute template

    @Inject
    AuthenticationService auth;

    @Inject
    StorageSettingsService settingsService;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance getSettingsPage(@QueryParam("success") String success) {
        String owner = auth.getConnectedProfile().getUsername();
        StorageSettings storageSettings = settingsService.getOrCreateSettings(owner);
        
        return settings.data("profile", auth.getConnectedProfile())
                       .data("settings", storageSettings)
                       .data("success", success);
    }

    @POST
    @Path("/backend/dropbox")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response updateDropboxSettings(
            @FormParam("dropboxEnabled") boolean enabled,
            @FormParam("dropboxAccessToken") String accessToken,
            @FormParam("dropboxAppKey") String appKey,
            @FormParam("dropboxAppSecret") String appSecret,
            @FormParam("dropboxRootPath") String rootPath) {
        
        String owner = auth.getConnectedProfile().getUsername();
        StorageSettings settings = settingsService.getOrCreateSettings(owner);
        
        settings.setDropboxEnabled(enabled);
        settings.setDropboxAccessToken(accessToken);
        settings.setDropboxAppKey(appKey);
        settings.setDropboxAppSecret(appSecret);
        settings.setDropboxRootPath(rootPath);
        
        settingsService.updateSettings(settings);
        
        // Redirect back to settings page with success message
        return Response.seeOther(URI.create("/settings?success=dropbox")).build();
    }
}
```

---

### 5. Database Migration

Liquibase changeset for the `storage_settings` table.

**File:** `store/src/main/resources/db/changeLog.xml`

```xml
<changeSet id="4" author="abderrazak">
    <createTable tableName="storage_settings">
        <column name="id" type="BIGINT" autoIncrement="true">
            <constraints primaryKey="true"/>
        </column>
        <column name="owner" type="VARCHAR(255)">
            <constraints nullable="false" unique="true"/>
        </column>
        
        <!-- General -->
        <column name="multi_backend_enabled" type="BOOLEAN" defaultValueBoolean="true"/>
        <column name="redundancy_level" type="INT" defaultValueNumeric="2"/>
        <column name="load_balancing_strategy" type="VARCHAR(50)" defaultValue="ROUND_ROBIN"/>
        <column name="encryption_enabled" type="BOOLEAN" defaultValueBoolean="true"/>
        
        <!-- S3 -->
        <column name="s3_enabled" type="BOOLEAN" defaultValueBoolean="true"/>
        <column name="s3_endpoint" type="VARCHAR(500)"/>
        <column name="s3_access_key" type="VARCHAR(255)"/>
        <column name="s3_secret_key" type="VARCHAR(255)"/>
        <column name="s3_bucket" type="VARCHAR(255)"/>
        <column name="s3_region" type="VARCHAR(50)"/>
        
        <!-- Dropbox -->
        <column name="dropbox_enabled" type="BOOLEAN" defaultValueBoolean="false"/>
        <column name="dropbox_access_token" type="VARCHAR(2048)"/>
        <column name="dropbox_app_key" type="VARCHAR(255)"/>
        <column name="dropbox_app_secret" type="VARCHAR(255)"/>
        <column name="dropbox_root_path" type="VARCHAR(500)"/>
        <column name="dropbox_refresh_token" type="VARCHAR(2048)"/>
        
        <!-- Timestamps -->
        <column name="created_at" type="TIMESTAMP" defaultValueComputed="CURRENT_TIMESTAMP"/>
        <column name="updated_at" type="TIMESTAMP" defaultValueComputed="CURRENT_TIMESTAMP"/>
    </createTable>
</changeSet>
```

---

## 🔄 Request Flow Sequence

```
User uploads file "document.pdf"
         │
         ▼
┌─────────────────┐
│ POST /api/nodes │
│ (file upload)   │
└────────┬────────┘
         │
         ▼
┌─────────────────────────────────┐
│ NodesResource.uploadFile()      │
│ - Validates file                │
│ - Generates hash                │
│ - Calls dataStore.put()         │
└────────┬────────────────────────┘
         │
         ▼
┌─────────────────────────────────┐
│ DataStoreBean.put()             │
│ - Encrypts if enabled           │
│ - Calls userAwareStorage.put()  │
└────────┬────────────────────────┘
         │
         ▼
┌─────────────────────────────────┐
│ UserAwareStorageService.put()   │
│ - Gets current user             │
│ - Loads StorageSettings from DB │
│ - Checks: Dropbox enabled?      │◄──┐
│   YES: putToDropbox()           │   │
│   NO:  Check next backend...    │   │
└────────┬────────────────────────┘   │
         │                            │
         ▼                            │
┌─────────────────────────────────┐   │
│ putToDropbox()                  │   │
│ - Creates DbxClientV2           │   │
│ - Builds path: /mbyte-store/... │   │
│ - Uploads to Dropbox API        │   │
│ - Returns success               │   │
│   OR throws exception ──────────┼───┘
└─────────────────────────────────┘   (fallback to S3)
         │
         ▼
    ✅ File stored in Dropbox
```

---

## 🧪 Testing

### Manual Testing Steps

1. **Configure Dropbox in Settings**
   - Go to `http://abderrazak.store.mbyte.fr/api/settings`
   - Enable Dropbox toggle
   - Enter Access Token (from Dropbox App Console)
   - Enter App Key and App Secret
   - Set Root Path to `/mbyte-store`
   - Click "Sauvegarder"

2. **Verify Backend Selection**
   ```bash
   # Check logs after upload
   docker logs mbyte.*.store 2>&1 | grep "Using.*backend"
   
   # Expected output:
   # Using Dropbox backend for user: abderrazak
   ```

3. **Verify File in Dropbox**
   - Check Dropbox web interface
   - File should appear at: `/mbyte-store/{store-id}/{file-hash}`

### Unit Tests

```java
@QuarkusTest
class UserAwareStorageServiceTest {

    @Inject
    UserAwareStorageService service;
    
    @Inject
    StorageSettingsService settingsService;

    @Test
    void shouldUseDropboxWhenEnabled() {
        // Setup
        StorageSettings settings = new StorageSettings();
        settings.setOwner("testuser");
        settings.setDropboxEnabled(true);
        settings.setDropboxAccessToken("test-token");
        settingsService.updateSettings(settings);
        
        // Test
        service.put("store1", "file.txt", new ByteArrayInputStream("data".getBytes()));
        
        // Verify - check logs or mock Dropbox client
    }
}
```

---

## 📋 Dropbox API Permissions Required

The Dropbox app must have these scopes enabled:

| Scope | Description |
|-------|-------------|
| `files.metadata.read` | Read file/folder metadata |
| `files.metadata.write` | Write file/folder metadata |
| `files.content.read` | Download file content |
| `files.content.write` | Upload file content |

**⚠️ After changing permissions, regenerate the Access Token!**

---

## 🚀 Deployment

### Environment Variables

```bash
# Default S3 backend (fallback)
MBYTE_STORE_BACKEND_TYPE=S3
MBYTE_STORE_BACKEND_S3_ENABLED=true
MBYTE_STORE_BACKEND_S3_ENDPOINT=http://minio:9000
MBYTE_STORE_BACKEND_S3_BUCKET=mbyte-stores
MBYTE_STORE_BACKEND_S3_ACCESS_KEY=minioadmin
MBYTE_STORE_BACKEND_S3_SECRET_KEY=minioadmin

# Encryption
MBYTE_STORE_CIPHER_ENABLED=true
```

### Docker Container

```bash
docker run -d \
  --name mbyte.store \
  --network mbyte.net \
  -e QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://db:5432/store_xxx \
  -e MBYTE_STORE_BACKEND_TYPE=S3 \
  -e MBYTE_STORE_BACKEND_S3_ENDPOINT=http://minio:9000 \
  ... \
  etudiant/store:25.1-SNAPSHOT
```
