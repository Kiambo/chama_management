package com.personal.chama.reference;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Function;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps payment references unique across every kind of payment (contributions, loan
 * disbursements and repayments, repayments to guarantors, MGR payouts). References are
 * compared without case or spaces. The database also refuses a duplicate, so two
 * payments recorded at once can't both get through.
 */
@Service
public class ReferenceService {

	private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

	private final PaymentReferenceRepository references;
	private final Clock clock;

	public ReferenceService(PaymentReferenceRepository references, Clock clock) {
		this.references = references;
		this.clock = clock;
	}

	/** "QWE 123" and "qwe123" are the same reference. */
	public static String keyOf(String reference) {
		return reference == null ? "" : reference.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
	}

	/**
	 * Refuses a reference that's already in use, with a message saying where. Call before
	 * saving a payment, so nothing is saved when the reference is a duplicate.
	 */
	@Transactional(readOnly = true)
	public void checkAvailable(String reference, Function<String, ? extends RuntimeException> error) {
		String key = keyOf(reference);
		if (key.isEmpty()) {
			return;
		}
		references.findByReferenceKeyAndReleasedAtIsNull(key).ifPresent(existing -> {
			throw error.apply("Reference " + existing.getReference() + " was already used: "
					+ existing.getDescription() + ", recorded "
					+ WHEN.format(existing.getRecordedAt().atZone(ZoneId.of(clock.getZone().getId()))) + ".");
		});
	}

	/** Records the reference as used by this payment. Blank references aren't tracked. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void claim(String reference, PaymentReference.Kind kind, Long recordId, String description,
			Function<String, ? extends RuntimeException> error) {
		String key = keyOf(reference);
		if (key.isEmpty()) {
			return;
		}
		checkAvailable(reference, error);
		references.saveAndFlush(new PaymentReference(key, reference.strip(), kind, recordId,
				description.length() > 300 ? description.substring(0, 300) : description, Instant.now(clock)));
	}

	/** A corrected (reversed) payment gives its reference back, so the right one can use it. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void release(PaymentReference.Kind kind, Long recordId) {
		Instant now = Instant.now(clock);
		references.findByKindAndRecordIdAndReleasedAtIsNull(kind, recordId).forEach(r -> r.release(now));
	}
}
