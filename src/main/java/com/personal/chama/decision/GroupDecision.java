package com.personal.chama.decision;

import java.time.Instant;
import java.time.LocalDate;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Something the group voted on (decision G1). Never changed once recorded. */
@Entity
@Table(name = "group_decision")
@EntityListeners(AuditingEntityListener.class)
public class GroupDecision {

	public enum Kind {
		FEE_WAIVER("Late fee waived"),
		EXTENSION("Loan extended"),
		SCHEDULE_CHANGE("Repayment schedule changed"),
		RESTRUCTURE("Loan restructured"),
		WRITE_OFF("Loan written off"),
		MGR_OPT_OUT_EXCEPTION("MGR opt-out allowed"),
		MGR_PAYOUT_EXCEPTION("MGR paid out before everyone paid in"),
		OTHER("Other");

		private final String label;

		Kind(String label) {
			this.label = label;
		}

		public String getLabel() {
			return label;
		}
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "decided_on", nullable = false)
	private LocalDate decidedOn;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private Kind kind;

	@Column(nullable = false, length = 300)
	private String summary;

	@Column(length = 1000)
	private String reason;

	@Column(name = "how_agreed", nullable = false, length = 300)
	private String howAgreed;

	@Column(name = "loan_id")
	private Long loanId;

	@Column(name = "evidence_file_name")
	private String evidenceFileName;

	@Column(name = "evidence_content_type", length = 100)
	private String evidenceContentType;

	@Basic(fetch = FetchType.LAZY)
	@Column(name = "evidence_file")
	private byte[] evidenceFile;

	@CreatedDate
	@Column(name = "recorded_at", nullable = false, updatable = false)
	private Instant recordedAt;

	@CreatedBy
	@Column(name = "recorded_by", nullable = false, updatable = false, length = 50)
	private String recordedBy;

	protected GroupDecision() {
	}

	GroupDecision(LocalDate decidedOn, Kind kind, String summary, String reason, String howAgreed, Long loanId) {
		this.decidedOn = decidedOn;
		this.kind = kind;
		this.summary = summary;
		this.reason = reason;
		this.howAgreed = howAgreed;
		this.loanId = loanId;
	}

	void attachEvidence(String fileName, String contentType, byte[] content) {
		this.evidenceFileName = fileName;
		this.evidenceContentType = contentType;
		this.evidenceFile = content;
	}

	public boolean hasEvidence() {
		return evidenceFileName != null;
	}

	public Long getId() {
		return id;
	}

	public LocalDate getDecidedOn() {
		return decidedOn;
	}

	public Kind getKind() {
		return kind;
	}

	public String getSummary() {
		return summary;
	}

	public String getReason() {
		return reason;
	}

	public String getHowAgreed() {
		return howAgreed;
	}

	public Long getLoanId() {
		return loanId;
	}

	public String getEvidenceFileName() {
		return evidenceFileName;
	}

	public String getEvidenceContentType() {
		return evidenceContentType;
	}

	public byte[] getEvidenceFile() {
		return evidenceFile;
	}

	public Instant getRecordedAt() {
		return recordedAt;
	}

	public String getRecordedBy() {
		return recordedBy;
	}
}
