package com.ua.pohribnyi.fitadvisorbot.model.entity.user;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "telegram_user_id", unique = true, nullable = false)
	private Long telegramUserId;

	@Column(name = "telegram_username")
	private String telegramUsername;

	@Column(name = "first_name")
	private String firstName;

	@Column(name = "last_name")
	private String lastName;

	@Column(name = "language_code")
	private String languageCode;
	
	@OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
	private UserSession session;

	@OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
	private UserProfile userProfile;
	
	/*
	 * @Column(name = "is_active") private Boolean isActive = true;
	 * 
	 * 
	 * 
	 * @Column(name = "timezone") private String timezone = "UTC";
	 * 
	 * @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch =
	 * FetchType.LAZY) private List<UserPreference> preferences;
	 * 
	 * @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch =
	 * FetchType.LAZY) private List<FitnessData> fitnessDataList;
	 */

	@CreationTimestamp
	@Column(name = "created_at", updatable = false)
	private LocalDateTime createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at")
	private LocalDateTime updatedAt;

}
