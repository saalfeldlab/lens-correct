package org.janelia.saalfeldlab.confocallens.json;

import mpicbg.trakem2.align.RegularizedAffineLayerAlignment;

/**
 * Align layers configuration parameters for regularized affine layer alignment
 */
public class AlignLayersParams {
    private int SIFTfdBins;
    private int SIFTfdSize;
    private double SIFTinitialSigma;
    private int SIFTminOctaveSize;
    private int SIFTmaxOctaveSize;
    private int SIFTsteps;
    private boolean clearCache;
    private double rod;
    private int desiredModelIndex;
    private int expectedModelIndex;
    private double identityTolerance;
    private double lambda;
    private double maxEpsilon;
    private int maxIterationsOptimize;
    private int maxNumFailures;
    private int maxNumNeighbors;
    private int maxPlateauwidthOptimize;
    private double minInlierRatio;
    private int minNumInliers;
    private boolean multipleHypotheses;
    private boolean widestSetOnly;
    private boolean regularize;
    private int regularizerIndex;
    private boolean rejectIdentity;

    /**
     * Convert to RegularizedAffineLayerAlignment.Param for use with TrakEM2
     */
    public RegularizedAffineLayerAlignment.Param toRegularizedAffineParam(int maxNumThreads) {
        return new RegularizedAffineLayerAlignment.Param(
            SIFTfdBins,
            SIFTfdSize,
            (float) SIFTinitialSigma,
            SIFTmaxOctaveSize,
            SIFTminOctaveSize,
            SIFTsteps,
            clearCache,
            maxNumThreads,
            (float) rod,
            desiredModelIndex,
            expectedModelIndex,
            (float) identityTolerance,
            (float) lambda,
            (float) maxEpsilon,
            maxIterationsOptimize,
            maxNumFailures,
            maxNumNeighbors,
            maxNumThreads,
            maxPlateauwidthOptimize,
            (float) minInlierRatio,
            minNumInliers,
            multipleHypotheses,
            widestSetOnly,
            regularize,
            regularizerIndex,
            rejectIdentity,
            false // visualize
        );
    }
}