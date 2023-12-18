package com.zero.ddd.akka.ratelimiter.actor;

import java.time.Duration;
import java.util.concurrent.ExecutionException;

import com.zero.ddd.akka.cluster.core.helper.ClientAskRemoteExecutorConfig;
import com.zero.ddd.akka.ratelimiter.actor.RateLimiterClientActor.AcquirePermitsResp;
import com.zero.ddd.akka.ratelimiter.actor.RateLimiterClientActor.TryAcquirePermitsResp;
import com.zero.ddd.akka.ratelimiter.limiter.VisitRateLimiter;
import com.zero.ddd.akka.ratelimiter.state.RateLimiterRunningState;
import com.zero.ddd.akka.ratelimiter.state.StateDatabase;
import com.zero.ddd.akka.ratelimiter.state.vo.RateLimiterConfig;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.SupervisorStrategy;
import akka.actor.typed.delivery.ConsumerController;
import akka.actor.typed.delivery.ConsumerController.Confirmed;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.receptionist.Receptionist;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-12-08 04:57:45
 * @Desc 些年若许,不负芳华.
 *
 */
@Slf4j
public class RateLimiterServerActor {
	
	public static Behavior<RateLimiterServerCommand> create(
			RateLimiterConfig rateLimiterConfig,
			StateDatabase rateLimiterStateDatabase,
			ActorRef<ConsumerController.Start<RateLimiterServerCommand>> consumerController) {
		return Init.create(rateLimiterConfig, rateLimiterStateDatabase, consumerController);
	}
	
	static class Init extends AbstractBehavior<RateLimiterServerCommand> {

		private final RateLimiterConfig rateLimiterConfig;
		private final StateDatabase rateLimiterStateDatabase;
		private final ActorRef<ConsumerController.Start<RateLimiterServerCommand>> consumerController;

		private Init(
				RateLimiterConfig rateLimiterConfig, 
				StateDatabase rateLimiterStateDatabase,
				ActorContext<RateLimiterServerCommand> context, 
				ActorRef<ConsumerController.Start<RateLimiterServerCommand>> consumerController) {
			super(context);
			this.rateLimiterConfig = rateLimiterConfig;
			this.rateLimiterStateDatabase = rateLimiterStateDatabase;
			this.consumerController = consumerController;
		}

		static Behavior<RateLimiterServerCommand> create(
				RateLimiterConfig rateLimiterConfig, 
				StateDatabase rateLimiterStateDatabase,
				ActorRef<ConsumerController.Start<RateLimiterServerCommand>> consumerController) {
			return Behaviors.setup(context -> {
				context.pipeToSelf(
						rateLimiterStateDatabase.loadRateLimiterRunningState(rateLimiterConfig.getRateLimiterName()),
						(state, exc) -> {
							log.info("限流器:[{}], 分布式缓存中的数据:{}", rateLimiterConfig.getRateLimiterName(), state);
							if (exc == null)
								return new InitialState(state);
							else
								return new InnerDBError(exc);
						});
				return new Init(
						rateLimiterConfig,
						rateLimiterStateDatabase,
						context, 
						consumerController);
			});
		}

		@Override
		public Receive<RateLimiterServerCommand> createReceive() {
			return newReceiveBuilder()
					.onMessage(InitialState.class, this::onInitialState)
					.onMessage(InnerDBError.class, this::onDBError)
					.build();
		}
		
		private Behavior<RateLimiterServerCommand> onDBError(
				InnerDBError error) throws Exception {
			throw error.cause;
		}

		private Behavior<RateLimiterServerCommand> onInitialState(
				InitialState initial) {
			ActorRef<ConsumerController.Delivery<RateLimiterServerCommand>> deliveryAdapter = 
					getContext()
					.messageAdapter(
							ConsumerController.deliveryClass(), 
							d -> new CommandDelivery(
									d.message(), 
									d.confirmTo()));
			consumerController.tell(
					new ConsumerController.Start<>(
							deliveryAdapter));
			return Active.create(
					this.rateLimiterConfig, 
					this.rateLimiterStateDatabase, 
					initial.state);
		}
	}
	
	
	static class Active {
		
		public static Behavior<RateLimiterServerCommand> create(
				RateLimiterConfig rateLimiterConfig, 
				StateDatabase rateLimiterStateDatabase,
				RateLimiterRunningState state) {
			return 
					Behaviors.supervise(
							Behaviors.<RateLimiterServerCommand>setup(
									context -> 
										new Active(
												rateLimiterConfig, 
												state,
												rateLimiterStateDatabase, 
												context)
										.running()))
					.onFailure(
							Exception.class, 
							SupervisorStrategy.restart());
		}
		
		private final RateLimiterConfig rateLimiterConfig;
		private final StateDatabase rateLimiterStateDatabase;
		private final ActorContext<RateLimiterServerCommand> context;
		
		private RateLimiterRunningState state;
		private VisitRateLimiter rateLimiter;
		
