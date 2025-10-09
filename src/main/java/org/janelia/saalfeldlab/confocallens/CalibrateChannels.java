package org.janelia.saalfeldlab.confocallens;

import java.awt.Color;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.image.IndexColorModel;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.janelia.saalfeldlab.confocallens.json.CalibrationParams;
import org.janelia.saalfeldlab.confocallens.json.InputParams;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import ij.ImagePlus;
import ij.ImageStack;
import ij.io.FileSaver;
import ij.plugin.HyperStackConverter;
import ij.plugin.ZProjector;
import ij.process.Blitter;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageConverter;
import ij.process.ImageProcessor;
import ij.process.LUT;
import ini.trakem2.ControlWindow;
import ini.trakem2.Project;
import ini.trakem2.display.Display;
import ini.trakem2.display.Displayable;
import ini.trakem2.display.Layer;
import ini.trakem2.display.LayerSet;
import ini.trakem2.display.Patch;
import lenscorrection.DistortionCorrectionTask;
import lenscorrection.DistortionCorrectionTask.CorrectDistortionFromSelectionParam;
import loci.plugins.BF;
import mpicbg.ij.plugin.NormalizeLocalContrast;
import mpicbg.models.AffineModel2D;
import mpicbg.models.CoordinateTransformList;
import mpicbg.models.IdentityModel;
import mpicbg.models.IllDefinedDataPointsException;
import mpicbg.models.Model;
import mpicbg.models.NotEnoughDataPointsException;
import mpicbg.models.Point;
import mpicbg.models.PointMatch;
import mpicbg.models.RigidModel2D;
import mpicbg.trakem2.align.Align;
import mpicbg.trakem2.align.AlignTask;
import mpicbg.trakem2.align.RegularizedAffineLayerAlignment;
import mpicbg.trakem2.transform.CoordinateTransform;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
    name = "automation",
    mixinStandardHelpOptions = true,
    description = "Automated lens-calibration for multiple channels"
)
public class CalibrateChannels implements java.util.concurrent.Callable<Integer> {

	@Option(names = {"-i", "--input"}, required = true, description = "input directory")
	private String inputDir;

	@Option(names = {"-o", "--output"}, required = true, description = "output directory")
	private String outputDir;

	@Option(names = {"-p", "--calibration-params"}, description = "path to calibration parameters file (default: built-in calibration-params.json)")
	private String calibrationParamsPath;

	@Option(names = {"-j", "--input-params"}, required = true, description = "path to input parameters file containing patterns and labels")
	private String inputParamsPath;

	@Option(names = {"-n", "--name"}, required = true, description = "project name for output files and transformation labels")
	private String projectName;

	static Class<?> invarianceModelClass = IdentityModel.class;

	static ImageProcessor visualizeDifference(
			final int w,
			final int h,
			final int pWidth,
			final int pHeight,
			final CoordinateTransform ct1,
			final CoordinateTransform ct2) {
		final double sx = (double)pWidth / w;
		final double sy = (double)pHeight / h;
		final FloatProcessor ip = new FloatProcessor(w, h);
		for (int y = 0; y < h; ++y) {
			for (int x = 0; x < w; ++x) {
				final double[] l1 = new double[]{x * sx, y * sy};
				final double[] l2 = new double[]{x * sx, y * sy};
				ct1.applyInPlace(l1);
				ct2.applyInPlace(l2);
				final double dx = l1[0] - l2[0];
				final double dy = l1[1] - l2[1];
				final double d = Math.sqrt(dx * dx + dy * dy);
				ip.setf(x, y, (float)d);
			}
		}
		//matrix.copyBits( ip, i * 256, j * 256, Blitter.COPY );
		return ip;
	}

	static ImageProcessor visualizeDifferenceVectorDistribution(
			final int w,
			final int h,
			final int pWidth,
			final int pHeight,
			final CoordinateTransformList<?> ct1,
			final CoordinateTransformList<?> ct2,
			final double max) {
		final double hw = 0.5 * w;
		final double hh = 0.5 * h;
		final double sx = (double)pWidth / w;
		final double sy = (double)pHeight / h;
		final FloatProcessor ip = new FloatProcessor(w, h);
		for (int y = 0; y < h; ++y) {
			for (int x = 0; x < w; ++x) {
				final double[] l1 = new double[]{x * sx, y * sy};
				final double[] l2 = new double[]{x * sx, y * sy};
				ct1.applyInPlace(l1);
				ct2.applyInPlace(l2);
				double dx = l1[0] - l2[0];
				double dy = l1[1] - l2[1];
				dx = Math.min(w - 1, Math.max(0.0, (dx / max + 1) * hw));
				dy = Math.min(w - 1, Math.max(0.0, (dy / max + 1) * hh));
				final int ix = (int)Math.round(dx);
				final int iy = (int)Math.round(dy);
				ip.setf(ix, iy, (float)(1.0 + ip.getPixelValue(ix, iy)));
			}
		}
		//matrix.copyBits( ip, i * 256, j * 256, Blitter.COPY );
		return ip;
	}

