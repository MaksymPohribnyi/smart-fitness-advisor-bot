package com.ua.pohribnyi.fitadvisorbot.util.retryable;

/*
* Predicate to test if exception should trigger retry.
*/
@FunctionalInterface
public interface ErrorPredicate {
	boolean test(Exception e);
}