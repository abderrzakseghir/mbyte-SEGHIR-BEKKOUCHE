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
package fr.jayblanc.mbyte.store.data.backend;

/**
 * Enumeration of supported storage backend types.
 * 
 * @author Abderrazak SEGHIR
 */
public enum StorageBackendType {
    
    /**
     * Local filesystem storage (default)
     */
    LOCAL,
    
    /**
     * Amazon S3 or S3-compatible storage (MinIO, etc.)
     */
    S3,
    
    /**
     * WebDAV storage
     */
    WEBDAV,
    
    /**
     * Google Drive storage
     */
    GOOGLE_DRIVE,
    
    /**
     * Dropbox storage
     */
    DROPBOX,
    
    /**
     * Microsoft OneDrive storage
     */
    ONEDRIVE,
    
    /**
     * Multi-backend mode (redundancy across multiple backends)
     */
    MULTI

}
