package com.ua.pohribnyi.fitadvisorbot.enums;

public enum UserState {

	// --- Onboarding Flow ---
	ONBOARDING_START, // Initial state for new users
	AWAITING_PROFILE_LEVEL, // Waiting for user to select their fitness level
	AWAITING_PROFILE_GOAL, // Waiting for user to select their goal
	AWAITING_PROFILE_AGE, // Waiting for user to input their age
	ONBOARDING_COMPLETED, // Onboarding finished, generating history
	
	// --- Main Flow ---
	DEFAULT, // User is in the main menu

	// --- Diary Flow ---
	AWAITING_DIARY_MOOD, 
	AWAITING_ACTIVITY_LOG, 
	AWAITING_ACTIVITY_TYPE, 
	AWAITING_ACTIVITY_DURATION,
	AWAITING_ACTIVITY_EFFORT
	
}
