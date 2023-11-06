package com.zero.ddd.core.starter;

import java.util.List;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2022-08-02 04:53:17
 * @Desc 些年若许,不负芳华.
 *
 */
@Getter
@Setter(value = AccessLevel.PROTECTED)
public class InitConfigHolder {
	
	private List<String> scanPackages; 

}

