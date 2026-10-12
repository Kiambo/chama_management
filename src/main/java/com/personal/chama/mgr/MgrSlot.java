package com.personal.chama.mgr;

import com.personal.chama.member.Member;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A member's place in a round. Their month is worked out from the order (see MgrService). */
@Entity
@Table(name = "mgr_slot")
public class MgrSlot {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "round_id")
	private MgrRound round;

	@Column(nullable = false)
	private int position;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id")
	private Member member;

	protected MgrSlot() {
	}

	MgrSlot(MgrRound round, int position, Member member) {
		this.round = round;
		this.position = position;
		this.member = member;
	}

	public Long getId() {
		return id;
	}

	public MgrRound getRound() {
		return round;
	}

	public int getPosition() {
		return position;
	}

	public Member getMember() {
		return member;
	}
}
