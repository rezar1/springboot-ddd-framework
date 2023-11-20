package com.zero.ddd.core.jpa.columnConverter.encry;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-11-20 03:17:36
 * @Desc 些年若许,不负芳华.
 *
 */
public class DefaultEncryDataAesEncryConverter extends EncryDataAesEncryConverter {
	
	private static final String DEFAULT_PERFIX = "_";
	private static final String DEFAULT_PASSWORD = "4.2q2,wo6jzpzzwy";

	public DefaultEncryDataAesEncryConverter() {
		super(false, DEFAULT_PERFIX, DEFAULT_PASSWORD);
	}
	
//	public static void main(String[] args) {
//		DefaultEncryDataAesEncryConverter converter = 
//				new DefaultEncryDataAesEncryConverter();
//		String convertToDatabaseColumn = 
//				converter.convertToDatabaseColumn(
//						new EncryData("Rezar"));
//		EncryData convertToEntityAttribute = 
//				converter.convertToEntityAttribute(
//						convertToDatabaseColumn);
//		System.out.println(convertToDatabaseColumn);
//		System.out.println(convertToEntityAttribute);
//		convertToDatabaseColumn = 
//				converter.convertToDatabaseColumn(
//						convertToEntityAttribute);
//		System.out.println(convertToDatabaseColumn);
//	}
	
}