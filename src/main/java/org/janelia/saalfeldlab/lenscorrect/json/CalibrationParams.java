package org.janelia.saalfeldlab.lenscorrect.json;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Calibration parameters (shared between different calibration tools)
 * Contains TrakEM2 alignment and distortion correction parameters
 */
public class CalibrationParams {
    private MontageLayersParams montageLayers;
    private AlignLayersParams alignLayers;
    private CorrectDistortionParams correctDistortion;
    private AlignLayersParams alignLayers2;
    private AlignLayersParams alignLayers3;

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .create();

    /**
     * Load calibration parameters from a JSON file
     *
     * @param paramPath Path to the params.json file
     * @return Parsed CalibrationParams object
     * @throws IOException if file cannot be read
     */
    public static CalibrationParams load(String paramPath) throws IOException {
        String jsonContent = Files.readString(Paths.get(paramPath));
        return GSON.fromJson(jsonContent, CalibrationParams.class);
    }

    /**
     * Load calibration parameters from an InputStream
     *
     * @param inputStream InputStream containing JSON data
     * @return Parsed CalibrationParams object
     * @throws IOException if stream cannot be read
     */
    public static CalibrationParams loadFromStream(InputStream inputStream) throws IOException {
        try (Reader reader = new InputStreamReader(inputStream)) {
            return GSON.fromJson(reader, CalibrationParams.class);
        }
    }

    /**
     * Load calibration parameters from a resource on the classpath
     *
     * @param resourcePath Path to the resource (e.g., "/calibration-params.json")
     * @return Parsed CalibrationParams object
     * @throws IOException if resource cannot be found or read
     */
    public static CalibrationParams loadFromResource(String resourcePath) throws IOException {
        InputStream inputStream = CalibrationParams.class.getResourceAsStream(resourcePath);
        if (inputStream == null) {
            throw new IOException("Resource not found: " + resourcePath);
        }
        return loadFromStream(inputStream);
    }

    public MontageLayersParams getMontageLayers() {
        return montageLayers;
    }

    public AlignLayersParams getAlignLayers() {
        return alignLayers;
    }

    public CorrectDistortionParams getCorrectDistortion() {
        return correctDistortion;
    }

    public AlignLayersParams getAlignLayers2() {
        return alignLayers2;
    }

    public AlignLayersParams getAlignLayers3() {
        return alignLayers3;
    }
}