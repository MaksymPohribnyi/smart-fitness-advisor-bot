package com.ua.pohribnyi.fitadvisorbot.util.math;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Utility class for statistical calculations. Used to calculate trends,
 * correlations, and variability in user data.
 */
public class MathUtils {

	private MathUtils() {
		// Prevent instantiation
	}

	/**
	 * Calculates the Coefficient of Variation (CV). CV = (Standard Deviation /
	 * Mean). Lower values indicate higher stability.
	 *
	 * @param values list of numerical values
	 * @return CV value or 0.0 if list is empty
	 */
	public static double calculateCV(List<Double> values) {
		if (values == null || values.isEmpty())
			return 0.0;
		double mean = values.stream().mapToDouble(val -> val).average().orElse(0.0);
		if (mean == 0)
			return 0.0;

		double variance = values.stream().mapToDouble(val -> Math.pow(val - mean, 2)).average().orElse(0.0);
		double stdDev = Math.sqrt(variance);
		return stdDev / mean;
	}

	/**
	 * Calculates the slope of the linear regression line. Used to determine the
	 * trend (momentum) of the data. Positive slope -> Increasing trend. Negative
	 * slope -> Decreasing trend.
	 *
	 * @param values list of time-series values (assumed sequential)
	 * @return slope value
	 */
	public static double calculateSlope(List<Double> values) {
		if (values == null || values.size() < 2)
			return 0.0;
		int n = values.size();
		double sumX = IntStream.range(0, n).sum();
		double sumY = values.stream().mapToDouble(v -> v).sum();
		double sumXY = IntStream.range(0, n).mapToDouble(i -> i * values.get(i)).sum();
		double sumX2 = IntStream.range(0, n).mapToDouble(i -> i * i).sum();

		double denominator = (n * sumX2 - sumX * sumX);
		if (denominator == 0)
			return 0.0;

		return (n * sumXY - sumX * sumY) / denominator;
	}

	/**
	 * Calculates values for the simple ratio.
	 *
	 * @param value   numerator
	 * @param divisor denominator
	 * @return result or 0.0 if divisor is 0
	 */
	public static double safeDivide(double value, double divisor) {
		return divisor == 0 ? 0.0 : value / divisor;
	}
}