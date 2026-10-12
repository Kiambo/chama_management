package com.personal.chama.reference;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A payment reference in use, and which payment it belongs to. */
@Entity
@Table(name = "payment_reference")
public class PaymentReference {

	public enum Kind {
		CONTRIBUTION, LOAN_DISBURSEMENT, LOAN_REPAYMENT, GUARANTOR_REPAYMENT, MGR_PAYOUT
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** The reference without case or spaces: what makes two references "the same". */
	@Column(name = "reference_key", nullable = false, length = 100)
	private String referenceKey;

	@Column(nullable = false, length = 100)
	private String reference;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private Kind kind;

	@Column(name = "record_id", nullable = false)
	private Long recordId;

	@Column(nullable = false, length = 300)
	private String description;

	@Column(name = "recorded_at", nullable = false)
	private Instant recordedAt;

	@Column(name = "released_at")
	private Instant releasedAt;

	protected PaymentReference() {
	}

	PaymentReference(String referenceKey, String reference, Kind kind, Long recordId, String description,
			Instant recordedAt) {
		this.referenceKey = referenceKey;
		this.reference = reference;
		this.kind = kind;
		this.recordId = recordId;
		this.description = description;
		this.recordedAt = recordedAt;
	}

	void release(Instant at) {
		this.releasedAt = at;
	}

	public String getReference() {
		return reference;
	}

	public String getDescription() {
		return description;
	}

	public Instant getRecordedAt() {
		return recordedAt;
	}
}
