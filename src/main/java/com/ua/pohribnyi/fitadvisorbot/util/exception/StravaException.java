package com.ua.pohribnyi.fitadvisorbot.util.exception;

public class StravaException extends RuntimeException {

	public StravaException(String message) {
		super(message);
	}

	public StravaException(String message, Throwable cause) {
		super(message, cause);
	}

}
