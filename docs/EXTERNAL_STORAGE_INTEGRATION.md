# 📦 Issue #20 : Intégration de Stockage Externe (S3/MinIO)

## Résumé de l'implémentation

Cette documentation décrit l'implémentation de l'Issue #20 qui permet le stockage des fichiers sur des backends externes (S3/MinIO, WebDAV) au lieu du système de fichiers local.

---

## Table des matières

1. [Objectif](#objectif)
2. [Architecture Avant/Après](#architecture-avantaprès)
3. [Composants Implémentés](#composants-implémentés)
4. [Configuration](#configuration)
5. [Intégration avec MinIO](#intégration-avec-minio)
6. [Guide de Test](#guide-de-test)
7. [Migration vers AWS S3](#migration-vers-aws-s3)

---

## Objectif

Permettre le stockage des fichiers utilisateurs sur des backends de stockage externes, offrant :

- **Scalabilité** : Stockage illimité avec S3/MinIO
- **Redondance** : Réplication native des données
- **Flexibilité** : Choix du backend par configuration
- **Portabilité** : Code compatible MinIO (dev) et AWS S3 (prod)

---

## Architecture Avant/Après

### ❌ Avant : Stockage Local

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│  FileService    │────▶│  DataStoreBean  │────▶│  Local Disk     │
│                 │     │                 │     │  /data/files    │
└─────────────────┘     └─────────────────┘     └─────────────────┘
```

**Problèmes :**
- Espace disque limité
- Pas de redondance
- Impossible de distribuer
- Couplage fort au système de fichiers

### ✅ Après : Multi-Backend

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│  FileService    │────▶│  DataStoreBean  │────▶│ StorageService  │
└─────────────────┘     └─────────────────┘     └────────┬────────┘
                                                         │
                        ┌────────────────────────────────┼────────────────────────────────┐
                        │                                │                                │
                        ▼                                ▼                                ▼
               ┌─────────────────┐              ┌─────────────────┐              ┌─────────────────┐
               │ LocalStorage    │              │ S3Storage       │              │ WebDAVStorage   │
               │ Backend         │              │ Backend         │              │ Backend         │
               └────────┬────────┘              └────────┬────────┘              └────────┬────────┘
                        │                                │                                │
                        ▼                                ▼                                ▼
               ┌─────────────────┐              ┌─────────────────┐              ┌─────────────────┐
               │   Local Disk    │              │   MinIO / S3    │              │   WebDAV        │
               └─────────────────┘              └─────────────────┘              └─────────────────┘
```

---

## Composants Implémentés

### 1. Interface StorageBackend

**Fichier :** `store/src/main/java/fr/jayblanc/mbyte/store/data/backend/StorageBackend.java`

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

### 2. S3StorageBackend

**Fichier :** `store/src/main/java/fr/jayblanc/mbyte/store/data/backend/s3/S3StorageBackend.java`

Implémentation utilisant AWS SDK v2 compatible avec MinIO et AWS S3.

**Fonctionnalités :**
- Connexion via endpoint configurable
- Support path-style access (requis pour MinIO)
- Création automatique du bucket
- Gestion des timeouts

### 3. WebDAVStorageBackend

**Fichier :** `store/src/main/java/fr/jayblanc/mbyte/store/data/backend/webdav/WebDAVStorageBackend.java`

Implémentation utilisant la librairie Sardine pour WebDAV.

### 4. LocalStorageBackend

**Fichier :** `store/src/main/java/fr/jayblanc/mbyte/store/data/backend/local/LocalStorageBackend.java`

Implémentation utilisant java.nio.file pour le stockage local.

### 5. StorageService

**Fichier :** `store/src/main/java/fr/jayblanc/mbyte/store/data/backend/StorageService.java`

Service CDI qui :
- Sélectionne le backend actif selon la configuration
- Fallback vers LOCAL si le backend externe est indisponible
- Construit les clés de stockage : `{storeId}/{fileHash}`

### 6. DataStoreBean (modifié)

**Fichier :** `store/src/main/java/fr/jayblanc/mbyte/store/data/DataStoreBean.java`

Modifié pour :
- Injecter `StorageService`
- Déléguer le stockage au backend actif
- Maintenir la compatibilité avec le code existant

---

## Configuration

### application.properties

```properties
# ========================================
# External Storage Backend Configuration
# ========================================

# Backend type: LOCAL, S3, WEBDAV
mbyte.store.backend.type=S3

# Enable encryption for stored data (future feature)
mbyte.store.backend.encrypt=false

# Fallback to local storage if external backend is unavailable
mbyte.store.backend.fallback-to-local=true

# --- S3/MinIO Configuration ---
mbyte.store.backend.s3.enabled=true
mbyte.store.backend.s3.endpoint=http://minio:9000
mbyte.store.backend.s3.access-key=minioadmin
mbyte.store.backend.s3.secret-key=minioadmin
mbyte.store.backend.s3.bucket=mbyte-stores
mbyte.store.backend.s3.region=us-east-1
mbyte.store.backend.s3.path-style-access=true
mbyte.store.backend.s3.auto-create-bucket=true

# --- WebDAV Configuration ---
mbyte.store.backend.webdav.enabled=false
#mbyte.store.backend.webdav.url=https://webdav.example.com/mbyte
#mbyte.store.backend.webdav.username=user
#mbyte.store.backend.webdav.password=secret
```

### Variables d'environnement Docker

```bash
# Pour le conteneur Store
-e MBYTE_STORE_BACKEND_TYPE=S3
-e MBYTE_STORE_BACKEND_S3_ENABLED=true
-e MBYTE_STORE_BACKEND_S3_ENDPOINT=http://mbyte.minio:9000
-e MBYTE_STORE_BACKEND_S3_BUCKET=mbyte-stores
-e MBYTE_STORE_BACKEND_S3_ACCESS_KEY=minioadmin
-e MBYTE_STORE_BACKEND_S3_SECRET_KEY=minioadmin
-e MBYTE_STORE_BACKEND_S3_REGION=us-east-1
-e MBYTE_STORE_BACKEND_S3_PATH_STYLE_ACCESS=true
```

---

## Intégration avec MinIO

### Service MinIO dans docker-compose.yml

```yaml
minio:
  image: minio/minio:latest
  hostname: minio
  container_name: mbyte.minio
  command: server /data --console-address ":9001"
  environment:
    MINIO_ROOT_USER: minioadmin
    MINIO_ROOT_PASSWORD: minioadmin
  ports:
    - "9000:9000"
    - "9001:9001"
  volumes:
    - mbyte.minio.volume:/data
  labels:
    - "traefik.enable=true"
    - "traefik.http.routers.minio.rule=Host(`minio.mbyte.fr`)"
    - "traefik.http.routers.minio.entrypoints=http"
    - "traefik.http.routers.minio.service=minio-api"
    - "traefik.http.services.minio-api.loadbalancer.server.port=9000"
    - "traefik.http.routers.minio-console.rule=Host(`minio-console.mbyte.fr`)"
    - "traefik.http.routers.minio-console.entrypoints=http"
    - "traefik.http.routers.minio-console.service=minio-console"
    - "traefik.http.services.minio-console.loadbalancer.server.port=9001"
  networks:
    mbyte:
      ipv4_address: 172.25.0.10
```

### Accès à la console MinIO

- **URL Console :** http://localhost:9001
- **Username :** minioadmin
- **Password :** minioadmin
- **Bucket :** mbyte-stores

---

## Guide de Test

### 1. Vérifier que MinIO est démarré

```bash
docker ps | grep minio
```

### 2. Vérifier les logs du Store

```bash
docker logs mbyte.6f73a9a0-9889-4538-bc46-834e5422dede.store 2>&1 | grep -i "storage\|s3\|backend"
```

Vous devriez voir :
```
S3 storage backend initialized - endpoint: http://mbyte.minio:9000, bucket: mbyte-stores
Storage service initialized with backend: S3 (available: true)
Storage backend: S3
```

### 3. Uploader un fichier via l'interface

1. Accédez à http://www.mbyte.fr
2. Connectez-vous
3. Cliquez sur "Go to Store"
4. Uploadez un fichier

### 4. Vérifier dans MinIO

1. Ouvrez http://localhost:9001
2. Connectez-vous (minioadmin/minioadmin)
3. Naviguez vers le bucket `mbyte-stores`
4. Vous devriez voir le fichier stocké avec son hash SHA-256

---

## Migration vers AWS S3

Pour migrer de MinIO vers AWS S3 en production :

### 1. Modifier la configuration

```properties
mbyte.store.backend.s3.endpoint=
# Laisser vide pour utiliser l'endpoint AWS par défaut

mbyte.store.backend.s3.access-key=AKIAIOSFODNN7EXAMPLE
mbyte.store.backend.s3.secret-key=wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY
mbyte.store.backend.s3.bucket=my-production-bucket
mbyte.store.backend.s3.region=eu-west-1
mbyte.store.backend.s3.path-style-access=false
# AWS utilise virtual-hosted style
```

### 2. Variables d'environnement AWS

```bash
-e MBYTE_STORE_BACKEND_S3_ENDPOINT=
-e MBYTE_STORE_BACKEND_S3_ACCESS_KEY=AKIAIOSFODNN7EXAMPLE
-e MBYTE_STORE_BACKEND_S3_SECRET_KEY=wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY
-e MBYTE_STORE_BACKEND_S3_BUCKET=my-production-bucket
-e MBYTE_STORE_BACKEND_S3_REGION=eu-west-1
-e MBYTE_STORE_BACKEND_S3_PATH_STYLE_ACCESS=false
```

### Compatibilité MinIO ↔ AWS S3

| Feature | MinIO | AWS S3 |
|---------|-------|--------|
| API | S3 Compatible | Native |
| Endpoint | Custom URL | Default AWS |
| Path Style | Required (`true`) | Optional (`false`) |
| Region | Any (us-east-1) | Real region |
| Bucket | Auto-create | Pre-create |

---

## Dépendances Maven

```xml
<!-- AWS SDK S3 -->
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>s3</artifactId>
    <version>2.25.16</version>
</dependency>

<!-- WebDAV Client (Sardine) -->
<dependency>
    <groupId>com.github.lookfirst</groupId>
    <artifactId>sardine</artifactId>
    <version>5.12</version>
</dependency>
```

---

## Structure des fichiers implémentés

```
store/src/main/java/fr/jayblanc/mbyte/store/data/
├── DataStore.java                    # Interface (existante)
├── DataStoreBean.java                # Modifié pour utiliser StorageService
├── DataStoreConfig.java              # Config (existante)
└── backend/
    ├── StorageBackend.java           # Interface abstraite
    ├── StorageBackendException.java  # Exception
    ├── StorageService.java           # Service de sélection backend
    ├── local/
    │   ├── LocalStorageBackend.java  # Implémentation locale
    │   └── LocalStorageConfig.java   # Config
    ├── s3/
    │   ├── S3StorageBackend.java     # Implémentation S3/MinIO
    │   └── S3StorageConfig.java      # Config
    └── webdav/
        ├── WebDAVStorageBackend.java # Implémentation WebDAV
        └── WebDAVStorageConfig.java  # Config
```

---

## Conclusion

L'implémentation de l'Issue #20 permet désormais de :

1. ✅ Stocker les fichiers sur MinIO (développement)
2. ✅ Migrer vers AWS S3 (production) sans modification de code
3. ✅ Fallback automatique vers le stockage local
4. ✅ Configuration flexible via properties ou variables d'environnement

Cette architecture ouvre la voie à des évolutions futures :
- Chiffrement AES-256-GCM côté client
- Support de nouveaux backends (Google Cloud Storage, Azure Blob)
- Réplication multi-backend

---

*Documentation mise à jour le 14 janvier 2026*
