package com.zero.ddd.akka.cluster.core.initializer.config;

import org.springframework.beans.factory.annotation.Value;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2022-06-30 09:34:38
 * @Desc 些年若许,不负芳华.
 *
 */
public class AkkaPersistenceConfigOverride implements IOverriderAkkaClusterConfig {
	
	@Value("${spring.datasource.url}")
	private String dbUrl;
	@Value("${spring.datasource.username}")
	private String dbUser;
	@Value("${spring.datasource.password}")
	private String dbPassword;

	@Override
	public void doOverride(
			AkkaClusterProperties clusterConfig, 
			AkkaOverrideConfig overrideConfig) {
		overrideConfig.resolveWith("jdbc-persistence");
		overrideConfig.appendOverride("akka-persistence-jdbc.shared-databases.slick.db.url", dbUrl);
		overrideConfig.appendOverride("akka-persistence-jdbc.shared-databases.slick.db.user", dbUser);
		overrideConfig.appendOverride("akka-persistence-jdbc.shared-databases.slick.db.password", dbPassword);
	}

}