	static ImageProcessor visualizeDifferenceVectors(
			final int w,
			final int h,
			final int pWidth,
			final int pHeight,
			final CoordinateTransformList<?> ct1,
			final CoordinateTransformList<?> ct2,
			final double max) {
		final double sx = (double)pWidth / w;
		final double sy = (double)pHeight / h;
		final ColorProcessor ip = new ColorProcessor(w, h);
		for (int y = 0; y < h; ++y) {
			for (int x = 0; x < w; ++x) {
				final double[] l1 = new double[]{x * sx, y * sy};
				final double[] l2 = new double[]{x * sx, y * sy};
				ct1.applyInPlace(l1);
				ct2.applyInPlace(l2);
				double dx = (l1[0] - l2[0] ) / max;
				double dy = (l1[1] - l2[1] ) / max;
				final double d = Math.sqrt(dx * dx + dy * dy);
				final double s = 1.0 / d;
				if (s < 1.0) {
					dx *= s;
					dy *= s;
				}

				ip.set(x, y, mpicbg.ij.util.Util.colorVector(dx, dy));
			}
		}
		return ip;
	}

	static Model<?> sampleModel(final CoordinateTransform ct, final Class<?> modelClass, final int width, final int height) throws NotEnoughDataPointsException, IllDefinedDataPointsException, InstantiationException, IllegalAccessException {
		final Model<?> model = (Model<?>) modelClass.newInstance();
		final ArrayList<PointMatch> matches = new ArrayList<PointMatch>();
		final double scaleX = ((double)width - 1.0f) / 63.0f;
		final double scaleY = ((double)height - 1.0f) / 63.0f;
		for (int y = 0; y < 64; ++y) {
			final double ys = scaleY * y;
			for (int x = 0; x < 64; ++x) {
				final double xs = scaleX * x;
				final Point p = new Point(new double[]{xs, ys});
				p.apply(ct);
				matches.add(new PointMatch(p, p));
			}
		}
		model.fit(matches);
		return model;
	}

	/**
	 * Estimate a transformation model between two CoordinateTransforms.
	 * The model maps ct1 into ct2.
	 * @throws IllDefinedDataPointsException
	 * @throws NotEnoughDataPointsException
	 * @throws IllegalAccessException
	 * @throws InstantiationException
	 */
	static Model<?> sampleModel2(
			final CoordinateTransformList<?> ct1,
			final CoordinateTransformList<?> ct2,
			final Class<?> modelClass,
			final int width,
			final int height) throws NotEnoughDataPointsException, IllDefinedDataPointsException, InstantiationException, IllegalAccessException {
		final Model<?> model = (Model<?>) modelClass.newInstance();
		final ArrayList<PointMatch> matches = new ArrayList<PointMatch>();
		final double scaleX = ((double)width - 1.0f) / 63.0f;
		final double scaleY = ((double)height - 1.0f) / 63.0f;
		for (int y = 0; y < 64; ++y) {
			final double ys = scaleY * y;
			for (int x = 0; x < 64; ++x) {
				final double xs = scaleX * x;
				final Point p = new Point(new double[]{xs, ys});
				final Point q = new Point(new double[]{xs, ys});
				ct1.applyInPlace(p.getL());
				q.apply(ct2);
				matches.add(new PointMatch(p, q));
			}
		}
		model.fit(matches);
		return model;
	}


