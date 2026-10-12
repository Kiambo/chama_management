package com.personal.chama.member;

import java.time.Instant;
import java.time.LocalDate;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * A chama member. Members are never deleted because they carry financial history;
 * someone who leaves is marked inactive.
 */
@Entity
@Table(name = "member")
@EntityListeners(AuditingEntityListener.class)
public class Member {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "full_name", nullable = false, length = 100)
	private String fullName;

	@Column(name = "join_date", nullable = false)
	private LocalDate joinDate;

	/** Null while the member is in the savings pool but not in the MGR rotation. */
	@Column(name = "mgr_start_date")
	private LocalDate mgrStartDate;

	@Column(nullable = false)
	private boolean active = true;

	@Version
	private long version;

	@CreatedDate
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@CreatedBy
	@Column(name = "created_by", nullable = false, updatable = false, length = 50)
	private String createdBy;

	@LastModifiedDate
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@LastModifiedBy
	@Column(name = "updated_by", nullable = false, length = 50)
	private String updatedBy;

	protected Member() {
	}

	public Member(String fullName, LocalDate joinDate, LocalDate mgrStartDate) {
		update(fullName, joinDate, mgrStartDate);
	}

	public void update(String fullName, LocalDate joinDate, LocalDate mgrStartDate) {
		if (mgrStartDate != null && mgrStartDate.isBefore(joinDate)) {
			throw new IllegalArgumentException("MGR start date cannot be before the join date");
		}
		this.fullName = fullName.strip();
		this.joinDate = joinDate;
		this.mgrStartDate = mgrStartDate;
	}

	public void deactivate() {
		this.active = false;
	}

	public void activate() {
		this.active = true;
	}

	/** "Grace Wanjiru Mwangi" gives "GM": first and last initials, for avatars. */
	public String getInitials() {
		String[] parts = fullName.split("\\s+");
		String initials = parts[0].substring(0, 1);
		if (parts.length > 1) {
			initials += parts[parts.length - 1].substring(0, 1);
		}
		return initials.toUpperCase();
	}

	public String getFirstName() {
		return fullName.split("\\s+")[0];
	}

	public Long getId() {
		return id;
	}

	public String getFullName() {
		return fullName;
	}

	public LocalDate getJoinDate() {
		return joinDate;
	}

	public LocalDate getMgrStartDate() {
		return mgrStartDate;
	}

	public boolean isActive() {
		return active;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public String getCreatedBy() {
		return createdBy;
	}
}
