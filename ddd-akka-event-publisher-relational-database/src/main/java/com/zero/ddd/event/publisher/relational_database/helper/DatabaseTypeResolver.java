package com.zero.ddd.event.publisher.relational_database.helper;

import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;

import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-10-12 07:55:29
 * @Desc 些年若许,不负芳华.
 *
 */
@Slf4j
public class DatabaseTypeResolver {

	public static Optional<String> getDatabaseType(
			JdbcTemplate jdbcTemplate) {
		try {
			return 
					Optional.ofNullable(
							jdbcTemplate.getDataSource().getConnection().getMetaData().getDatabaseProductName());
		} catch (Exception e) {
			log.error("获取数据库实例类型失败:{}", e);
		}
		return Optional.empty();
	}

}