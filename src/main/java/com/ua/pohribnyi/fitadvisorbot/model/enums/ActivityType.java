package com.ua.pohribnyi.fitadvisorbot.model.enums;

import java.util.Arrays;

public enum ActivityType {
	RUN("Run", "running"), 
	BIKE("Bike", "cycling"), 
	SWIM("Swim", "swimming"), 
	HIKE("Hike", "hiking"),
	WALK("Walk", "walking"), 
	WORKOUT("Workout", "training"), 
	ELLIPTICAL("Elliptical", "elliptical"),
	STAIR_STEPPER("Stair Stepper", "stair_stepper"),
	OTHER("Other", "other");

	private final String displayName;
	private final String stravaType;

	ActivityType(String displayName, String stravaType) {
		this.displayName = displayName;
		this.stravaType = stravaType;
	}

	public String getDisplayName() {
		return displayName;
	}

	public String getStravaType() {
		return stravaType;
	}

	public static ActivityType fromStravaType(String stravaType) {
		return Arrays.stream(ActivityType.values())
				.filter(t -> t.stravaType.equalsIgnoreCase(stravaType))
				.findFirst()
				.orElse(OTHER);
	}
}