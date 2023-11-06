package com.zero.ddd.core.toolsets.lock;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2022-08-01 07:41:50
 * @Desc 些年若许,不负芳华.
 *
 */
public enum LockHoldModel {
	
	DEFAULT, 		//正常获取释放模式
	HOLD_TIMES, 	//占有指定次数
	HOLD_DURATION,	//占用指定时长
	HOLD_ALWAYS;		//一直占用
	
	public static interface HoldValidition {
		default void hold() {}
		boolean canContinueHold();
		default void exitHold() {}
	}
	
	public static class AlwaysHoldValidition implements HoldValidition {
		
		public static final AlwaysHoldValidition INSTANCE = new AlwaysHoldValidition();
		
		@Override
		public boolean canContinueHold() {
			return true;
		}
		
	}
	
	public static class DefaultHoldValidition implements HoldValidition {
		
		public static final DefaultHoldValidition INSTANCE = new DefaultHoldValidition();
		
		@Override
		public boolean canContinueHold() {
			return false;
		}
		
	}
	
	public static class TimesHoldValidition implements HoldValidition {
		
		int holdMaxTimes;
		int curHoldTimes;
		
		TimesHoldValidition(
				int holdMaxTimes) {
			this.holdMaxTimes = holdMaxTimes;
		}
		
		@Override
		public void hold() {
			this.curHoldTimes += 1;
		}
		
		@Override
		public boolean canContinueHold() {
			return this.curHoldTimes < this.holdMaxTimes;
		}
		
		public void exitHold() {
			this.curHoldTimes = 0;
		}
		
	}
	
	public static class DurationHoldValidition implements HoldValidition {
		
		long continueHoldMillTime;
		long holdStart;
		
		DurationHoldValidition(
				long continueHoldMillTime) {
			this.continueHoldMillTime = continueHoldMillTime;
		}

		@Override
		public void hold() {
			this.holdStart = System.currentTimeMillis();
		}

		@Override
		public boolean canContinueHold() {
			return System.currentTimeMillis() - this.holdStart <= this.continueHoldMillTime;
		}
		
	}

	public static HoldValidition init(
			LockHoldModel holdModel, 
			long lockHoldUnit) {
		if (holdModel == null) {
			return DefaultHoldValidition.INSTANCE;
		}
		switch(holdModel) {
		case DEFAULT:
			return DefaultHoldValidition.INSTANCE;
		case HOLD_ALWAYS:
			return AlwaysHoldValidition.INSTANCE;
		case HOLD_DURATION:
			assertLockHoldUnitGtZero(HOLD_DURATION, lockHoldUnit);
			return new DurationHoldValidition(lockHoldUnit);
		case HOLD_TIMES:
			assertLockHoldUnitGtZero(HOLD_TIMES, lockHoldUnit);
			return new TimesHoldValidition((int) lockHoldUnit);
		default:
			break;
		}
		return null;
	}

	static void assertLockHoldUnitGtZero(
			LockHoldModel model,
			long lockHoldUnit) {
		if (lockHoldUnit <= 0) {
			throw new IllegalArgumentException("在"+ model +"模式下lockHoldUnit需大于0");
		}
	}

}

