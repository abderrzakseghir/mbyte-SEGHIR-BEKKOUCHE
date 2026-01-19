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
package fr.jayblanc.mbyte.store.api.resources;

import fr.jayblanc.mbyte.store.auth.AuthenticationService;
import fr.jayblanc.mbyte.store.data.settings.StorageSettings;
import fr.jayblanc.mbyte.store.data.settings.StorageSettingsService;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * REST Resource for managing storage settings.
 * Provides both HTML views and JSON API.
 * 
 * @author Abderrazak SEGHIR
 */
@Path("/settings")
public class SettingsResource {

    private static final Logger LOGGER = Logger.getLogger(SettingsResource.class.getName());

    @Inject
    Template settings;

    @Inject
    AuthenticationService auth;

    @Inject
    StorageSettingsService settingsService;

    /**
     * Get settings page (HTML).
     */
    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance getSettingsPage() {
        LOGGER.log(Level.INFO, "GET /api/settings (html)");
        String owner = auth.getConnectedProfile().getUsername();
        StorageSettings storageSettings = settingsService.getOrCreateSettings(owner);
        
        return settings.data("profile", auth.getConnectedProfile())
                       .data("settings", storageSettings)
                       .data("section", "settings");
    }

    /**
     * Get settings as JSON.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public StorageSettings getSettings() {
        LOGGER.log(Level.INFO, "GET /api/settings (json)");
        String owner = auth.getConnectedProfile().getUsername();
        return settingsService.getOrCreateSettings(owner);
    }

    /**
     * Update general settings.
     */
    @POST
    @Path("/general")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public Response updateGeneralSettings(
            @FormParam("multiBackendEnabled") boolean multiBackendEnabled,
            @FormParam("redundancyLevel") int redundancyLevel,
            @FormParam("loadBalancingStrategy") String loadBalancingStrategy,
            @FormParam("encryptionEnabled") boolean encryptionEnabled) {
        
        String owner = auth.getConnectedProfile().getUsername();
        StorageSettings storageSettings = settingsService.getOrCreateSettings(owner);
        
        storageSettings.setMultiBackendEnabled(multiBackendEnabled);
        storageSettings.setRedundancyLevel(redundancyLevel);
        storageSettings.setLoadBalancingStrategy(loadBalancingStrategy);
        storageSettings.setEncryptionEnabled(encryptionEnabled);
        
        settingsService.save(storageSettings);
        LOGGER.log(Level.INFO, "Updated general settings for user: " + owner);
        
        return Response.seeOther(java.net.URI.create("/api/settings?success=general")).build();
    }

    /**
     * Update S3/MinIO backend settings.
     */
    @POST
    @Path("/backend/s3")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public Response updateS3Settings(
            @FormParam("enabled") boolean enabled,
            @FormParam("endpoint") String endpoint,
            @FormParam("accessKey") String accessKey,
            @FormParam("secretKey") String secretKey,
            @FormParam("bucket") String bucket,
            @FormParam("region") String region) {
        
        String owner = auth.getConnectedProfile().getUsername();
        Map<String, String> credentials = new HashMap<>();
        credentials.put("endpoint", endpoint);
        credentials.put("accessKey", accessKey);
        credentials.put("secretKey", secretKey);
        credentials.put("bucket", bucket);
        credentials.put("region", region);
        
        settingsService.updateBackendSettings(owner, "S3", enabled, credentials);
        LOGGER.log(Level.INFO, "Updated S3 settings for user: " + owner);
        
        return Response.seeOther(java.net.URI.create("/api/settings?success=s3")).build();
    }

    /**
     * Update Google Drive backend settings.
     */
    @POST
    @Path("/backend/googledrive")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public Response updateGoogleDriveSettings(
            @FormParam("enabled") boolean enabled,
            @FormParam("clientId") String clientId,
            @FormParam("clientSecret") String clientSecret,
            @FormParam("refreshToken") String refreshToken,
            @FormParam("folderId") String folderId) {
        
        String owner = auth.getConnectedProfile().getUsername();
        Map<String, String> credentials = new HashMap<>();
        credentials.put("clientId", clientId);
        credentials.put("clientSecret", clientSecret);
        credentials.put("refreshToken", refreshToken);
        credentials.put("folderId", folderId);
        
        settingsService.updateBackendSettings(owner, "GOOGLE_DRIVE", enabled, credentials);
        LOGGER.log(Level.INFO, "Updated Google Drive settings for user: " + owner);
        
        return Response.seeOther(java.net.URI.create("/api/settings?success=googledrive")).build();
    }

