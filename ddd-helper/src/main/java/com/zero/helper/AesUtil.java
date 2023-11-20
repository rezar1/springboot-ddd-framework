package com.zero.helper;

import java.security.Key;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2022-05-25 02:48:56
 * @Desc 些年若许,不负芳华.
 *
 */
public class AesUtil {
	
	private static final String UTF_8 = "utf-8";

	private static final String KEY_ALGORITHM = "AES";

	private static final String DEFAULT_CIPHER_ALGORITHM = "AES/ECB/PKCS5Padding";

	private static final String DEFAULT_PASSWORD = "4.2q2,wo6jzpfwyv";


	public static byte[] cipher(String password, byte[] cipherDatas) {
		// AES加密
		Cipher cipher;
		try {
			cipher = Cipher.getInstance(DEFAULT_CIPHER_ALGORITHM);
			Key key = 
					new SecretKeySpec(
							password.getBytes(UTF_8),
							KEY_ALGORITHM);
			cipher.init(Cipher.ENCRYPT_MODE, key);
			byte[] aesData = cipher.doFinal(cipherDatas);
			return aesData;
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	public static byte[] decrypt(String password, byte[] datas) {
		try {
			Cipher cipher = 
					Cipher.getInstance(DEFAULT_CIPHER_ALGORITHM);
			Key key = new SecretKeySpec(password.getBytes(UTF_8), KEY_ALGORITHM);
			cipher.init(Cipher.DECRYPT_MODE, key);
			return cipher.doFinal(datas);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}
	
	public static byte[] decryptByDefault(byte[] data) {
		return decrypt(DEFAULT_PASSWORD, data);
	}
	
	public static byte[] cipherByDefault(byte[] data) {
		return cipher(DEFAULT_PASSWORD, data);
	}
	
	public static String cipherAndBase64(
			String password,
			String data) {
		return 
				Base64.getEncoder().encodeToString(
						cipher(
								password,
								data.getBytes()));
	}
	
	public static String decryptAndBase64Decode(
			String password,
			String chpheredData) {
		return 
				new String(
						AesUtil.decrypt(
								password,
								Base64.getDecoder()
								.decode(
										chpheredData.getBytes())));
	}
	
	public static String decryptByDefaultAndBase64Decode(
			String chpheredData) {
		return 
				new String(
						AesUtil.decryptByDefault(
								Base64.getDecoder()
								.decode(
										chpheredData.getBytes())));
	}
	
	public static String cipherByDefaultAndBase64(
			String data) {
		return 
				Base64.getEncoder().encodeToString(
						cipherByDefault(
								data.getBytes()));
	}
	
}

