package com.personal.chama.pool;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.personal.chama.ledger.LedgerEntry;
import com.personal.chama.ledger.LedgerEntryRepository;
import com.personal.chama.ledger.LedgerService;

@Service
@Transactional(readOnly = true)
public class FundEarningService {

	/** Statements are kept as PDFs or images. */
	static final Set<String> ALLOWED_TYPES = Set.of("application/pdf", "image/png", "image/jpeg");

	private final FundEarningRepository earnings;
	private final LedgerService ledger;
	private final LedgerEntryRepository ledgerEntries;
	private final AuditorAware<String> auditor;
	private final Clock clock;

	public FundEarningService(FundEarningRepository earnings, LedgerService ledger,
			LedgerEntryRepository ledgerEntries, AuditorAware<String> auditor, Clock clock) {
		this.earnings = earnings;
		this.ledger = ledger;
		this.ledgerEntries = ledgerEntries;
		this.auditor = auditor;
		this.clock = clock;
	}

	public List<FundEarning> list() {
		return earnings.findAllByOrderByMonthDescIdDesc();
	}

	public FundEarning get(Long id) {
		return earnings.findById(id).orElseThrow(() -> new PoolException("No such fund earnings entry."));
	}

	@Transactional
	public FundEarning record(YearMonth month, BigDecimal amount, String reference, MultipartFile statement) {
		if (month.isAfter(YearMonth.now(clock))) {
			throw new PoolException("Earnings can't be recorded for a future month.");
		}
		FundEarning earning = new FundEarning(month, amount, blankToNull(reference));
		if (statement != null && !statement.isEmpty()) {
			if (!ALLOWED_TYPES.contains(statement.getContentType())) {
				throw new PoolException("The statement must be a PDF, PNG or JPEG file.");
			}
			try {
				earning.attachStatement(statement.getOriginalFilename(), statement.getContentType(), statement.getBytes());
			} catch (IOException e) {
				throw new PoolException("The statement file could not be read. Please try again.");
			}
		}
		earning = earnings.save(earning);
		LocalDate date = month.equals(YearMonth.now(clock)) ? LocalDate.now(clock) : month.atEndOfMonth();
		ledger.post(LedgerEntry.forFundEarning(amount, month, date, earning.getId(), earning.getStatementReference()));
		return earning;
	}

	@Transactional
	public void voidEarning(Long id, String reason) {
		FundEarning earning = get(id);
		try {
			earning.voidWith(reason, auditor.getCurrentAuditor().orElse("system"), Instant.now(clock));
		} catch (IllegalStateException e) {
			throw new PoolException(e.getMessage());
		}
		for (LedgerEntry entry : ledgerEntries.findByFundEarningIdAndReversesIdIsNull(id)) {
			if (!ledger.isReversed(entry.getId())) {
				ledger.reverse(entry.getId(), reason);
			}
		}
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}
}
