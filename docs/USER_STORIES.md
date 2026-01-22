# MByte - External Storage Integration
## User Stories Documentation

**Issue #20: External Storage Integration (S3, WebDAV, etc.) with Ciphering**

**Author:** Abderrazak SEGHIR  
**Date:** January 2026  
**Version:** 25.1-SNAPSHOT

---

## 📋 Functional Scope

### Overview
The MByte file store application allows users to store and share files securely. This feature extends the storage capabilities by enabling users to configure **external cloud storage backends** (Google Drive, Dropbox, OneDrive, WebDAV, S3/MinIO) as their primary storage location, with support for **data encryption** and **multi-backend redundancy**.

### Key Capabilities
- **Multi-cloud storage**: Connect to 5 different storage providers
- **User-level configuration**: Each user can configure their own storage backends
- **Automatic encryption**: AES-256-GCM encryption before external storage
- **Redundancy**: Store files on multiple backends for data safety
- **Seamless integration**: Backend selection is transparent to the user experience

---

## 📖 User Story 1: Configure Storage Backend

### Title
**As a user, I want to configure my preferred cloud storage backend so that my files are stored in my personal cloud account**

### Description
Users should be able to access a settings page where they can enable and configure external storage backends. Each backend requires specific credentials (API keys, tokens, endpoints) that the user provides.

### Acceptance Criteria
- [ ] User can access a "Storage Settings" page from the main menu
- [ ] User can enable/disable each storage backend independently
- [ ] User can enter and save credentials for each backend:
  - **S3/MinIO**: Endpoint, Access Key, Secret Key, Bucket, Region
  - **Google Drive**: Client ID, Client Secret, Refresh Token, Folder ID
  - **Dropbox**: Access Token, App Key, App Secret, Root Path
  - **OneDrive**: Access Token, App Key, App Secret, Root Path
  - **WebDAV**: Server URL, Username, Password, Base Path
- [ ] Credentials are securely stored in the database
- [ ] Success/error feedback is shown after saving

### GUI Mock

```
┌──────────────────────────────────────────────────────────────────────────┐
│  ⚙️ Storage Settings                                                     │
├──────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  ┌─────────────────────────────────────────────────────────────────────┐ │
│  │ 📊 General Settings                                                 │ │
│  ├─────────────────────────────────────────────────────────────────────┤ │
│  │  [✓] Multi-Backend (Redundancy)    [✓] AES-256 Encryption          │ │
│  │                                                                      │ │
│  │  Redundancy Level: [2 - Double copy     ▼]                          │ │
│  │  Load Balancing:   [Round Robin         ▼]                          │ │
│  │                                              [💾 Save]              │ │
│  └─────────────────────────────────────────────────────────────────────┘ │
│                                                                          │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐          │
│  │  🟢 S3/MinIO    │  │  ⚪ Google Drive │  │  🟢 Dropbox     │          │
│  │  ━━━━━━━━━━━━━  │  │  ━━━━━━━━━━━━━  │  │  ━━━━━━━━━━━━━  │          │
│  │  [✓] Enable     │  │  [ ] Enable     │  │  [✓] Enable     │          │
│  │                 │  │                 │  │                 │          │
│  │  Endpoint:      │  │  Client ID:     │  │  Access Token:  │          │
│  │  [minio:9000  ] │  │  [___________]  │  │  [••••••••••• ] │          │
│  │                 │  │                 │  │                 │          │
│  │  Access Key:    │  │  Client Secret: │  │  App Key:       │          │
│  │  [minioadmin  ] │  │  [___________]  │  │  [qxv2bnw...  ] │          │
│  │                 │  │                 │  │                 │          │
│  │  Secret Key:    │  │  Refresh Token: │  │  Root Path:     │          │
│  │  [••••••••••• ] │  │  [___________]  │  │  [/mbyte-store] │          │
│  │                 │  │                 │  │                 │          │
│  │  Bucket:        │  │                 │  │   [💾 Save]     │          │
│  │  [mbyte-stores] │  │   [💾 Save]     │  │                 │          │
│  │                 │  │                 │  │                 │          │
│  │   [💾 Save]     │  └─────────────────┘  └─────────────────┘          │
│  └─────────────────┘                                                     │
│                                                                          │
│  ┌─────────────────┐  ┌─────────────────┐                               │
│  │  ⚪ OneDrive    │  │  ⚪ WebDAV      │                               │
│  │  ━━━━━━━━━━━━━  │  │  ━━━━━━━━━━━━━  │                               │
│  │  [ ] Enable     │  │  [ ] Enable     │                               │
│  │  ...            │  │  ...            │                               │
│  └─────────────────┘  └─────────────────┘                               │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

### Priority
**HIGH** - Core feature for external storage integration

---

## 📖 User Story 2: Upload Files to Cloud Backend

### Title
**As a user, I want my uploaded files to be automatically stored in my configured cloud backend so that I can access them from anywhere**

### Description
When a user uploads a file, the system should automatically detect the user's configured storage backend and store the file there. If encryption is enabled, the file should be encrypted before storage. If multi-backend is enabled, the file should be stored on multiple backends for redundancy.

### Acceptance Criteria
- [ ] Files are stored in the user's primary enabled backend
- [ ] System follows backend priority: Dropbox → Google Drive → OneDrive → WebDAV → S3
- [ ] Files are encrypted with AES-256-GCM if encryption is enabled
- [ ] Files are stored in multiple backends if redundancy is enabled
- [ ] Fallback to default S3 storage if user backend fails
- [ ] Upload status is logged for debugging

### Flow Diagram

```
┌──────────────┐     ┌─────────────────────┐     ┌───────────────────────┐
│              │     │                     │     │                       │
│    User      │────▶│   Upload Request    │────▶│   DataStoreBean       │
│              │     │   (POST /api/data)  │     │                       │
└──────────────┘     └─────────────────────┘     └───────────┬───────────┘
                                                             │
                                                             ▼
                     ┌───────────────────────────────────────────────────┐
                     │                                                   │
                     │            UserAwareStorageService                │
                     │                                                   │
                     │  1. Get current user                              │
                     │  2. Load user's StorageSettings from DB           │
                     │  3. Check enabled backends in priority order      │
                     │  4. Encrypt data if encryption enabled            │
                     │                                                   │
                     └───────────────────────┬───────────────────────────┘
                                             │
               ┌─────────────────────────────┼─────────────────────────────┐
               │                             │                             │
               ▼                             ▼                             ▼
    ┌─────────────────┐          ┌─────────────────┐          ┌─────────────────┐
    │                 │          │                 │          │                 │
    │     Dropbox     │          │  Google Drive   │          │    S3/MinIO     │
    │                 │          │                 │          │   (Fallback)    │
    │  PUT /files/    │          │  files.insert   │          │                 │
    │  {path}         │          │                 │          │  PutObject      │
    │                 │          │                 │          │                 │
    └─────────────────┘          └─────────────────┘          └─────────────────┘
