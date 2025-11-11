package org.janelia.saalfeldlab.lenscorrect.params;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.janelia.saalfeldlab.lenscorrect.json.CalibrationParams;
import org.junit.Before;
import org.junit.Test;

import lenscorrection.DistortionCorrectionTask.CorrectDistortionFromSelectionParam;
import mpicbg.trakem2.align.Align;
import mpicbg.trakem2.align.RegularizedAffineLayerAlignment;

public class CalibrationParamsTest {

    private CalibrationParams calibParams;
    private static final int MAX_NUM_THREADS = 8;

    @Before
    public void setUp() throws IOException {
        // Load the calibration params from test resources
        calibParams = CalibrationParams.loadFromResource("/calibration-params.json");
    }

    @Test
    public void testCalibrationParamsLoadedSuccessfully() {
        assertNotNull("CalibrationParams should not be null", calibParams);
        assertNotNull("MontageLayers params should not be null", calibParams.getMontageLayers());
        assertNotNull("AlignLayers params should not be null", calibParams.getAlignLayers());
        assertNotNull("CorrectDistortion params should not be null", calibParams.getCorrectDistortion());
        assertNotNull("AlignLayers2 params should not be null", calibParams.getAlignLayers2());
        assertNotNull("AlignLayers3 params should not be null", calibParams.getAlignLayers3());
    }

    @Test
    public void testMontageLayersConversion() {
        Align.ParamOptimize alignParam = calibParams.getMontageLayers().toAlignParam();

        assertNotNull("Align param should not be null", alignParam);
        assertEquals("Initial sigma should be 1.6", 1.6f, alignParam.sift.initialSigma, 0.001f);
        assertEquals("Steps should be 3", 3, alignParam.sift.steps);
        assertEquals("Min octave size should be 600", 600, alignParam.sift.minOctaveSize);
        assertEquals("Max octave size should be 1200", 1200, alignParam.sift.maxOctaveSize);
    }

    @Test
    public void testCorrectDistortionConversion() {
        CorrectDistortionFromSelectionParam distortionParam =
            calibParams.getCorrectDistortion().toDistortionParam(MAX_NUM_THREADS);

        assertNotNull("CorrectDistortionFromSelectionParam should not be null", distortionParam);
        assertEquals("Dimension should be 5", 5, distortionParam.dimension);
        assertEquals("Lambda should be 0.01", 0.01f, distortionParam.lambda, 0.001f);
        assertTrue("Clear transform should be true", distortionParam.clearTransform);
    }

    @Test
    public void testAlignLayersConversions() {
        RegularizedAffineLayerAlignment.Param alignParam1 =
            calibParams.getAlignLayers().toRegularizedAffineParam(MAX_NUM_THREADS);
        RegularizedAffineLayerAlignment.Param alignParam2 =
            calibParams.getAlignLayers2().toRegularizedAffineParam(MAX_NUM_THREADS);
        RegularizedAffineLayerAlignment.Param alignParam3 =
            calibParams.getAlignLayers3().toRegularizedAffineParam(MAX_NUM_THREADS);

        assertNotNull("AlignLayers param should not be null", alignParam1);
        assertNotNull("AlignLayers2 param should not be null", alignParam2);
        assertNotNull("AlignLayers3 param should not be null", alignParam3);

        assertFalse("AlignLayers regularize should be false", alignParam1.regularize);
        assertTrue("AlignLayers2 regularize should be true", alignParam2.regularize);
        assertTrue("AlignLayers3 regularize should be true", alignParam3.regularize);
    }

    @Test(expected = IOException.class)
    public void testLoadNonExistentResource() throws IOException {
        CalibrationParams.loadFromResource("/nonexistent-calibration-params.json");
    }
}