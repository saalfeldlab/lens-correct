package org.janelia.saalfeldlab.confocallens.params;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.janelia.saalfeldlab.confocallens.json.InputParams;
import org.janelia.saalfeldlab.confocallens.json.InputParamsSplit;
import org.junit.Test;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class InputParamsTest {
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    @Test
    public void testInputParamsChannels() throws IOException {
        try (InputStream is = getClass().getResourceAsStream("/input-channels-example.json");
             InputStreamReader reader = new InputStreamReader(is)) {
            InputParams params = gson.fromJson(reader, InputParams.class);

            assertNotNull(params);
            assertNotNull(params.getPatterns());
            assertNotNull(params.getLabels());

            assertEquals(2, params.getPatterns().size());
            assertEquals(5, params.getLabels().size());

            assertTrue(params.getPatterns().get(0).contains("AF488"));
            assertEquals("488nm, pass1", params.getLabels().get(0));
        }
    }

    @Test
    public void testInputParamsSplit() throws IOException {
        try (InputStream is = getClass().getResourceAsStream("/input-split-example.json");
             InputStreamReader reader = new InputStreamReader(is)) {
            InputParamsSplit params = gson.fromJson(reader, InputParamsSplit.class);

            assertNotNull(params);
            assertNotNull(params.getPatterns());
            assertNotNull(params.getLabels());
            assertNotNull(params.getMasks());

            assertEquals(1, params.getPatterns().size());
            assertEquals(2, params.getLabels().size());
            assertEquals(2, params.getMasks().size());

            assertEquals("left", params.getLabels().get(0));
            assertEquals("right", params.getLabels().get(1));
            assertTrue(params.getMasks().get(0).contains("mask-left"));
        }
    }

    @Test
    public void testPolymorphism() {
        // Test that InputParamsSplit can be used as InputParams
        InputParamsSplit splitParams = new InputParamsSplit();
        InputParams baseParams = splitParams;

        assertNotNull(baseParams);
        assertTrue(baseParams instanceof InputParamsSplit);
    }
}