/*

package com.fulfilment.application.monolith.warehouses.adapters.restapi;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.common.http.TestHTTPResource;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import java.net.URL;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusIntegrationTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class WarehouseEndpointIT {

    // If your controller path is singular, change to "warehouse"
    private static final String PATH = "warehouses";

    // Inject the full base URL to the endpoint, respecting test port and root-path
    // Example: http://localhost:8081/api/warehouses if quarkus.http.root-path=/api
    @TestHTTPResource(PATH)
    URL warehousesUrl;

    @BeforeAll
    static void enableRestAssuredLogging() {
        // Useful when assertions fail
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @Test
    @Order(1)
    void listWarehouses_shouldReturnSeededWarehouses() {
        given()
                .accept(ContentType.JSON)
                .when()
                .get(warehousesUrl)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                // Assert using JSONPath/GPath over arrays of objects
                // *.property collects that field from every item in the array
                .body("*.businessUnitCode", hasItems("MWH.001", "MWH.012", "MWH.023"))
                .body("*.location", hasItems("ZWOLLE-001", "AMSTERDAM-001", "TILBURG-001"))
                .body("size()", greaterThan(0));
    }

    @Test
    @Order(2)
    void archiveWarehouse_shouldRemoveFromListing() {
        // 1) Find the warehouse ID for the given businessUnitCode (MWH.001) from the list
        Response listResp = given()
                .accept(ContentType.JSON)
                .when()
                .get(warehousesUrl)
                .then()
                .statusCode(200)
                .extract().response();

        Integer id = listResp.jsonPath()
                .getInt("find { it.businessUnitCode == 'MWH.001' }.id");

        // If your API deletes by businessUnitCode instead of numeric id,
        // we'll fall back to deleting by the code.
        int deleteStatus;
        deleteStatus = given()
                .when()
                .delete(warehousesUrl + "/" + id)
                .then()
                .extract().statusCode();

        Assertions.assertTrue(deleteStatus == 204 || deleteStatus == 200,
                "Expected 200/204 on DELETE, got " + deleteStatus);

        // 2) Re-list and assert that MWH.001 is gone but others still exist
        given()
                .accept(ContentType.JSON)
                .when()
                .get(warehousesUrl)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("*.businessUnitCode", not(hasItem("MWH.001")))
                .body("*.businessUnitCode", hasItems("MWH.012", "MWH.023"));
    }
}
*/
