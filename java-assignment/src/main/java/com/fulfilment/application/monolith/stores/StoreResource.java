
package com.fulfilment.application.monolith.stores;

import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;


import java.util.List;
import java.util.logging.Logger;

@Path("store")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class StoreResource {

    private static final Logger LOGGER = Logger.getLogger(StoreResource.class.getName());

    @Inject
    Event<StoreChangeEvent> storeChangeEvent; // CDI event to be observed after commit

    @GET
    public List<Store> get() {
        return Store.listAll(Sort.by("name"));
    }

    @GET
    @Path("{id}")
    public Store getSingle(@PathParam("id") Long id) {
        Store entity = Store.findById(id);
        if (entity == null) {
            throw new WebApplicationException("Store with id of " + id + " does not exist.", 404);
        }
        return entity;
    }

    @POST
    @Transactional
    public Response create(Store store) {
        if (store.id != null) {
            throw new WebApplicationException("Id was invalidly set on request.", 422);
        }

        store.persist();

        // Fire event during the transaction; observer will run AFTER_SUCCESS (post-commit)
        storeChangeEvent.fire(new StoreChangeEvent(StoreChangeType.CREATE, store));

        return Response.status(201).entity(store).build();
    }

    @PUT
    @Path("{id}")
    @Transactional
    public Store update(@PathParam("id") Long id, Store updatedStore) {
        if (updatedStore == null || updatedStore.name == null) {
            throw new WebApplicationException("Store Name was not set on request.", 422);
        }

        Store entity = Store.findById(id);
        if (entity == null) {
            throw new WebApplicationException("Store with id of " + id + " does not exist.", 404);
        }

        entity.name = updatedStore.name;
        entity.quantityProductsInStock = updatedStore.quantityProductsInStock;

        storeChangeEvent.fire(new StoreChangeEvent(StoreChangeType.UPDATE, entity));

        return entity;
    }

    @PATCH
    @Path("{id}")
    @Transactional
    public Store patch(@PathParam("id") Long id, Store updatedStore) {
        if (updatedStore == null) {
            throw new WebApplicationException("Invalid request payload.", 422);
        }

        Store entity = Store.findById(id);
        if (entity == null) {
            throw new WebApplicationException("Store with id of " + id + " does not exist.", 404);
        }

        // Only update fields provided in the payload
        if (updatedStore.name != null && !updatedStore.name.isBlank()) {
            entity.name = updatedStore.name;
        }
        // If quantity is meaningful only when positive/non-zero, guard it accordingly
        if (updatedStore.quantityProductsInStock > 0) {
            entity.quantityProductsInStock = updatedStore.quantityProductsInStock;
        }

        storeChangeEvent.fire(new StoreChangeEvent(StoreChangeType.PATCH, entity));

        return entity;
    }

    @DELETE
    @Path("{id}")
    @Transactional
    public Response delete(@PathParam("id") Long id) {
        Store entity = Store.findById(id);
        if (entity == null) {
            throw new WebApplicationException("Store with id of " + id + " does not exist.", 404);
        }

        entity.delete();

        // Notify legacy after commit that the store was deleted
        storeChangeEvent.fire(new StoreChangeEvent(StoreChangeType.DELETE, entity));

        return Response.status(204).build();
    }

    }
