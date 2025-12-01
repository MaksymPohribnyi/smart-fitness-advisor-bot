package com.ua.pohribnyi.fitadvisorbot.enums;

/**
 * Tracks the status of an asynchronous generation job.
 */
public enum JobStatus {
	PENDING, // Job created, waiting for worker
	DOWNLOADING, // Worker 1 (ApiClient) is fetching data
	DOWNLOADED, // Worker 1 finished, raw JSON is saved
	PROCESSING, // Worker 2 (Processor) is parsing
	PENDING_RETRY, // Waiting to retry after a failure
	PROCESSED, // Worker 2 finished, relational data is saved
	FAILED // An error occurred in either step
}
