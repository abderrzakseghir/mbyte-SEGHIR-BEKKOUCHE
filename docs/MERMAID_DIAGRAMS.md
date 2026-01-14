# 📊 Schémas Mermaid pour le Rapport

Ce fichier contient tous les schémas Mermaid prêts à être copiés dans votre rapport.

> **Note**: Ces schémas sont au format Mermaid et peuvent être rendus dans GitHub, GitLab, ou tout éditeur Markdown supportant Mermaid.

---

## 1. Architecture Globale du Projet

```mermaid
graph TB
    subgraph "🌍 Accès Client"
        U[👤 Utilisateur]
    end
    
    subgraph "🔀 Load Balancer / Reverse Proxy"
        T[Traefik v3.6<br/>Ports 80, 443, 8080]
    end
    
    subgraph "🏢 Microservices"
        M[Manager<br/>Quarkus 3.30<br/>www.mbyte.fr]
        S1[Store User1<br/>user1.store.mbyte.fr]
        S2[Store User2<br/>user2.store.mbyte.fr]
        SN[Store UserN<br/>userN.store.mbyte.fr]
    end
    
    subgraph "🔐 Identity Provider"
        K[Keycloak 24<br/>OAuth2 / OIDC<br/>auth.mbyte.fr]
    end
    
    subgraph "🗂️ Service Discovery"
        C[Consul 1.19<br/>registry.mbyte.fr]
    end
    
    subgraph "📦 Stockage"
        MI[MinIO<br/>S3 Compatible<br/>minio.mbyte.fr]
    end
    
    subgraph "🗃️ Base de données"
        P[(PostgreSQL<br/>Métadonnées)]
    end
    
    U --> T
    T --> M
    T --> S1
    T --> S2
    T --> SN
    T --> K
    T --> C
    T --> MI
    
    M --> K
    M --> C
    M --> P
    
    S1 --> K
    S1 --> C
    S1 --> P
    S1 --> MI
    
    style T fill:#00d4aa,stroke:#333,color:#000
    style K fill:#f0a30a,stroke:#333,color:#000
    style MI fill:#c72c48,stroke:#333,color:#fff
    style C fill:#ca2171,stroke:#333,color:#fff
    style P fill:#336791,stroke:#333,color:#fff
```

---

## 2. Architecture Ancienne (Stockage Local)

```mermaid
flowchart TB
    subgraph "❌ Architecture Ancienne - Stockage Local"
        A[Client HTTP] --> B[REST API<br/>/api/files]
        B --> C[FileServiceBean]
        C --> D[DataStoreBean]
        D --> E[java.nio.file.Files]
        E --> F[(Système de fichiers<br/>Local Disk)]
    end
    
    subgraph "Limitations"
        L1[❌ Pas de chiffrement]
        L2[❌ Non scalable]
        L3[❌ Single point of failure]
        L4[❌ Couplage fort au FS]
    end
    
    style F fill:#ffcccc,stroke:#cc0000,stroke-width:2px
    style L1 fill:#ffeeee,stroke:#cc0000
    style L2 fill:#ffeeee,stroke:#cc0000
    style L3 fill:#ffeeee,stroke:#cc0000
    style L4 fill:#ffeeee,stroke:#cc0000
```

---

## 3. Architecture Nouvelle (Multi-Backend S3)

```mermaid
flowchart TB
    subgraph "✅ Architecture Nouvelle - Multi-Backend"
        A[Client HTTP] --> B[REST API<br/>/api/files]
        B --> C[FileServiceBean]
        C --> D[DataStoreBean]
        
        D --> E[StorageService]
        
        E --> F{Type Backend<br/>configuré}
        
        F -->|LOCAL| G[LocalStorageBackend]
        F -->|S3| H[S3StorageBackend]
        F -->|WEBDAV| I[WebDAVStorageBackend]
        
        G --> J[(Disque Local)]
        H --> K[(MinIO / AWS S3)]
        I --> L[(Serveur WebDAV)]
    end
    
    subgraph "Avantages"
        A1[✅ Scalable horizontalement]
        A2[✅ Multi-backend configurable]
        A3[✅ Redondance S3 native]
        A4[✅ Migration cloud facile]
    end
    
    style K fill:#ccffcc,stroke:#00cc00,stroke-width:2px
    style H fill:#ccffcc,stroke:#00cc00,stroke-width:2px
    style A1 fill:#eeffee,stroke:#00cc00
    style A2 fill:#eeffee,stroke:#00cc00
    style A3 fill:#eeffee,stroke:#00cc00
    style A4 fill:#eeffee,stroke:#00cc00
```