```

### GUI Mock (Upload with Backend Indicator)

```
┌──────────────────────────────────────────────────────────────────────────┐
│  📁 Mon Espace                                                           │
├──────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  ┌─────────────────────────────────────────────────────────────────────┐ │
│  │  📤 Upload File                                                     │ │
│  │  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━│ │
│  │                                                                      │ │
│  │  ┌─────────────────────────────────────────────────────────────┐    │ │
│  │  │                                                             │    │ │
│  │  │         🔒 Drag & drop files here                          │    │ │
│  │  │             or click to browse                              │    │ │
│  │  │                                                             │    │ │
│  │  │  📦 Storage: Dropbox (encrypted)                           │    │ │
│  │  │  🔄 Redundancy: 2 copies                                   │    │ │
│  │  │                                                             │    │ │
│  │  └─────────────────────────────────────────────────────────────┘    │ │
│  │                                                                      │ │
│  │  Uploading: document.pdf                                            │ │
│  │  ████████████████████░░░░░░░░░░░ 65%                                │ │
│  │                                                                      │ │
│  │  ✅ report.xlsx → Dropbox (/mbyte-store/abc123/data)                │ │
│  │  ✅ photo.jpg → Dropbox (/mbyte-store/abc123/data)                  │ │
│  │                                                                      │ │
│  └─────────────────────────────────────────────────────────────────────┘ │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

### Priority
**HIGH** - Core file storage functionality

---

## 📖 User Story 3: Multi-Backend Redundancy

### Title
**As a user, I want my files stored on multiple backends so that I don't lose data if one cloud provider fails**

### Description
When multi-backend mode is enabled, files should be stored on multiple configured backends simultaneously. The redundancy level determines how many copies are stored. Load balancing strategy determines how reads are distributed.

### Acceptance Criteria
- [ ] User can enable "Multi-Backend" mode in settings
- [ ] User can set redundancy level (1, 2, 3, or more copies)
- [ ] User can choose load balancing strategy (Round Robin, Random, Priority)
- [ ] Files are stored to N backends based on redundancy level
- [ ] Read operations use load balancing strategy
- [ ] If one backend fails, system continues with remaining backends
- [ ] Status page shows health of each backend

### Architecture Diagram

