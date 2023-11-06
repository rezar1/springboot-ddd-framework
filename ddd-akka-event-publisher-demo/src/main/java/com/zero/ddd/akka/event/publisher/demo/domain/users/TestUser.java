package com.zero.ddd.akka.event.publisher.demo.domain.users;

import java.util.List;

import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import org.hibernate.annotations.DynamicUpdate;

import com.zero.ddd.akka.event.publisher.demo.domain.company.CompanyId;
import com.zero.ddd.akka.event.publisher.demo.domain.users.dmEvents.DemoUserProfileChanged;
import com.zero.ddd.akka.event.publisher.demo.domain.users.dmEvents.NewDemoUser;
import com.zero.ddd.akka.event.publisher.demo.domain.users.vo.UserAddress;
import com.zero.ddd.akka.event.publisher.demo.domain.users.vo.UserId;
import com.zero.ddd.akka.event.publisher.demo.domain.users.vo.UserProfile;
import com.zero.ddd.core.event.publish.DomainEventPublisher;
import com.zero.ddd.core.jpa.columnConverter.ListToJsonConverter;
import com.zero.ddd.core.model.IdentifiedEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-09-26 08:07:08
 * @Desc 些年若许,不负芳华.
 *
 */
@Data
@Entity
@DynamicUpdate
@NoArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Table(
        name = "demo_user",
        uniqueConstraints = {
        		@UniqueConstraint(columnNames = { "companyId", "userId" })})
public class TestUser extends IdentifiedEntity {
	
	private CompanyId companyId;
	private UserId userId;
	private UserProfile userProfile;
	@Convert(converter = ListToJsonConverter.class)
	private List<UserAddress> addresses;
	
	public TestUser(
			CompanyId companyId, 
			UserId userId, 
			UserProfile userProfile,
			List<UserAddress> addresses) {
		this.companyId = companyId;
		this.userId = userId;
		this.userProfile = userProfile;
		this.addresses = addresses;
		DomainEventPublisher.publish(
				new NewDemoUser(
						this.companyId,
						this.userId,
						this.userProfile,
						this.addresses));
	}
	
	public void tryModifyUserProfile(
			UserProfile userProfile) {
		if (this.userProfile == null
				|| !this.userProfile.equals(userProfile)) {
			this.userProfile = userProfile;
			DomainEventPublisher.publish(
					new DemoUserProfileChanged(
							this.companyId,
							this.userId,
							this.userProfile));
		}
	}
	
	// others
	
	
}