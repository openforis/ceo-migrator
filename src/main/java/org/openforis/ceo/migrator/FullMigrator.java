package org.openforis.ceo.migrator;

import java.util.logging.Logger;

import org.apache.commons.dbcp2.BasicDataSource;
import org.openforis.ceo.migrator.ConfigurationProvider.Configuration;
import org.openforis.collect.persistence.DbInitializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class FullMigrator {

	private static final Logger LOGGER = Logger.getAnonymousLogger();
	
	@Autowired
	private ImageryMigrator imageryMigrator;
	@Autowired
	private ProjectsMigrator projectMigrator;
	@Autowired
	private DataMigrator dataMigrator;
	
	public void migrate() {
		imageryMigrator.migrate();
		projectMigrator.migrate();
		dataMigrator.migrate();
	}
	
	public static void main(String[] args) {
		LOGGER.info("========== Starting migration... ============");
		BasicDataSource dataSource = new BasicDataSource();
		Configuration config = ConfigurationProvider.getConfig();
		dataSource.setUrl(config.getCollectDbUrl());
		dataSource.setDriverClassName(config.getCollectDbDriver());
		
		LOGGER.info("Initializing Collect schema...");
		new DbInitializer(dataSource).start();
		LOGGER.info("Collect schema initialized.");
		
		try (ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext("ceo-migrator-context.xml")) {
			FullMigrator fullMigrator = ctx.getBean(FullMigrator.class);
			fullMigrator.migrate();
		}
		LOGGER.info("=========== Migration completed ==============");
	}
	
}