```
                              ┌────────────────────────────┐
                              │                            │
                              │    MultiBackendService     │
                              │                            │
                              │   - redundancyLevel: 2     │
                              │   - strategy: ROUND_ROBIN  │
                              │                            │
                              └────────────┬───────────────┘
                                           │
                                           │ PUT (file)
                                           │
              ┌────────────────────────────┼────────────────────────────┐
              │                            │                            │
              ▼                            ▼                            ▼
   ┌────────────────────┐      ┌────────────────────┐      ┌────────────────────┐
   │                    │      │                    │      │                    │
   │   📦 Dropbox       │      │   ☁️ Google Drive  │      │   🪣 S3/MinIO      │
   │                    │      │                    │      │                    │
   │   Copy 1 ✅        │      │   Copy 2 ✅        │      │   (standby)        │
   │                    │      │                    │      │                    │
   └────────────────────┘      └────────────────────┘      └────────────────────┘

                                    GET (file)
                                        │
                        ┌───────────────┴───────────────┐
                        │                               │
                        ▼                               ▼
              ┌────────────────┐              ┌────────────────┐
              │  Request 1     │              │  Request 2     │
              │  → Dropbox     │              │  → Google Drive│
              └────────────────┘              └────────────────┘
                   (Round Robin alternates between backends)
```

### GUI Mock (Status Dashboard)

```
┌──────────────────────────────────────────────────────────────────────────┐
│  📊 Status - Storage Backends                                            │
├──────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  ┌─────────────────────────────────────────────────────────────────────┐ │
│  │  Backend Health                                                     │ │
│  ├─────────────────────────────────────────────────────────────────────┤ │
│  │                                                                      │ │
│  │   Backend         Status      Latency    Files    Storage           │ │
│  │   ──────────────────────────────────────────────────────────────    │ │
│  │   🟢 Dropbox      Connected    45ms      1,234    2.5 GB            │ │
│  │   🟢 S3/MinIO     Connected    12ms      1,234    2.5 GB            │ │
│  │   🔴 Google Drive Error        --        --       --                │ │
│  │   ⚪ OneDrive     Disabled     --        --       --                │ │
│  │   ⚪ WebDAV       Disabled     --        --       --                │ │
│  │                                                                      │ │
│  └─────────────────────────────────────────────────────────────────────┘ │
│                                                                          │
│  ┌─────────────────────────────────────────────────────────────────────┐ │
│  │  Redundancy Status                                                  │ │
│  ├─────────────────────────────────────────────────────────────────────┤ │
│  │                                                                      │ │
│  │   Mode: Multi-Backend (2 copies)                                    │ │
│  │   Strategy: Round Robin                                             │ │
│  │                                                                      │ │
│  │   ████████████████████████████████████████ 100%                     │ │
│  │   All files have 2+ copies                                          │ │
│  │                                                                      │ │
│  └─────────────────────────────────────────────────────────────────────┘ │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

### Priority
**MEDIUM** - Enhanced reliability feature

---

## 🔧 Technical Requirements

### Backend Storage Providers
| Provider | API | Authentication |
|----------|-----|----------------|
| S3/MinIO | AWS SDK v2 | Access Key + Secret |
| Google Drive | Google Drive API v3 | OAuth2 + Refresh Token |
| Dropbox | Dropbox Java SDK | OAuth2 Access Token |
| OneDrive | Microsoft Graph API | OAuth2 + Refresh Token |
| WebDAV | Sardine HTTP Client | Basic Auth or Digest |

### Security
- **Encryption**: AES-256-GCM with random IV
- **Key Storage**: Encrypted in database with master key
- **Token Refresh**: Automatic refresh for OAuth2 tokens
- **Credential Masking**: Passwords hidden in UI (type=password)

### Database Schema
```sql
CREATE TABLE storage_settings (
    id BIGSERIAL PRIMARY KEY,
    owner VARCHAR(255) UNIQUE NOT NULL,
    
    -- General
    multi_backend_enabled BOOLEAN DEFAULT true,
    redundancy_level INTEGER DEFAULT 2,
    load_balancing_strategy VARCHAR(50) DEFAULT 'ROUND_ROBIN',
    encryption_enabled BOOLEAN DEFAULT true,
    
    -- S3
    s3_enabled BOOLEAN DEFAULT true,
    s3_endpoint VARCHAR(500),
    s3_access_key VARCHAR(255),
    s3_secret_key VARCHAR(255),
    s3_bucket VARCHAR(255),
    s3_region VARCHAR(50),
    
    -- Dropbox
    dropbox_enabled BOOLEAN DEFAULT false,
    dropbox_access_token VARCHAR(2048),
    dropbox_app_key VARCHAR(255),
    dropbox_app_secret VARCHAR(255),
    dropbox_root_path VARCHAR(500),
    
    -- ... other backends
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

---

## 📅 Implementation Timeline

| Phase | Duration | Deliverables |
|-------|----------|--------------|
| Phase 1 | 2 days | Storage backend implementations (S3, WebDAV, Google Drive, Dropbox, OneDrive) |
| Phase 2 | 1 day | StorageSettings entity and database migration |
| Phase 3 | 1 day | Settings UI (settings.qute.html) and REST API |
| Phase 4 | 1 day | UserAwareStorageService for dynamic backend selection |
| Phase 5 | 1 day | Testing and bug fixes |

**Total: ~6 days**
