package com.zero.ddd.akka.event.publisher.demo.domain.users.dmEvents;

import java.util.List;

import com.zero.ddd.akka.event.publisher.demo.domain.company.CompanyId;
import com.zero.ddd.akka.event.publisher.demo.domain.users.vo.UserAddress;
import com.zero.ddd.akka.event.publisher.demo.domain.users.vo.UserId;
import com.zero.ddd.akka.event.publisher.demo.domain.users.vo.UserProfile;
import com.zero.ddd.core.model.DomainEvent;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-09-26 08:23:14
 * @Desc 些年若许,不负芳华.
 *
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NewDemoUser implements DomainEvent {
	
	private CompanyId companyId;
	private UserId userId;
	private UserProfile userProfile;
	private List<UserAddress> address;

}