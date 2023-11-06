package com.zero.ddd.core.starter;

import java.util.Arrays;

import org.springframework.boot.ConfigurableBootstrapContext;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringApplicationRunListener;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2022-08-02 05:31:01
 * @Desc 些年若许,不负芳华.
 *
 *	使用spring.factories初始化
 *	
 */
public class StartAppClassNameListener implements SpringApplicationRunListener {
	
	public static final InitConfigHolder config = new InitConfigHolder();
	
	private SpringApplication application;
	
	public StartAppClassNameListener(
			SpringApplication application, 
			String[] args) {
		this.application = application;
		config.setScanPackages(
				Arrays.asList(
						this.application.getMainApplicationClass().getPackage().getName()));
	}
	
	public void environmentPrepared(
			ConfigurableBootstrapContext bootstrapContext,
			ConfigurableEnvironment environment) {
		
	}
}