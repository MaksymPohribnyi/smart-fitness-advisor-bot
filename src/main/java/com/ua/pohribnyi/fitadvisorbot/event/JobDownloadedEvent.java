package com.ua.pohribnyi.fitadvisorbot.event;

import org.springframework.context.ApplicationEvent;

/**
 * This event is published *after* the GeminiApiClient successfully downloads
 * and commits the raw JSON response.
 */
public class JobDownloadedEvent extends ApplicationEvent {

	private final Long jobId;

	public JobDownloadedEvent(Object source, Long jobId) {
		super(source);
		this.jobId = jobId;
	}

	public Long getJobId() {
		return jobId;
	}
}