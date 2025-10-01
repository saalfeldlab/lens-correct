/**
 * License: GPL
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License 2
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.janelia.saalfeldlab.confocallens;

import java.awt.Rectangle;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Supplier;

import org.imagearchive.lsm.reader.Reader;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.io.Opener;
import ij.plugin.ZProjector;
import ij.process.Blitter;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ini.trakem2.imaging.filters.NormalizeLocalContrast;
import ini.trakem2.utils.M;
import mpicbg.ij.FeatureTransform;
import mpicbg.ij.SIFT;
import mpicbg.imagefeatures.Feature;
import mpicbg.imagefeatures.FloatArray2DSIFT;
import mpicbg.models.CoordinateTransformMesh;
import mpicbg.models.Model;
import mpicbg.models.NotEnoughDataPointsException;
import mpicbg.models.PointMatch;
import mpicbg.trakem2.transform.CoordinateTransform;
import mpicbg.trakem2.transform.CoordinateTransformList;
import mpicbg.trakem2.transform.TransformMesh;
import mpicbg.trakem2.transform.TransformMeshMappingWithMasks;
import mpicbg.trakem2.transform.TranslationModel2D;
import picocli.CommandLine;
import picocli.CommandLine.Option;

/**
 * Apply a 2D transformation to all slices of a stack that is either lsm or tif.
 * Save as tif.
 *
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 */
public class GuptaScopeApply implements Callable<Integer> {

	@Option(names = {"-i", "--input" }, required = true, description = "input path, e.g. /home/input.tif")
	private String inputPath = null;

	@Option(names = {"-o", "--output" }, required = true, description = "output path, e.g. /nrs/flyem/data/tmp/aligned.tif")
	private String outputPath = null;

	@Option(names = {"-t", "--transform" }, required = true, description = "transform JSON path, e.g. /nrs/flyem/data/tmp/lenses.json")
	private String transformPath = null;

	@Option(names = {"-r", "--numTriangles" }, required = false, description = "number of triangles per image width, e.g. 128")
	private int meshResolution = 128;

	static public class Calibration {

		public CoordinateTransform[] transform;
		public String name;
	}

	private static final Gson gson = new GsonBuilder()
			.setPrettyPrinting()
			.disableHtmlEscaping()
			.registerTypeAdapter(CoordinateTransform.class, new TransformationAdapter())
			.create();

	final static public ImagePlus openImagePlus(
			final String path) {
		final ImagePlus imp;
		if (path.endsWith(".tif"))
			imp = new Opener().openImage(path);
		else if (path.endsWith(".lsm"))
			imp = new Reader().open(path);
		else
			imp = null;

		return imp;
	}

	final static public ImageStack createTransformedStack(
			final ImageStack srcStack,
			final CoordinateTransform t,
			final int cropWidth,
			final int meshResolution) {
		final TransformMesh mesh = new TransformMesh(t, meshResolution, srcStack.getWidth(), srcStack.getHeight());
		final Rectangle bounds = mesh.getBoundingBox();
		final ImageStack stack = new ImageStack(bounds.width - 2 * cropWidth, bounds.height - 2 * cropWidth);
		final TransformMeshMappingWithMasks<TransformMesh> mapping = new TransformMeshMappingWithMasks<TransformMesh>(mesh);
		for (int i = 0; i < srcStack.getSize(); ++i) {
			final ImageProcessor src = srcStack.getProcessor(i + 1);
			src.setInterpolationMethod(ImageProcessor.BILINEAR);
			final ImageProcessor dst = src.createProcessor(bounds.width, bounds.height);
			mapping.mapInterpolated(src, dst);
			final ImageProcessor cropped = dst.createProcessor(bounds.width - 2 * cropWidth, bounds.height - 2 * cropWidth);
			cropped.copyBits(dst, -cropWidth, -cropWidth, Blitter.COPY);

			stack.addSlice(cropped);
		}
		return stack;
	}

	final static public ImageStack createTransformedStack(
			final ImageStack srcStack,
			final CoordinateTransform t,
			final int cropWidth) {
		return createTransformedStack(srcStack, t, cropWidth, 128);
	}



	/**
	 * Load an {@link ImagePlus}, transform all its slices with a
	 * {@link CoordinateTransform}, and crop its borders.
	 *
	 * @param dirStr
	 * @param fileName
	 * @param t
	 * @param cropWidth
	 * @return
	 */
	final static public ImagePlus loadAndTransformImagePlus(
			final String path,
			final CoordinateTransform t,
			final int cropWidth) {
		final ImagePlus imp = openImagePlus(path);
		if (imp != null) {
			imp.setStack(createTransformedStack(imp.getStack(), t, cropWidth));
			return imp;
		}
		return null;
	}

