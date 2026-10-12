package com.personal.chama.ledger;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.personal.chama.member.MemberService;

/**
 * Every ledger entry, newest first. Corrections are made from the record an entry
 * belongs to (a contribution, fund earnings, or the opening balances), so that all of
 * that record's entries are reversed together.
 */
@Controller
@RequestMapping("/ledger")
public class LedgerController {

	private static final int PAGE_SIZE = 50;

	private final LedgerEntryRepository entries;
	private final LedgerService ledger;
	private final MemberService memberService;

	public LedgerController(LedgerEntryRepository entries, LedgerService ledger, MemberService memberService) {
		this.entries = entries;
		this.ledger = ledger;
		this.memberService = memberService;
	}

	@GetMapping
	@Transactional(readOnly = true)
	public String list(@RequestParam(required = false) Long memberId,
			@RequestParam(required = false) LedgerEntryType type, @RequestParam(defaultValue = "0") int page,
			Model model) {
		Page<LedgerEntry> result = entries.search(memberId, type, PageRequest.of(Math.max(page, 0), PAGE_SIZE));
		List<Long> ids = result.getContent().stream().map(LedgerEntry::getId).toList();
		Set<Long> reversed = ids.isEmpty() ? Set.of() : new HashSet<>(entries.findReversedAmong(ids));

		model.addAttribute("page", result);
		model.addAttribute("reversed", reversed);
		model.addAttribute("memberId", memberId);
		model.addAttribute("type", type);
		model.addAttribute("types", LedgerEntryType.values());
		model.addAttribute("members", memberService.listAll());
		model.addAttribute("mmfBalance", ledger.mmfBalance());
		return "ledger/list";
	}
}
