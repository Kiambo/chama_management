package com.personal.chama.reference;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentReferenceRepository extends JpaRepository<PaymentReference, Long> {

	Optional<PaymentReference> findByReferenceKeyAndReleasedAtIsNull(String referenceKey);

	List<PaymentReference> findByKindAndRecordIdAndReleasedAtIsNull(PaymentReference.Kind kind, Long recordId);
}
