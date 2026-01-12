
package com.fulfilment.application.monolith.stores;

import jakarta.enterprise.context.ApplicationScoped;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

@ApplicationScoped
public class LegacyStoreManagerGateway {

    public void createStoreOnLegacySystem(Store store) {
        writeToFile(store, "CREATE");
    }

    public void updateStoreOnLegacySystem(Store store) {
        writeToFile(store, "UPDATE");
    }

    public void deleteStoreOnLegacySystem(Store store) {
        writeToFile(store, "DELETE");
    }

    private void writeToFile(Store store, String action) {
        try {
            // Use a stable, safe filename (avoid spaces/special chars if present)
            String safeName = (store.name == null || store.name.isBlank()) ? ("store-" + store.id) : store.name;
            Path tempFile = Files.createTempFile(safeName + "-" + action.toLowerCase(), ".txt");

            String content =
                    "[action=" + action + "] [ts=" + Instant.now() + "] " +
                            "[id=" + store.id + "] [name=" + store.name + "] " +
                            "[items=" + store.quantityProductsInStock + "]";

            Files.write(tempFile, content.getBytes());

            // Read back to verify (simulated legacy integration acknowledgement)
            String readContent = Files.readString(tempFile);

            System.out.println("Legacy sync temp file: " + tempFile);
            System.out.println("Written: " + content);
            System.out.println("Read back: " + readContent);

            Files.deleteIfExists(tempFile);
        } catch (Exception e) {
            // In real-world, log & retry or send to a DLQ; here we just print the stack
            e.printStackTrace();
        }
    }
}
