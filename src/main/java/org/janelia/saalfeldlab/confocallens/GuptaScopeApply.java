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
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.TransformationAdapter;
import org.imagearchive.lsm.reader.Reader;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.io.Opener;
import ij.process.Blitter;
import ij.process.ImageProcessor;
import mpicbg.ij.Mapping;
import mpicbg.trakem2.transform.CoordinateTransform;
import mpicbg.trakem2.transform.CoordinateTransformList;
import mpicbg.trakem2.transform.TransformMesh;
import mpicbg.trakem2.transform.TransformMeshMappingWithMasks;
import mpicbg.trakem2.transform.TranslationModel2D;
import net.imglib2.realtransform.Translation2D;
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

	static public record Calibration(
		CoordinateTransform[] transform,
		String name) {}

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


	@Override
	public Integer call() throws IOException {

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
			int width = imp.getWidth();
			int height = imp.getHeight();
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

			final ArrayList<TransformMeshMappingWithMasks<TransformMesh>> mappings = new ArrayList<>();
			for (final CoordinateTransformList<CoordinateTransform> t : transforms) {
				t.add(offset);
				mappings.add(new TransformMeshMappingWithMasks<>(new TransformMesh(t, meshResolution, w, h)));
			}

			/* Render and save the transformed slices */
			ImageStack targetStack = new ImageStack(w, h);
			for (int i = 1; i <= imp.getStackSize(); ++i) {
				final ImageProcessor ip = imp.getStack().getProcessor(i);
				ip.setInterpolationMethod(ImageProcessor.BILINEAR);
				for (TransformMeshMappingWithMasks<TransformMesh> m : mappings) {
					final ImageProcessor dst = ip.createProcessor(w, h);
					m.mapInterpolated(ip, dst);
					targetStack.addSlice(dst);
				}
			}
			imp.setStack(targetStack);
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
