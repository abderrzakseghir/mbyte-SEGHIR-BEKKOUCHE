# MByte - External Storage Integration
## Presentation Slides (Issue #20)

---

# 📍 Slide 1: Project Overview

## MByte File Store - External Storage Integration
### Issue #20: Multi-Cloud Storage with Encryption

**Author:** Abderrazak SEGHIR  
**Date:** January 2026

---

### 🎯 What is MByte?
A **decentralized file storage platform** that allows users to:
- Store files securely with AES-256 encryption
- Share files with other users
- Access files from anywhere

### ❓ The Challenge
| Before | Problem |
|--------|---------|
| Single S3 backend | No user choice |
| Server-controlled storage | Users can't use their own cloud |
| No redundancy | Risk of data loss |

### ✅ The Solution
**Multi-cloud storage integration** with:
- 5 storage backends (S3, Dropbox, Google Drive, OneDrive, WebDAV)
- User-configurable settings UI
- Automatic encryption before storage
- Multi-backend redundancy

---

# 📍 Slide 2: User Stories

## 📖 3 Main User Stories

### Story 1: Configure Storage Backend
> "As a user, I want to configure my preferred cloud storage backend so that my files are stored in my personal cloud account"

**Acceptance Criteria:**
- ✅ Settings page accessible from main menu
- ✅ Enable/disable each backend independently
- ✅ Enter and save credentials securely
- ✅ Success/error feedback displayed

**GUI Mock:**
```
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│  🟢 S3/MinIO    │  │  ⚪ Google Drive │  │  🟢 Dropbox     │
│  ━━━━━━━━━━━━━  │  │  ━━━━━━━━━━━━━  │  │  ━━━━━━━━━━━━━  │
│  [✓] Enabled    │  │  [ ] Enabled    │  │  [✓] Enabled    │
│                 │  │                 │  │                 │
│  Endpoint:      │  │  Client ID:     │  │  Access Token:  │
│  [minio:9000  ] │  │  [___________]  │  │  [•••••••••••]  │
│                 │  │                 │  │                 │
│   [💾 Save]     │  │   [💾 Save]     │  │   [💾 Save]     │
└─────────────────┘  └─────────────────┘  └─────────────────┘
```

---

### Story 2: Upload Files to Cloud Backend
> "As a user, I want my uploaded files to be automatically stored in my configured cloud backend"

**Flow:**
```
User uploads file
        │
        ▼
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│ Detect User's   │────▶│ Encrypt File    │────▶│ Store in Cloud  │
│ Backend Config  │     │ (AES-256-GCM)   │     │ (Dropbox/S3)    │
└─────────────────┘     └─────────────────┘     └─────────────────┘
```

**Priority Order:**
1. Dropbox (if enabled)
2. Google Drive (if enabled)
3. OneDrive (if enabled)
4. WebDAV (if enabled)
5. S3/MinIO (default fallback)

---

### Story 3: Multi-Backend Redundancy
> "As a user, I want my files stored on multiple backends so I don't lose data if one cloud provider fails"

**Features:**
- Redundancy level: 1, 2, or 3 copies
- Load balancing: Round Robin, Random, Priority
- Automatic failover on errors

```
                    File Upload
                        │
           ┌────────────┼────────────┐
           ▼            ▼            ▼
      ┌────────┐   ┌────────┐   ┌────────┐
      │Dropbox │   │ Google │   │   S3   │
      │ Copy 1 │   │ Copy 2 │   │(standby│
      └────────┘   └────────┘   └────────┘
```

---

# 📍 Slide 3: Technical Architecture

## 🏗️ Layered Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                    PRESENTATION LAYER                        │
│                                                              │
│   settings.qute.html    │    SettingsResource.java          │
│   (HTML form)           │    (@Path("/settings"))           │
└──────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────┐
│                     SERVICE LAYER                            │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐  │
│  │                  DataStoreBean                         │  │
│  │                       │                                │  │
│  │         ┌─────────────▼──────────────┐                 │  │
│  │         │  UserAwareStorageService   │  ◄── KEY CLASS  │  │
│  │         │  @RequestScoped            │                 │  │
│  │         │  - Reads user settings     │                 │  │
│  │         │  - Selects backend         │                 │  │
│  │         │  - Creates API clients     │                 │  │
│  │         └────────────────────────────┘                 │  │
│  │                       │                                │  │
│  │         ┌─────────────▼──────────────┐                 │  │
│  │         │  StorageSettingsService    │                 │  │
│  │         │  - CRUD for user settings  │                 │  │
│  │         └────────────────────────────┘                 │  │
│  └────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────┐
│                     BACKEND LAYER                            │
│                                                              │
│  ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐     │
│  │   S3   │ │ WebDAV │ │ Google │ │Dropbox │ │OneDrive│     │
│  │Backend │ │Backend │ │ Drive  │ │Backend │ │Backend │     │
│  └────┬───┘ └────┬───┘ └────┬───┘ └────┬───┘ └────┬───┘     │
└───────┼──────────┼──────────┼──────────┼──────────┼──────────┘
        │          │          │          │          │
        ▼          ▼          ▼          ▼          ▼
   ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐
   │  MinIO  │ │  WebDAV │ │  Google │ │ Dropbox │ │Microsoft│
   │ Server  │ │  Server │ │  Cloud  │ │   API   │ │  Graph  │
   └─────────┘ └─────────┘ └─────────┘ └─────────┘ └─────────┘
