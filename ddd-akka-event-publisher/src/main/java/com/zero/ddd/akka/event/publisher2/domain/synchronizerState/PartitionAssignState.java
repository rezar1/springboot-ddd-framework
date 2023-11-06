package com.zero.ddd.akka.event.publisher2.domain.synchronizerState;

import com.zero.ddd.akka.event.publisher2.domain.synchronizerState.vo.AssignStatus;

import lombok.Data;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-06-12 05:35:24
 * @Desc 些年若许,不负芳华.
 *
 */
@Data
public class PartitionAssignState {
	
	private int partitionId;
	private AssignStatus assignStatus;
	private String assignedTo;
	
	public PartitionAssignState(int partitionId) {
		this.partitionId = partitionId;
		this.assignStatus = AssignStatus.WAIT_ASSIGN;
	}
	
	public void resetToWaitAssign() {
		this.assignStatus = AssignStatus.WAIT_ASSIGN;
	}

	public boolean waitAssign() {
		return this.assignStatus == AssignStatus.WAIT_ASSIGN;
	}

	public void assignTo(String consumerId) {
		this.assignedTo = consumerId;
		this.assignStatus = AssignStatus.ASSIGNED;
	}

}