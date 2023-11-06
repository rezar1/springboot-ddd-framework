package com.zero.ddd.akka.event.publisher.demo.domain.users.vo;

import javax.persistence.Embeddable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-03-22 11:27:44
 * @Desc 些年若许,不负芳华.
 *
 */
@Data
@Embeddable
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = { "userId" })
public class UserId {
    
    private Integer userId;
    
}