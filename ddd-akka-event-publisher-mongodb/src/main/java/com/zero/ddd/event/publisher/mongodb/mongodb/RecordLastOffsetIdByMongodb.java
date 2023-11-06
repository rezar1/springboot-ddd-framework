package com.zero.ddd.event.publisher.mongodb.mongodb;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import com.zero.ddd.akka.event.publisher2.event.IRecordLastOffsetId;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-06-13 05:47:20
 * @Desc 些年若许,不负芳华.
 *
 */
public class RecordLastOffsetIdByMongodb implements IRecordLastOffsetId {
	
	@Autowired
	private MongoTemplate mongoTemplate;

	@Override
	public void saveLastOffset(String key, String lastOffset) {
		Update update = new Update();
		update.set("lastOffset", lastOffset);
		this.mongoTemplate.upsert(
				new Query(
						Criteria.where("key").is(key)),
				update, 
				LastOffset.class);
	}

	@Override
	public Optional<String> lastOffset(String key) {
		return 
				Optional.ofNullable(
						this.mongoTemplate.findOne(
								new Query(
										Criteria.where("key").is(key)), 
								LastOffset.class))
				.map(LastOffset::getLastOffset);
	}
	
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	@Document("last_offset_recorder")
	public static class LastOffset {
		@Indexed
		private String key;
		private String lastOffset;
	}
	
}