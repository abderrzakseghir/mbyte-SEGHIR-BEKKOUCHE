/*
 * Copyright (C) 2025 Jerome Blanchard <jayblanc@gmail.com>
 * Copyright (C) 2026 Abderrazak SEGHIR <abderrazakseghir1@gmail.com>
 *
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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package fr.jayblanc.mbyte.store.data.backend.multi;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.List;
import java.util.Optional;

/**
 * Configuration for multi-backend storage with redundancy and load balancing.
 * 
 * @author Abderrazak SEGHIR
 */
@ConfigMapping(prefix = "mbyte.store.backend.multi")
public interface MultiBackendConfig {

    /**
     * Whether multi-backend mode is enabled.
     */
    @WithDefault("false")
    boolean enabled();

    /**
     * List of backend types to use for redundancy (comma-separated).
     * Example: S3,GOOGLE_DRIVE,DROPBOX
     */
    @WithDefault("S3,LOCAL")
    List<String> backends();

    /**
     * Redundancy level: how many backends should store each file.
     * 1 = no redundancy, 2 = duplicate, etc.
     */
    @WithDefault("2")
    int redundancyLevel();

    /**
     * Load balancing strategy for read operations.
     * ROUND_ROBIN: Distribute reads evenly across available backends
     * RANDOM: Random backend selection
     * FASTEST: Use the backend that responds fastest (requires health checks)
     * PRIMARY_FIRST: Always try primary backend first, fallback to others
     */
    @WithDefault("ROUND_ROBIN")
    LoadBalancingStrategy loadBalancingStrategy();

    /**
     * Whether to enable automatic file placement based on file characteristics.
     */
    @WithDefault("false")
    boolean autoPlacementEnabled();

    /**
     * File size threshold in bytes for placement decisions.
     * Files larger than this may be placed on different backends.
     */
    @WithDefault("10485760")
    long largeFileThreshold();

    /**
     * Preferred backend for large files.
     */
    Optional<String> largeFileBackend();

    /**
     * Preferred backend for frequently accessed files.
     */
    Optional<String> hotStorageBackend();

    /**
     * Preferred backend for rarely accessed files.
     */
    Optional<String> coldStorageBackend();

    /**
     * Health check interval in seconds.
     */
    @WithDefault("60")
    int healthCheckInterval();

    /**
     * Whether to fail if not all redundancy targets are met.
     */
    @WithDefault("false")
    boolean failOnPartialWrite();

    /**
     * Minimum number of successful writes required.
     */
    @WithDefault("1")
    int minimumWrites();

    enum LoadBalancingStrategy {
        ROUND_ROBIN,
        RANDOM,
        FASTEST,
        PRIMARY_FIRST
    }
}
