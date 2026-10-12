package com.personal.chama.mgr;

import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/** A round of the merry-go-round: members in order, one per month from the start month. */
@Entity
@Table(name = "mgr_round")
@EntityListeners(AuditingEntityListener.class)
public class MgrRound {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private int number;

	@Column(name = "start_month", nullable = false)
	private YearMonth startMonth;

	@OneToMany(mappedBy = "round", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("position")
	private List<MgrSlot> slots = new ArrayList<>();

	@CreatedDate
	@Column(name = "recorded_at", nullable = false, updatable = false)
	private Instant recordedAt;

	@CreatedBy
	@Column(name = "recorded_by", nullable = false, updatable = false, length = 50)
	private String recordedBy;

	protected MgrRound() {
	}

	MgrRound(int number, YearMonth startMonth) {
		this.number = number;
		this.startMonth = startMonth;
	}

	void setOrder(List<com.personal.chama.member.Member> order, YearMonth startMonth) {
		this.startMonth = startMonth;
		slots.clear();
		for (int i = 0; i < order.size(); i++) {
			slots.add(new MgrSlot(this, i + 1, order.get(i)));
		}
	}

	public Long getId() {
		return id;
	}

	public int getNumber() {
		return number;
	}

	public YearMonth getStartMonth() {
		return startMonth;
	}

	public List<MgrSlot> getSlots() {
		return slots;
	}
}