	static CoordinateTransform createTransform(
			final String className,
			final String dataString) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
		final CoordinateTransform ct = (CoordinateTransform)Class.forName(className).newInstance();
		ct.init(dataString);
		return ct;
	}

	static CoordinateTransformList<mpicbg.models.CoordinateTransform> createTransformList(final int j, final String[][] transforms) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
		final CoordinateTransformList<mpicbg.models.CoordinateTransform> ctl = new CoordinateTransformList<mpicbg.models.CoordinateTransform>();
		for (int k = 2; k < transforms[j].length; k +=2)
			ctl.add(createTransform(transforms[j][k-1], transforms[j][k]));
		return ctl;
	}

	static ImagePlus showDifferenceVectors(final String[][] transforms, final int pWidth, final int pHeight, final int w, final int h, final int xSkip, final int ySkip, final double max) throws InstantiationException, IllegalAccessException, NotEnoughDataPointsException, IllDefinedDataPointsException, ClassNotFoundException {
		final ColorProcessor table = new ColorProcessor(
			(w + xSkip) * transforms.length - xSkip,
			(h + ySkip) * transforms.length - ySkip);
		final ImagePlus impTable = new ImagePlus("Matrix", table);
		//impTable.show();

		for (int i = 0; i < transforms.length; ++i) {
			final CoordinateTransformList<mpicbg.models.CoordinateTransform> ct1 = createTransformList(i, transforms);
			for (int j = 0; j < transforms.length; ++j) {
				final CoordinateTransformList<mpicbg.models.CoordinateTransform> ct2 = createTransformList(j, transforms);

				/* fit a simple linear model to compare with using some transferred samples */
				final mpicbg.models.CoordinateTransform t = (mpicbg.models.CoordinateTransform)sampleModel2(ct2, ct1, invarianceModelClass, pWidth, pHeight);
				ct2.add(t);

				final ImageProcessor ip = visualizeDifferenceVectors(
					w,
					h,
					pWidth,
					pHeight,
					ct1,
					ct2,
					max);

				table.copyBits(ip, (w + xSkip) * i, (h + ySkip) * j, Blitter.COPY);
				impTable.updateAndDraw();
			}
		}
		return impTable;
	}


	static ImagePlus showDifferenceVectorDistributions(final String[][] transforms, final int pWidth, final int pHeight, final int w, final int h, final int xSkip, final int ySkip, final double max) throws InstantiationException, IllegalAccessException, NotEnoughDataPointsException, IllDefinedDataPointsException, ClassNotFoundException {
		final FloatProcessor table = new FloatProcessor(
			(w + xSkip) * transforms.length - xSkip,
			(h + ySkip) * transforms.length - ySkip);
		final ImagePlus impTable = new ImagePlus("Matrix", table);
		//impTable.show();


		for (int i = 0; i < transforms.length; ++i) {
			final CoordinateTransformList<mpicbg.models.CoordinateTransform> ct1 = createTransformList(i, transforms);
			for (int j = 0; j < transforms.length; ++j) {
				final CoordinateTransformList<mpicbg.models.CoordinateTransform> ct2 = createTransformList(j, transforms);

				/* fit a simple linear model to compare with using some transferred samples */
				final mpicbg.models.CoordinateTransform t = (mpicbg.models.CoordinateTransform)sampleModel2(ct2, ct1, invarianceModelClass, pWidth, pHeight);
				ct2.add(t);

				final ImageProcessor ip = visualizeDifferenceVectorDistribution(
					w,
					h,
					pWidth,
					pHeight,
					ct1,
					ct2,
					max);

				table.copyBits(ip, (w + xSkip) * i, (h + ySkip) * j, Blitter.COPY);
				impTable.updateAndDraw();
			}
		}
		return impTable;
	}

	static void drawCircles(final ColorProcessor ip, final String[][] transforms, final int w, final int h, final int xSkip, final int ySkip, final double max) {
		for (int s = 1; s <= max; ++s) {
			ip.setColor(
				new Color(
					(float)(Math.min(1.0f, 2.0f * s / max)),
					(float)(Math.min(1.0f, 2.0f - 2 * s / max)),
					0.0f));
			for (int i = 0; i < transforms.length; ++i) {
				final int x = (w + xSkip) * i;
				for (int j = i + 1; j < transforms.length; ++j) {
					final int y = (h + ySkip) * j;
					ip.drawOval(
						(int)(w / 2 + x - Math.round(s * w / max / 2)),
						(int)(h / 2 + y - Math.round(s * h / max / 2)),
						(int)Math.round(s * w / max) + 1,
						(int)Math.round(s * h / max) + 1);
				}
			}
		}
	}

	static void drawLabels(final ImagePlus imp, final int offset, final String[][] transforms, final int w, final int h, final int xSkip, final int ySkip) {
		ImageProcessor ip = imp.getProcessor().createProcessor(imp.getWidth() + offset, imp.getHeight() +offset);
		ip.copyBits(imp.getProcessor(), offset, offset, Blitter.COPY);
		ip.setColor(Color.WHITE);
		ip.setAntialiasedText(true);
		ip.setJustification(ImageProcessor.CENTER_JUSTIFY);
		final Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 14);
		ip.setFont(font);
		for (int i = 0; i < transforms.length; ++i) {
			final int x = (w + xSkip) * i;
			ip.drawString(
				transforms[i][0],
				(int)(w / 2 + x + offset),
				20);
		}
		ip = ip.rotateRight();
