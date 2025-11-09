package com.ua.pohribnyi.fitadvisorbot.util.exception;

public class StravaAuthException extends RuntimeException {

	public StravaAuthException(String message) {
		super(message);
	}

	public StravaAuthException(String message, Throwable cause) {
		super(message, cause);
	}

}
