package org.openforis.ceo.migrator;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import org.openforis.ceo.migrator.ConfigurationProvider.Configuration;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

public abstract class BaseMigrator {
	
	protected JsonArray readSourceFileAsArray(String fileName) {
		Configuration config = ConfigurationProvider.getConfig();
		String sourceFileLocation = config.getSourceFileLocation();
		File file = new File(sourceFileLocation, fileName);
		if (file.exists()) {
			try (FileReader fileReader = new FileReader(file)) {
	            JsonElement parsed = new JsonParser().parse(fileReader);
	            return parsed.getAsJsonArray();
			} catch(IOException e) {
				throw new RuntimeException(e);
			}
		} else {
			return null;
		}
	}

}
