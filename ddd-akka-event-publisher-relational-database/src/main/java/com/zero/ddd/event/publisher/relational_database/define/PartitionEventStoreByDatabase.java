package com.zero.ddd.event.publisher.relational_database.define;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;

import com.zero.ddd.akka.event.publisher2.domain.partitionEvent.PartitionEvent;
import com.zero.ddd.akka.event.publisher2.domain.partitionEvent.PartitionEventStore;
import com.zero.ddd.akka.event.publisher2.helper.MicrTimeFormat;
import com.zero.helper.GU;
import com.zero.helper.JacksonUtil;

import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-07-03 11:44:13
 * @Desc 些年若许,不负芳华.
 *
 */
@Slf4j(topic = "event")
public class PartitionEventStoreByDatabase implements PartitionEventStore {
	
	private JdbcTemplate jdbcTemplate;
	private String appendSql;

    public PartitionEventStoreByDatabase(
            JdbcTemplate jdbcTemplate,
            String partitionEventTableName) {
        if (GU.isNullOrEmpty(partitionEventTableName)) {
            throw new IllegalArgumentException("若需要指定存储分片事件的表名，则不允许传入空值");
        }
        this.jdbcTemplate = jdbcTemplate;
        this.checkTableExists(partitionEventTableName);
        this.appendSql = 
        		String.format(
        				"INSERT INTO %s (`synchronizer_id`, `event_id`, `type_name`, `event_body`, `event_time`, `partition_id`, `insert_time`) VALUES (?, ?, ?, ?, ?, ?, ?)", 
        				partitionEventTableName);
    }

    private void checkTableExists(
    		String eventTableName) {
        //检查并在不存在表结构的时候初始化表
        String checkInitSql = 
        		"Create Table If Not Exists `" + eventTableName + "` (\n" + 
                "  `partition_event_id` BIGINT NOT NULL AUTO_INCREMENT,\n" + 
                "  `synchronizer_id` VARCHAR(125)  NOT NULL,\n" + 
                "  `event_id` BIGINT NOT NULL,\n" + 
                "  `type_name` VARCHAR(225)  NOT NULL,\n" + 
                "  `event_body` VARCHAR(2048)  NOT NULL,\n" + 
                "  `event_time` DATETIME NOT NULL,\n" + 
                "  `partition_id` INT NOT NULL,\n" + 
                "  `insert_time` varchar(30) ,\n" + 
                "  PRIMARY KEY (`partition_event_id`),\n" + 
                "  INDEX `time_index` (`insert_time` ASC),\n" +
                "  INDEX `synid_partition_time_index` (`synchronizer_id` ASC, `partition_id` ASC, `insert_time` ASC),\n" +
                "  UNIQUE INDEX `syn_event_id_uq` (`synchronizer_id` ASC, `event_id` ASC))\n";
        this.jdbcTemplate.execute(checkInitSql);
        this.jdbcTemplate.execute("ALTER TABLE `" + eventTableName + "` MODIFY COLUMN `insert_time` varchar(30) NULL");
    }

	@Override
	public boolean storePartitionEvent(
			List<PartitionEvent> storedEventList) {
		storedEventList.forEach(event -> {
			try {
				this.jdbcTemplate.update(
						appendSql, 
						event.getSynchronizerId(),
						event.getEventId(),
						event.getTypeName(),
						event.getEventBody(),
						event.getEventTime(),
						event.getPartition(),
						MicrTimeFormat.currentFormatTime());
			} catch(org.springframework.dao.DuplicateKeyException ex) {
				log.warn("storePartitionEvent:{} duplicated", JacksonUtil.obj2Str(event));
			} catch (Exception e) {
				log.warn("storePartitionEvent:{} error", JacksonUtil.obj2Str(event));
				log.error("storePartitionEvent error:{}", e);
			}
		});
		return true;
	}

}