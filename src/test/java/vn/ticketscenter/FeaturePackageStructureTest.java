package vn.ticketscenter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class FeaturePackageStructureTest {

    @Test
    @DisplayName("Verify package-by-feature directory structure exists")
    void testFeaturePackageStructure() {
        File baseDir = new File("src/main/java/vn/ticketscenter");
        assertTrue(baseDir.exists() && baseDir.isDirectory(), "Base source package vn.ticketscenter should exist");

        List<String> requiredFeatures = List.of(
                "config",
                "identity",
                "event",
                "ticketing",
                "order",
                "fulfillment",
                "settlement",
                "audit",
                "persistence"
        );

        for (String feature : requiredFeatures) {
            File featureDir = new File(baseDir, feature);
            assertTrue(featureDir.exists() && featureDir.isDirectory(),
                    "Feature package '" + feature + "' should exist under vn.ticketscenter");
        }
    }
}
