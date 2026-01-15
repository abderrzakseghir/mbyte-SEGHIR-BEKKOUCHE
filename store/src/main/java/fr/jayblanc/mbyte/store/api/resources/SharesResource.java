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
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Resource for managing file shares.
 * 
 * @author Abderrazak SEGHIR
 */
@Path("/shares")
public class SharesResource {

    private static final Logger LOGGER = Logger.getLogger(SharesResource.class.getName());

    @Inject
    Template shares;

    @Inject
    AuthenticationService auth;

    /**
     * Get all shares for the current user (JSON/XML).
     */
    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public List<Object> getShares() {
        LOGGER.log(Level.INFO, "GET /api/shares");
        // TODO: Implement share service to retrieve actual shares
        return new ArrayList<>();
    }

    /**
     * Get shares view (HTML).
     */
    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance getSharesView() {
        LOGGER.log(Level.INFO, "GET /api/shares (html)");
        return shares.data("profile", auth.getConnectedProfile())
                     .data("shares", new ArrayList<>());
    }

}
