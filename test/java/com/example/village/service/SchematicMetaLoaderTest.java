package com.example.village.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SchematicMetaLoaderTest {

    @Test
    void loadParsesNamedMarkerAnchors() throws IOException {
        Path tempDir = Files.createTempDirectory("village-schem-meta-");
        Path schematicFile = tempDir.resolve("sample.schem");
        Path metaFile = tempDir.resolve("sample.schem.meta");

        Files.createFile(schematicFile);
        Files.writeString(metaFile, """
markers:
  roof_center: "1,2,3"
  north_wall: "0,2,4"
blocks:
  0,0,0: STRUCTURE
""");

        SchematicMetaLoader.SchematicMeta meta = SchematicMetaLoader.load(schematicFile.toFile(), null);

        assertEquals("1,2,3", meta.anchor("roof_center"));
        assertEquals("0,2,4", meta.anchor("north_wall"));
    }
}
