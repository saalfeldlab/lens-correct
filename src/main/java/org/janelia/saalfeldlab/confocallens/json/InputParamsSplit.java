package org.janelia.saalfeldlab.confocallens.json;

import java.util.List;

/**
 * Input configuration parameters for split/beam-splitter calibration
 * Extends InputParams with masks for split regions
 */
public class InputParamsSplit extends InputParams {
    private List<String> masks;

    public List<String> getMasks() {
        return masks;
    }
}