	final static public void saveTransformedImages(
			final String dirStr,
			final Iterable<String> fileNames,
			final String outDirStr,
			final CoordinateTransform t,
			final int cropWidth) {
		for (final String fileName : fileNames) {
			final ImagePlus imp = loadAndTransformImagePlus(
					dirStr + fileName,
					t,
					cropWidth);
			if (imp != null) {
				IJ.saveAsTiff(imp, outDirStr + fileName + ".tif");
			}
		}
	}

	// static public boolean setup(final String... args) {
	// 	if (args.length < 4) return false;
	// 	impInput = openImagePlus(args[0]);
	// 	if (impInput == null) return false;
	// 	pathOutput = args[1];
	// 	try {
	// 		final File f = new File(pathOutput).getParentFile();
	// 		if (f == null || !(f.mkdirs() || f.exists())) return false;
	// 		System.out.println(f);
	// 	}
	// 	catch (final Exception e) {
	// 		e.printStackTrace(System.err);;
	// 		return false;
	// 	}
	// 	final NonLinearCoordinateTransform t = new NonLinearCoordinateTransform();
	// 	t.init(args[2]);
	// 	transform = t;
	// 	crop = Integer.parseInt(args[3]);
	// 	return true;
	// }


	/**
	 * Extract features from a {@link FloatProcessor}. Also adds noise to 0 and 255
	 * pixels because I believe that this does better on saturation and background.
	 *
	 * @param ip
	 * @param maxScale
	 * @param minScale
	 * @param fdSize
	 * @return
	 */
	private static ArrayList<Feature> extractFeatures(
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

	private static ArrayList<PointMatch> matchFeatures(
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
	 * @param a
	 * @param b
	 * @param maxScale
	 * @param minScale
	 * @param fdSize
	 * @param rod
	 * @param scale
	 *
	 * @return
	 */
	private static List<PointMatch> matchSIFT(
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
	 * Match and filter two images with SIFT features and a filter model.
	 *
	 * @param a
	 * @param b
	 * @param maxScale
	 * @param minScale
	 * @param fdSize
	 * @param rod
	 * @param scale
	 * @param filter
	 *
	 * @return
	 */
	private static List<PointMatch> filterMatchSIFT(
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
	 * Align two images with SIFT features. Returns the inverse transform of mapping
	 * a into b which is, well, the forward transform for mapping b into a.
	 *
	 * @param a
	 * @param b
	 * @param maxScale
	 * @param minScale
	 * @param fdSize
	 * @param rod
	 * @param scale
	 * @param filter
	 * @param modelSupplier
	 * @param modelTransformConverter
	 * @return
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

	public static ImagePlus ZAverageProjection(final ImagePlus imp) {
		final ZProjector zp = new ZProjector(imp);
		zp.setMethod(ZProjector.AVG_METHOD);
		if (imp.isHyperStack())
		{
			zp.setStopSlice(imp.getNSlices());
			zp.doHyperStackProjection(false);
		}
		else
			zp.doProjection();
		
		ImagePlus impProjection = zp.getProjection();
		IJ.run(impProjection, "Enhance Contrast", "saturated=0.35");

		return impProjection;
	}


	@Override
	public Integer call() throws Exception {

		try (FileReader reader = new FileReader(transformPath)) {
			/* Read the transform file into a list of CoordinateTransformList */
			final List<Calibration> calibrations = gson.fromJson(reader, new TypeToken<List<Calibration>>(){});
			final ArrayList<CoordinateTransformList<CoordinateTransform>> transforms = new ArrayList<>();
			for (final Calibration calibration : calibrations) {
				CoordinateTransformList<CoordinateTransform> transform = new CoordinateTransformList<>();
				for (final CoordinateTransform t : calibration.transform)
					transform.add(t);
				transforms.add(transform);
			}
            System.out.println(gson.toJson(transforms));
			if (transforms.isEmpty()) {
				System.err.println("No transforms found in: " + transformPath);
				return 1;
			}

			/* Open the source image stack */
			final ImagePlus imp = openImagePlus(inputPath);
			if (imp == null) {
				System.err.println("Could not open image: " + inputPath);
				return 1;
			}

			/* Calculate the intersection of the bounding boxes of all transformations */
			final int width = imp.getWidth();
			final int height = imp.getHeight();
			final int nChannels = imp.getNChannels();
			final int nSlices = imp.getNSlices();
			final int nFrames = imp.getNFrames();
			final int stackSize = imp.getStackSize();
			Rectangle bounds = null;
			for (final CoordinateTransform t : transforms) {
				final TransformMesh mesh = new TransformMesh(t, meshResolution, width, height);
				Rectangle boundsT = mesh.getBoundingBox();
				if (bounds == null) bounds = boundsT;
				else bounds = bounds.intersection(boundsT);
			}
			if (bounds == null || bounds.isEmpty()) {
				System.err.println("No valid bounding box found for the transformations.");
				return 1;
			}

			/* Apply the bounding box offset to all transformations */
			final int x = bounds.x;
			final int y = bounds.y;
			final int w = bounds.width;
			final int h = bounds.height;

			TranslationModel2D offset = new TranslationModel2D();
			offset.set(-x, -y);

			final ArrayList<TransformMeshMappingWithMasks<CoordinateTransformMesh>> mappings = new ArrayList<>();
			for (final CoordinateTransformList<CoordinateTransform> t : transforms) {
				t.add(offset);
				mappings.add(new TransformMeshMappingWithMasks<>(new CoordinateTransformMesh(t, meshResolution, imp.getWidth(), imp.getHeight())));
			}

			/* Render and align average projection */
			final ImagePlus impAvg = ZAverageProjection(imp);
			ImageStack alignStack = new ImageStack(w, h);
			final ImageProcessor ipAvg = impAvg.getProcessor();
			ipAvg.setInterpolationMethod(ImageProcessor.BILINEAR);
			for (TransformMeshMappingWithMasks<CoordinateTransformMesh> m : mappings) {
				final ImageProcessor dst =  ipAvg.createProcessor(w, h);
				m.mapInterpolated(ipAvg, dst);
				alignStack.addSlice(dst);
			}
			ArrayList<CoordinateTransform> alignments = new ArrayList<>();
			alignments.add(new TranslationModel2D());
			FloatProcessor reference = alignStack.getProcessor(1).convertToFloatProcessor();
			for (int i = 2; i <= alignStack.getSize(); ++i) {
				FloatProcessor target = alignStack.getProcessor(i).convertToFloatProcessor();
				TranslationModel2D model = (TranslationModel2D)alignSIFT(
						target,
						reference,
						1.0,
						0.2,
						4,
						0.92,
						candidates -> {
							TranslationModel2D filterModel = new TranslationModel2D();
							List<PointMatch> inliers = new ArrayList<>();
							try {
								filterModel.filterRansac(candidates, inliers, 1000, 5, 0, 10, 3);
							} catch (NotEnoughDataPointsException e) {
								e.printStackTrace();
								return null;
							}
							return inliers;
						},
						TranslationModel2D::new);
				alignments.add(model);
				System.out.println("Found alignment: " + model);
			}

			for (int i = 0; i < transforms.size(); ++i) {
				final CoordinateTransformList<CoordinateTransform> t = transforms.get(i);
				t.add(alignments.get(i));
				mappings.set(i, new TransformMeshMappingWithMasks<>(new CoordinateTransformMesh(t, meshResolution, imp.getWidth(), imp.getHeight())));
			}

			
			/* Render and save the transformed slices */
			ImageStack targetStack = new ImageStack(w, h);
			for (int i = 1; i <= stackSize; ++i) {
				final ImageProcessor ip = imp.getStack().getProcessor(i);
				//ip.setInterpolationMethod(ImageProcessor.BILINEAR);
				//ip.setInterpolationMethod(ImageProcessor.NEAREST_NEIGHBOR);
				ip.setInterpolationMethod(ImageProcessor.BICUBIC);
				for (TransformMeshMappingWithMasks<CoordinateTransformMesh> m : mappings) {
					final ImageProcessor dst = ip.createProcessor(w, h);
					m.mapInterpolated(ip, dst);
					targetStack.addSlice(dst);
				}
			}
			imp.setStack(targetStack);
			imp.setDimensions(nChannels * transforms.size(), nSlices, nFrames);
			imp.setDisplayMode(IJ.GRAYSCALE);
			IJ.save(imp, outputPath);

        } catch (final IOException e) {
			System.err.println("Error reading transform file: " + e.getMessage());
			return 1;
		}

		return 0;
	}

	public static void main(final String... args) {

		final Integer exitCode = new CommandLine(new GuptaScopeApply()).execute(args);
		System.exit(exitCode);
	}
}
