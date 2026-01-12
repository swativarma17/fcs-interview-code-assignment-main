package com.fulfilment.application.monolith.products;

/*
import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.core.IsNot.not;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class ProductEndpointTest {

  @Test
  public void testCrudProduct() {
    final String path = "product";

    // List all, should have all 3 products the database has initially:
    given()
        .when()
        .get(path)
        .then()
        .statusCode(200)
        .body(containsString("TONSTAD"), containsString("KALLAX"), containsString("BESTÅ"));

    // Delete the TONSTAD:
    given().when().delete(path + "/1").then().statusCode(204);

    // List all, TONSTAD should be missing now:
    given()
        .when()
        .get(path)
        .then()
        .statusCode(200)
        .body(not(containsString("TONSTAD")), containsString("KALLAX"), containsString("BESTÅ"));
  }
}
*/

import io.quarkus.panache.common.Sort;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)

@MockitoSettings(strictness = Strictness.LENIENT)

class ProductResourceTest {

    @Mock
    ProductRepository productRepository;

    @InjectMocks
    ProductResource resource;



    @Test
    void get_shouldReturnListSortedByName() {
        Product p1 = product(1L, "Alpha");
        Product p2 = product(2L, "Beta");

        when(productRepository.listAll(any(Sort.class)))
                .thenReturn(List.of(p1, p2));

        List<Product> result = resource.get();

        assertEquals(2, result.size());
        assertEquals("Alpha", result.get(0).name);
        assertEquals("Beta", result.get(1).name);

        ArgumentCaptor<Sort> sortCaptor = ArgumentCaptor.forClass(Sort.class);
        verify(productRepository, times(1)).listAll(sortCaptor.capture());
        Sort used = sortCaptor.getValue();

        assertNotNull(used);

    }


    @Test
    void getSingle_whenFound_shouldReturnEntity() {
        Product p = product(10L, "Phone");
        when(productRepository.findById(10L)).thenReturn(p);

        Product result = resource.getSingle(10L);

        assertNotNull(result);
        assertEquals("Phone", result.name);
        verify(productRepository, times(1)).findById(10L);
    }

    @Test
    void getSingle_whenMissing_shouldThrow404() {
        when(productRepository.findById(99L)).thenReturn(null);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> resource.getSingle(99L));

        assertEquals(404, ex.getResponse().getStatus());
        assertTrue(ex.getMessage().contains("99"));
    }

    @Test
    void create_whenIdPreset_shouldThrow422() {
        Product p = product(1L, "Preset");
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> resource.create(p));
        assertEquals(422, ex.getResponse().getStatus());
        assertTrue(ex.getMessage().contains("Id was invalidly set"));
        verify(productRepository, never()).persist((Product) any());
    }

    @Test
    void create_whenValid_shouldPersist_andReturn201WithBody() {
        Product p = product(null, "New");
        Response resp = resource.create(p);

        verify(productRepository, times(1)).persist(p);
        assertEquals(201, resp.getStatus());
        assertSame(p, resp.getEntity());
    }

    @Test
    void update_whenNameMissing_shouldThrow422() {
        Product incoming = product(null, null); // name missing
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> resource.update(5L, incoming));
        assertEquals(422, ex.getResponse().getStatus());
        verify(productRepository, never()).findById(anyLong());
        verify(productRepository, never()).persist((Product) any());
    }

    @Test
    void update_whenTargetMissing_shouldThrow404() {
        Product incoming = product(null, "Updated");
        when(productRepository.findById(7L)).thenReturn(null);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> resource.update(7L, incoming));

        assertEquals(404, ex.getResponse().getStatus());
        verify(productRepository, times(1)).findById(7L);
        verify(productRepository, never()).persist((Product) any());
    }


    @Test
    void update_whenValid_shouldCopyFieldsPersist_andReturnEntity() {
        Product existing = product(3L, "Old");
        existing.description = "Old desc";
        existing.price = BigDecimal.valueOf(100.0);
        existing.stock = 10;

        Product incoming = product(null, "NewName");
        incoming.description = "New desc";
        incoming.price = BigDecimal.valueOf(200.0);
        incoming.stock = 20;

        when(productRepository.findById(3L)).thenReturn(existing);

        Product result = resource.update(3L, incoming);


        // fields copied
        assertEquals("NewName", existing.name);
        assertEquals("New desc", existing.description);

        // Prefer compareTo if you want to ignore scale differences
        assertEquals(0, existing.price.compareTo(BigDecimal.valueOf(200.0)));

        // or strict equality:
        // assertEquals(BigDecimal.valueOf(200.0), existing.price);

        assertEquals(20, existing.stock);

        // persist called on updated entity
        verify(productRepository, times(1)).persist(existing);
        // returned instance is the updated entity
        assertSame(existing, result);
    }

    @Test
    void delete_whenMissing_shouldThrow404() {
        when(productRepository.findById(77L)).thenReturn(null);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> resource.delete(77L));

        assertEquals(404, ex.getResponse().getStatus());
        verify(productRepository, times(1)).findById(77L);
        verify(productRepository, never()).delete(any());
    }

    @Test
    void delete_whenFound_shouldDelete_andReturn204() {
        Product existing = product(12L, "ToDelete");
        when(productRepository.findById(12L)).thenReturn(existing);

        Response resp = resource.delete(12L);

        verify(productRepository, times(1)).delete(existing);
        assertEquals(204, resp.getStatus());
        assertNull(resp.getEntity());
    }

    // --- helper to create products with sensible defaults ---
    private static Product product(Long id, String name) {
        Product p = new Product();
        p.id = id;
        p.name = name;
        p.description = (name == null ? null : name + " desc");
        p.price = BigDecimal.valueOf(99.0);
        p.stock = 5;
        return p;
    }
}
