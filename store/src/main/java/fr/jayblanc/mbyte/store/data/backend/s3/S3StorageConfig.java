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

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Optional;

/**
 * Configuration for S3-compatible storage backend.
 * 
 * This configuration supports both AWS S3 and MinIO.
 * 
 * Example configuration for MinIO:
 * <pre>
 * mbyte.store.backend.s3.enabled=true
 * mbyte.store.backend.s3.endpoint=http://minio:9000
 * mbyte.store.backend.s3.access-key=minioadmin
 * mbyte.store.backend.s3.secret-key=minioadmin
 * mbyte.store.backend.s3.bucket=mbyte-stores
 * mbyte.store.backend.s3.region=us-east-1
 * </pre>
 * 
 * Example configuration for AWS S3:
 * <pre>
 * mbyte.store.backend.s3.enabled=true
 * mbyte.store.backend.s3.endpoint=https://s3.amazonaws.com
 * mbyte.store.backend.s3.access-key=YOUR_AWS_ACCESS_KEY
 * mbyte.store.backend.s3.secret-key=YOUR_AWS_SECRET_KEY
 * mbyte.store.backend.s3.bucket=your-bucket-name
 * mbyte.store.backend.s3.region=eu-west-1
 * </pre>
 * 
 * @author MByte Team
 */
@ConfigMapping(prefix = "mbyte.store.backend.s3")
public interface S3StorageConfig {

    /**
     * Whether S3 storage backend is enabled.
     * Default: false
     */
    @WithDefault("false")
    boolean enabled();

    /**
     * S3 endpoint URL.
     * 
     * For MinIO: http://minio:9000
     * For AWS S3: https://s3.amazonaws.com or region-specific endpoint
     */
    Optional<String> endpoint();

    /**
     * AWS/MinIO access key ID.
     */
    Optional<String> accessKey();

    /**
     * AWS/MinIO secret access key.
     */
    Optional<String> secretKey();

    /**
     * S3 bucket name for storing files.
     * Default: mbyte-stores
     */
    @WithDefault("mbyte-stores")
    String bucket();

    /**
     * AWS region.
     * Default: us-east-1 (required for MinIO compatibility)
     */
    @WithDefault("us-east-1")
    String region();

    /**
     * Use path-style access (required for MinIO).
     * Default: true (for MinIO compatibility)
     * 
     * Set to false for AWS S3 virtual-hosted-style access.
     */
    @WithDefault("true")
    boolean pathStyleAccess();

    /**
     * Connection timeout in milliseconds.
     * Default: 10000 (10 seconds)
     */
    @WithDefault("10000")
    int connectionTimeout();

    /**
     * Read timeout in milliseconds.
     * Default: 30000 (30 seconds)
     */
    @WithDefault("30000")
    int readTimeout();

    /**
     * Maximum number of retry attempts for failed operations.
     * Default: 3
     */
    @WithDefault("3")
    int maxRetries();

    /**
     * Whether to auto-create the bucket if it doesn't exist.
     * Default: true
     */
    @WithDefault("true")
    boolean autoCreateBucket();
}
