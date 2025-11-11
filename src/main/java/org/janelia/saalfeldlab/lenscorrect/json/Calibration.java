package org.janelia.saalfeldlab.lenscorrect.json;

import mpicbg.trakem2.transform.CoordinateTransform;

/**
 * Calibration record holding the transformation for a specific channel and its
 * name.  The transformation is an array of {@link CoordinateTransform} that
 * are applied in sequence.
 *
 * This is used to import the calibration from JSON files.  Example with two
 * calibrations:
 * <pre>
 * [{
 *     "transform": [{
 *         "dataString": "...",
 *         "className": "lenscorrection.NonLinearTransform"
 *       }, {
 *         "dataString": "...",
 *         "className": "mpicbg.trakem2.transform.AffineModel2D"
 *       }],
 *     "name": "scope, sample, left"
 *   }, {
 *     "transform": [...],
 *     "name": "scope, sample, right"
 *   }]
 * </pre>
 *
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 */
public record Calibration(
		CoordinateTransform[] transform,
		String name) {}