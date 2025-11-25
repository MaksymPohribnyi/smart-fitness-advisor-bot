package com.ua.pohribnyi.fitadvisorbot.util.math;

import java.util.List;

/**
 * Represents a threshold for metric evaluation.
 * 
 * @param min Minimum value to trigger this level.
 * @param key Message key or value associated with this level.
 */
public record MetricThreshold(double min, String key) {

	/**
	 * Picks the best matching key based on the value and thresholds. Logic:
	 * Iterates through sorted thresholds. Updates selection if value >=
	 * threshold.min. Requires thresholds to be sorted by min value (ascending).
	 */
	public static String pick(double value, List<MetricThreshold> thresholds) {
		if (thresholds == null || thresholds.isEmpty())
			return null;

		MetricThreshold selected = thresholds.get(0); // Default to first (lowest)
		for (MetricThreshold t : thresholds) {
			if (value >= t.min()) {
				selected = t;
			} else {
				break; // Value is smaller than next threshold, stop
			}
		}
		return selected.key();
	}

}
