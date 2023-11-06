package com.zero.ddd.core.helper;

import java.nio.charset.Charset;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-05-24 04:46:53
 * @Desc 些年若许,不负芳华.
 *
 */
public class HashUtil {
	
	private static final HashFunction hashFun = Hashing.murmur3_128();
	
	public static long hashValue(
			String str) {
		return hashFun.hashString(
				str,
				Charset.defaultCharset()).asLong();
	}

}