package com.fulfilment.application.monolith.warehouses.adapters.restapi;

import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/warehouse") // singular, matches the test URL
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class WarehouseController {

  @Inject
  WarehouseResourceImpl warehouseResource;

  @GET
  public List<Warehouse> listAll() {
    return warehouseResource.listAllWarehousesUnits();
  }

  @POST
  public Warehouse create(Warehouse data) {
    return warehouseResource.createANewWarehouseUnit(data);
  }

  @GET
  @Path("/{id}")
  public Warehouse get(@PathParam("id") String id) {
    return warehouseResource.getAWarehouseUnitByID(id);
  }

  @DELETE
  @Path("/{id}")
  public void archive(@PathParam("id") String id) {
    warehouseResource.archiveAWarehouseUnitByID(id);
  }

  @POST
  @Path("/{businessUnitCode}/replacement")
  public Warehouse replace(@PathParam("businessUnitCode") String code, Warehouse data) {
    return warehouseResource.replaceTheCurrentActiveWarehouse(code, data);
  }
}