		public Active(
				RateLimiterConfig rateLimiterConfig,
				RateLimiterRunningState state,
				StateDatabase rateLimiterStateDatabase,
				ActorContext<RateLimiterServerCommand> context) {
			this.context = context;
			this.rateLimiterConfig = rateLimiterConfig;
			this.rateLimiter = 
					this.initRateLimiter(
							rateLimiterConfig.getPermitsPerSecond(),
							state);
			this.rateLimiterStateDatabase = rateLimiterStateDatabase;
			this.registeAsSchedulerService();
			log.info(
					"限流器:[{}] 启动成功!!", 
					this.rateLimiterConfig.getRateLimiterName());
		}
		
		private Behavior<RateLimiterServerCommand> running() {
			return Behaviors
					.receive(RateLimiterServerCommand.class)
					.onMessage(CommandDelivery.class, this::onCommandDelivery)
					.build();
		}
		
		private Behavior<RateLimiterServerCommand> onCommandDelivery(
				CommandDelivery delivery) {
			RateLimiterServerCommand command = delivery.command;
			if (command instanceof TryAcquirePermitsCommand) {
				TryAcquirePermitsCommand tryAcquireCmd =
						(TryAcquirePermitsCommand) delivery.command;
				long tryAcquireNeedSleepMicro = 
						this.rateLimiter.tryAcquire(
								tryAcquireCmd.permits, 
								tryAcquireCmd.timeout);
				tryAcquireCmd.acquireReplyTo
				.tell(
						new TryAcquirePermitsResp(
								tryAcquireNeedSleepMicro));
			} else if(command instanceof AcquirePermitsCommand) {
				AcquirePermitsCommand acquireCmd = 
						(AcquirePermitsCommand) delivery.command;
				long acquire = 
						this.rateLimiter.acquire(
								acquireCmd.permits);
				acquireCmd.acquireReplyTo
				.tell(
						new AcquirePermitsResp(acquire));
			} else {
				return Behaviors.unhandled();
			}
			this.saveState(
					delivery.confirmTo);
			return Behaviors.same();
		}
		
		private void saveState(
				ActorRef<Confirmed> confirmTo) {
			try {
				String rateLimiterName = this.rateLimiterConfig.getRateLimiterName();
				this.rateLimiterStateDatabase.updateRateLimiterRunningState(
						rateLimiterName,
						state)
				.whenCompleteAsync(
						(done, error) -> {
							if (done != null) {
								this.confirmToClient(confirmTo);
							} else {
								log.error("限流器:[" + rateLimiterName + "] 状态更新异常", error);
								throw new IllegalStateException(
										"限流器:[" + rateLimiterName + "] 状态更新异常",
										error);
							}
						}, 
						ClientAskRemoteExecutorConfig.askRemoteExecutor())
				.toCompletableFuture()
				.get();
			} catch (InterruptedException | ExecutionException e) {
				e.printStackTrace();
			}
		}

		private void confirmToClient(
				ActorRef<Confirmed> confirmTo) {
			if (confirmTo != null) {
				confirmTo.tell(
						ConsumerController.confirmed());
			}
		}

		private void registeAsSchedulerService() {
			this.context
			.getSystem()
			.receptionist()
			.tell(
					Receptionist.register(
							ServiceKeyHolder.rateLimiterServerKey(
									this.rateLimiterConfig.getRateLimiterName()),
							this.context.getSelf()));
		}
		
		private VisitRateLimiter initRateLimiter(
				double permitsPerSecond, 
				RateLimiterRunningState state) {
			if (state != null
					&& permitsPerSecond != state.getPermitsPerSecond()) {
				state = null;
			}
			if (state == null) {
				return 
						VisitRateLimiter.create(permitsPerSecond);
			}
			return 
					VisitRateLimiter.create(state);
		}
		
	}
	
	// -------- 接口/命令类定义 --------
	
	public static interface RateLimiterServerCommand extends RateLimiterCommand {}
	
	static class TryAcquirePermitsCommand implements RateLimiterServerCommand {
		private int permits;
		private Duration timeout;
		private ActorRef<TryAcquirePermitsResp> acquireReplyTo;
	} 
	
	static class AcquirePermitsCommand implements RateLimiterServerCommand {
		private int permits;
		private ActorRef<AcquirePermitsResp> acquireReplyTo;
	} 
	
	private static class CommandDelivery implements RateLimiterServerCommand {
		
		final RateLimiterServerCommand command;
		final ActorRef<ConsumerController.Confirmed> confirmTo;

		private CommandDelivery(
				RateLimiterServerCommand command, 
				ActorRef<ConsumerController.Confirmed> confirmTo) {
			this.command = command;
			this.confirmTo = confirmTo;
		}
		
	}
	
	private static class InitialState implements RateLimiterServerCommand {
		final RateLimiterRunningState state;
		private InitialState(
				RateLimiterRunningState state) {
			this.state = state;
		}
	}
	
	private static class InnerDBError implements RateLimiterServerCommand {
		final Exception cause;
		private InnerDBError(Throwable cause) {
			if (cause instanceof Exception)
				this.cause = (Exception) cause;
			else
				this.cause = 
				new RuntimeException(
						cause.getMessage(), 
						cause);
		}
	}

}