package org.openforis.ceo.migrator;

import org.apache.commons.dbcp2.BasicDataSource;
import org.openforis.ceo.migrator.ConfigurationProvider.Configuration;
import org.openforis.collect.persistence.DbInitializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class FullMigrator {

	@Autowired
	private ImageryMigrator imageryMigrator;
	@Autowired
	private ProjectsMigrator projectMigrator;
	
	public void migrate() {
		imageryMigrator.migrate();
		projectMigrator.migrate();
	}
	
	public static void main(String[] args) {
		BasicDataSource dataSource = new BasicDataSource();
		Configuration config = ConfigurationProvider.getConfig();
		dataSource.setUrl(config.getCollectDbUrl());
		dataSource.setDriverClassName(config.getCollectDbDriver());
		
		new DbInitializer(dataSource).start();
		
		try (ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext("ceo-migrator-context.xml")) {
			FullMigrator fullMigrator = ctx.getBean(FullMigrator.class);
			fullMigrator.migrate();
		}
	}
	
}
