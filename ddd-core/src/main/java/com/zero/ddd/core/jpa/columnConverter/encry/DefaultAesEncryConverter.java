package com.zero.ddd.core.jpa.columnConverter.encry;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-11-20 03:55:32
 * @Desc 些年若许,不负芳华.
 *
 */
public class DefaultAesEncryConverter extends AesEncryConverter<String> {
	
	private static final String DEFAULT_PERFIX = "_";
	private static final String DEFAULT_PASSWORD = "42q2,wo6jzpzzwyv";

	public DefaultAesEncryConverter() {
		super(DEFAULT_PERFIX, DEFAULT_PASSWORD);
	}

	@Override
	protected String parseToEncryData(String attribute) {
		return attribute;
	}

	@Override
	protected String parseFromEncryData(String encryDataRet) {
		return encryDataRet;
	}

}