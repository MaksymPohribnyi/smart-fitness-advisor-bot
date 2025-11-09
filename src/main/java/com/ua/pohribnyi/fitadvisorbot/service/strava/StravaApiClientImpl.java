package com.ua.pohribnyi.fitadvisorbot.service.strava;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.ua.pohribnyi.fitadvisorbot.config.StravaConfig;
import com.ua.pohribnyi.fitadvisorbot.model.dto.strava.StravaActivityDto;
import com.ua.pohribnyi.fitadvisorbot.model.dto.strava.StravaAthleteDto;
import com.ua.pohribnyi.fitadvisorbot.model.dto.strava.StravaTokenResponseDto;
import com.ua.pohribnyi.fitadvisorbot.util.exception.StravaAuthException;
import com.ua.pohribnyi.fitadvisorbot.util.exception.StravaException;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class StravaApiClientImpl implements StravaApiClient {

	private final RestTemplate restTemplate;
	private final StravaConfig stravaConfig;

	public StravaApiClientImpl(RestTemplate restTemplate, StravaConfig stravaConfig) {
		this.restTemplate = restTemplate;
		this.stravaConfig = stravaConfig;
	}

	@Override
	public StravaTokenResponseDto exchangeCodeForToken(String code) {
		log.info("Exchanging authorization code for access token");
		
		try {
			MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
	        params.add("client_id", stravaConfig.getClientId());
	        params.add("client_secret", stravaConfig.getClientSecret());
	        params.add("code", code);
	        params.add("grant_type", "authorization_code");

	        HttpHeaders headers = new HttpHeaders();
	        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

	        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

			ResponseEntity<StravaTokenResponseDto> response = restTemplate.postForEntity(
					stravaConfig.getTokenUrl(), 
					request,
					StravaTokenResponseDto.class);

			if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
				throw new StravaAuthException("Failed to exchange code for token");
			}

			log.info("Successfully exchanged code for token");
			return response.getBody();

		} catch (RestClientException e) {
			log.error("Error exchanging code for token: {}", e.getMessage(), e);
			throw new StravaAuthException("Failed to authenticate with Strava", e);
		}
	}

	
	@Override
	public StravaTokenResponseDto refreshAccessToken(String refreshToken) {
		log.info("Refreshing Strava access token");

		try {
			MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
	        params.add("client_id", stravaConfig.getClientId());
	        params.add("client_secret", stravaConfig.getClientSecret());
	        params.add("refresh_token", refreshToken);
	        params.add("grant_type", "refresh_token");

	        HttpHeaders headers = new HttpHeaders();
	        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

	        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

			ResponseEntity<StravaTokenResponseDto> response = restTemplate.postForEntity(
					stravaConfig.getTokenUrl(),
					request, 
					StravaTokenResponseDto.class);

			if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
				throw new StravaAuthException("Failed to refresh access token");
			}

			log.info("Successfully refreshed access token");
			return response.getBody();

		} catch (RestClientException e) {
			log.error("Error refreshing token: {}", e.getMessage(), e);
			throw new StravaException("Failed to refresh Strava token", e);
		}
	}

	public StravaAthleteDto getAuthenticatedAthlete(String accessToken) {
        log.debug("Fetching authenticated athlete info");

        String url = stravaConfig.getApiBaseUrl() + "/api/v3/athlete";
        HttpHeaders headers = createAuthHeaders(accessToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<StravaAthleteDto> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                request,
                StravaAthleteDto.class
            );

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
				throw new StravaAuthException("Empty response from Strava athlete endpoint");
			}
            
            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to fetch athlete info: {}", e.getMessage());
            throw new StravaException("Failed to fetch athlete info: " + e.getMessage(), e);
        }
    }
	
	
	@Override
	public List<StravaActivityDto> getAthleteActivities(String accessToken, Integer page, Integer perPage) {
		log.debug("Fetching athlete activities with page: {}, perPage: {}", page, perPage);

		try {
			String url = stravaConfig.getActivitiesUrl() + "?page=" + page + "&per_page=" + perPage;
	        HttpHeaders headers = createAuthHeaders(accessToken);
	        HttpEntity<Void> request = new HttpEntity<>(headers);

			ResponseEntity<List<StravaActivityDto>> response = restTemplate.exchange(
	                url,
	                HttpMethod.GET,
	                request,
	                new ParameterizedTypeReference<List<StravaActivityDto>>() {}
	            );

			if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
				return List.of();
			}

			log.debug("Successfully fetched {} activities", response.getBody().size());
			return response.getBody();

		} catch (RestClientException e) {
			log.error("Error fetching activities: {}", e.getMessage(), e);
			throw new StravaException("Failed to fetch Strava activities", e);
		}
	}

	public List<StravaActivityDto> getActivitiesAfter(String accessToken, LocalDateTime after, Integer page,
			Integer perPage) {
		log.debug("Fetching activities after: {}, page={}, perPage={}", after, page, perPage);

		long afterEpoch = after.atZone(java.time.ZoneId.systemDefault()).toEpochSecond();
		String url = stravaConfig.getActivitiesUrl() + "?after=" + afterEpoch + "&page=" + page + "&per_page="
				+ perPage;

		HttpHeaders headers = createAuthHeaders(accessToken);
		HttpEntity<Void> request = new HttpEntity<>(headers);

		try {
			ResponseEntity<List<StravaActivityDto>> response = restTemplate.exchange(url, HttpMethod.GET, request,
					new ParameterizedTypeReference<List<StravaActivityDto>>() {
					});

			if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
				return List.of();
			}
			log.debug("Successfully fetched {} activities after {}", response.getBody().size(), after);
			return response.getBody();
		} catch (Exception e) {
			log.error("Failed to fetch activities after date: {}", e.getMessage());
			throw new StravaException("Failed to fetch activities: " + e.getMessage(), e);
		}
	}
	
	@Override
	public StravaActivityDto getActivity(String accessToken, Long activityId) {
		log.debug("Fetching activity with id: {}", activityId);

		try {
			String url = stravaConfig.getApiBaseUrl() + "/api/v3/activities/" + activityId;

			HttpHeaders headers = createAuthHeaders(accessToken);
			HttpEntity<?> entity = new HttpEntity<>(headers);

			ResponseEntity<StravaActivityDto> response = restTemplate.exchange(
					url, 
					HttpMethod.GET, 
					entity,
					StravaActivityDto.class);

			if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
				throw new StravaException("Activity not found");
			}
			log.debug("Successfully fetched activity with id{}", activityId);
			return response.getBody();

		} catch (RestClientException e) {
			log.error("Error fetching activity {}: {}", activityId, e.getMessage(), e);
			throw new StravaException("Failed to fetch Strava activity", e);
		}
	}

	public Map<String, Object> getAthleteStats(String accessToken, Long athleteId) {
		log.debug("Fetching athlete stats for ID: {}", athleteId);

		String url = stravaConfig.getApiBaseUrl() + "/api/v3/athletes/" + athleteId + "/stats";
		HttpHeaders headers = createAuthHeaders(accessToken);
		HttpEntity<Void> request = new HttpEntity<>(headers);

		try {
			ResponseEntity<Map<String, Object>> response = restTemplate.exchange(url, HttpMethod.GET, request,
					new ParameterizedTypeReference<Map<String, Object>>() {
					});
			if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
				return Map.of();
			}
			log.debug("Successfully fetched athlete stats for ID: {}", athleteId);
			return response.getBody();
		} catch (Exception e) {
			log.error("Failed to fetch athlete stats: {}", e.getMessage());
			throw new StravaException("Failed to fetch athlete stats: " + e.getMessage(), e);
		}
	}

	public void deauthorize(String accessToken) {
		log.debug("Deauthorizing Strava access");

		String url = stravaConfig.getApiBaseUrl() + "/api/v3/oauth/deauthorize";
		HttpHeaders headers = createAuthHeaders(accessToken);
		HttpEntity<Void> request = new HttpEntity<>(headers);

		try {
			restTemplate.postForEntity(url, request, Void.class);
			log.info("Successfully deauthorized Strava access");
		} catch (Exception e) {
			log.error("Failed to deauthorize: {}", e.getMessage());
			throw new StravaException("Failed to deauthorize: " + e.getMessage(), e);
		}
	}
	
	@Override
	public boolean validateToken(String accessToken) {
		log.debug("Validating Strava access token");

		try {
			String url = stravaConfig.getApiBaseUrl() + "/api/v3/athlete";

			HttpHeaders headers = createAuthHeaders(accessToken);
			HttpEntity<?> entity = new HttpEntity<>(headers);

			ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

			boolean isValid = response.getStatusCode().is2xxSuccessful();
			log.debug("Token validation result: {}", isValid);
			return isValid;

		} catch (RestClientException e) {
			log.debug("Token validation failed: {}", e.getMessage());
			return false;
		}
	}

	@Override
	public StravaAthleteDto getAthlete(String accessToken) {
		log.debug("Fetching authenticated athlete");

		try {
			String url = stravaConfig.getApiBaseUrl() + "/api/v3/athlete";

			HttpHeaders headers = createAuthHeaders(accessToken);
			HttpEntity<?> entity = new HttpEntity<>(headers);

			ResponseEntity<StravaAthleteDto> response = restTemplate.exchange(
					url, 
					HttpMethod.GET, 
					entity,
					StravaAthleteDto.class);

			if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
				throw new StravaException("Failed to fetch athlete");
			}

			return response.getBody();

		} catch (RestClientException e) {
			log.error("Error fetching athlete: {}", e.getMessage(), e);
			throw new StravaException("Failed to fetch athlete data", e);
		}
	}
	
	private HttpHeaders createAuthHeaders(String accessToken) {
		HttpHeaders headers = new HttpHeaders();
		headers.set("Authorization", "Bearer " + accessToken);
		headers.set("Content-Type", "application/json");
		return headers;
	}

}
