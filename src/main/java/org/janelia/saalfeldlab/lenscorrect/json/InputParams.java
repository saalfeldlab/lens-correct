package org.janelia.saalfeldlab.lenscorrect.json;

import java.util.List;

/**
 * Base input configuration parameters
 */
public class InputParams {
    private List<String> patterns;
    private List<String> labels;

    public List<String> getPatterns() {
        return patterns;
    }

    public List<String> getLabels() {
        return labels;
    }
}