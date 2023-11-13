package com.zero.ddd.event.publisher.relational_database.define;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import com.zero.ddd.akka.event.publisher2.event.IRecordLastOffsetId;
import com.zero.ddd.event.publisher.relational_database.helper.DatabaseTypeResolver;
import com.zero.helper.GU;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-07-03 11:44:44
 * @Desc 些年若许,不负芳华.
 *
 */
public class RecordLastOffsetIdByDatabase implements IRecordLastOffsetId {
	
	private JdbcTemplate jdbcTemplate;
	
	private String appendSql;
	private String queryOffsetSql;

    public RecordLastOffsetIdByDatabase(
            JdbcTemplate jdbcTemplate,
            String partitionEventTableName) {
        if (GU.isNullOrEmpty(partitionEventTableName)) {
            throw new IllegalArgumentException("若需要指定存储LastOffset的表名，则不允许传入空值");
        }
        this.jdbcTemplate = jdbcTemplate;
        this.checkTableExists(partitionEventTableName);
        this.appendSql = 
        		String.format(
        				this.initAppendOffsetRecordSqlByEnv(), 
        				partitionEventTableName);
        this.queryOffsetSql = 
        		String.format(
        				"select sync_offset from %s where syn_partition_id = ?" , 
        				partitionEventTableName);
    }

    protected String initAppendOffsetRecordSqlByEnv() {
    	return 
    			DatabaseTypeResolver.getDatabaseType(jdbcTemplate)
    			.map(databaseType -> {
    				if (databaseType.equalsIgnoreCase("mysql")) {
    					return "REPLACE INTO";
    				}
    				return "MERGE INTO";
    			})
    			.orElse("MERGE INTO") + " %s (syn_partition_id, sync_offset) values(?, ?);";
	}

	private void checkTableExists(
    		String eventTableName) {
        //检查并在不存在表结构的时候初始化表
        String checkInitSql = 
        		"Create Table If Not Exists `" + eventTableName + "` (\n" + 
                "  `syn_partition_id` VARCHAR(125) NOT NULL,\n" + 
                "  `sync_offset` VARCHAR(255) NOT NULL,\n" + 
                "  `updated_at` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),\n" + 
                "  PRIMARY KEY (`syn_partition_id`))\n";
        this.jdbcTemplate.execute(checkInitSql);
    }

	@Override
	public void saveLastOffset(
			String synPartitionId,
			String lastOffset) {
		try {
			this.jdbcTemplate.update(
					appendSql, 
					synPartitionId,
					lastOffset);
		} catch (DataAccessException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public Optional<String> lastOffset(
			String synPartitionId) {
		return Optional.ofNullable(
				this.jdbcTemplate.query(
						queryOffsetSql, 
						new ResultSetExtractor<String>() {
							@Override
							public String extractData(ResultSet rs) throws SQLException, DataAccessException {
								if (rs.next()) {
									return rs.getString("sync_offset");
								}
								return null;
							}
						}, 
						synPartitionId));
	}

}