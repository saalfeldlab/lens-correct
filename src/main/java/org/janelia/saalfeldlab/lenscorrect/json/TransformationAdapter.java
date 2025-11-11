package org.janelia.saalfeldlab.lenscorrect.json;

import java.lang.reflect.Type;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializer;

import mpicbg.trakem2.transform.CoordinateTransform;
import mpicbg.trakem2.transform.CoordinateTransformList;

/**
 * GSON adapter to serialize and deserialize {@link CoordinateTransform}s.
 * This is used to import and export the calibration from/to JSON files. Example:
 * <pre>
 * {
 *   "className": "mpicbg.trakem2.transform.AffineModel2D",
 *   "dataString": "...",
 * }
 * </pre>
 *
 * Or nested transformation lists:
 * <pre>
 * {
 *   "className": "mpicbg.trakem2.transform.CoordinateTransformList",
 *   "transforms": [{
 *       "className": "mpicbg.trakem2.transform.AffineModel2D",
 *       "dataString": "..."}, {
 *       "className": "lenscorrection.NonLinearTransform",
 *       "dataString": "..."}, {
 *       "className": "mpicbg.trakem2.transform.CoordinateTransformList",
 *       "transforms": [...]},
 *      ...]}
 * </pre>
 */
public class TransformationAdapter implements JsonDeserializer<CoordinateTransform>, JsonSerializer<CoordinateTransform> {

    @SuppressWarnings("unchecked")
    @Override
    public CoordinateTransform deserialize(
            final JsonElement json,
            final Type typeOfT,
            final JsonDeserializationContext context) throws com.google.gson.JsonParseException {

        final JsonObject jsonObject = json.getAsJsonObject();
		final JsonElement jsonType = jsonObject.get("className");
		if (jsonType == null)
			return null;

        final String className = jsonType.getAsString();
        if (CoordinateTransformList.class.getName().equals(className)) {
            @SuppressWarnings("rawtypes")
            final CoordinateTransformList list = new CoordinateTransformList();
            final JsonElement transformsJson = jsonObject.get("transforms");
            if (transformsJson != null && transformsJson.isJsonArray()) {
                final JsonArray transformsArray = transformsJson.getAsJsonArray();
                for (final JsonElement transformElement : transformsArray) {
                    final CoordinateTransform transform = context.deserialize(transformElement, CoordinateTransform.class);
                    list.add(transform);
                }
            }
        } else {
            try {
                final Class<?> clazz = Class.forName(className);
                final CoordinateTransform transform = (CoordinateTransform)clazz.getDeclaredConstructor().newInstance();
                transform.init(jsonObject.get("dataString").getAsString());
                return transform;
            } catch (final Exception e) {
                throw new com.google.gson.JsonParseException("Failed to deserialize CoordinateTransform: " + className, e);
            }
        }

        return null;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public com.google.gson.JsonElement serialize(
            final CoordinateTransform src,
            final java.lang.reflect.Type typeOfSrc,
            final com.google.gson.JsonSerializationContext context) {

        final Class<? extends CoordinateTransform> clazz = src.getClass();
        final JsonObject json = new JsonObject();

        if (CoordinateTransformList.class.isAssignableFrom(clazz)) {
            json.addProperty("className", CoordinateTransformList.class.getName());
            json.add("transforms", context.serialize(((CoordinateTransformList)src).getList(null)));
        } else {
            json.addProperty("className", clazz.getName());
            json.addProperty("dataString", src.toDataString());
        }

        return json;
    }

}
