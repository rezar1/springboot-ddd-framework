package com.zero.ddd.event.publisher.relational_database.define;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;

import com.zero.ddd.core.event.store.EventStore;
import com.zero.ddd.core.event.store.StoredEvent;
import com.zero.ddd.core.model.DomainEvent;
import com.zero.helper.GU;
import com.zero.helper.JacksonUtil;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-07-03 11:43:19
 * @Desc 些年若许,不负芳华.
 *
 */
public class EventStoreByDatabase implements EventStore {
	
    private JdbcTemplate jdbcTemplate;
    private String appendSql;

    public EventStoreByDatabase(
            JdbcTemplate jdbcTemplate,
            String eventTableName) {
        if (GU.isNullOrEmpty(eventTableName)) {
            throw new IllegalArgumentException("若需要指定存储事件的表名，则不允许传入空值");
        }
        this.jdbcTemplate = jdbcTemplate;
        this.checkTableExists(eventTableName);
        this.appendSql = 
        		String.format(
        				"insert into %s (type_name, event_body, event_time) values(?, ?, ?);", 
        				eventTableName);
    }

    private void checkTableExists(
    		String eventTableName) {
        //检查并在不存在表结构的时候初始化表
        String checkInitSql = "Create Table If Not Exists `" + eventTableName + "` (\n" + 
                "  `event_id` BIGINT NOT NULL AUTO_INCREMENT,\n" + 
                "  `type_name` VARCHAR(125)  NOT NULL,\n" + 
                "  `event_body` VARCHAR(2048)  NOT NULL,\n" + 
                "  `event_time` DATETIME NOT NULL,\n" + 
                "  `insert_time` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),\n" + 
                "  PRIMARY KEY (`event_id`),\n" + 
                "  INDEX `type_name_time_index` (`type_name` ASC, `insert_time` ASC))\n";
        this.jdbcTemplate.execute(checkInitSql);
    }

	@Override
	public List<StoredEvent> allStoredEventsBetween(
			long lowStoredEventId, 
			long highStoredEventId) {
		throw new UnsupportedOperationException();
	}

	@Override
	public List<StoredEvent> allStoredEventsSince(
			long storedEventId) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void append(
			DomainEvent domainEvent) {
        jdbcTemplate.update(
                appendSql,
                domainEvent.getClass().getSimpleName(), 
                JacksonUtil.obj2Str(domainEvent), 
                LocalDateTime.now());
	}

	@Override
	public long countStoredEvents() {
		throw new UnsupportedOperationException();
	}

}