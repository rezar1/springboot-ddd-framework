package com.zero.ddd.core.jpa.columnConverter;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-11-20 03:55:32
 * @Desc 些年若许,不负芳华.
 *
 */
public class DefaultAesEncryConverter extends AesEncryConverter {
	
	private static final String DEFAULT_PASSWORD = "4.2q2,wo6jzpzzwyv";

	public DefaultAesEncryConverter() {
		super(DEFAULT_PASSWORD, "_");
	}

}