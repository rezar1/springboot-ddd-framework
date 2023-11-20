package com.zero.ddd.core.jpa.columnConverter.encry;

import javax.persistence.AttributeConverter;
import javax.validation.constraints.Size;

import com.zero.ddd.core.model.AssertionConcern;
import com.zero.helper.AesUtil;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-11-20 03:17:36
 * @Desc 些年若许,不负芳华.
 *
 */
public abstract class AesEncryConverter<T> extends AssertionConcern implements AttributeConverter<T, String> {
	
	private final boolean decrypty;
	@Size(min = 1, max = 4, message = "加密后字符串前缀必须在1-4个字符, 如Task")
	private final String encryPrefix;
	@Size(min = 16, max = 16, message = "密码必须为16个字符")
	private final String password;
	
	public AesEncryConverter(
			String encryPrefix,
			String password) {
		this(true, encryPrefix, password);
	}
	
	public AesEncryConverter(
			boolean decrypty,
			String encryPrefix,
			String password) {
		this.decrypty = decrypty;
		this.encryPrefix = encryPrefix + ":";
		this.password = password;
		this.validate();
	}

	@Override
	public String convertToDatabaseColumn(
			T attribute) {
		if (attribute == null) {
			return null;
		}
		String encryData = 
				this.parseToEncryData(attribute);
		if (encryData.startsWith(encryPrefix)) {
			return encryData;
		} else {
			return
					this.encryPrefix + AesUtil.cipherAndBase64(password, encryData);
		}
	}

	@Override
	public T convertToEntityAttribute(
			String encryData) {
		if (encryData == null) {
			return null;
		}
		String encryDataRet = encryData;
		if (this.decrypty
				&& encryData.startsWith(this.encryPrefix)) {
			encryDataRet = 
					AesUtil.decryptAndBase64Decode(
    						this.password, 
    						encryData.replace(
    								this.encryPrefix, ""));
		}
		return 
				parseFromEncryData(encryDataRet);
	}

	protected abstract String parseToEncryData(T attribute);
	protected abstract T parseFromEncryData(String encryDataRet);
	
}