package com.zero.ddd.event.publisher.relational_database.define;

import org.springframework.jdbc.core.JdbcTemplate;

import com.zero.ddd.akka.event.publisher2.event.IRecordEventConsumeResult;
import com.zero.helper.GU;

import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-08-06 10:25:45
 * @Desc 些年若许,不负芳华.
 *
 */
@Slf4j
public class RecordEventConsumeResultByDatabase implements IRecordEventConsumeResult {
	
	private JdbcTemplate jdbcTemplate;
	private String appendSql;

    public RecordEventConsumeResultByDatabase(
            JdbcTemplate jdbcTemplate,
            String recordTableName) {
        if (GU.isNullOrEmpty(recordTableName)) {
            throw new IllegalArgumentException("若需要指定存储事件消费结果的表名，则不允许传入空值");
        }
        this.jdbcTemplate = jdbcTemplate;
        this.checkTableExists(recordTableName);
        this.appendSql = 
        		String.format(
        				"INSERT INTO %s (`synchronizer_id`, `event_id`, `result`) VALUES (?, ?, ?)", 
        				recordTableName);
    }

    private void checkTableExists(
    		String eventTableName) {
        //检查并在不存在表结构的时候初始化表
        String checkInitSql = 
        		"Create Table If Not Exists `" + eventTableName + "` (\n" + 
                "  `id` BIGINT NOT NULL AUTO_INCREMENT,\n" + 
                "  `synchronizer_id` VARCHAR(125)  NOT NULL,\n" + 
                "  `event_id` BIGINT NOT NULL,\n" + 
                "  `result` VARCHAR(1024),\n" + 
                "  `consume_time` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),\n" + 
                "  PRIMARY KEY (`id`),\n" + 
                "  INDEX `synid_event_id_index` (`synchronizer_id` ASC, `event_id` ASC))\n";
        this.jdbcTemplate.execute(checkInitSql);
    }

	@Override
	public void recordResult(String synchornizerId, String eventId, String resultMsg) {
		try {
			this.jdbcTemplate.update(
					appendSql, 
					synchornizerId,
					eventId,
					resultMsg);
		} catch (Exception e) {
			log.warn("recordResult:{}-{}-{} error", synchornizerId, eventId, resultMsg);
			log.error("recordResult error:{}", e);
		}
	}

}