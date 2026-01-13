
package com.fulfilment.application.monolith.location;

import com.fulfilment.application.monolith.warehouses.domain.models.Location;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

class LocationGatewayTest {

    private final LocationGateway gateway = new LocationGateway();

    @Test
    @DisplayName("resolveByIdentifier returns Location for exact match")
    void resolveByIdentifier_exactMatch() {
        Location loc = gateway.resolveByIdentifier("ZWOLLE-001");
        assertNotNull(loc, "Expected a location for a valid identifier");
        assertEquals("ZWOLLE-001", loc.identification);
    }

    @Test
    @DisplayName("resolveByIdentifier is case-insensitive")
    void resolveByIdentifier_caseInsensitive() {
        Location loc = gateway.resolveByIdentifier("amsterdam-002");
        assertNotNull(loc, "Expected a location irrespective of case");
        assertEquals("AMSTERDAM-002", loc.identification);
    }

    @Test
    @DisplayName("resolveByIdentifier returns null for null identifier")
    void resolveByIdentifier_null() {
        assertNull(gateway.resolveByIdentifier(null), "Null identifier should return null");
    }

    @Test
    @DisplayName("resolveByIdentifier returns null for blank/whitespace identifier")
    void resolveByIdentifier_blank() {
        assertNull(gateway.resolveByIdentifier(""), "Blank identifier should return null");
        assertNull(gateway.resolveByIdentifier("   "), "Whitespace-only identifier should return null");
    }

    @Test
    @DisplayName("resolveByIdentifier throws NoSuchElementException for unknown identifier")
    void resolveByIdentifier_unknown() {
        NoSuchElementException ex = assertThrows(
                NoSuchElementException.class,
                () -> gateway.resolveByIdentifier("UNKNOWN-123"),
                "Unknown identifier should throw NoSuchElementException"
        );
        assertTrue(ex.getMessage().contains("Location not found for identifier 'UNKNOWN-123'"),
                "Exception message should include the missing identifier");
    }
}
