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
package org.janelia.saalfeldlab.lenscorrect;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

import org.janelia.saalfeldlab.lenscorrect.json.Calibration;
import org.janelia.saalfeldlab.lenscorrect.json.TransformationAdapter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import mpicbg.trakem2.transform.CoordinateTransform;
import mpicbg.trakem2.transform.CoordinateTransformList;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Apply wavelength-dependent lens correction to multi-channel input that is
 * either lsm or tif, several files possible. Save as single multi-channel tif.
 *
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 */
@Command(
    name = "apply-channels",
    mixinStandardHelpOptions = true,
    description = "Apply wavelength-dependent lens-distortion correction and alignment to multi-channel image stacks"
)
public class ApplyChannels implements Callable<Integer> {

	@Option(names = {"-i", "--inputs"}, required = true, description = "input image file paths (comma-separated)")
	private String[] inputPaths;

	@Option(names = {"-o", "--output"}, required = true, description = "output image file path")
	private String outputPath;

	@Option(names = {"-t", "--transforms"}, required = true, description = "JSON file containing transforms for each channel")
	private String transformsPath;

	@Option(names = {"-c", "--crop"}, defaultValue = "0", description = "crop width (default: 0)")
	private int cropWidth;

	private static final Gson gson = new GsonBuilder()
			.setPrettyPrinting()
			.disableHtmlEscaping()
			.registerTypeAdapter(CoordinateTransform.class, new TransformationAdapter())
			.create();

	@Override
	public Integer call() throws Exception {

		/* open input channels */
		ImagePlus[] channels = Util.openChannels(inputPaths);
		if (channels == null) {
			System.err.println("Could not open input images: " + Arrays.toString(inputPaths));
			return 1;
		}

		/* create output directory */
		try {
			final File f = new File(outputPath).getParentFile();
			if (f == null || !(f.mkdirs() || f.exists())) {
				System.err.println("Could not create output directory");
				return 1;
			}
		} catch (final Exception e) {
			e.printStackTrace(System.err);
			return 1;
		}

		/* read the transform file into a list of CoordinateTransformList */
		final List<Calibration> calibrations;
		final ArrayList<CoordinateTransformList<CoordinateTransform>> transforms;
		try (FileReader reader = new FileReader(transformsPath)) {
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
				System.err.println("No transforms found in: " + transformsPath);
				return 1;
			}
		} catch (final IOException e) {
			System.err.println("Error reading transform file: " + e.getMessage());
			return 1;
		}

		/* apply transforms */
		for (int i = 0; i < channels.length; ++i) {
			ImageStack transformedStack =
					Util.createTransformedStack(channels[i].getStack(), transforms.get(i), cropWidth);
			channels[i].setStack(transformedStack);
		}

		IJ.saveAsTiff(Util.combineChannels(channels), outputPath);

		return 0;
	}

	public static void main(final String[] args) {

		final Integer exitCode = new CommandLine(new ApplyChannels()).execute(args);
		System.exit(exitCode);
	}
}