```

---

## 📊 Key Components

| Component | File | Purpose |
|-----------|------|---------|
| `StorageSettings` | Entity | JPA entity storing user config |
| `SettingsResource` | Resource | REST API for settings page |
| `UserAwareStorageService` | Service | Dynamic backend selection |
| `StorageService` | Service | Default S3 fallback |
| `settings.qute.html` | Template | Settings UI |

---

## 💾 Database Schema

```sql
CREATE TABLE storage_settings (
    id BIGSERIAL PRIMARY KEY,
    owner VARCHAR(255) UNIQUE NOT NULL,  -- Username
    
    -- General Settings
    multi_backend_enabled BOOLEAN DEFAULT true,
    redundancy_level INTEGER DEFAULT 2,
    encryption_enabled BOOLEAN DEFAULT true,
    
    -- Dropbox
    dropbox_enabled BOOLEAN DEFAULT false,
    dropbox_access_token VARCHAR(2048),
    dropbox_app_key VARCHAR(255),
    dropbox_app_secret VARCHAR(255),
    dropbox_root_path VARCHAR(500) DEFAULT '/mbyte-store',
    
    -- S3, Google Drive, OneDrive, WebDAV...
    -- (similar pattern)
);
```

---

# 📍 Slide 4: Implementation & Demo

## 🔧 Key Implementation: UserAwareStorageService

```java
@RequestScoped  // New instance per HTTP request
public class UserAwareStorageService {

    @Inject AuthenticationService authService;
    @Inject StorageSettingsService settingsService;
    @Inject StorageService defaultStorageService;

    public void put(String storeId, String path, InputStream data) {
        
        // 1. Get current user
        String owner = authService.getConnectedProfile().getUsername();
        
        // 2. Load user's settings from database
        StorageSettings settings = settingsService.getOrCreateSettings(owner);
        
        // 3. Select backend based on settings
        if (settings.isDropboxEnabled() && hasCredentials(settings)) {
            LOG.info("Using Dropbox backend for user: " + owner);
            putToDropbox(settings, storeId, path, data);
        } else {
            // Fallback to default S3
            defaultStorageService.put(storeId, path, data);
        }
    }
}
```

---

## 🎬 Demo: Dropbox Integration

### Step 1: Create Dropbox App
1. Go to developers.dropbox.com
2. Create App → Scoped access
3. Enable permissions:
   - `files.content.read`
   - `files.content.write`
   - `files.metadata.read`
   - `files.metadata.write`
4. Generate Access Token

### Step 2: Configure in MByte
1. Go to `http://abderrazak.store.mbyte.fr/api/settings`
2. Enable Dropbox toggle
3. Enter Access Token, App Key, App Secret
4. Click "Sauvegarder"

### Step 3: Upload File
1. Go to "Mon Espace"
2. Upload any file
3. Check Docker logs:
   ```bash
   docker logs mbyte.store 2>&1 | grep "backend"
   # Output: Using Dropbox backend for user: abderrazak
   ```
4. Verify file appears in Dropbox!

---

## 📊 Technologies Used

| Technology | Version | Purpose |
|------------|---------|---------|
| **Quarkus** | 3.30.1 | Java framework |
| **Qute** | - | HTML templating |
| **JPA/Hibernate** | - | Database ORM |
| **Liquibase** | - | DB migrations |
| **Dropbox SDK** | 7.0.0 | Dropbox API |
| **AWS SDK v2** | - | S3/MinIO |
| **Docker** | - | Containerization |

---

## 🚀 Future Improvements

| Feature | Description | Priority |
|---------|-------------|----------|
| OAuth2 Flow | Full redirect flow for Google/Dropbox | High |
| Token Refresh | Auto-refresh expired OAuth tokens | High |
| Credential Encryption | Encrypt stored API keys | Medium |
| Health Checks | Real-time backend monitoring | Medium |
| Sync Status | Show which backends have each file | Low |

---

## ✅ Summary

### What We Achieved

| Feature | Status |
|---------|--------|
| 5 storage backends | ✅ Implemented |
| User settings UI | ✅ Complete |
| Database persistence | ✅ Working |
| Dynamic backend selection | ✅ Working |
| AES-256 encryption | ✅ Enabled |
| Multi-backend redundancy | ✅ Available |
| Dropbox tested | ✅ Working |

### Impact
- Users can store files in their **own cloud accounts**
- **Data redundancy** prevents data loss
- **Encryption** ensures privacy

---

## ❓ Questions?

### Links:
- **Demo URL:** http://abderrazak.store.mbyte.fr
- **Settings:** http://abderrazak.store.mbyte.fr/api/settings
- **Branch:** `20-external-storage-integration-s3-webdav-etc-with-ciphering`
- **Repository:** github.com/abderrzakseghir/mbyte-SEGHIR-BEKKOUCHE

---

# Thank you! 🎉
