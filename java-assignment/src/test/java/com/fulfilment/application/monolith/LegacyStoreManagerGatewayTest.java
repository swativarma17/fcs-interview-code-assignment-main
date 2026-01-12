package com.fulfilment.application.monolith;

import com.fulfilment.application.monolith.stores.LegacyStoreManagerGateway;
import com.fulfilment.application.monolith.stores.Store;
import org.junit.jupiter.api.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LegacyStoreManagerGatewayTest {

    private LegacyStoreManagerGateway gateway;

    private PrintStream originalOut;
    private PrintStream originalErr;
    private ByteArrayOutputStream outBuffer;
    private ByteArrayOutputStream errBuffer;

    @BeforeEach
    void setUp() {
        gateway = new LegacyStoreManagerGateway();

        // capture console
        originalOut = System.out;
        originalErr = System.err;
        outBuffer = new ByteArrayOutputStream();
        errBuffer = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outBuffer));
        System.setErr(new PrintStream(errBuffer));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    // ---------- helpers ----------
    private static Store store(Long id, String name, int items) {
        Store s = new Store();
        s.id = id;
        s.name = name;
        s.quantityProductsInStock = items;
        return s;
    }

    private static String getLineContaining(String output, String startsWith) {
        for (String line : output.split(System.lineSeparator())) {
            if (line.startsWith(startsWith)) {
                return line;
            }
        }
        return null;
    }

    private static String extractPathFromPrintedLine(String line) {
        // line format: "Legacy sync temp file: <path>"
        return line == null ? null : line.replace("Legacy sync temp file: ", "").trim();
    }

    private static String extractPayload(String line, String prefix) {
        // "Written: <payload>" or "Read back: <payload>"
        return line == null ? null : line.replace(prefix, "").trim();
    }

    // ---------- CREATE ----------
    @Test
    void create_shouldWriteAndReadBack_andDeleteTempFile() throws Exception {
        Store s = store(101L, "Warehouse-A", 15);

        gateway.createStoreOnLegacySystem(s);

        String out = outBuffer.toString();

        // path and payload lines present
        String pathLine = getLineContaining(out, "Legacy sync temp file: ");
        String writtenLine = getLineContaining(out, "Written: ");
        String readBackLine = getLineContaining(out, "Read back: ");

        assertNotNull(pathLine, "Expected path line to be printed");
        assertNotNull(writtenLine, "Expected written payload line");
        assertNotNull(readBackLine, "Expected read-back payload line");

        // payload equality check (ignoring timestamp format, but the method prints identical content)
        String writtenPayload = extractPayload(writtenLine, "Written: ");
        String readBackPayload = extractPayload(readBackLine, "Read back: ");
        assertEquals(writtenPayload, readBackPayload, "Read-back content should equal written content");

        // file removed
        String tempFilePath = extractPathFromPrintedLine(pathLine);
        assertNotNull(tempFilePath);
        assertFalse(Files.exists(Path.of(tempFilePath)), "Temp file should be deleted");
    }

    @Test
    void create_shouldUseSafeFilename_whenNameIsNullOrBlank() throws Exception {
        Store s1 = store(202L, null, 5);
        gateway.createStoreOnLegacySystem(s1);
        String out1 = outBuffer.toString();
        String pathLine1 = getLineContaining(out1, "Legacy sync temp file: ");
        assertNotNull(pathLine1);
        assertTrue(pathLine1.contains("store-202-create"),
                "Filename prefix should use 'store-<id>-create' when name is null");

        // reset buffer and test blank name
        outBuffer.reset();
        Store s2 = store(303L, "   ", 5);
        gateway.createStoreOnLegacySystem(s2);
        String out2 = outBuffer.toString();
        String pathLine2 = getLineContaining(out2, "Legacy sync temp file: ");
        assertNotNull(pathLine2);
        assertTrue(pathLine2.contains("store-303-create"),
                "Filename prefix should use 'store-<id>-create' when name is blank");
    }

    // ---------- UPDATE ----------
    @Test
    void update_shouldIncludeActionUpdate_inPrintedPayload() {
        Store s = store(11L, "My Shop", 42);

        gateway.updateStoreOnLegacySystem(s);

        String out = outBuffer.toString();
        String writtenLine = getLineContaining(out, "Written: ");
        assertNotNull(writtenLine);
        assertTrue(writtenLine.contains("[action=UPDATE]"),
                "Payload should indicate UPDATE action");
        assertTrue(out.contains("Legacy sync temp file: "),
                "Path line should be printed");
    }

    @Test
    void update_shouldUseNameInTempFilename_whenProvided() {
        Store s = store(12L, "Fancy Store", 10);

        gateway.updateStoreOnLegacySystem(s);

        String out = outBuffer.toString();
        String pathLine = getLineContaining(out, "Legacy sync temp file: ");
        assertNotNull(pathLine);
        assertTrue(pathLine.contains("Fancy Store-update"),
                "Temp filename should include provided store name and action suffix");
    }

    // ---------- DELETE ----------
    @Test
    void delete_shouldIncludeActionDelete_inPrintedPayload() {
        Store s = store(99L, "Demo", 0);

        gateway.deleteStoreOnLegacySystem(s);

        String out = outBuffer.toString();
        String writtenLine = getLineContaining(out, "Written: ");
        assertNotNull(writtenLine);
        assertTrue(writtenLine.contains("[action=DELETE]"),
                "Payload should indicate DELETE action");
    }

    }
