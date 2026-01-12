package com.fulfilment.application.monolith.location;

/*import com.fulfilment.application.monolith.warehouses.domain.models.Location;
import org.junit.jupiter.api.Test;

public class LocationGatewayTest {

    @Test
    public void testWhenResolveExistingLocationShouldReturn() {
        // given
         LocationGateway locationGateway = new LocationGateway();

        // when
        Location location = locationGateway.resolveByIdentifier("ZWOLLE-001");

        // then
        // assertEquals(location.identification, "ZWOLLE-001");
    }
}*/

import com.fulfilment.application.monolith.warehouses.domain.models.Location;
import org.junit.jupiter.api.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LocationGatewayTest {

    private LocationGateway gateway;

    // Access to the static locations list for test setup tweaks
    private static List<Location> staticLocations;

    @BeforeAll
    static void grabStaticList() throws Exception {
        Field f = LocationGateway.class.getDeclaredField("locations");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Location> list = (List<Location>) f.get(null);
        staticLocations = list; // This is the same instance used by the gateway
    }

    @BeforeEach
    void setUp() {
        gateway = new LocationGateway();
    }

    @Test
    void resolve_nullIdentifier_returnsNull() {
        assertNull(gateway.resolveByIdentifier(null));
    }

    @Test
    void resolve_blankIdentifier_returnsNull() {
        assertNull(gateway.resolveByIdentifier(""));
        assertNull(gateway.resolveByIdentifier("   "));
    }

    @Test
    void resolve_exactMatch_returnsLocation() {
        Location found = gateway.resolveByIdentifier("ZWOLLE-001");
        assertNotNull(found);
        assertEquals("ZWOLLE-001", found.identification);
    }

    @Test
    void resolve_caseInsensitiveMatch_returnsLocation() {
        Location found = gateway.resolveByIdentifier("amsterdam-001");
        assertNotNull(found);
        assertEquals("AMSTERDAM-001", found.identification);
    }

    @Test
    void resolve_notFound_returnsNull() {
        Location found = gateway.resolveByIdentifier("NOPE-999");
        assertNull(found);
    }

    @Test
    void resolve_withLeadingOrTrailingSpaces_currentCodeDoesNotTrim_returnsNull() {
        // Current implementation uses equalsIgnoreCase without trim; should be null.
        assertNull(gateway.resolveByIdentifier("  ZWOLLE-001"));
        assertNull(gateway.resolveByIdentifier("ZWOLLE-001   "));
    }

    @Test
    void resolve_afterAddingDynamicEntry_shouldFindNewEntry() {
        // Arrange: temporarily add a new location to the static list
        Location temp = new Location("BENGALURU-001", 9, 50);
        staticLocations.add(temp);

        try {
            // Act
            Location found = gateway.resolveByIdentifier("BENGALURU-001");

            // Assert
            assertNotNull(found);
            assertEquals("BENGALURU-001", found.identification);
        } finally {
            // Cleanup: remove to avoid test pollution
            staticLocations.removeIf(l -> "BENGALURU-001".equals(l.identification));
        }
    }

    @Test
    void resolve_multipleCandidates_sameIdentifier_returnsFirstMatchInListOrder() {
        // If duplicates could exist, verify loop returns the first match
        // Arrange
        Location duplicate = new Location("ZWOLLE-001", 99, 999);
        staticLocations.add(0, duplicate); // add at the beginning so it's first encountered

        try {
            Location found = gateway.resolveByIdentifier("ZWOLLE-001");
            assertNotNull(found);
            // Because we added a duplicate at index 0, we expect to get that one back
            assertSame(duplicate, found);
        } finally {
            staticLocations.remove(duplicate);
        }
    }

    // ----- Optional: parameterized edge cases -----
    @Nested
    class ParameterizedEdgeCases {
        @Test
        void resolve_mixedCase_identifiers_shouldMatchIgnoringCase() {
            String[] samples = { "ZwOlLe-002", "aMsTeRdAm-002", "tilburg-001", "EINDHOVEN-001" };
            for (String s : samples) {
                Location loc = gateway.resolveByIdentifier(s);
                assertNotNull(loc, "Expected match for: " + s);
                assertEquals(s.toUpperCase(), loc.identification, "Normalize check");
            }
        }
    }
}