    /**
     * Update Dropbox backend settings.
     */
    @POST
    @Path("/backend/dropbox")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public Response updateDropboxSettings(
            @FormParam("enabled") boolean enabled,
            @FormParam("accessToken") String accessToken,
            @FormParam("appKey") String appKey,
            @FormParam("appSecret") String appSecret,
            @FormParam("refreshToken") String refreshToken,
            @FormParam("rootPath") String rootPath) {
        
        String owner = auth.getConnectedProfile().getUsername();
        Map<String, String> credentials = new HashMap<>();
        credentials.put("accessToken", accessToken);
        credentials.put("appKey", appKey);
        credentials.put("appSecret", appSecret);
        credentials.put("refreshToken", refreshToken);
        credentials.put("rootPath", rootPath != null && !rootPath.isEmpty() ? rootPath : "/mbyte-store");
        
        settingsService.updateBackendSettings(owner, "DROPBOX", enabled, credentials);
        LOGGER.log(Level.INFO, "Updated Dropbox settings for user: " + owner);
        
        return Response.seeOther(java.net.URI.create("/api/settings?success=dropbox")).build();
    }

    /**
     * Update OneDrive backend settings.
     */
    @POST
    @Path("/backend/onedrive")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public Response updateOneDriveSettings(
            @FormParam("enabled") boolean enabled,
            @FormParam("clientId") String clientId,
            @FormParam("clientSecret") String clientSecret,
            @FormParam("tenantId") String tenantId,
            @FormParam("refreshToken") String refreshToken,
            @FormParam("rootPath") String rootPath) {
        
        String owner = auth.getConnectedProfile().getUsername();
        Map<String, String> credentials = new HashMap<>();
        credentials.put("clientId", clientId);
        credentials.put("clientSecret", clientSecret);
        credentials.put("tenantId", tenantId != null && !tenantId.isEmpty() ? tenantId : "common");
        credentials.put("refreshToken", refreshToken);
        credentials.put("rootPath", rootPath != null && !rootPath.isEmpty() ? rootPath : "/mbyte-store");
        
        settingsService.updateBackendSettings(owner, "ONEDRIVE", enabled, credentials);
        LOGGER.log(Level.INFO, "Updated OneDrive settings for user: " + owner);
        
        return Response.seeOther(java.net.URI.create("/api/settings?success=onedrive")).build();
    }

    /**
     * Update WebDAV backend settings.
     */
    @POST
    @Path("/backend/webdav")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public Response updateWebDavSettings(
            @FormParam("enabled") boolean enabled,
            @FormParam("url") String url,
            @FormParam("username") String username,
            @FormParam("password") String password) {
        
        String owner = auth.getConnectedProfile().getUsername();
        Map<String, String> credentials = new HashMap<>();
        credentials.put("url", url);
        credentials.put("username", username);
        credentials.put("password", password);
        
        settingsService.updateBackendSettings(owner, "WEBDAV", enabled, credentials);
        LOGGER.log(Level.INFO, "Updated WebDAV settings for user: " + owner);
        
        return Response.seeOther(java.net.URI.create("/api/settings?success=webdav")).build();
    }

    /**
     * Toggle a backend on/off quickly (AJAX).
     */
    @POST
    @Path("/toggle/{backend}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response toggleBackend(@PathParam("backend") String backend, Map<String, Boolean> body) {
        String owner = auth.getConnectedProfile().getUsername();
        boolean enabled = body.getOrDefault("enabled", false);
        
        settingsService.updateBackendSettings(owner, backend, enabled, null);
        LOGGER.log(Level.INFO, "Toggled " + backend + " to " + enabled + " for user: " + owner);
        
        return Response.ok(Map.of("success", true, "backend", backend, "enabled", enabled)).build();
    }
}