---

## 4. Diagramme de Classes - Storage Backend

```mermaid
classDiagram
    direction TB
    
    class StorageBackend {
        <<interface>>
        +getName() String
        +isAvailable() boolean
        +exists(key: String) boolean
        +put(key: String, data: InputStream) void
        +get(key: String) InputStream
        +delete(key: String) void
        +size(key: String) long
    }
    
    class LocalStorageBackend {
        -basePath: Path
        -config: LocalStorageConfig
        +getName() String
        +isAvailable() boolean
    }
    
    class S3StorageBackend {
        -s3Client: S3Client
        -config: S3StorageConfig
        -available: boolean
        +getName() String
        +isAvailable() boolean
        -initializeClient() void
        -ensureBucketExists() void
    }
    
    class WebDAVStorageBackend {
        -sardine: Sardine
        -config: WebDAVStorageConfig
        -available: boolean
        +getName() String
        +isAvailable() boolean
        -buildUrl(key: String) String
    }
    
    class StorageService {
        -backendType: String
        -localBackend: LocalStorageBackend
        -s3Backend: S3StorageBackend
        -webdavBackend: WebDAVStorageBackend
        -activeBackend: StorageBackend
        +getActiveBackend() StorageBackend
        +exists(storeId, path) boolean
        +put(storeId, path, data) void
        +get(storeId, path) InputStream
        +delete(storeId, path) void
    }
    
    class DataStoreBean {
        -config: DataStoreConfig
        -storageService: StorageService
        -storeId: String
        +exists(key) boolean
        +put(is) String
        +get(key) InputStream
        +delete(key) void
        -useExternalStorage() boolean
    }
    
    StorageBackend <|.. LocalStorageBackend
    StorageBackend <|.. S3StorageBackend
    StorageBackend <|.. WebDAVStorageBackend
    
    StorageService --> StorageBackend
    StorageService --> LocalStorageBackend
    StorageService --> S3StorageBackend
    StorageService --> WebDAVStorageBackend
    
    DataStoreBean --> StorageService
```

---

## 5. Flow d'Authentification OAuth2/OIDC

```mermaid
sequenceDiagram
    participant U as 👤 Utilisateur
    participant B as 🌐 Browser
    participant T as 🔀 Traefik
    participant M as 📋 Manager
    participant K as 🔐 Keycloak
    
    U->>B: 1. Accès à www.mbyte.fr
    B->>T: 2. GET /
    T->>M: 3. Route vers Manager
    M->>M: 4. Pas de session valide
    M-->>B: 5. HTTP 302 → Keycloak
    
    B->>K: 6. GET /realms/mbyte/auth
    K-->>B: 7. Page de login
    U->>K: 8. Username + Password
    K->>K: 9. Validation credentials
    K-->>B: 10. HTTP 302 + Authorization Code
    
    B->>M: 11. GET /callback?code=xxx
    M->>K: 12. POST /token (code → tokens)
    K-->>M: 13. Access Token + Refresh Token
    M->>M: 14. Créer session
    M-->>B: 15. HTTP 302 → /api/profiles
    
    B->>M: 16. GET /api/profiles (avec cookie)
    M->>K: 17. Introspection token
    K-->>M: 18. Token valide + user info
    M-->>B: 19. HTTP 200 + Page profil
```

---

