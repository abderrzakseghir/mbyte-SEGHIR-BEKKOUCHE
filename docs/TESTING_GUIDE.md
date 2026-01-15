#  Guide de Test - Issue #20 : Intégration Stockage Externe

Ce guide explique comment tester l'implémentation de l'Issue #20 qui permet le stockage des fichiers sur des backends externes (S3/MinIO, WebDAV) avec chiffrement optionnel.

---

##  Table des matières

1. [Prérequis](#prérequis)
2. [Démarrage rapide](#démarrage-rapide)
3. [Configuration](#configuration)
4. [Tests fonctionnels](#tests-fonctionnels)
5. [Vérification du chiffrement](#vérification-du-chiffrement)
6. [Troubleshooting](#troubleshooting)

---

##  Prérequis

- Docker et Docker Compose installés
- Navigateur web moderne
- Accès au terminal

---

##  Démarrage rapide

### 1. Démarrer l'infrastructure

```bash
cd /home/etudiant/Bureau/TP/mbyte-SEGHIR-BEKKOUCHE

# Démarrer tous les services
docker-compose up -d
```

### 2. Vérifier que tous les services sont opérationnels

```bash
docker ps
```

Services attendus :
| Service | Container | Port |
|---------|-----------|------|
| Traefik | mbyte.proxy | 80, 443, 8080 |
| PostgreSQL | mbyte.db | 5432 |
| Keycloak | mbyte.auth | 80 |
| Consul | mbyte.registry | 8500 |
| Manager | mbyte.manager | 8080 |
| MinIO | mbyte.minio | 9000, 9001 |

### 3. Accéder à l'application

- **Manager** : http://www.mbyte.fr
- **Keycloak Admin** : http://auth.mbyte.fr/admin (admin/admin)
- **MinIO Console** : http://localhost:9001 (minioadmin/minioadmin)
- **Consul UI** : http://registry.mbyte.fr
- **Traefik Dashboard** : http://localhost:8080

---

##  Configuration

### Configuration du backend de stockage

Fichier : `store/src/main/resources/application.properties`

#### Mode S3/MinIO (recommandé pour test)

```properties
# Type de backend : LOCAL, S3, WEBDAV
mbyte.store.backend.type=S3

# Configuration S3/MinIO
mbyte.store.backend.s3.enabled=true
mbyte.store.backend.s3.endpoint=http://minio:9000
mbyte.store.backend.s3.access-key=minioadmin
mbyte.store.backend.s3.secret-key=minioadmin
mbyte.store.backend.s3.bucket=mbyte-stores
mbyte.store.backend.s3.region=us-east-1
mbyte.store.backend.s3.path-style-access=true
mbyte.store.backend.s3.auto-create-bucket=true
```

#### Mode Local (fallback)

```properties
mbyte.store.backend.type=LOCAL
```

#### Mode WebDAV

```properties
mbyte.store.backend.type=WEBDAV
mbyte.store.backend.webdav.enabled=true
mbyte.store.backend.webdav.url=https://webdav.example.com/mbyte
mbyte.store.backend.webdav.username=user
mbyte.store.backend.webdav.password=secret
```

### Configuration du chiffrement

```properties
# Activer le chiffrement AES-256-GCM
mbyte.store.cipher.enabled=true
mbyte.store.cipher.algorithm=AES/GCM/NoPadding
mbyte.store.cipher.key-size=256

# Clé secrète (Base64) - IMPORTANT pour la production !
# Générer avec : openssl rand -base64 32
mbyte.store.cipher.secret-key=YOUR_BASE64_ENCODED_32_BYTE_KEY_HERE
```

---

## Tests fonctionnels

### Test 1 : Vérifier le backend S3/MinIO

1. **Créer un store** via le Manager :
   - Aller sur http://www.mbyte.fr
   - Se connecter (créer un compte si nécessaire)
   - Votre store sera automatiquement créé

2. **Vérifier les logs du store** :
   ```bash
   docker logs mbyte.6f73a9a0-9889-4538-bc46-834e5422dede.store 2>&1 | grep -i "storage\|s3\|backend"
   ```
   
   Résultat attendu :
   ```
   S3 storage backend initialized - endpoint: http://mbyte.minio:9000, bucket: mbyte-stores
   Storage service initialized with backend: S3 (available: true)
   Storage backend: S3
   ```

### Test 2 : Upload de fichier

1. **Accéder à votre store** :
   - http://abderrazak.store.mbyte.fr (remplacer par votre username)

2. **Uploader un fichier** :
   - Cliquer sur le bouton d'upload
   - Sélectionner un fichier (image, PDF, etc.)
   - Soumettre

3. **Vérifier dans MinIO** :
   - Ouvrir http://localhost:9001
   - Se connecter (minioadmin/minioadmin)
   - Naviguer vers le bucket `mbyte-stores`
   - Le fichier doit apparaître avec son hash SHA-256 comme nom

### Test 3 : Téléchargement de fichier

1. **Cliquer sur un fichier** dans votre store
2. **Télécharger** le fichier
3. **Vérifier** que le contenu est identique à l'original

### Test 4 : Vérifier la déduplication

1. **Uploader le même fichier** une seconde fois
2. **Vérifier dans MinIO** qu'il n'y a pas de nouveau fichier (même hash)

---

##  Vérification du chiffrement

### Activer le chiffrement

1. **Arrêter le store** :
   ```bash
   docker stop mbyte.6f73a9a0-9889-4538-bc46-834e5422dede.store
   docker rm mbyte.6f73a9a0-9889-4538-bc46-834e5422dede.store
   ```

2. **Générer une clé de chiffrement** :
   ```bash
   openssl rand -base64 32
   ```
   
   Exemple de sortie : `K7gNU3sdo+OL0wNhqoVWhr3g6s1xYv72ol/pe/Unols=`

3. **Relancer le store avec chiffrement** :
   ```bash
   docker run -d \
     --name mbyte.6f73a9a0-9889-4538-bc46-834e5422dede.store \
     --network mbyte.net \
     -e QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://mbyte.db:5432/store_6f73a9a0_9889_4538_bc46_834e5422dede \
     -e QUARKUS_DATASOURCE_PASSWORD=password \
     -e QUARKUS_OIDC_AUTH_SERVER_URL=http://mbyte.auth:80/realms/mbyte \
     -e STORE_AUTH_OWNER=abderrazak \
     -e STORE_TOPOLOGY_HOST=mbyte.registry \
     -e MBYTE_STORE_BACKEND_TYPE=S3 \
     -e MBYTE_STORE_BACKEND_S3_ENABLED=true \
     -e MBYTE_STORE_BACKEND_S3_ENDPOINT=http://mbyte.minio:9000 \
     -e MBYTE_STORE_BACKEND_S3_BUCKET=mbyte-stores \
     -e MBYTE_STORE_BACKEND_S3_ACCESS_KEY=minioadmin \
     -e MBYTE_STORE_BACKEND_S3_SECRET_KEY=minioadmin \
     -e MBYTE_STORE_CIPHER_ENABLED=true \
     -e MBYTE_STORE_CIPHER_SECRET_KEY=K7gNU3sdo+OL0wNhqoVWhr3g6s1xYv72ol/pe/Unols= \
     -l "traefik.enable=true" \
     -l "traefik.http.routers.store-abderrazak.rule=Host(\`abderrazak.store.mbyte.fr\`)" \
     -l "traefik.http.routers.store-abderrazak.entrypoints=http" \
     -l "traefik.http.services.store-abderrazak.loadbalancer.server.port=8089" \
     etudiant/store:25.1-SNAPSHOT
   ```

4. **Vérifier les logs** :
   ```bash
   docker logs mbyte.6f73a9a0-9889-4538-bc46-834e5422dede.store 2>&1 | grep -i "cipher\|encrypt"
   ```
   
   Résultat attendu :
   ```
   Cipher service initialized with algorithm: AES/GCM/NoPadding, key size: 256 bits
   Encryption enabled: AES-256-GCM
   ```

### Test du chiffrement

1. **Uploader un fichier texte** simple (ex: test.txt contenant "Hello World")

2. **Vérifier dans MinIO** :
   - Télécharger le fichier directement depuis MinIO Console
   - Ouvrir avec un éditeur de texte
   - **Le contenu doit être illisible** (données chiffrées)

3. **Télécharger via l'application** :
   - Le fichier doit être lisible (déchiffrement automatique)

---

##  Troubleshooting

### Erreur 502 Bad Gateway

1. Vérifier que le store est démarré :
   ```bash
   docker ps | grep store
   ```

2. Vérifier les logs :
   ```bash
   docker logs mbyte.6f73a9a0-9889-4538-bc46-834e5422dede.store --tail 50
   ```

3. Vérifier le port dans Traefik (doit être 8089, pas 8080)

### MinIO non accessible

1. Vérifier que MinIO est démarré :
   ```bash
   docker ps | grep minio
   ```

2. Créer le bucket manuellement si nécessaire :
   - Ouvrir http://localhost:9001
   - Se connecter
   - Créer le bucket `mbyte-stores`

### Erreur de chiffrement

1. Vérifier que la clé est en Base64 valide
2. S'assurer que la clé fait 32 octets (256 bits)
3. Vérifier les logs pour les erreurs de chiffrement

### Fichiers non trouvés après activation du chiffrement

 **Important** : Les fichiers stockés avant l'activation du chiffrement ne peuvent pas être lus après activation (et vice-versa). Il faut soit :
- Désactiver le chiffrement pour lire les anciens fichiers
- Re-uploader les fichiers après activation

---

##  Résumé des fonctionnalités implémentées

| Fonctionnalité | Statut | Description |
|----------------|--------|-------------|
| S3 Storage Backend | ✅ | Stockage compatible AWS S3 et MinIO |
| WebDAV Storage Backend | ✅ | Stockage sur serveur WebDAV |
| Local Storage Backend | ✅ | Stockage sur système de fichiers local |
| Sélection automatique du backend | ✅ | Via configuration |
| Fallback vers local | ✅ | Si backend externe indisponible |
| Chiffrement AES-256-GCM | ✅ | Encryption avant stockage externe |
| Déduplication | ✅ | Basée sur hash SHA-256 |
| Intégration Docker | ✅ | MinIO dans docker-compose |

---



