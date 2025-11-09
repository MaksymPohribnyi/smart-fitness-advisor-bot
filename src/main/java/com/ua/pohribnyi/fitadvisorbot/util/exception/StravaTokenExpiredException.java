package com.ua.pohribnyi.fitadvisorbot.util.exception;

public class StravaTokenExpiredException extends RuntimeException {

	public StravaTokenExpiredException(String message) {
		super(message);
	}

	public StravaTokenExpiredException(String message, Throwable cause) {
		super(message, cause);
	}

}
