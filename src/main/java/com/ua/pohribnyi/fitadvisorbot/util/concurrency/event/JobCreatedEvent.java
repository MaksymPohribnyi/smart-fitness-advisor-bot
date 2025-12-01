package com.ua.pohribnyi.fitadvisorbot.util.concurrency.event;

public class JobCreatedEvent {

	private final Long jobId;
	private final String prompt;

	public JobCreatedEvent(Long jobId, String prompt) {
		super();
		this.jobId = jobId;
		this.prompt = prompt;
	}

	public Long getJobId() {
		return jobId;
	}

	public String getPrompt() {
		return prompt;
	}

}
