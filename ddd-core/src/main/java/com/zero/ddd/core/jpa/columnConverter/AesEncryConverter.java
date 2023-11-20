package com.zero.ddd.core.jpa.columnConverter;

import javax.persistence.AttributeConverter;
import javax.validation.constraints.Size;

import com.zero.ddd.core.model.AssertionConcern;
import com.zero.helper.AesUtil;
import com.zero.helper.GU;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-11-20 03:17:36
 * @Desc 些年若许,不负芳华.
 *
 */
public abstract class AesEncryConverter extends AssertionConcern implements AttributeConverter<String, String> {
	
	@Size(min = 1, max = 4, message = "加密后字符串前缀必须在1-4个字符, 如Task")
	private final String encryPrefix;
	@Size(min = 16, max = 16, message = "密码必须为16个字符")
	private final String password;
	
	public AesEncryConverter(
			String encryPrefix,
			String password) {
		this.encryPrefix = encryPrefix + ":";
		this.password = password;
		this.validate();
	}

	@Override
	public String convertToDatabaseColumn(
			String attribute) {
		if (GU.isNullOrEmpty(attribute)) {
			return null;
		}
		if (attribute.startsWith(encryPrefix)) {
			return attribute;
		} else {
			return
					this.cipherPassword();
		}
	}

	@Override
	public String convertToEntityAttribute(
			String dbData) {
    	if (dbData.startsWith(this.encryPrefix)) {
    		return 
    				AesUtil.decryptAndBase64Decode(
    						this.password, 
    						dbData.replace(
    								this.encryPrefix, ""));
    	} else {
    		return dbData;
    	}
	}
	
	private String cipherPassword() {
		return 
				this.encryPrefix + AesUtil.cipherByDefaultAndBase64(this.password);
	}

}