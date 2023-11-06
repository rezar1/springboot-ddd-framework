package com.zero.ddd.akka.event.publisher.demo.applications;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.zero.ddd.akka.event.publisher.demo.domain.company.CompanyId;
import com.zero.ddd.akka.event.publisher.demo.domain.users.TestUser;
import com.zero.ddd.akka.event.publisher.demo.domain.users.TestUserRepo;
import com.zero.ddd.akka.event.publisher.demo.domain.users.vo.UserAddress;
import com.zero.ddd.akka.event.publisher.demo.domain.users.vo.UserId;
import com.zero.ddd.akka.event.publisher.demo.domain.users.vo.UserProfile;
import com.zero.ddd.akka.event.publisher.demo.helper.RetryTransactional;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-09-26 08:28:03
 * @Desc 些年若许,不负芳华.
 *
 */
@Component
public class TestUserApplication {
	
	@Autowired
	private TestUserRepo userRepo;
	
	@RetryTransactional
	public void registeDemoUser(
			CompanyId companyId,
			UserId userId,
			UserProfile profile,
			List<UserAddress> userAddresses) {
		if (this.userRepo.existsByCompanyIdAndUserId(companyId, userId)) {
			throw new IllegalArgumentException("用户已存在，请勿重复创建");
		}
		this.userRepo.save(
				new TestUser(
						companyId,
						userId,
						profile, 
						userAddresses));
	}
	
	@RetryTransactional
	public void modifyUserProfile(
			CompanyId companyId,
			UserId userId,
			UserProfile profile) {
		this.userRepo.findByCompanyIdAndUserId(
				companyId,
				userId)
		.ifPresent(user -> user.tryModifyUserProfile(profile));
	}

}