package org.janelia.saalfeldlab.lenscorrect.json;

import lenscorrection.DistortionCorrectionTask.CorrectDistortionFromSelectionParam;

/**
 * Correct distortion configuration parameters
 */
public class CorrectDistortionParams {
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
    private boolean multipleHypotheses;
    private boolean rejectIdentity;
    private double identityTolerance;
    private boolean tilesAreInPlace;
    private int desiredModelIndex;
    private boolean regularize;
    private int regularizerIndex;
    private double lambdaRegularize;
    private int maxIterationsOptimize;
    private int maxPlateauwidthOptimize;
    private int dimension;
    private double lambda;
    private boolean clearTransform;

    /**
     * Convert to CorrectDistortionFromSelectionParam for use with lens correction
     */
    public CorrectDistortionFromSelectionParam toDistortionParam(int maxNumThreads) {
        CorrectDistortionFromSelectionParam p = new CorrectDistortionFromSelectionParam();
        p.sift.initialSigma = (float) initialSigma;
        p.sift.steps = steps;
        p.sift.minOctaveSize = minOctaveSize;
        p.sift.maxOctaveSize = maxOctaveSize;
        p.sift.fdSize = fdSize;
        p.sift.fdBins = fdBins;
        p.rod = (float) rod;
        p.maxNumThreadsSift = maxNumThreads;

        p.maxEpsilon = (float) maxEpsilon;
        p.minInlierRatio = (float) minInlierRatio;
        p.minNumInliers = minNumInliers;
        p.expectedModelIndex = expectedModelIndex;
        p.multipleHypotheses = multipleHypotheses;
        p.rejectIdentity = rejectIdentity;
        p.identityTolerance = (float) identityTolerance;
        p.tilesAreInPlace = tilesAreInPlace;

        p.desiredModelIndex = desiredModelIndex;
        p.regularize = regularize;
        p.regularizerIndex = regularizerIndex;
        p.lambdaRegularize = (float) lambdaRegularize;
        p.maxIterationsOptimize = maxIterationsOptimize;
        p.maxPlateauwidthOptimize = maxPlateauwidthOptimize;

        p.dimension = dimension;
        p.lambda = (float) lambda;
        p.clearTransform = clearTransform;
        p.visualize = false;

        return p;
    }
}