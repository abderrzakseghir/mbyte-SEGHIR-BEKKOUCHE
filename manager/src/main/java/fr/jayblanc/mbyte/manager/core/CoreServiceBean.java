/*
 * Copyright (C) 2025 Jerome Blanchard <jayblanc@gmail.com>
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
package fr.jayblanc.mbyte.manager.core;

import fr.jayblanc.mbyte.manager.auth.AuthenticationService;
import fr.jayblanc.mbyte.manager.core.entity.Store;
import fr.jayblanc.mbyte.manager.store.StoreManager;
import fr.jayblanc.mbyte.manager.store.StoreProviderException;
import fr.jayblanc.mbyte.manager.store.StoreProviderNotFoundException;
import fr.jayblanc.mbyte.manager.topology.TopologyService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.transaction.Transactional;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class CoreServiceBean implements CoreService {

    private static final Logger LOGGER = Logger.getLogger(CoreServiceBean.class.getName());

    @Inject EntityManager em;
    @Inject AuthenticationService authenticationService;
    @Inject StoreManager manager;
    @Inject TopologyService topology;

    @Override
    @Transactional(Transactional.TxType.REQUIRED)
    public Store createStore(String name) {
        LOGGER.log(Level.INFO, "Creating new store with name: " + name);
        Store store = new Store();
        store.setId(UUID.randomUUID().toString());
        store.setName(name);
        store.setCreationDate(System.currentTimeMillis());
        store.setOwner(authenticationService.getConnectedProfile().getUsername());
        store.setUsage(0);
        store.setStatus(Store.Status.PENDING);
        try {
            String output = manager.getProvider().createStore(store.getId(), store.getOwner(), store.getName());
            store.setLocation(topology.lookup(store.getOwner()));
            store.setStatus(Store.Status.AVAILABLE);
            store.setLog(output);
        } catch (StoreProviderException | StoreProviderNotFoundException e ) {
            LOGGER.log(Level.INFO, "Unable to create store, see logs", e);
        }
        em.persist(store);
        return store;
    }

    @Override
    @Transactional(Transactional.TxType.REQUIRED)
    public Store getConnectedUserStore() throws StoreNotFoundException, CoreServiceException {
        LOGGER.log(Level.INFO, "Getting store for connected user");
        String owner = authenticationService.getConnectedProfile().getUsername();
        Store store = findByOwner(owner);
        String location = lookup(store.getOwner());
        if ( location != null ) {
            LOGGER.log(Level.INFO, "Found store instance at location: " + location);
            store.setLocation(location);
        } else {
            LOGGER.log(Level.INFO, "Store NOT found in the topology");
            store.setLocation("#");
        }
        return store;
    }

    private Store findByOwner(String owner) throws StoreNotFoundException {
        try {
            Store store = em.createNamedQuery("Store.findByOwner", Store.class).setParameter("owner", authenticationService.getConnectedProfile().getUsername()).getSingleResult();
            if ( store == null ) {
                throw new StoreNotFoundException(owner);
            }
            return store;
        } catch (NoResultException e) {
            throw new StoreNotFoundException(owner);
        }
    }

    private String lookup(String name) throws CoreServiceException {
        return topology.lookup(name);
    }

}
