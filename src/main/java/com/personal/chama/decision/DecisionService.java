package com.personal.chama.decision;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly = true)
public class DecisionService {

	static final Set<String> EVIDENCE_TYPES = Set.of("application/pdf", "image/png", "image/jpeg");

	private final GroupDecisionRepository decisions;
	private final Clock clock;

	public DecisionService(GroupDecisionRepository decisions, Clock clock) {
		this.decisions = decisions;
		this.clock = clock;
	}

	/** Records a decision as part of the action it authorises, in the same transaction. */
	@Transactional(propagation = Propagation.MANDATORY)
	public GroupDecision record(GroupDecision.Kind kind, String summary, DecisionInput input, Long loanId) {
		if (input == null || input.getHowAgreed() == null || input.getHowAgreed().isBlank()) {
			throw new DecisionException("Say how the group agreed this, e.g. \"WhatsApp vote, 6 of 7\".");
		}
		LocalDate date = input.getDecidedOn() != null ? input.getDecidedOn() : LocalDate.now(clock);
		if (date.isAfter(LocalDate.now(clock))) {
			throw new DecisionException("The decision date can't be in the future.");
		}
		GroupDecision decision = new GroupDecision(date, kind, summary, blankToNull(input.getReason()),
				input.getHowAgreed().strip(), loanId);
		MultipartFile evidence = input.getEvidence();
		if (evidence != null && !evidence.isEmpty()) {
			if (!EVIDENCE_TYPES.contains(evidence.getContentType())) {
				throw new DecisionException("The evidence must be a PDF, PNG or JPEG file.");
			}
			try {
				decision.attachEvidence(evidence.getOriginalFilename(), evidence.getContentType(), evidence.getBytes());
			} catch (IOException e) {
				throw new DecisionException("The evidence file could not be read. Please try again.");
			}
		}
		return decisions.save(decision);
	}

	public List<GroupDecision> all() {
		return decisions.findAllByOrderByDecidedOnDescIdDesc();
	}

	public List<GroupDecision> forLoan(Long loanId) {
		return decisions.findByLoanIdOrderByDecidedOnDescIdDesc(loanId);
	}

	public GroupDecision get(Long id) {
		return decisions.findById(id).orElseThrow(() -> new DecisionException("No such decision."));
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}
}
