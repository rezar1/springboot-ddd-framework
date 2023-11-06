package com.zero.ddd.akka.event.publisher.demo.domain.users;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.zero.ddd.akka.event.publisher.demo.domain.company.CompanyId;
import com.zero.ddd.akka.event.publisher.demo.domain.users.vo.UserId;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-09-26 08:26:38
 * @Desc 些年若许,不负芳华.
 *
 */
@Repository
public interface TestUserRepo extends JpaRepository<TestUser, Long> {
	
	public Optional<TestUser> findByCompanyIdAndUserId(CompanyId companyId, UserId userId);

	public boolean existsByCompanyIdAndUserId(CompanyId companyId, UserId userId);

}