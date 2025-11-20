package com.ua.pohribnyi.fitadvisorbot.service.analytics;

import org.springframework.stereotype.Service;

import com.ua.pohribnyi.fitadvisorbot.model.entity.user.UserProfile;

/**
 * Domain service for physiological calculations (Heart Rate Zones, BMI, etc).
 */
@Service
public class UserPhysiologyService {

	/**
	 * Calculates Zone 2 (Fat Burn Zone) boundaries based on Karvonen or Simple Max
	 * HR formula. For MVP we use: (220 - age) * 0.6 to 0.75
	 */
	public HeartRateZone calculateFatBurnZone(UserProfile profile) {
		int age = profile.getAge();
		int maxHr = 220 - age;

		int minPulse = (int) (maxHr * 0.60); // 60%
		int maxPulse = (int) (maxHr * 0.75); // 75%

		return new HeartRateZone(minPulse, maxPulse);
	}

	public record HeartRateZone(int min, int max) {
		public boolean contains(Integer pulse) {
			return pulse != null && pulse >= min && pulse <= max;
		}
	}
}