## 6. Flow d'Upload de Fichier vers MinIO

```mermaid
sequenceDiagram
    participant U as 👤 Utilisateur
    participant T as 🔀 Traefik
    participant S as 📦 Store
    participant DS as 💾 DataStoreBean
    participant SS as 🔧 StorageService
    participant S3 as ☁️ S3Backend
    participant MI as 🪣 MinIO
    participant DB as 🗃️ PostgreSQL
    
    U->>T: 1. POST /api/files (multipart)
    T->>S: 2. Route vers Store
    S->>S: 3. Vérification token OIDC
    
    S->>DS: 4. put(inputStream)
    DS->>DS: 5. Lire contenu en mémoire
    DS->>DS: 6. Calculer SHA-256 hash
    
    DS->>SS: 7. put(storeId, hash, data)
    SS->>SS: 8. Sélectionner backend actif (S3)
    SS->>S3: 9. put(key, inputStream)
    
    S3->>MI: 10. PutObjectRequest
    MI-->>S3: 11. OK (stored)
    
    S3-->>SS: 12. Success
    SS-->>DS: 13. Success
    DS-->>S: 14. Return hash (file key)
    
    S->>DB: 15. INSERT node metadata
    DB-->>S: 16. OK
    
    S-->>U: 17. HTTP 201 Created + file info
```

---

## 7. Infrastructure Docker Compose

```mermaid
graph TB
    subgraph "Network: mbyte.net<br/>Subnet: 172.25.0.0/16"
        
        subgraph "Edge Layer"
            T[traefik<br/>172.25.0.2<br/>Ports: 80, 443, 8080]
        end
        
        subgraph "Application Layer"
            M[manager<br/>mbyte.manager<br/>www.mbyte.fr]
            S[store<br/>mbyte.*.store<br/>*.store.mbyte.fr]
        end
        
        subgraph "Identity Layer"
            K[keycloak<br/>mbyte.auth<br/>172.25.0.5<br/>auth.mbyte.fr]
        end
        
        subgraph "Storage Layer"
            MI[minio<br/>mbyte.minio<br/>172.25.0.10<br/>Ports: 9000, 9001]
        end
        
        subgraph "Infrastructure Layer"
            P[(postgres<br/>mbyte.db<br/>172.25.0.4)]
            C[consul<br/>mbyte.registry<br/>172.25.0.3<br/>Port: 8500]
        end
    end
    
    T -.->|route| M
    T -.->|route| S
    T -.->|route| K
    T -.->|route| MI
    T -.->|route| C
    
    M -->|auth| K
    M -->|discovery| C
    M -->|metadata| P
    
    S -->|auth| K
    S -->|discovery| C
    S -->|metadata| P
    S -->|files| MI
    
    K -->|users| P
    
    style T fill:#00d4aa,stroke:#333,stroke-width:2px
    style MI fill:#c72c48,stroke:#333,color:#fff
    style K fill:#f0a30a,stroke:#333
    style C fill:#ca2171,stroke:#333,color:#fff
    style P fill:#336791,stroke:#333,color:#fff
```

---

## 8. Service Discovery avec Consul

```mermaid
sequenceDiagram
    participant M as 📋 Manager
    participant C as 🗂️ Consul
    participant S as 📦 Store
    participant T as 🔀 Traefik
    
    Note over S: Démarrage du Store
    S->>C: 1. Register service<br/>"mbyte.store.abderrazak"
    C-->>S: 2. OK
    S->>C: 3. Health check pass
    
    Note over M: Utilisateur clique "Go to Store"
    M->>C: 4. Lookup "mbyte.store.abderrazak"
    C-->>M: 5. Service info + tags<br/>[fqdn.http://abderrazak.store.mbyte.fr]
    M->>M: 6. Extraire URL du store
    M-->>T: 7. Redirect vers URL
    
    T->>S: 8. Route request vers Store
```

---

## 9. Cycle de vie d'un Store

