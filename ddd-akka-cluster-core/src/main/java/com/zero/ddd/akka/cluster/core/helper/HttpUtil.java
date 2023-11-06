package com.zero.ddd.akka.cluster.core.helper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Optional;

import org.apache.commons.io.IOUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2022-07-15 12:12:41
 * @Desc 些年若许,不负芳华.
 *
 */
@Slf4j
public class HttpUtil {
	
	public static Optional<byte[]> readFromUrl(
			String url) {
		try {
			URL urlObj = new URL(url);
			try (InputStream openStream = urlObj.openStream()) {
				byte[] buffer = new byte[openStream.available()];
				IOUtils.readFully(openStream, buffer);
				return Optional.of(buffer);
			}
		} catch (IOException e) {
			log.error("error read from url:{}", url, e);
		}
		return Optional.empty();
	}

}

