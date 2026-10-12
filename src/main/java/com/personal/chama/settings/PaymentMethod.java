package com.personal.chama.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A payment method offered in the dropdowns. Hidden rather than deleted, as old payments use it. */
@Entity
@Table(name = "payment_method")
public class PaymentMethod {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 50)
	private String name;

	@Column(nullable = false)
	private boolean active = true;

	@Column(nullable = false)
	private int position;

	protected PaymentMethod() {
	}

	PaymentMethod(String name, int position) {
		this.name = name;
		this.position = position;
	}

	void setActive(boolean active) {
		this.active = active;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public boolean isActive() {
		return active;
	}

	public int getPosition() {
		return position;
	}
}
