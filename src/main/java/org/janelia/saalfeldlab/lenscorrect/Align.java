package org.janelia.saalfeldlab.lenscorrect;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import ij.ImagePlus;
import ij.plugin.ContrastEnhancer;
import ij.plugin.ZProjector;
import ij.process.FloatProcessor;
import ini.trakem2.imaging.filters.NormalizeLocalContrast;
import mpicbg.ij.FeatureTransform;
import mpicbg.ij.SIFT;
import mpicbg.imagefeatures.Feature;
import mpicbg.imagefeatures.FloatArray2DSIFT;
import mpicbg.models.Model;
import mpicbg.models.PointMatch;

interface Align {

    /**
	 * Extract features from a {@link FloatProcessor}. Also adds noise to 0 and 255
	 * pixels because I believe that this does better on saturation and background.
	 *
	 * @param ip input {@link FloatProcessor}
	 * @param maxScale max scale factor for feature extraction
	 * @param minScale min scale factor for feature extraction
	 * @param fdSize feature descriptor size (4 or 8 recommended)
     * 
	 * @return list of extracted features
	 */
	public static ArrayList<Feature> extractFeatures(
			FloatProcessor ip,
			final double maxScale,
			final double minScale,
			final int fdSize) {

		final FloatArray2DSIFT.Param p = new FloatArray2DSIFT.Param();

		p.maxOctaveSize = (int) Math.round(Math.max(ip.getWidth(), ip.getHeight()) * maxScale);
		p.minOctaveSize = (int) (Math.min(ip.getWidth(), ip.getHeight()) * minScale);

		ip = addNoise(ip);

		final FloatArray2DSIFT sift = new FloatArray2DSIFT(p);
		final SIFT ijSIFT = new SIFT(sift);

		final ArrayList<Feature> fs = new ArrayList<Feature>();
		ijSIFT.extractFeatures(ip, fs);

		return fs;
	}

	/**
	 * Add noise to an image.
     * 
     * @param ip input {@link FloatProcessor}
     * 
     * @return {@link FloatProcessor} with noise added
     */
    public static FloatProcessor addNoise(final FloatProcessor ip) {

		// final ValueToNoise filter1 = new ValueToNoise(0, 0, 255);
		// final ValueToNoise filter2 = new ValueToNoise(255, 0, 255);
		final NormalizeLocalContrast filter3 = new NormalizeLocalContrast(100, 100, 3, true, true);

		// FloatProcessor fp = filter1.process(ip).convertToFloatProcessor();
		// fp = filter2.process(fp).convertToFloatProcessor();
		// fp.setMinAndMax(0, 255);
		FloatProcessor fp = filter3.process(ip).convertToFloatProcessor();

		return fp;
	}

    /**
     * Match features between two lists.
     *
     * @param fs1 list of features from image 1
     * @param fs2 list of features from image 2
     * @param rod ratio of distances between best and second best match
     *      threshold for feature matching (0.92)
     * 
     * @return list of matched {@link PointMatch}es
     */
	public static ArrayList<PointMatch> matchFeatures(
			final List<Feature> fs1,
			final List<Feature> fs2,
			final double rod) {

		final ArrayList<PointMatch> candidates = new ArrayList<>();
		FeatureTransform.matchFeatures(fs1, fs2, candidates, (float) rod);
		return candidates;
	}

	/**
	 * Match two images with SIFT features.
	 *
	 * @param a input {@link FloatProcessor} for image 1
	 * @param b input {@link FloatProcessor} for image 2
	 * @param maxScale max scale factor for feature extraction
	 * @param minScale min scale factor for feature extraction
	 * @param fdSize feature descriptor size (4 or 8 recommended)
	 * @param rod ratio of distances between best and second best match
     *      threshold for feature matching (0.92)
	 *
	 * @return list of candidate {@link PointMatch}es
	 */
	public static List<PointMatch> matchSIFT(
			final FloatProcessor a,
			final FloatProcessor b,
			final double maxScale,
			final double minScale,
			final int fdSize,
			final double rod) {

		final List<Feature> fs1 = extractFeatures(a, maxScale, minScale, fdSize);
		final List<Feature> fs2 = extractFeatures(b, maxScale, minScale, fdSize);
		
		System.out.printf("%d and %d features extracted.", fs1.size(), fs2.size());
		System.out.println();

		final List<PointMatch> candidates = matchFeatures(fs1, fs2, rod);

		return candidates;
	}

	/**
	 * Match and filter two images with SIFT features and a filter model.  The
     * filter model is the forward transformation of coordinates from image a
     * to b.
	 *
	 * @param a input {@link FloatProcessor} for image 1
	 * @param b input {@link FloatProcessor} for image 2
	 * @param maxScale max scale factor for feature extraction
	 * @param minScale min scale factor for feature extraction
	 * @param fdSize feature descriptor size (4 or 8 recommended)
	 * @param rod ratio of distances between best and second best match
     *      threshold for feature matching (0.92)
	 * @param filter function that filters candidate matches with a model
	 *
	 * @return list of filtered {@link PointMatch}es
	 */
	public static List<PointMatch> filterMatchSIFT(
			final FloatProcessor a,
			final FloatProcessor b,
			final double maxScale,
			final double minScale,
			final int fdSize,
			final double rod,
			final Function<List<PointMatch>, List<PointMatch>> filter) {

		final List<PointMatch> candidates = matchSIFT(a, b, maxScale, minScale, fdSize, rod);
		final int numCandidates = candidates.size(); // size of candidates is changed below

		final List<PointMatch> matches = filter.apply(candidates);

		System.out.printf("%d of %d matches found.", matches.size(), numCandidates);
		System.out.println();

		return matches;
	}

	/**
	 * Align two images with SIFT features.  The model is the forward
     * transformation of coordinates from image a to b.
	 *
	 * @param a input {@link FloatProcessor} for image 1
	 * @param b input {@link FloatProcessor} for image 2
	 * @param maxScale max scale factor for feature extraction
	 * @param minScale min scale factor for feature extraction
	 * @param fdSize feature descriptor size (4 or 8 recommended)
	 * @param rod ratio of distances between best and second best match
     *      threshold for feature matching (0.92)
	 * @param filter function that filters candidate matches with a model
	 * @param modelSupplier supplier for the model to fit
     * 
	 * @return fitted model
	 */
	static public <M extends Model<M>> M alignSIFT(
			final FloatProcessor a,
			final FloatProcessor b,
			final double maxScale,
			final double minScale,
			final int fdSize,
			final double rod,
			final Function<List<PointMatch>, List<PointMatch>> filter,
			final Supplier<M> modelSupplier) throws Exception {

		final List<PointMatch> matches = filterMatchSIFT(a, b, maxScale, minScale, fdSize, rod, filter);
		if (matches == null) {
			throw new Exception("No matches found");
		}

		final M model = modelSupplier.get();
		model.fit(matches);

		return model;
	}

	/**
     * Z-average projection of a stack.
     * 
     * @param imp input {@link ImagePlus}
     * 
     * @return Z-average projection {@link ImagePlus}
     */
    public static ImagePlus ZAverageProjection(final ImagePlus imp) {

		final ZProjector zp = new ZProjector(imp);
		zp.setMethod(ZProjector.AVG_METHOD);
		if (imp.isHyperStack()) {
			zp.setStopSlice(imp.getNSlices());
			zp.doHyperStackProjection(false);
		}
		else
			zp.doProjection();
		
		ImagePlus impProjection = zp.getProjection();

		new ContrastEnhancer().stretchHistogram(impProjection, 0.35);
		
		return impProjection;
	}
}