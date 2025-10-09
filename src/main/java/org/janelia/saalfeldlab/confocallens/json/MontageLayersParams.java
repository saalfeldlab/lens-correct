package org.janelia.saalfeldlab.confocallens.json;

import mpicbg.trakem2.align.Align;

/**
 * Montage layers configuration parameters for SIFT alignment
 */
public class MontageLayersParams {
    private double initialSigma;
    private int steps;
    private int minOctaveSize;
    private int maxOctaveSize;
    private int fdSize;
    private int fdBins;
    private double rod;
    private double maxEpsilon;
    private double minInlierRatio;
    private int minNumInliers;
    private int expectedModelIndex;
    private boolean rejectIdentity;
    private double identityTolerance;
    private int desiredModelIndex;
    private double correspondenceWeight;
    private boolean regularize;
    private int maxIterations;
    private int maxPlateauwidth;
    private boolean filterOutliers;
    private double meanFactor;

    /**
     * Convert to Align.ParamOptimize for use with TrakEM2
     */
    public Align.ParamOptimize toAlignParam() {
        Align.ParamOptimize param = new Align.ParamOptimize();
        param.sift.initialSigma = (float) initialSigma;
        param.sift.steps = steps;
        param.sift.minOctaveSize = minOctaveSize;
        param.sift.maxOctaveSize = maxOctaveSize;
        param.sift.fdSize = fdSize;
        param.sift.fdBins = fdBins;
        param.rod = (float) rod;
        param.maxEpsilon = (float) maxEpsilon;
        param.minInlierRatio = (float) minInlierRatio;
        param.minNumInliers = minNumInliers;
        param.expectedModelIndex = expectedModelIndex;
        param.rejectIdentity = rejectIdentity;
        param.identityTolerance = (float) identityTolerance;
        param.desiredModelIndex = desiredModelIndex;
        param.correspondenceWeight = (float) correspondenceWeight;
        param.regularize = regularize;
        param.maxIterations = maxIterations;
        param.maxPlateauwidth = maxPlateauwidth;
        param.filterOutliers = filterOutliers;
        param.meanFactor = (float) meanFactor;
        return param;
    }
}