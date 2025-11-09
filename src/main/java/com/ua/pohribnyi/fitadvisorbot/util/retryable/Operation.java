package com.ua.pohribnyi.fitadvisorbot.util.retryable;

@FunctionalInterface
public interface Operation<T> {
	T execute() throws Exception;
}
