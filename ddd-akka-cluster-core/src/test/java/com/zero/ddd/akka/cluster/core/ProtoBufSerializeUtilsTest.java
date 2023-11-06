package com.zero.ddd.akka.cluster.core;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.zero.ddd.akka.cluster.core.helper.ProtoBufSerializeUtils;
import com.zero.helper.GU;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2022-08-16 11:54:54
 * @Desc 些年若许,不负芳华.
 *
 */
@Slf4j
public class ProtoBufSerializeUtilsTest {
	
	@Test
	public void testSplit() {
		List<Integer> datas = new ArrayList<>();
		datas.add(0);
		datas.add(1);
		datas.add(2);
		GU.split(datas, 2)
		.forEach(taskIds -> {
			log.info("batchTaskIds are:{}", taskIds);
		});;
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testList() {
		List<Integer> datas = new ArrayList<>();
		datas.add(0);
		datas.add(1);
		datas.add(2);
		byte[] serialize = ProtoBufSerializeUtils.serialize(datas);
		List<Integer> deserialize = ProtoBufSerializeUtils.deserialize(serialize, List.class);
		log.info("deserialize are:{}", deserialize);
	}
	
	@Test
	public void testSerialLong() {
		Long val = System.nanoTime();
		log.info("val:{}", val);
		byte[] serialize = ProtoBufSerializeUtils.serialize(val);
		assertTrue(ProtoBufSerializeUtils.deserialize(serialize, Long.class).equals(val));
	}
	
	@Test
	public void testInteger() {
		Integer val = 23;
		byte[] serialize = 
				ProtoBufSerializeUtils.serialize(val);
		log.info("deserialize:{}", ProtoBufSerializeUtils.deserialize(serialize, Integer.class));
	}
	
	@Test
	public void testArray() {
		HashMap<String, Object> valMap = new HashMap<>();
		valMap.put("age", 23);
		valMap.put("child", new TestUser("ys", null));
		Object[] array = 
				new Object[] {123, "Rezar", new TestUser("Rezar", valMap)};
		TestArgs args = new TestArgs(array);
		byte[] serialize = ProtoBufSerializeUtils.serialize(args);
		log.info("array deserialize:{}", ProtoBufSerializeUtils.deserialize(serialize, TestArgs.class));
	}
	
	@Test
	public void test() {
//		HashMap<String, Object> valMap = new HashMap<>();
//		valMap.put("age", 23);
//		valMap.put("child", new TestUser("ys", null));
//		TestUser user = new TestUser("Rezar", valMap);
//		byte[] serialize = ProtoBufSerializeUtils.serialize(user);
//		TestUser2 deserialize = deserialize(serialize, TestUser2.class);
//		log.info("deserialize:{}", deserialize);
	}
	
	@Data
	@AllArgsConstructor
	@ToString
	static class TestArgs {
		Object[] array;
	}
	
	@Data
	@AllArgsConstructor
	static class TestUser {
		String name;
		Map<String, Object> valMap;
	}
	
	@Data
	@AllArgsConstructor
	static class TestUser2 {
		String name;
		Map<String, Object> valMap;
	}

}

