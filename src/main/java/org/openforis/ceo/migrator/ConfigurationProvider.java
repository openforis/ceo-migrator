package org.openforis.ceo.migrator;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ConfigurationProvider {

	private static Configuration config = loadConfiguration();
	
	private static Configuration loadConfiguration() {
		String userDir = System.getProperty("user.dir");
		File configFile = new File(userDir + File.separator + "config.properties");
		try (InputStream input = new FileInputStream(configFile)) {
			Properties prop = new Properties();
			prop.load(input);
			Configuration config = new Configuration();
			config.sourceFileLocation = prop.getProperty("source_files");
			config.collectDbUrl = prop.getProperty("collect.db.url");
			config.collectDbDriver = prop.getProperty("collect.db.driver");
			config.collectDbUsername = prop.getProperty("collect.db.username");
			config.collectDbPassword = prop.getProperty("collect.db.password");
			return config;
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}
	}
	
	public static Configuration getConfig() {
		return config;
	}
	
	public static class Configuration {
		
		private String sourceFileLocation;
		private String collectDbUrl;
		private String collectDbDriver;
		private String collectDbUsername;
		private String collectDbPassword;

		public String getSourceFileLocation() {
			return sourceFileLocation;
		}

		public String getCollectDbUrl() {
			return collectDbUrl;
		}

		public String getCollectDbDriver() {
			return collectDbDriver;
		}

		public String getCollectDbUsername() {
			return collectDbUsername;
		}

		public String getCollectDbPassword() {
			return collectDbPassword;
		}
	}
	
}
