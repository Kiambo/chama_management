package com.personal.chama.loan;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GuarantorDebtPaymentRepository extends JpaRepository<GuarantorDebtPayment, Long> {

	List<GuarantorDebtPayment> findByDebtIdOrderByPaidDateAscIdAsc(Long debtId);

	List<GuarantorDebtPayment> findByDebtIdAndVoidedAtIsNull(Long debtId);
}
