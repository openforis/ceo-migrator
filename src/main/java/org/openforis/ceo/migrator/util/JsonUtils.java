package org.openforis.ceo.migrator.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class JsonUtils {

    private static final SimpleDateFormat DATE_FORMATTER = new SimpleDateFormat("yyyy-MM-dd");

	public static String expandResourcePath(String filename) {
        try {
            URI uri = JsonUtils.class.getResource(filename).toURI();
            return Paths.get(uri).toFile().getAbsolutePath();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public static JsonElement parseJson(String jsonString) {
        return (new JsonParser()).parse(jsonString);
    }
    
    public static String toJson(Object obj) {
    	return new Gson().toJson(obj);
    }

    public static JsonElement readJsonFile(String filename) {
        String jsonDataDir = expandResourcePath("/json/");
        try (FileReader fileReader = new FileReader(new File(jsonDataDir, filename))) {
            return (new JsonParser()).parse(fileReader);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void writeJsonFile(String filename, JsonElement data) {
        String jsonDataDir = expandResourcePath("/json/");
        try (FileWriter fileWriter = new FileWriter(new File(jsonDataDir, filename))) {
            fileWriter.write(data.toString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Note: The JSON array may contain elements of any type.
    public static Stream<JsonElement> toElementStream(JsonArray array) {
        return StreamSupport.stream(array.spliterator(), false);
    }

    // Note: The JSON array must only contain JSON objects.
    public static Stream<JsonObject> toStream(JsonArray array) {
        return toElementStream(array).map(element -> element.getAsJsonObject());
    }

    public static Collector<JsonElement, ?, JsonArray> intoJsonArray =
        Collector.of(JsonArray::new, JsonArray::add,
                     (left, right) -> { left.addAll(right); return left; });

    public static JsonArray mapJsonArray(JsonArray array, Function<JsonObject, JsonObject> mapper) {
        return toStream(array)
            .map(mapper)
            .collect(intoJsonArray);
    }

    public static JsonArray flatMapJsonArray(JsonArray array, Function<JsonObject, Stream<JsonObject>> mapper) {
        return toStream(array)
            .flatMap(mapper)
            .collect(intoJsonArray);
    }

    public static JsonArray filterJsonArray(JsonArray array, Predicate<JsonObject> predicate) {
        return toStream(array)
            .filter(predicate)
            .collect(intoJsonArray);
    }

    public static Optional<JsonObject> findInJsonArray(JsonArray array, Predicate<JsonObject> predicate) {
        return toStream(array)
            .filter(predicate)
            .findFirst();
    }

    public static void forEachInJsonArray(JsonArray array, Consumer<JsonObject> action) {
        toStream(array)
            .forEach(action);
    }

    // Note: All objects in the JSON array must contain "id" fields.
    public static int getNextId(JsonArray array) {
        return toStream(array)
            .map(object -> object.get("id").getAsInt())
            .max(Comparator.naturalOrder())
            .get() + 1;
    }

	public static JsonArray singletonArray(JsonElement el) {
		JsonArray array = new JsonArray();
		array.add(el);
		return array;
	}

    // Note: The JSON file must contain an array of objects.
    public static void mapJsonFile(String filename, Function<JsonObject, JsonObject> mapper) {
        JsonArray array = readJsonFile(filename).getAsJsonArray();
        JsonArray updatedArray = mapJsonArray(array, mapper);
        writeJsonFile(filename, updatedArray);
    }

    // Note: The JSON file must contain an array of objects.
    public static void filterJsonFile(String filename, Predicate<JsonObject> predicate) {
        JsonArray array = readJsonFile(filename).getAsJsonArray();
        JsonArray updatedArray = filterJsonArray(array, predicate);
        writeJsonFile(filename, updatedArray);
    }

    private static JsonElement walkJsonPath(JsonElement currentEl, String path, LinkedList<String> pathParts) {
        if (pathParts.peekFirst() == null) {
            return currentEl;
        } else {
            String pathPart = pathParts.removeFirst();
            if (currentEl instanceof JsonObject) {
                JsonObject currentObj = (JsonObject) currentEl;
                Pattern arrayIndexPattern = Pattern.compile("(\\w+)\\[(\\d+)\\]");
                Matcher arrayIndexMatcher = arrayIndexPattern.matcher(pathPart);
                if (arrayIndexMatcher.matches()) {
                    String arrayObjName = arrayIndexMatcher.group(1);
                    String arrayIdxStr = arrayIndexMatcher.group(2);
                    JsonArray array = currentObj.get(arrayObjName).getAsJsonArray();
                    JsonElement nextEl = array.get(Integer.parseInt(arrayIdxStr));
                    return walkJsonPath(nextEl, path, pathParts);
                } else {
                    Pattern propertyNamePattern = Pattern.compile("\\w+");
                    Matcher propertyNameMatcher = propertyNamePattern.matcher(pathPart);
                    if (propertyNameMatcher.matches()) {
                        String propertyName = propertyNameMatcher.group();
                        JsonElement nextEl = currentObj.get(propertyName);
                        return walkJsonPath(nextEl, path, pathParts);
                    } else {
                        throw new IllegalArgumentException("Unexpected path part for a JSON object : " + pathPart);
                    }
                }
            } else {
                throw new IllegalArgumentException("Invalid path for JSON object: " + path);
            }
        }
    }

    public static JsonElement findElement(JsonObject jsonObj, String path) {
        LinkedList<String> pathParts = new LinkedList<String>(Arrays.asList(path.split("\\.")));
        return walkJsonPath(jsonObj, path, pathParts);
    }

	public static <T> T getMemberValue(JsonObject obj, String property, Class<T> type) {
    	return getMemberValue(obj, property, type, null);
    }
    
	@SuppressWarnings("unchecked")
    public static <T> T getMemberValue(JsonObject obj, String property, Class<T> type, Object defaultIfNull) {
    	JsonElement el = findElement(obj, property);
    	if (el == null || el.isJsonNull()) {
    		return (T) defaultIfNull;
    	} else if (type == String.class) {
    		return (T) el.getAsString();
    	} else if (type == Double.class) {
    		return (T) Double.valueOf(el.getAsDouble());
    	} else if (type == Integer.class) {
    		return (T) Integer.valueOf(el.getAsInt());
    	} else if (type == Boolean.class) {
    		return (T) Boolean.valueOf(el.getAsBoolean());
    	} else {
    		throw new IllegalArgumentException("Unsupported type: " + type);
    	}
    }
    
    public static String getDateAsString(JsonObject obj, String property) {
    	JsonElement el = findElement(obj, property);
    	if (el.isJsonNull()) {
    		return null;
    	} else {
    		String dateStr = el.getAsString();
			Date date = new Date(Long.parseLong(dateStr));
			return DATE_FORMATTER.format(date);
    	}
    }

}
