package com.ua.pohribnyi.fitadvisorbot.util.concurrency.event;

public class DailyAdviceJobSubmittedEvent {

	private final Long jobId;

	public DailyAdviceJobSubmittedEvent(Long jobId) {
		this.jobId = jobId;
	}

	public Long getJobId() {
		return jobId;
	}

}
