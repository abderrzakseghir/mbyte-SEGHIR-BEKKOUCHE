# 🎓 MByte - Documentation de Présentation

> **Projet de TP - Janvier 2026**  
> Réalisé par : SEGHIR & BEKKOUCHE

---

## 📋 Table des matières

1. [Vue d'ensemble du projet](#vue-densemble-du-projet)
2. [Architecture globale](#architecture-globale)
3. [Technologies utilisées](#technologies-utilisées)
4. [Flow d'authentification](#flow-dauthentification)
5. [Flow de stockage de fichiers](#flow-de-stockage-de-fichiers)
6. [Infrastructure Docker](#infrastructure-docker)
7. [Issue #20 - Intégration stockage externe](#issue-20---intégration-stockage-externe)
8. [Choix technologiques](#choix-technologiques)
9. [Schémas détaillés](#schémas-détaillés)

---

## 🌐 Vue d'ensemble du projet

MByte est une plateforme de stockage de fichiers distribuée permettant à chaque utilisateur de disposer de son propre **store** (espace de stockage). L'architecture est basée sur des microservices avec :

- **Manager** : Gestion des profils utilisateurs et orchestration des stores
- **Store** : Service de stockage de fichiers par utilisateur
- **Auth (Keycloak)** : Authentification et autorisation OAuth2/OIDC
- **Consul** : Service Discovery et configuration
- **Traefik** : Reverse proxy et routage dynamique
- **MinIO** : Stockage objet compatible S3

```mermaid
graph TB
    subgraph "🌍 Internet"
        U[👤 Utilisateur]
    end
    
    subgraph "🔀 Reverse Proxy"
        T[Traefik<br/>Port 80/443]
    end
    
    subgraph "🏢 Services MByte"
        M[Manager<br/>www.mbyte.fr]
        S1[Store abderrazak<br/>abderrazak.store.mbyte.fr]
        S2[Store user2<br/>user2.store.mbyte.fr]
    end
    
    subgraph "🔐 Authentification"
        K[Keycloak<br/>auth.mbyte.fr]
    end
    
    subgraph "📦 Stockage"
        MI[MinIO<br/>S3 Compatible]
    end
    
    subgraph "🗃️ Infrastructure"
        C[Consul<br/>Service Discovery]
        P[(PostgreSQL)]
    end
    
    U --> T
    T --> M
    T --> S1
    T --> S2
    T --> K
    
    M --> C
    M --> P
    M --> K
    
    S1 --> C
    S1 --> P
    S1 --> MI
    S1 --> K
    
    S2 --> C
    S2 --> P
    S2 --> MI
    
    style T fill:#00d4aa,stroke:#333,color:#000
    style K fill:#f0a30a,stroke:#333,color:#000
    style MI fill:#c72c48,stroke:#333,color:#fff
    style C fill:#ca2171,stroke:#333,color:#fff
```

---

## 🏗️ Architecture globale

### Architecture 3-tiers avec microservices

```mermaid
flowchart TB
    subgraph "Couche Présentation"
        WEB[Interface Web<br/>HTML/CSS/JS]
    end
    
    subgraph "Couche Application - Microservices"
        direction LR
        API1[Manager API<br/>Quarkus REST]
        API2[Store API<br/>Quarkus REST]
    end
    
    subgraph "Couche Données"
        direction LR
        DB[(PostgreSQL<br/>Metadata)]
        OBJ[(MinIO<br/>Object Storage)]
        IDX[(Lucene<br/>Index)]
    end
    
    WEB --> API1
    WEB --> API2
    API1 --> DB
    API2 --> DB
    API2 --> OBJ
    API2 --> IDX
    
    style WEB fill:#4a90d9,stroke:#333,color:#fff
    style API1 fill:#ee0000,stroke:#333,color:#fff
    style API2 fill:#ee0000,stroke:#333,color:#fff
    style DB fill:#336791,stroke:#333,color:#fff
    style OBJ fill:#c72c48,stroke:#333,color:#fff
```

### Communication entre services

```mermaid
sequenceDiagram
    participant U as 👤 Utilisateur
    participant T as 🔀 Traefik
    participant M as 📋 Manager
    participant C as 🗂️ Consul
    participant S as 📦 Store
    participant K as 🔐 Keycloak
    
    U->>T: GET www.mbyte.fr
    T->>M: Route vers Manager
    M->>K: Vérification token OIDC
    K-->>M: Token valide
    M->>C: Lookup store.abderrazak
    C-->>M: URL du store
    M-->>U: Redirect vers store
    
    U->>T: GET abderrazak.store.mbyte.fr
    T->>S: Route vers Store
    S->>K: Vérification token
    K-->>S: Token valide
    S-->>U: Page du store
```

---

## 🛠️ Technologies utilisées

### Backend

| Technologie | Version | Rôle |
|-------------|---------|------|
| **Quarkus** | 3.30.1 | Framework Java cloud-native |
| **Java** | 21 (LTS) | Langage de programmation |
| **JAX-RS (RESTEasy)** | - | API REST |
| **Hibernate ORM** | - | Mapping objet-relationnel |
| **Liquibase** | - | Migration de base de données |
| **Apache Tika** | - | Détection de types MIME |
| **Apache Lucene** | - | Indexation full-text |

### Stockage

| Technologie | Rôle |
|-------------|------|
| **PostgreSQL** | Base de données relationnelle (métadonnées) |
| **MinIO** | Stockage objet compatible S3 |
| **AWS SDK 2.25** | Client S3 pour MinIO/AWS |
| **Sardine** | Client WebDAV |

### Infrastructure

| Technologie | Version | Rôle |
|-------------|---------|------|
| **Docker** | 29.x | Conteneurisation |
| **Traefik** | 3.6 | Reverse proxy / Load balancer |
| **Consul** | 1.19 | Service discovery & Config |
| **Keycloak** | 24.x | Identity & Access Management |

### Sécurité

| Technologie | Rôle |
|-------------|------|
| **OAuth2 / OIDC** | Protocole d'authentification |
| **Keycloak** | Serveur d'identité |
| **AES-256-GCM** | Chiffrement des données (optionnel) |

---

## 🔐 Flow d'authentification

### OAuth2 / OpenID Connect avec Keycloak

```mermaid
sequenceDiagram
    participant U as 👤 Utilisateur
    participant B as 🌐 Browser
    participant M as 📋 Manager
    participant K as 🔐 Keycloak
    
    U->>B: Accès à www.mbyte.fr
    B->>M: GET /api/profiles
    M->>M: Pas de token valide
    M-->>B: 302 Redirect to Keycloak
    
    B->>K: Login page
    U->>K: Credentials (username/password)
    K->>K: Validation
    K-->>B: 302 Redirect + Authorization Code
    
    B->>M: GET /callback?code=xxx
    M->>K: POST /token (code exchange)
    K-->>M: Access Token + Refresh Token
    M->>M: Set session cookie
    M-->>B: 302 Redirect to /api/profiles
    
    B->>M: GET /api/profiles (with cookie)
    M->>K: Validate token
    K-->>M: Token valid + user info
    M-->>B: 200 OK + Profile page
```

### Configuration OIDC dans Quarkus

```properties
# application.properties
quarkus.oidc.auth-server-url=http://auth.mbyte.fr/realms/mbyte
quarkus.oidc.client-id=mbyte
quarkus.oidc.application-type=hybrid
quarkus.oidc.token.principal-claim=preferred_username
quarkus.oidc.roles.source=accesstoken
```

---

## 📁 Flow de stockage de fichiers

### Upload de fichier avec MinIO

```mermaid
sequenceDiagram
    participant U as 👤 Utilisateur
    participant S as 📦 Store Service
    participant DS as 💾 DataStoreBean
    participant SS as 🔧 StorageService
    participant S3 as 🪣 S3Backend
    participant MI as ☁️ MinIO
    
    U->>S: POST /api/files (multipart)
    S->>S: Vérification auth
    S->>DS: put(inputStream)
    
    DS->>DS: Calcul SHA256 hash
    DS->>SS: put(storeId, hash, data)
    
    SS->>SS: Sélection backend (S3)
    SS->>S3: put(key, inputStream)
    
    S3->>MI: PutObject(bucket, key, data)
    MI-->>S3: OK
    
    S3-->>SS: OK
    SS-->>DS: OK
    DS-->>S: hash (file key)
    
    S->>S: Save metadata in DB
    S-->>U: 201 Created + file info
```

### Architecture du stockage (Issue #20)

```mermaid
flowchart TB
    subgraph "API Layer"
        A[FileServiceBean]
    end
    
    subgraph "Data Layer"
        B[DataStoreBean]
    end
    
    subgraph "Storage Abstraction"
        C[StorageService]
        D{Backend Type?}
    end
    
    subgraph "Backend Implementations"
        E[LocalStorageBackend]
        F[S3StorageBackend]
        G[WebDAVStorageBackend]
    end
    
    subgraph "Storage Systems"
        H[(Local Disk)]
        I[(MinIO / AWS S3)]
        J[(WebDAV Server)]
    end
    
    A --> B
    B --> C
    C --> D
    
    D -->|LOCAL| E
    D -->|S3| F
    D -->|WEBDAV| G
    
    E --> H
    F --> I
    G --> J
    
    style I fill:#c72c48,stroke:#333,color:#fff
    style F fill:#00d4aa,stroke:#333,color:#000
```

---

## 🐳 Infrastructure Docker

### Docker Compose - Services

```mermaid
graph TB
    subgraph "Network: mbyte.net (172.25.0.0/16)"
        
        subgraph "Proxy Layer"
            T[traefik<br/>172.25.0.2<br/>:80, :443, :8080]
        end
        
        subgraph "Application Layer"
            M[manager<br/>www.mbyte.fr]
            S[store<br/>*.store.mbyte.fr]
        end
        
        subgraph "Auth Layer"
            K[keycloak<br/>auth.mbyte.fr<br/>172.25.0.5]
        end
        
        subgraph "Storage Layer"
            MI[minio<br/>172.25.0.10<br/>:9000, :9001]
        end
        
        subgraph "Data Layer"
            P[(postgres<br/>172.25.0.4)]
            C[consul<br/>172.25.0.3<br/>:8500]
        end
    end
    
    T --> M
    T --> S
    T --> K
    T --> MI
    
    M --> P
    M --> C
    M --> K
    
    S --> P
    S --> C
    S --> MI
    S --> K
    
    K --> P
    
    style T fill:#00d4aa,stroke:#333
    style MI fill:#c72c48,stroke:#333,color:#fff
    style K fill:#f0a30a,stroke:#333
```

### Labels Traefik pour le routage

```yaml
# Exemple pour le store
labels:
  - "traefik.enable=true"
  - "traefik.http.routers.store-abderrazak.rule=Host(`abderrazak.store.mbyte.fr`)"
  - "traefik.http.routers.store-abderrazak.entrypoints=http"
  - "traefik.http.services.store-abderrazak.loadbalancer.server.port=8080"
```

---

## 🔌 Issue #20 - Intégration stockage externe

### Objectif

Permettre le stockage des fichiers sur des backends externes (S3, WebDAV) au lieu du système de fichiers local.

### Avant (Architecture locale)

```mermaid
flowchart LR
    A[DataStoreBean] --> B[java.nio.file.Files]
    B --> C[(Local Disk)]
    
    style C fill:#ffcccc,stroke:#cc0000
```

**Limitations :**
- ❌ Non scalable
- ❌ Single point of failure
- ❌ Pas de redondance
- ❌ Difficile à distribuer

### Après (Architecture multi-backend)

```mermaid
flowchart TB
    A[DataStoreBean] --> B[StorageService]
    
    B --> C{Backend configuré}
    
    C -->|LOCAL| D[LocalStorageBackend]
    C -->|S3| E[S3StorageBackend]
    C -->|WEBDAV| F[WebDAVStorageBackend]
    
    D --> G[(Local Disk)]
    E --> H[(MinIO / AWS S3)]
    F --> I[(WebDAV Server)]
    
    style H fill:#ccffcc,stroke:#00cc00
    style E fill:#ccffcc,stroke:#00cc00
```

**Avantages :**
- ✅ Scalabilité horizontale
- ✅ Redondance native (S3)
- ✅ Choix du backend par configuration
- ✅ Migration facile vers le cloud

### Interface StorageBackend

```java
public interface StorageBackend {
    String getName();
    boolean isAvailable();
    boolean exists(String key);
    void put(String key, InputStream data) throws StorageBackendException;
    InputStream get(String key) throws StorageBackendException;
    void delete(String key) throws StorageBackendException;
    long size(String key) throws StorageBackendException;
}
```

### Configuration

```properties
# Type de backend: LOCAL, S3, WEBDAV
mbyte.store.backend.type=S3

# Configuration S3/MinIO
mbyte.store.backend.s3.enabled=true
mbyte.store.backend.s3.endpoint=http://minio:9000
mbyte.store.backend.s3.access-key=minioadmin
mbyte.store.backend.s3.secret-key=minioadmin
mbyte.store.backend.s3.bucket=mbyte-stores
mbyte.store.backend.s3.region=us-east-1
mbyte.store.backend.s3.path-style-access=true
```

---

## 💡 Choix technologiques

### Pourquoi Quarkus ?

```mermaid
mindmap
  root((Quarkus))
    Performance
      Démarrage rapide
      Faible empreinte mémoire
      GraalVM Native
    Productivité
      Dev mode avec hot reload
      Extensions riches
      Configuration unifiée
    Cloud Native
      Container first
      Kubernetes ready
      MicroProfile
    Écosystème
      CDI
      JAX-RS
      Hibernate
      SmallRye
```

| Critère | Quarkus | Spring Boot |
|---------|---------|-------------|
| Temps de démarrage | ~0.5s | ~3-5s |
| Mémoire (RSS) | ~50MB | ~200MB |
| Native compilation | ✅ GraalVM | ⚠️ Expérimental |
| Dev experience | ✅ Hot reload | ✅ DevTools |

### Pourquoi MinIO ?

| Critère | MinIO | AWS S3 |
|---------|-------|--------|
| Coût | Gratuit (self-hosted) | Pay per use |
| API | 100% compatible S3 | Native |
| Déploiement | Docker simple | Cloud only |
| Latence | Locale (rapide) | Réseau |
| Dev/Test | ✅ Idéal | Coûteux |

**Stratégie :** Développer avec MinIO, migrer vers AWS S3 en production sans changer le code.

### Pourquoi Keycloak ?

```mermaid
flowchart LR
    subgraph "Keycloak Features"
        A[OAuth2 / OIDC]
        B[SSO]
        C[Identity Brokering]
        D[User Federation]
        E[Admin Console]
    end
    
    subgraph "Alternatives"
        F[Auth0 - Payant]
        G[Okta - Payant]
        H[Custom - Complexe]
    end
    
    style A fill:#f0a30a,stroke:#333
    style B fill:#f0a30a,stroke:#333
```

- ✅ Open source et gratuit
- ✅ Standards OAuth2/OIDC
- ✅ Console d'administration
- ✅ Intégration Quarkus native

### Pourquoi Consul ?

```mermaid
flowchart TB
    subgraph "Consul"
        A[Service Discovery]
        B[Health Checking]
        C[KV Store]
        D[Multi-datacenter]
    end
    
    M[Manager] -->|Register| A
    S[Store] -->|Register| A
    M -->|Lookup store| A
    
    style A fill:#ca2171,stroke:#333,color:#fff
```

- ✅ Service discovery dynamique
- ✅ Health checks automatiques
- ✅ Configuration centralisée
- ✅ Scalabilité multi-DC

### Pourquoi Traefik ?

| Feature | Traefik | Nginx |
|---------|---------|-------|
| Config dynamique | ✅ Auto (Docker labels) | ❌ Reload manuel |
| Let's Encrypt | ✅ Automatique | ⚠️ Certbot |
| Dashboard | ✅ Intégré | ❌ Externe |
| Load balancing | ✅ Natif | ✅ Natif |

---

## 📊 Schémas détaillés

### Cycle de vie d'un store

```mermaid
stateDiagram-v2
    [*] --> Created: Utilisateur créé
    Created --> Provisioning: Manager crée le store
    Provisioning --> DBCreated: PostgreSQL database
    DBCreated --> ContainerStarted: Docker container
    ContainerStarted --> Registered: Consul registration
    Registered --> Ready: Store accessible
    Ready --> [*]
    
    Ready --> Stopping: Arrêt demandé
    Stopping --> Stopped: Container arrêté
    Stopped --> Ready: Redémarrage
```

### Flux de données complet

```mermaid
flowchart TB
    subgraph "Client"
        U[👤 Browser]
    end
    
    subgraph "Edge"
        T[🔀 Traefik]
    end
    
    subgraph "Auth"
        K[🔐 Keycloak]
    end
    
    subgraph "Manager Service"
        M1[REST API]
        M2[ProfileService]
        M3[CoreService]
    end
    
    subgraph "Store Service"
        S1[REST API]
        S2[FileService]
        S3[DataStore]
        S4[StorageService]
    end
    
    subgraph "Discovery"
        C[🗂️ Consul]
    end
    
    subgraph "Storage"
        MI[☁️ MinIO]
    end
    
    subgraph "Database"
        P[(PostgreSQL)]
    end
    
    U -->|1. Request| T
    T -->|2. Route| M1
    M1 -->|3. Auth| K
    M1 --> M2
    M2 --> M3
    M3 -->|4. Lookup| C
    C -->|5. Store URL| M3
    M3 -->|6. Redirect| U
    
    U -->|7. Store request| T
    T -->|8. Route| S1
    S1 -->|9. Auth| K
    S1 --> S2
    S2 --> S3
    S3 --> S4
    S4 -->|10. Store file| MI
    S2 -->|11. Save metadata| P
```

---

## 📝 Résumé

### Points clés de l'architecture

1. **Microservices** : Manager et Store sont des services indépendants
2. **Service Discovery** : Consul permet la découverte dynamique des stores
3. **Authentification centralisée** : Keycloak gère tous les accès
4. **Stockage flexible** : Support LOCAL, S3 (MinIO), WebDAV
5. **Routage dynamique** : Traefik route automatiquement vers les services

### Ce qui a été implémenté (Issue #20)

- [x] Interface `StorageBackend` abstraite
- [x] Implémentation `LocalStorageBackend`
- [x] Implémentation `S3StorageBackend` (MinIO compatible)
- [x] Implémentation `WebDAVStorageBackend`
- [x] Service `StorageService` avec sélection automatique
- [x] Intégration avec `DataStoreBean` existant
- [x] Configuration via `application.properties`
- [x] Service MinIO dans docker-compose

### URLs du projet

| Service | URL |
|---------|-----|
| Manager | http://www.mbyte.fr |
| Store (abderrazak) | http://abderrazak.store.mbyte.fr |
| Keycloak | http://auth.mbyte.fr |
| Consul | http://registry.mbyte.fr |
| MinIO Console | http://localhost:9001 |
| Traefik Dashboard | http://localhost:8080 |

---

*Documentation générée le 14 janvier 2026*
