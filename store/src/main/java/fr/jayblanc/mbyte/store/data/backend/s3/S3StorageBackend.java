/*
 * #%L
 * Musik Byte Store Service
 * %%
 * Copyright (C) 2024 Music Byte
 * %%
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
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */
package fr.jayblanc.mbyte.store.data.backend.s3;

import fr.jayblanc.mbyte.store.data.backend.StorageBackend;
import fr.jayblanc.mbyte.store.data.backend.StorageBackendException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;

/**
 * S3-compatible storage backend implementation.
 * Supports both AWS S3 and MinIO.
 * 
 * @author MByte Team
 */
@ApplicationScoped
public class S3StorageBackend implements StorageBackend {

    private static final Logger LOG = Logger.getLogger(S3StorageBackend.class);
    private static final String BACKEND_NAME = "S3";

    @Inject
    S3StorageConfig config;

    private S3Client s3Client;
    private boolean available = false;

    @PostConstruct
    void init() {
        if (!config.enabled()) {
            LOG.info("S3 storage backend is disabled");
            return;
        }

        try {
            initializeClient();
            ensureBucketExists();
            this.available = true;
            LOG.infof("S3 storage backend initialized - endpoint: %s, bucket: %s",
                    config.endpoint().orElse("default"), config.bucket());
        } catch (Exception e) {
            LOG.error("Failed to initialize S3 storage backend", e);
            this.available = false;
        }
    }

    private void initializeClient() {
        if (config.accessKey().isEmpty() || config.secretKey().isEmpty()) {
            throw new IllegalStateException("S3 access key and secret key are required");
        }

        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                config.accessKey().get(),
                config.secretKey().get()
        );

        S3Configuration s3Config = S3Configuration.builder()
                .pathStyleAccessEnabled(config.pathStyleAccess())
                .build();

        var clientBuilder = S3Client.builder()
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .region(Region.of(config.region()))
                .serviceConfiguration(s3Config)
                .overrideConfiguration(c -> c
                        .apiCallTimeout(Duration.ofMillis(config.readTimeout()))
                        .apiCallAttemptTimeout(Duration.ofMillis(config.connectionTimeout()))
                );

        if (config.endpoint().isPresent()) {
            clientBuilder.endpointOverride(URI.create(config.endpoint().get()));
        }

        this.s3Client = clientBuilder.build();
    }

    private void ensureBucketExists() {
        if (!config.autoCreateBucket()) {
            return;
        }

        try {
            HeadBucketRequest headBucketRequest = HeadBucketRequest.builder()
                    .bucket(config.bucket())
                    .build();
            s3Client.headBucket(headBucketRequest);
            LOG.debugf("Bucket '%s' already exists", config.bucket());
        } catch (NoSuchBucketException e) {
            LOG.infof("Creating bucket '%s'", config.bucket());
            CreateBucketRequest createBucketRequest = CreateBucketRequest.builder()
                    .bucket(config.bucket())
                    .build();
            s3Client.createBucket(createBucketRequest);
            LOG.infof("Bucket '%s' created successfully", config.bucket());
        }
    }

    @PreDestroy
    void cleanup() {
        if (s3Client != null) {
            s3Client.close();
        }
    }

    @Override
    public String getName() {
        return BACKEND_NAME;
    }

    @Override
    public boolean isAvailable() {
        return available && config.enabled();
    }

    @Override
    public boolean exists(String key) {
        if (!isAvailable()) return false;
        
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(config.bucket())
                    .key(key)
                    .build();
            s3Client.headObject(request);
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            LOG.warnf("Failed to check if object exists: %s", key);
            return false;
        }
    }

    @Override
    public void put(String key, InputStream data) throws StorageBackendException {
        checkAvailable();
        
        try {
            byte[] bytes = readAllBytes(data);
            
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(config.bucket())
                    .key(key)
                    .contentLength((long) bytes.length)
                    .build();
            
            s3Client.putObject(request, RequestBody.fromBytes(bytes));
            LOG.debugf("Stored object: %s (%d bytes)", key, bytes.length);
            
        } catch (IOException e) {
            throw new StorageBackendException("Failed to read input stream", e);
        } catch (S3Exception e) {
            throw new StorageBackendException("Failed to store object: " + key, e);
        }
    }

    @Override
    public InputStream get(String key) throws StorageBackendException {
        checkAvailable();
        
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(config.bucket())
                    .key(key)
                    .build();
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            s3Client.getObject(request, ResponseTransformer.toOutputStream(baos));
            
            return new ByteArrayInputStream(baos.toByteArray());
            
        } catch (NoSuchKeyException e) {
            throw new StorageBackendException("Object not found: " + key, e);
        } catch (S3Exception e) {
            throw new StorageBackendException("Failed to retrieve object: " + key, e);
        }
    }

    @Override
    public void delete(String key) throws StorageBackendException {
        checkAvailable();
        
        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(config.bucket())
                    .key(key)
                    .build();
            
            s3Client.deleteObject(request);
            LOG.debugf("Deleted object: %s", key);
            
        } catch (S3Exception e) {
            throw new StorageBackendException("Failed to delete object: " + key, e);
        }
    }

    @Override
    public long size(String key) throws StorageBackendException {
        checkAvailable();
        
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(config.bucket())
                    .key(key)
                    .build();
            
            HeadObjectResponse response = s3Client.headObject(request);
            return response.contentLength();
            
        } catch (NoSuchKeyException e) {
            throw new StorageBackendException("Object not found: " + key, e);
        } catch (S3Exception e) {
            throw new StorageBackendException("Failed to get object size: " + key, e);
        }
    }

    private void checkAvailable() throws StorageBackendException {
        if (!isAvailable()) {
            throw new StorageBackendException("S3 storage backend is not available");
        }
    }

    private byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = is.read(buffer)) != -1) {
            baos.write(buffer, 0, bytesRead);
        }
        return baos.toByteArray();
    }
}