//		ip.flipHorizontal();
		ip.setColor(Color.WHITE);
		ip.setAntialiasedText(true);
		ip.setJustification(ImageProcessor.CENTER_JUSTIFY);
		//ip.setFont(font.deriveFont(new AffineTransform(-1, 0, 0, 1, 0, 0)));
		ip.setFont(font);
		for (int i = 0; i < transforms.length; ++i) {
			final int x = (w + xSkip) * i;
			ip.drawString(
				transforms[transforms.length - 1 - i][0],
				(int)(w / 2 + x),
				20);
		}
		ip = ip.rotateLeft();
//		ip.flipHorizontal();
		imp.setProcessor(ip);
	}



	public static HashMap<String, Object> exportTransform(final String name, final List<HashMap<String, String>> transform) {
		final HashMap<String, Object> map = new HashMap<String, Object>();
		map.put("name", name);
		map.put("transform", transform);
		return map;
	}

	public static HashMap<String, String> exportTransform(final CoordinateTransform ct) {
		final HashMap<String, String> map = new HashMap<String, String>();
		String className = ct.getClass().getCanonicalName();
		if (className.equals("lenscorrection.NonLinearTransform"))
			className = "mpicbg.trakem2.transform.NonLinearCoordinateTransform";
		map.put("className", ct.getClass().getCanonicalName());
		map.put("dataString", ct.toDataString());
		return map;
	}

	public static ArrayList<PointMatch> samplePoints(final Patch patch) {
		final CoordinateTransform ct = patch.getFullCoordinateTransform();
		final ArrayList<PointMatch> matches = new ArrayList<PointMatch>();
		final double scaleX = (patch.getOWidth() - 1.0f) / 63.0f;
		final double scaleY = (patch.getOHeight() - 1.0f) / 63.0f;
		for (int y = 0; y < 64; ++y) {
			final double ys = scaleY * y;
			for (int x = 0; x < 64; ++x) {
				final double xs = scaleX * x;
				final Point p = new Point(new double[]{xs, ys});
				final Point q = new Point(new double[]{xs, ys});
				ct.applyInPlace(p.getL());
				matches.add(new PointMatch(p, q));
			}
		}
		return matches;
	}

	public static List<String> findFiles(final Path path, final String[] fileExtensions) throws IOException {
		if (!Files.isDirectory(path)) {
			throw new IllegalArgumentException("path must be a directory.");
		}

		List<String> result;
		try (Stream<Path> flist = Files.list(path)) {
			result = flist.filter(p -> !Files.isDirectory(p))
					.map(p -> p.toString())
					.filter(f -> Arrays.stream(fileExtensions).anyMatch(f::endsWith)).collect(Collectors.toList());
		}
		return result;
	}

	public static ImagePlus ZMaxProjection(final ImagePlus imp) {
		final ZProjector zp = new ZProjector(imp);
		zp.setMethod(ZProjector.MAX_METHOD);
		if (imp.isHyperStack())
		{
			zp.setStopSlice(imp.getNSlices());
			zp.doHyperStackProjection(false);
		}
		else
			zp.doProjection();
		return zp.getProjection();
	}

	/**
	 * Interpolates between LUT values such that the result array has the
	 * specified number of colors.
	 *
	 * @param baseLut
	 *            the LUT to interpolated
	 * @param nColors
	 *            the number of colors of the new LUT
	 * @return a nColors-by-3 array of color components
	 */
	public final static byte[][] interpolateLut(final byte[][] baseLut, final int nColors) {

		final int n0 = baseLut.length;
		// allocate memory for new lut
		final byte[][] lut = new byte[nColors][3];

		// linear interpolation of each color of new lut
		for (int i = 0; i < nColors; i++) {
			// compute color index in original lut
			final float i0 = ((float) i) * n0 / nColors;
			final int i1 = (int) Math.floor(i0);

			// the two surrounding colors
			final byte[] col1 = baseLut[i1];
			final byte[] col2 = baseLut[Math.min(i1 + 1, n0 - 1)];

			// the ratio between the two surrounding colors
			final float f = i0 - i1;

			// linear interpolation of surrounding colors with cast
			lut[i][0] = (byte) ((1. - f) * (col1[0] & 0xFF) + f * (col2[0] & 0xFF));
			lut[i][1] = (byte) ((1. - f) * (col1[1] & 0xFF) + f * (col2[1] & 0xFF));
			lut[i][2] = (byte) ((1. - f) * (col1[2] & 0xFF) + f * (col2[2] & 0xFF));
		}

		return lut;
	}

	/**
	 * Creates a byte array representing the Fire LUT.
	 *
	 * @param nColors
	 *            number of colors
	 * @return a nColors-by-3 array of color components.
	 */
	public final static byte[][] createFireLut(final int nColors) {
		byte[][] lut = createFireLut();
		if (nColors != lut.length)
			lut = interpolateLut(lut, nColors);
		return lut;
	}

	/**
	 * Creates a byte array representing the Fire LUT.
	 *
	 * @return an array of color components.
	 */
	public final static byte[][] createFireLut() {
		// initial values
		final int[] r = { 0, 0, 1, 25, 49, 73, 98, 122, 146, 162, 173, 184, 195, 207,
				217, 229, 240, 252, 255, 255, 255, 255, 255, 255, 255, 255,
				255, 255, 255, 255, 255, 255 };
		final int[] g = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 14, 35, 57, 79, 101,
				117, 133, 147, 161, 175, 190, 205, 219, 234, 248, 255, 255,
				255, 255 };
		final int[] b = { 0, 61, 96, 130, 165, 192, 220, 227, 210, 181, 151, 122, 93,
				64, 35, 5, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 35, 98, 160, 223,
				255 };

		// create map
		final byte[][] map = new byte[r.length][3];

		// cast elements
		for (int i = 0; i < r.length; i++) {
			map[i][0] = (byte) r[i];
			map[i][1] = (byte) g[i];
			map[i][2] = (byte) b[i];
		}

		return  map;
	}

	private static LUT createFireLUT(final double maxVal)
	{
		final byte[][] lut = createFireLut(256);
		final byte[] red = new byte[256];
		final byte[] green = new byte[256];
		final byte[] blue = new byte[256];
		for (int i = 0; i < 256; i++)
		{
			red[i] 		= lut[i][0];
			green[i] 	= lut[i][1];
			blue[i] 	= lut[i][2];
		}
		final IndexColorModel cm = new IndexColorModel(8, 256, red, green, blue);
		return new LUT(cm, 0, maxVal);
	}


	@Override
	public Integer call() throws Exception {
		// Remove trailing separators from paths
		String dirPath = inputDir.endsWith(File.separator)
			? inputDir.substring(0, inputDir.length() - 1)
			: inputDir;
		String outdir = outputDir.endsWith(File.separator)
			? outputDir.substring(0, outputDir.length() - 1)
			: outputDir;

        System.out.println("input directory: " + inputDir);
        System.out.println("output directory: " + outputDir);
        System.out.println("calibration settings: " + (calibrationParamsPath != null ? calibrationParamsPath : "default (built-in calibration-params.json)"));
        System.out.println("input parameters: " + inputParamsPath);
        System.out.println("project name: " + projectName);

		try
		{
			// Load calibration parameters from JSON file or use default resource
			final CalibrationParams calibParams;
			if (calibrationParamsPath != null) {
				calibParams = CalibrationParams.load(calibrationParamsPath);
			} else {
				calibParams = CalibrationParams.loadFromResource("/calibration-params.json");
			}

			// Load input parameters (must be provided by user)
			final Gson gson = new GsonBuilder().setPrettyPrinting().create();
			final InputParams inputParams = gson.fromJson(
				Files.readString(Paths.get(inputParamsPath)),
				InputParams.class
			);

			final int maxNumThreads = Runtime.getRuntime().availableProcessors();

			// Convert to TrakEM2 parameters
			final Align.ParamOptimize param = calibParams.getMontageLayers().toAlignParam();
			final RegularizedAffineLayerAlignment.Param param2 = calibParams.getAlignLayers().toRegularizedAffineParam(maxNumThreads);
			final CorrectDistortionFromSelectionParam p = calibParams.getCorrectDistortion().toDistortionParam(maxNumThreads);
			final RegularizedAffineLayerAlignment.Param param3 = calibParams.getAlignLayers2().toRegularizedAffineParam(maxNumThreads);
			final RegularizedAffineLayerAlignment.Param param4 = calibParams.getAlignLayers3().toRegularizedAffineParam(maxNumThreads);

			final List<String> inputPatterns = inputParams.getPatterns();
			final List<String> inputLabels = inputParams.getLabels();

			final String[] extensions = {"lsm", "LSM"};

			//new ImageJ();

			//read lsm files and generate mip images
			final List<String> flist = findFiles(Paths.get(dirPath), extensions);
			flist.sort(Comparator.naturalOrder());

			final HashMap<String, List<ImagePlus>> mapMips = new HashMap<String, List<ImagePlus>>();
			for (int i = 0; i < flist.size(); i++)
			{
				for (final String pattern : inputPatterns)
				{
					final Pattern pt = Pattern.compile(pattern);
				    final Matcher mt = pt.matcher(flist.get(i));
				    if (mt.find())
				    {
				    	final String path = flist.get(i);
						final ImagePlus[] impStack = BF.openImagePlus(path);

						System.out.println(
								"Number of images: " + impStack.length + " (this should always be 1), we ignore others");

						if (impStack.length > 1)
							throw new RuntimeException("More than one image was opened, please check the input carefully.");

						final ImagePlus imp = impStack[0];

						System.out.println("dimensions: " + imp.getStack().getProcessor(1).getWidth() + "x"
								+ imp.getStack().getProcessor(1).getHeight() + ", channels: " + imp.getNChannels()
								+ ", z-slices:" + imp.getNSlices() + ", timepoints: " + imp.getNFrames());

						imp.resetDisplayRange();

						final ImagePlus mipImp = ZMaxProjection(imp);
						HyperStackConverter.toStack(mipImp);

						if (!mapMips.containsKey(pattern))
							mapMips.put(pattern, new ArrayList<ImagePlus>());
						mapMips.get(pattern).add(mipImp);

						imp.close();
						break;
				    }
				}
			}

			if (mapMips.size() == 0)
				throw new RuntimeException("mip creation failed.");

			//save mip images
			//normalize local contrast brx 127 bry 127 stds 3.0 (all layers)
			final String storageDir = outdir + File.separator + projectName;
			FileUtils.forceMkdir(new File(storageDir));

			final int brx = 127;
			final int bry = 127;
			final float stds = 3.0f;
			int layernum = 0;
			final HashMap<Integer, ArrayList<String>> layerPatchPaths = new HashMap<Integer, ArrayList<String>>();
			for (final List<ImagePlus> implist : mapMips.values()) {
				int maxChNum = 0;
				for (int i = 0; i < implist.size(); i++)
				{
					final ImagePlus mip = implist.get(i);
					int layerId = layernum;
					final ImageStack sstack = mip.getStack();
					for (int s = 1; s <= sstack.getSize(); s++)
					{
						NormalizeLocalContrast.run(sstack.getProcessor(s), brx, bry, stds, true, true);
						final String fname = String.format("layer_%02d_pos_%02d.tif", layerId, i);
						final ImagePlus tmp = new ImagePlus(fname, sstack.getProcessor(s).duplicate());
						final FileSaver saver = new FileSaver(tmp);
						final String fpath = storageDir + File.separator + fname;
						saver.saveAsTiff(fpath);
						if (!layerPatchPaths.containsKey(layerId))
							layerPatchPaths.put(layerId, new ArrayList<String>());
						layerPatchPaths.get(layerId).add(fpath);
						tmp.close();
						layerId++;
					}
					if (layerId > maxChNum)
						maxChNum = layerId;
				}
				layernum = maxChNum;
			}

			for (final String pattern : inputPatterns)
				System.out.println(pattern);
			System.out.println(layernum);
			for (int i = 0; i < layernum; i++)
			{
				final ArrayList<String> pathList = layerPatchPaths.get(i);
				for (int s = 0; s < pathList.size(); s++)
				{
					System.out.println(pathList.get(s));
				}
			}

			//create a new trakem project.
			ControlWindow.setGUIEnabled(false);

			final Project project = Project.newFSProject("blank", null, storageDir);
			final LayerSet layerset = project.getRootLayerSet();
			for (int i = 0; i < layernum; i++)
				  layerset.getLayer(i, 1, true);
			project.getLayerTree().updateList(layerset);
			Display.updateLayerScroller(layerset);

			for (int i = 0; i < layernum; i++)
			{
				final Layer layer = layerset.getLayer(i);
				final ArrayList<String> pathList = layerPatchPaths.get(i);
				for (int s = 0; s < pathList.size(); s++)
				{
					final Patch patch = Patch.createPatch(project, pathList.get(s));
					layer.add(patch);
				}
				layer.recreateBuckets();
			}

			//montage all layers. least square, translation.
			AlignTask.montageLayers(param, layerset.getLayers(), true, true, true, false, true);


			//Align layers. (least square)
			boolean propagateTransformBefore = false;
			boolean propagateTransformAfter = false;

			Rectangle box = null;
			HashSet< Layer > emptyLayers = new HashSet< Layer >();
			for ( final Iterator< Layer > it = layerset.getLayers().iterator(); it.hasNext(); )
			{
				/* remove empty layers */
				final Layer la = it.next();
				if ( !la.contains( Patch.class, true ) )
				{
					emptyLayers.add( la );
//					it.remove();
				}
				else
				{
					/* accumulate boxes */
					if ( null == box ) // The first layer:
						box = la.getMinimalBoundingBox( Patch.class, true );
					else
						box = box.union( la.getMinimalBoundingBox( Patch.class, true ) );
				}
			}

			new RegularizedAffineLayerAlignment().exec(param2, layerset.getLayers(), new HashSet<Layer>(), emptyLayers, box, propagateTransformBefore, propagateTransformAfter, null);


			//Auto resize canvas
			layerset.setMinimumDimensions();


			//Lens correction (All layers)
			for (int i = 0; i < layernum; i++)
			{
				p.firstLayerIndex = i;
				p.lastLayerIndex = i;
				final Layer layer = layerset.getLayer(i);
				final ArrayList<Patch> patches = layer.getPatches(true);
				if (patches.size() > 0)
					DistortionCorrectionTask.run(p, patches, patches.get(0), layer);
			}

			//montage all layers. least square, translation.
			AlignTask.montageLayers(param, layerset.getLayers(), true, true, true, false, true);


			//Align layers. least square
			propagateTransformBefore = false;
			propagateTransformAfter = false;

			box = null;
			emptyLayers = new HashSet< Layer >();
			for ( final Iterator< Layer > it = layerset.getLayers().iterator(); it.hasNext(); )
			{
				/* remove empty layers */
				final Layer la = it.next();
				if ( !la.contains( Patch.class, true ) )
				{
					emptyLayers.add( la );
				}
				else
				{
					/* accumulate boxes */
					if ( null == box ) // The first layer:
						box = la.getMinimalBoundingBox( Patch.class, true );
					else
						box = box.union( la.getMinimalBoundingBox( Patch.class, true ) );
				}
			}

			new RegularizedAffineLayerAlignment().exec(param3, layerset.getLayers(), new HashSet<Layer>(), emptyLayers, box, propagateTransformBefore, propagateTransformAfter, null);



			// TODO Is another alignment required?  Don't think so, or is it?
			//Align layers. least square
			propagateTransformBefore = false;
			propagateTransformAfter = false;

			box = null;
			emptyLayers = new HashSet< Layer >();
			for ( final Iterator< Layer > it = layerset.getLayers().iterator(); it.hasNext(); )
			{
				/* remove empty layers */
				final Layer la = it.next();
				if ( !la.contains( Patch.class, true ) )
				{
					emptyLayers.add( la );
				}
				else
				{
					/* accumulate boxes */
					if ( null == box ) // The first layer:
						box = la.getMinimalBoundingBox( Patch.class, true );
					else
						box = box.union( la.getMinimalBoundingBox( Patch.class, true ) );
				}
			}

			new RegularizedAffineLayerAlignment().exec(param4, layerset.getLayers(), new HashSet<Layer>(), emptyLayers, box, propagateTransformBefore, propagateTransformAfter, null);


			//save trakem project
			project.saveAs(storageDir + File.separator + projectName + "_trakem_proj.xml", true);

			//String storageDir = outdir+File.separator+projectName;
			//Project project = Project.openFSProject(storageDir + File.separator + projectName + "_trakem_proj.xml");
			//LayerSet layerset = project.getRootLayerSet();

			//output coordinate transform
			// gson already defined above

			layerset.setMinimumDimensions();

			final Rectangle topLeftBox = new Rectangle(100, 100);
			final ArrayList<Patch> patches = new ArrayList<Patch>();

			for (final Layer layer : layerset.getLayers()) {
				final Collection<Displayable> displayables = layer.getDisplayables(Patch.class, topLeftBox);
				patches.add((Patch)displayables.iterator().next());
			}

			final ArrayList<PointMatch> matches = new ArrayList<PointMatch>();

			for (final Patch patch : patches)
				matches.addAll(samplePoints(patch));

			final RigidModel2D model = new RigidModel2D();
			model.fit(matches);
			final AffineModel2D affineModel = new AffineModel2D();
			affineModel.set(model.createAffine());

			final ArrayList<HashMap<String, Object>> transformExports = new ArrayList<HashMap<String, Object>>();
			for (int i = 0; i < patches.size(); ++i) {
				final Patch patch = patches.get(i);
				@SuppressWarnings("unchecked")
				final CoordinateTransformList< CoordinateTransform > ctl = (CoordinateTransformList< CoordinateTransform >) patch.getFullCoordinateTransform();
				final List<CoordinateTransform> cts = ctl.getList(null);
				final AffineModel2D affine = (AffineModel2D) cts.get(1);
				affine.preConcatenate(affineModel);
				final List<HashMap<String, String>> maplist = new ArrayList<HashMap<String, String>>();
				maplist.add(exportTransform(ctl.get(0)));
				maplist.add(exportTransform(ctl.get(1)));
				final HashMap<String, Object> export = exportTransform(
					String.format("%s, %s", projectName, inputLabels.get(i)), maplist);
				transformExports.add(export);
			}

			final String resultJson = gson.toJson(transformExports);
			final String jsonpath = outdir + File.separator + projectName + ".json";
			Files.write(Paths.get(jsonpath), resultJson.getBytes());
			System.out.println(resultJson);


			//compare lenses
			final ArrayList<ArrayList<String>> trLists = new ArrayList<ArrayList<String>>();
			final ArrayList<String> tempIdList = new ArrayList<String>();
			tempIdList.add("Identity");
			tempIdList.add("mpicbg.trakem2.transform.AffineModel2D");
			tempIdList.add("1.0 0.0 0.0 1.0 0.0 0.0");
			trLists.add(tempIdList);
			for (int i = 0; i < patches.size(); ++i) {
				final Patch patch = patches.get(i);
				@SuppressWarnings("unchecked")
				final CoordinateTransformList< CoordinateTransform > ctl = (CoordinateTransformList< CoordinateTransform >) patch.getFullCoordinateTransform();
				final List<CoordinateTransform> cts = ctl.getList(null);
				final AffineModel2D affine = (AffineModel2D) cts.get(1);
				affine.preConcatenate(affineModel);
				final ArrayList<String> templist = new ArrayList<String>();
				final String label = String.format("%s, %s", projectName, inputLabels.get(i));
				final String classname1 = ctl.get(0).getClass().getName();
				final String ctstr1 = ctl.get(0).toDataString();
				final String classname2 = ctl.get(1).getClass().getName();
				final String ctstr2 = ctl.get(1).toDataString();
				templist.add(label);
				templist.add(classname1);
				templist.add(ctstr1);
				templist.add(classname2);
				templist.add(ctstr2);
				trLists.add(templist);
			}
			final String[][] transforms = new String[trLists.size()][];
			final String[] blankArray = new String[0];
			for(int i = 0; i < trLists.size(); i++) {
				transforms[i] = trLists.get(i).toArray(blankArray);
			}

			final int iw = 256;
			final int ih = 256;
			final double max = 5;
			final int pWidth = mapMips.values().iterator().next().get(0).getWidth();
			final int pHeight = mapMips.values().iterator().next().get(0).getHeight();

			final int ySkip = 4;
			final int xSkip = 4;

			final ImagePlus impVectors = showDifferenceVectors(transforms, pWidth, pHeight, iw, ih, xSkip, ySkip, max);
			final ImagePlus impDists = showDifferenceVectorDistributions(transforms, pWidth, pHeight, iw, ih, xSkip, ySkip, max);

			impDists.setDisplayRange(0, 32);
			Thread.sleep(1000);
			impDists.setLut(createFireLUT(32.0));
			Thread.sleep(1000);
			new ImageConverter(impDists).convertToRGB();
			Thread.sleep(1000);
			impDists.getProcessor().snapshot();

//			{
//				FileSaver saver = new FileSaver(impDists);
//				saver.saveAsTiff(outdir + File.separator + projectName + "_dists" + ".tif");
//				FileSaver saver2 = new FileSaver(impVectors);
//				saver2.saveAsTiff(outdir + File.separator + projectName + "_vectors" + ".tif");
//			}

			final ColorProcessor ipSrc = (ColorProcessor)impVectors.getProcessor();
			final ColorProcessor ipDst = (ColorProcessor)impDists.getProcessor();

			for(int y = 0; y < ipSrc.getHeight(); y++) {
				for(int x = y; x < ipSrc.getWidth(); x++) {
					ipDst.set(x, y, ipSrc.get(x, y));
				}
			}

			drawCircles((ColorProcessor)impDists.getProcessor(), transforms, iw, ih, xSkip, ySkip, max);
			drawLabels(impDists, 26, transforms, iw, ih, xSkip, ySkip);

			final FileSaver saver = new FileSaver(impDists);
			final String comparePath = outdir + File.separator + projectName + "_compare_lenses" + ".tif";
			saver.saveAsTiff(comparePath);

			System.out.println("Done");

		} catch (final Exception e) {
			e.printStackTrace();
			return 1;
		}

		return 0;
	}

	public static void main(final String[] args) {
		final Integer exitCode = new CommandLine(new CalibrateChannels()).execute(args);
		System.exit(exitCode);
	}
}
