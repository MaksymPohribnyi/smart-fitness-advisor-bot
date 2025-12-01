package com.ua.pohribnyi.fitadvisorbot.util.concurrency.event;

import org.springframework.context.ApplicationEvent;

import com.ua.pohribnyi.fitadvisorbot.enums.JobStatus;

/**
 * This event is published *after* the SyntheticDataProcessorService
 * successfully committed its transaction
 */
public class JobProcessedEvent extends ApplicationEvent {

	private final Long jobId;
	private final JobStatus status;

	public JobProcessedEvent(Object source, Long jobId, JobStatus status) {
		super(source);
		this.jobId = jobId;
		this.status = status;
	}

	public Long getJobId() {
		return jobId;
	}

	public JobStatus getStatus() {
		return status;
	}

}
