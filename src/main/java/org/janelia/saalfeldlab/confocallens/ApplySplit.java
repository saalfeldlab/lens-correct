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

import org.janelia.saalfeldlab.confocallens.json.Calibration;
import org.janelia.saalfeldlab.confocallens.json.TransformationAdapter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import mpicbg.models.CoordinateTransformMesh;
import mpicbg.models.NotEnoughDataPointsException;
import mpicbg.models.PointMatch;
import mpicbg.trakem2.transform.CoordinateTransform;
import mpicbg.trakem2.transform.CoordinateTransformList;
import mpicbg.trakem2.transform.TransformMesh;
import mpicbg.trakem2.transform.TransformMeshMappingWithMasks;
import mpicbg.trakem2.transform.TranslationModel2D;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Apply a 2D transformation to all slices of a stack that is either lsm or tif.
 *
 * Each slice contains multiple channels, imaged simultaneously, either with a
 * beam splitter or a micro-lens array.  The calibration transforms unwarp and
 * align all channels.
 *
 * The output image size will be the intersection of the bounding boxes of all
 * channels.  It will be composite image including all channels that is saved as
 * a multi-channel tif.
 *
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 */
@Command(
    name = "apply-split",
    mixinStandardHelpOptions = true,
    description = "Apply lens-distortion correction and alignment to stacks of split-images or lens arrays"
)
public class ApplySplit implements Callable<Integer> {

	@Option(names = {"-i", "--input" }, required = true, description = "input path, e.g. /home/input.tif")
	private String inputPath = null;

	@Option(names = {"-o", "--output" }, required = true, description = "output path, e.g. /nrs/flyem/data/tmp/aligned.tif")
	private String outputPath = null;

	@Option(names = {"-t", "--transform" }, required = true, description = "transform JSON path, e.g. /nrs/flyem/data/tmp/lenses.json")
	private String transformPath = null;

	@Option(names = {"-r", "--numTriangles" }, required = false, description = "number of triangles per image width, e.g. 128")
	private int meshResolution = 128;

	@Option(names = {"-a", "--align" }, required = false, description = "attempt an affine alignment of the split image channels to the first channel")
	private boolean align = false;


	private static final Gson gson = new GsonBuilder()
			.setPrettyPrinting()
			.disableHtmlEscaping()
			.registerTypeAdapter(CoordinateTransform.class, new TransformationAdapter())
			.create();


	@Override
	public Integer call() throws Exception {

		/* Read the transform file into a list of CoordinateTransformList */
		final List<Calibration> calibrations;
		final ArrayList<CoordinateTransformList<CoordinateTransform>> transforms;
		try (FileReader reader = new FileReader(transformPath)) {
			calibrations = gson.fromJson(reader, new TypeToken<List<Calibration>>(){});
			transforms = new ArrayList<>();
			for (final Calibration calibration : calibrations) {
				CoordinateTransformList<CoordinateTransform> transform = new CoordinateTransformList<>();
				for (final CoordinateTransform t : calibration.transform())
					transform.add(t);
				transforms.add(transform);
			}
			System.out.println(gson.toJson(transforms));
			if (transforms.isEmpty()) {
				System.err.println("No transforms found in: " + transformPath);
				return 1;
			}
		} catch (final IOException e) {
			System.err.println("Error reading transform file: " + e.getMessage());
			return 1;
		}

		/* Open the source image stack */
		final ImagePlus imp = Util.openImagePlus(inputPath);
		if (imp == null) {
			System.err.println("Could not open image: " + inputPath);
			return 1;
		}

		/* calculate the intersection of the bounding boxes of all transformations */
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

		/* apply the bounding box offset to all transformations */
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

		/* render and align average projection */
		if (align) {
			final ImagePlus impAvg = Align.ZAverageProjection(imp);
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
				TranslationModel2D model = (TranslationModel2D)Align.alignSIFT(
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
		}

		/* Render and save the transformed slices */
		ImageStack targetStack = new ImageStack(w, h);
		for (int i = 1; i <= stackSize; ++i) {
			final ImageProcessor ip = imp.getStack().getProcessor(i);
			ip.setInterpolationMethod(ImageProcessor.BILINEAR);
			//ip.setInterpolationMethod(ImageProcessor.NEAREST_NEIGHBOR);
			//ip.setInterpolationMethod(ImageProcessor.BICUBIC);
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

		return 0;
	}

	public static void main(final String... args) {

		final Integer exitCode = new CommandLine(new ApplySplit()).execute(args);
		System.exit(exitCode);
	}
}
