package com.zero.ddd.akka.event.publisher.demo;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.junit4.SpringRunner;

import com.zero.ddd.akka.event.publisher.demo.applications.TestUserApplication;
import com.zero.ddd.akka.event.publisher.demo.domain.company.CompanyId;
import com.zero.ddd.akka.event.publisher.demo.domain.users.vo.UserAddress;
import com.zero.ddd.akka.event.publisher.demo.domain.users.vo.UserId;
import com.zero.ddd.akka.event.publisher.demo.domain.users.vo.UserProfile;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-09-27 03:23:40
 * @Desc 些年若许,不负芳华.
 *
 */
@Rollback(false)
@RunWith(SpringRunner.class)
@SpringBootTest(classes = EventPublisherDemoApp.class)
public class EventPublisherDemoAppTest {
	
	@Autowired
	private TestUserApplication testUserApp;
	
	@Test
	public void testSome() throws InterruptedException {
		TimeUnit.SECONDS.sleep(10);
		int companyId = (int) System.nanoTime();
		IntStream.range(0, 1000)
		.parallel()
		.forEach(index -> {
			this.testUserApp.registeDemoUser(
					new CompanyId(companyId),
					new UserId(index),
					new UserProfile("Rezar", "https://profile-avatar.csdnimg.cn/e9455bf5671243e383f3e3ffdcd16ece_sinat_29131797.jpg!1"),
					Arrays.asList(new UserAddress("北京", "海淀", "xxxxx")));
		});
		TimeUnit.SECONDS.sleep(69);
	}
	
}
