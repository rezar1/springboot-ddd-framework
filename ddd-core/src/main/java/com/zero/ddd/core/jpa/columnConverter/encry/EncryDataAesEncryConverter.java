package com.zero.ddd.core.jpa.columnConverter.encry;

import com.zero.ddd.core.jpa.columnConverter.encry.EncryDataAesEncryConverter.EncryData;
import com.zero.helper.GU;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-11-20 03:17:36
 * @Desc 些年若许,不负芳华.
 *
 */
public class EncryDataAesEncryConverter extends AesEncryConverter<EncryData> {
	
	public EncryDataAesEncryConverter(
			String encryPrefix,
			String password) {
		this(false, encryPrefix, password);
	}
	
	public EncryDataAesEncryConverter(
			boolean decrypty,
			String encryPrefix,
			String password) {
		super(decrypty, encryPrefix, password);
	}
	
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class EncryData {
		private String encryData;
	}

	@Override
	protected String parseToEncryData(
			EncryData attribute) {
		return attribute == null || GU.isNullOrEmpty(attribute.getEncryData()) ? 
				null : attribute.getEncryData();
	}

	@Override
	protected EncryData parseFromEncryData(
			String encryDataRet) {
		return 
				new EncryData(encryDataRet);
	}
	
}