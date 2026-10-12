package com.personal.chama.loan;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Charges late fees and marks defaults every night, and once at start-up so nothing is
 * missed on a computer that was switched off. Each loan page also checks its own loan.
 */
@Component
@Order(100)
public class LatePaymentJob implements ApplicationRunner {

	private final LatePaymentService latePayments;

	public LatePaymentJob(LatePaymentService latePayments) {
		this.latePayments = latePayments;
	}

	@Scheduled(cron = "0 30 0 * * *", zone = "${chama.timezone}")
	public void nightly() {
		latePayments.reconcileAll();
	}

	@Override
	public void run(ApplicationArguments args) {
		latePayments.reconcileAll();
	}
}
