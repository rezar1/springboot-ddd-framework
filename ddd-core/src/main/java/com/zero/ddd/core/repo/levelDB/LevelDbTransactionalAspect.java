package com.zero.ddd.core.repo.levelDB;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import javax.annotation.PostConstruct;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.iq80.leveldb.DB;
import org.springframework.beans.factory.annotation.Value;

@Aspect
public class LevelDbTransactionalAspect {

	@Value("${leveldb.path:/tmp/leveldb}")
	private String databasePath;

	private DB db;

	@PostConstruct
	public void init() throws IOException {
		Files.createDirectories(Paths.get(databasePath));
		db = LevelDBProvider.instance().databaseFrom(databasePath);
	}

	@Around("@annotation(com.zero.ddd.core.repo.levelDB.LevelDbTransactional)")
	public Object doRequestMethod(ProceedingJoinPoint pjp) throws Throwable {
		return process(pjp);
	}

	Object process(ProceedingJoinPoint pjp) throws Throwable {
		// 判断是否存在事务
		boolean hasTransBefore = false;
		try {
			LevelDBUnitOfWork current = 
					LevelDBUnitOfWork.current();
			if (current == null) {
				LevelDBUnitOfWork.start(db);
				hasTransBefore = true;
			}
			Object result = pjp.proceed();
			if (hasTransBefore) {
				LevelDBUnitOfWork.current().commit();
			}
			return result;
		} catch (Throwable e) {
			if (hasTransBefore) {
				LevelDBUnitOfWork.current().rollback();
			}
			throw e;
		}
	}
}
