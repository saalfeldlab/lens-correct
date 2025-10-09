package org.janelia.saalfeldlab.confocallens;

import static org.junit.Assert.assertNotNull;

import org.janelia.saalfeldlab.confocallens.json.CalibrationParams;
import org.junit.Test;

public class CalibrateSplitParamsTest {

    @Test
    public void testLoadDefaultCalibrationParams() throws Exception {
        // This should load from the resource without error
        CalibrationParams params = CalibrationParams.loadFromResource("/calibration-params.json");

        assertNotNull("Default calibration params should load", params);
        assertNotNull("MontageLayers should not be null", params.getMontageLayers());
        assertNotNull("AlignLayers should not be null", params.getAlignLayers());
        assertNotNull("CorrectDistortion should not be null", params.getCorrectDistortion());
    }
}