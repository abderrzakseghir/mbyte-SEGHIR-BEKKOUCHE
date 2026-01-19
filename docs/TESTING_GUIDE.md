#  Guide de Test - Issue #20 : Intégration Stockage Externe

Ce guide explique comment tester l'implémentation de l'Issue #20 qui permet le stockage des fichiers sur des backends externes (S3/MinIO, WebDAV, Google Drive, Dropbox, OneDrive) avec chiffrement optionnel, redondance multi-backend et load balancing.

---

##  Table des matières

1. [Prérequis](#prérequis)
2. [Démarrage rapide](#démarrage-rapide)
3. [Configuration des backends](#configuration-des-backends)
4. [Tests fonctionnels](#tests-fonctionnels)
5. [Vérification du chiffrement](#vérification-du-chiffrement)
6. [Mode Multi-Backend (Redondance)](#mode-multi-backend-redondance)
7. [Load Balancing](#load-balancing)
8. [Placement automatique des fichiers](#placement-automatique-des-fichiers)
9. [Troubleshooting](#troubleshooting)

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

##  Configuration des backends

### Configuration du backend de stockage

Fichier : `store/src/main/resources/application.properties`

#### Mode S3/MinIO (recommandé pour test)

```properties
# Type de backend : LOCAL, S3, WEBDAV, GOOGLE_DRIVE, DROPBOX, ONEDRIVE, MULTI
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

#### Mode Google Drive

```properties
mbyte.store.backend.type=GOOGLE_DRIVE
mbyte.store.backend.googledrive.enabled=true
mbyte.store.backend.googledrive.client-id=YOUR_GOOGLE_CLIENT_ID
mbyte.store.backend.googledrive.client-secret=YOUR_GOOGLE_CLIENT_SECRET
mbyte.store.backend.googledrive.refresh-token=YOUR_REFRESH_TOKEN
mbyte.store.backend.googledrive.application-name=MByte-Store
# Optionnel: ID du dossier racine (sinon utilise "My Drive")
mbyte.store.backend.googledrive.root-folder-id=root
```

Pour obtenir les credentials Google:
1. Créer un projet sur [Google Cloud Console](https://console.cloud.google.com/)
2. Activer l'API Google Drive
3. Créer des identifiants OAuth 2.0
4. Utiliser l'OAuth Playground pour obtenir un refresh token

#### Mode Dropbox

```properties
mbyte.store.backend.type=DROPBOX
mbyte.store.backend.dropbox.enabled=true
mbyte.store.backend.dropbox.access-token=YOUR_DROPBOX_ACCESS_TOKEN
# OU utiliser refresh token pour accès long terme:
mbyte.store.backend.dropbox.app-key=YOUR_APP_KEY
mbyte.store.backend.dropbox.app-secret=YOUR_APP_SECRET
mbyte.store.backend.dropbox.refresh-token=YOUR_REFRESH_TOKEN
mbyte.store.backend.dropbox.root-path=/mbyte-store
```

Pour obtenir les credentials Dropbox:
1. Créer une app sur [Dropbox App Console](https://www.dropbox.com/developers/apps)
2. Générer un access token ou configurer OAuth

#### Mode OneDrive

```properties
mbyte.store.backend.type=ONEDRIVE
mbyte.store.backend.onedrive.enabled=true
mbyte.store.backend.onedrive.client-id=YOUR_AZURE_CLIENT_ID
mbyte.store.backend.onedrive.client-secret=YOUR_AZURE_CLIENT_SECRET
mbyte.store.backend.onedrive.tenant-id=YOUR_TENANT_ID
mbyte.store.backend.onedrive.root-path=/mbyte-store
```

Pour obtenir les credentials OneDrive:
1. Enregistrer une application sur [Azure Portal](https://portal.azure.com/)
2. Configurer les permissions Microsoft Graph (Files.ReadWrite.All)
3. Créer un secret client

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
| Google Drive Backend | ✅ | Stockage sur Google Drive via API |
| Dropbox Backend | ✅ | Stockage sur Dropbox via API |
| OneDrive Backend | ✅ | Stockage sur Microsoft OneDrive via Graph API |
| Multi-Backend (Redondance) | ✅ | Stockage sur plusieurs backends simultanément |
| Load Balancing | ✅ | Distribution des lectures entre backends |
| Placement automatique | ✅ | Routage intelligent selon taille/type de fichier |
| Sélection automatique du backend | ✅ | Via configuration |
| Fallback vers local | ✅ | Si backend externe indisponible |
| Chiffrement AES-256-GCM | ✅ | Encryption avant stockage externe |
| Déduplication | ✅ | Basée sur hash SHA-256 |
| Intégration Docker | ✅ | MinIO dans docker-compose |

---

##  Mode Multi-Backend (Redondance)

Le mode multi-backend permet de stocker les fichiers sur plusieurs backends simultanément pour assurer la redondance.

### Configuration

```properties
# Activer le mode multi-backend
mbyte.store.backend.type=MULTI
mbyte.store.backend.multi.enabled=true

# Backends à utiliser (dans l'ordre de priorité)
mbyte.store.backend.multi.backends=S3,GOOGLE_DRIVE,LOCAL

# Niveau de redondance (nombre de copies)
mbyte.store.backend.multi.redundancy-level=2

# Minimum de writes requis pour considérer l'opération réussie
mbyte.store.backend.multi.minimum-writes=1

# Échouer si le niveau de redondance n'est pas atteint
mbyte.store.backend.multi.fail-on-partial-write=false
```

### Comportement

1. **Écriture** : Le fichier est écrit sur N backends (selon `redundancy-level`)
2. **Lecture** : Le fichier est lu depuis un backend (selon la stratégie de load balancing)
3. **Suppression** : Le fichier est supprimé de tous les backends où il existe

### Test de la redondance

```bash
# Vérifier les logs pour voir la réplication
docker logs mbyte.store 2>&1 | grep -i "redundancy"

# Résultat attendu:
# Stored file with 2-way redundancy: abc123 -> [S3, LOCAL]
```

---

##  Load Balancing

Le load balancing permet de distribuer les lectures entre les backends disponibles.

### Stratégies disponibles

| Stratégie | Description |
|-----------|-------------|
| `ROUND_ROBIN` | Distribution circulaire entre backends |
| `RANDOM` | Sélection aléatoire |
| `FASTEST` | Utilise le backend avec la meilleure latence |
| `PRIMARY_FIRST` | Essaie toujours le premier backend, fallback sur les autres |

### Configuration

```properties
mbyte.store.backend.multi.load-balancing-strategy=ROUND_ROBIN

# Intervalle de vérification de santé (secondes)
mbyte.store.backend.multi.health-check-interval=60
```

### Test du load balancing

```bash
# Faire plusieurs requêtes de lecture
for i in {1..5}; do
  curl -s http://store.mbyte.fr/api/files/test.txt > /dev/null
done

# Vérifier les logs pour voir la distribution
docker logs mbyte.store 2>&1 | grep -i "Read from"

# Résultat avec ROUND_ROBIN:
# Read from S3 in 45ms: test.txt
# Read from LOCAL in 12ms: test.txt
# Read from S3 in 38ms: test.txt
# ...
```

---

##  Placement automatique des fichiers

Le placement automatique permet de router les fichiers vers différents backends selon leurs caractéristiques.

### Configuration

```properties
# Activer le placement automatique
mbyte.store.backend.multi.auto-placement-enabled=true

# Seuil pour les gros fichiers (10 MB)
mbyte.store.backend.multi.large-file-threshold=10485760

# Backend préféré pour les gros fichiers
mbyte.store.backend.multi.large-file-backend=S3

# Backend pour les fichiers fréquemment accédés (hot storage)
mbyte.store.backend.multi.hot-storage-backend=LOCAL

# Backend pour les fichiers rarement accédés (cold storage)
mbyte.store.backend.multi.cold-storage-backend=GOOGLE_DRIVE
```

### Règles de placement

1. **Gros fichiers** (> threshold) → `large-file-backend` (ex: S3)
2. **Fichiers fréquents** → `hot-storage-backend` (ex: LOCAL pour accès rapide)
3. **Archives/backups** → `cold-storage-backend` (ex: Google Drive pour coût réduit)

---

##  Interface utilisateur de paramétrage

MByte offre une interface web permettant aux utilisateurs de configurer facilement leurs backends de stockage externes sans avoir besoin de modifier les fichiers de configuration.

### Accéder aux paramètres

1. **Se connecter** à votre store (ex: http://abderrazak.store.mbyte.fr)
2. **Cliquer sur "Paramètres"** dans le menu latéral gauche
3. La page des paramètres de stockage s'affiche

### Fonctionnalités de l'interface

#### Paramètres généraux

| Option | Description | Valeur par défaut |
|--------|-------------|-------------------|
| Multi-Backend | Active la redondance sur plusieurs backends | ✅ Activé |
| Chiffrement AES-256 | Chiffre les fichiers avant stockage | ✅ Activé |
| Niveau de redondance | Nombre de copies (1-3) | 2 |
| Stratégie de load balancing | ROUND_ROBIN, RANDOM, FASTEST, PRIMARY_FIRST | ROUND_ROBIN |

#### Configuration des backends

Chaque backend dispose d'un formulaire dédié avec :
- **Toggle d'activation** : Activer/désactiver le backend
- **Champs de credentials** : Clés d'API, tokens, etc.
- **Bouton de sauvegarde** : Enregistre la configuration

##### S3/MinIO
- Endpoint (URL du serveur)
- Access Key / Secret Key
- Bucket
- Region

##### Google Drive
- Client ID / Client Secret
- Refresh Token
- Folder ID (optionnel)

##### Dropbox
- Access Token
- App Key / App Secret (optionnel)
- Root Path

##### OneDrive
- Client ID (Azure)
- Client Secret
- Tenant ID
- Root Path

##### WebDAV
- URL du serveur
- Nom d'utilisateur / Mot de passe

### API REST pour les paramètres

L'interface utilise une API REST pour gérer les paramètres :

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| GET | `/api/settings` | Récupère les paramètres (HTML ou JSON) |
| POST | `/api/settings/general` | Met à jour les paramètres généraux |
| POST | `/api/settings/backend/s3` | Configure S3/MinIO |
| POST | `/api/settings/backend/googledrive` | Configure Google Drive |
| POST | `/api/settings/backend/dropbox` | Configure Dropbox |
| POST | `/api/settings/backend/onedrive` | Configure OneDrive |
| POST | `/api/settings/backend/webdav` | Configure WebDAV |
| POST | `/api/settings/toggle/{backend}` | Toggle on/off (AJAX) |

### Test de l'interface

1. **Aller sur la page des paramètres** :
   ```
   http://abderrazak.store.mbyte.fr/api/settings
   ```

2. **Activer un backend** (ex: Google Drive) :
   - Activer le toggle
   - Remplir les credentials
   - Cliquer sur "Sauvegarder"

3. **Vérifier la configuration** :
   - Le résumé en bas de page affiche le nombre de backends actifs
   - Le badge passe de "Inactif" à "Actif"

4. **Tester via l'API** :
   ```bash
   curl -s http://abderrazak.store.mbyte.fr/api/settings \
     -H "Accept: application/json" \
     -H "Authorization: Bearer YOUR_TOKEN" | jq
   ```

### Sécurité des credentials

- Les mots de passe et secrets sont affichés en mode "password" (masqués)
- Les credentials sont stockés dans la base de données du store
- Chaque utilisateur a ses propres paramètres (isolation par owner)
- ⚠️ **Recommandation** : Utiliser des tokens avec permissions limitées

---