```mermaid
stateDiagram-v2
    [*] --> UserCreated: Création utilisateur
    
    UserCreated --> StoreProvisioning: Manager provisionne
    
    state StoreProvisioning {
        [*] --> CreateDB: Créer database PostgreSQL
        CreateDB --> CreateDirs: Créer répertoires
        CreateDirs --> StartContainer: Démarrer container Docker
        StartContainer --> [*]
    }
    
    StoreProvisioning --> ConsulRegistration: Container démarré
    ConsulRegistration --> Ready: Service enregistré
    
    Ready --> Serving: Requêtes utilisateur
    Serving --> Ready: Idle
    
    Ready --> Stopping: Arrêt demandé
    Stopping --> Stopped: Container arrêté
    Stopped --> Ready: Redémarrage
    
    Stopped --> [*]: Suppression
```

---

## 10. Sélection du Backend de Stockage

```mermaid
flowchart TD
    A[StorageService.init] --> B{Lire config<br/>mbyte.store.backend.type}
    
    B -->|"S3"| C{S3Backend<br/>disponible?}
    B -->|"WEBDAV"| D{WebDAV Backend<br/>disponible?}
    B -->|"LOCAL"| E[Utiliser LocalBackend]
    
    C -->|Oui| F[Utiliser S3Backend]
    C -->|Non| G{Fallback activé?}
    
    D -->|Oui| H[Utiliser WebDAVBackend]
    D -->|Non| G
    
    G -->|Oui| E
    G -->|Non| I[Erreur: Backend indisponible]
    
    F --> J[Backend Actif: S3]
    H --> K[Backend Actif: WebDAV]
    E --> L[Backend Actif: LOCAL]
    
    style F fill:#ccffcc,stroke:#00cc00
    style J fill:#ccffcc,stroke:#00cc00
```

---

## 11. Comparaison des Technologies

```mermaid
mindmap
  root((MByte Stack))
    Backend
      Quarkus 3.30
        CDI
        JAX-RS
        Hibernate
        SmallRye Config
      Java 21
        Virtual Threads
        Pattern Matching
    Storage
      MinIO
        S3 Compatible
        Self-hosted
        Gratuit
      PostgreSQL
        Métadonnées
        Transactions
      Lucene
        Full-text search
    Infrastructure
      Docker
        Containerization
        Compose
      Traefik
        Reverse Proxy
        Auto-discovery
      Consul
        Service Registry
        Health Checks
    Security
      Keycloak
        OAuth2
        OIDC
        SSO
      AES-256-GCM
        Encryption at rest
        Optional
```

---

## 12. Endpoints et Routage

```mermaid
flowchart LR
    subgraph "DNS / Hosts"
        D1[www.mbyte.fr]
        D2[auth.mbyte.fr]
        D3[*.store.mbyte.fr]
        D4[registry.mbyte.fr]
        D5[minio.mbyte.fr]
    end
    
    subgraph "Traefik Routers"
        R1[manager@docker]
        R2[keycloak@docker]
        R3[store-*@docker]
        R4[consul@docker]
        R5[minio@docker]
    end
    
    subgraph "Services"
        S1[Manager:8080]
        S2[Keycloak:80]
        S3[Store:8080]
        S4[Consul:8500]
        S5[MinIO:9000]
    end
    
    D1 --> R1 --> S1
    D2 --> R2 --> S2
    D3 --> R3 --> S3
    D4 --> R4 --> S4
    D5 --> R5 --> S5
```

---

## 📋 Comment utiliser ces schémas

### Dans GitHub/GitLab

Les schémas Mermaid sont rendus automatiquement dans les fichiers Markdown.

### Dans un rapport Word/PDF

1. Copiez le code Mermaid
2. Utilisez [Mermaid Live Editor](https://mermaid.live)
3. Exportez en PNG ou SVG
4. Insérez l'image dans votre rapport

### Dans une présentation

Exportez les schémas en images haute résolution depuis Mermaid Live Editor.

---

*Dernière mise à jour : 14 janvier 2026*
