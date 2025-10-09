package org.janelia.saalfeldlab.confocallens;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import org.imagearchive.lsm.reader.Reader;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.io.Opener;
import ij.process.Blitter;
import ij.process.ImageProcessor;
import mpicbg.models.CoordinateTransform;
import mpicbg.trakem2.transform.TransformMesh;
import mpicbg.trakem2.transform.TransformMeshMappingWithMasks;

/**
 * Utility functions for image loading, saving, channel splitting and
 * combining, and applying transformations.
 *
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 */
interface Util {

    /**
	 * Split a multi-channel {@link ImagePlus} into an array of
     * single-channel {@link ImagePlus}'.
     * 
     * @param imp multi-channel {@link ImagePlus}
     * @return array of single-channel {@link ImagePlus}'
	 */
	static public ImagePlus[] splitChannels(final ImagePlus imp) {

		final int nChannels = imp.getNChannels();
		final int nSlices = imp.getNSlices();
		final ImageStack stack = imp.getStack();
		final ImagePlus[] channels = new ImagePlus[nChannels];

		for (int c = 0; c < nChannels; ++c) {
			final ImageStack channelStack = new ImageStack(imp.getWidth(), imp.getHeight());
			for (int s = 0; s < nSlices; ++s)
				channelStack.addSlice(stack.getProcessor(imp.getStackIndex(c + 1, s + 1, 1)));

			final ImagePlus channelImp = new ImagePlus(imp.getTitle() + "-" + c, channelStack);
			channelImp.setCalibration(imp.getCalibration());
			channelImp.setDimensions(1, nSlices, 1);
			channels[c] = channelImp;
		}
		return channels;
	}

    /**
	 * Combine an array of single-channel {@link ImagePlus}' into a
     * multi-channel {@link ImagePlus}.
     * 
     * @param channels array of single-channel {@link ImagePlus}'
     * @return multi-channel {@link ImagePlus}
	 */
	static public ImagePlus combineChannels(final ImagePlus[] channels) {

		final ImageStack stack = new ImageStack(channels[0].getWidth(), channels[0].getHeight());
		final int nSlices = channels[0].getNSlices();

		for (int s = 0; s < nSlices; ++s)
			for (int c = 0; c < channels.length; ++c)
				stack.addSlice(channels[c].getStack().getProcessor(s + 1));

		final ImagePlus imp = new ImagePlus(
			channels[0].getTitle().replaceAll("\\.[^.]*$", "-1-" + channels.length),
			stack);
		imp.setCalibration(channels[0].getCalibration());
		imp.setDimensions(channels.length, nSlices, 1);
		return imp;
	}

    /**
     * Open an {@link ImagePlus}. Supports .tif and .lsm files.
     * 
     * @param path path to image file
     * @return opened {@link ImagePlus}, or null if the file could not be
     *     opened
     */
    static public ImagePlus openImagePlus(final String path) {

		final ImagePlus imp;
		if (path.endsWith(".tif"))
			imp = new Opener().openImage(path);
		else if (path.endsWith(".lsm"))
			imp = new Reader().open(path);
		else
			imp = null;

		return imp;
	}

    /**
	 * Open a list of {@link ImagePlus}s, that each contain one or more
     * channels as an array of single channel {@link ImagePlus}s.  Original
     * order of channels is preserved.
     * 
     * @param filePaths array of paths to image files
     * @return array of single-channel {@link ImagePlus}s
	 */
	static public ImagePlus[] openChannels(final String[] filePaths) {

		final List<ImagePlus> channels = new ArrayList<>();
		for (final String filePath : filePaths) {
			if (filePath == null || filePath.trim().isEmpty())
				continue;
			final ImagePlus imp = openImagePlus(filePath);
			if (imp != null) {
				final ImagePlus[] splitChannels = splitChannels(imp);
				for (final ImagePlus channel : splitChannels)
					channels.add(channel);
			}
		}
		return channels.toArray(new ImagePlus[0]);
	}

    /**
     * Create a transformed and cropped copy of an {@link ImageStack}.
     * 
     * @param srcStack source {@link ImageStack}
     * @param t {@link CoordinateTransform} to apply
     * @param cropWidth width of border to crop
     * @param meshResolution number of triangles per image width
     * @return transformed and cropped {@link ImageStack}
     */
    static public ImageStack createTransformedStack(
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

    /**
     * Create a transformed and cropped copy of an {@link ImageStack}.
     * Uses a default mesh resolution of 128 triangles per image width.
     * 
     * @param srcStack source {@link ImageStack}
     * @param t {@link CoordinateTransform} to apply
     * @param cropWidth width of border to crop
     * @return transformed and cropped {@link ImageStack}
     */
	static public ImageStack createTransformedStack(
			ImageStack srcStack,
			CoordinateTransform t,
			int cropWidth) {
        
		return createTransformedStack(srcStack, t, cropWidth, 128);
	}

    /**
	 * Load an {@link ImagePlus}, transform all its slices with a
	 * {@link CoordinateTransform}, and crop its borders.
	 *
	 * @param path path to image file
	 * @param t {@link CoordinateTransform} to apply
	 * @param cropWidth width of border to crop
	 * @return transformed and cropped {@link ImagePlus}, or null if the file
	 *     could not be opened
	 */
	static public ImagePlus loadAndTransformImagePlus(
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

	/**
	 * Load a list of {@link ImagePlus}es, transform all their slices with a
	 * {@link CoordinateTransform}, and crop their borders.  Save the results
	 * as .tif files in the specified output directory.
	 * 
	 * @param dirStr 
	 * @param fileNames
	 * @param outDirStr
	 * @param t
	 * @param cropWidth
	 */
	static public void saveTransformedImages(
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
}
