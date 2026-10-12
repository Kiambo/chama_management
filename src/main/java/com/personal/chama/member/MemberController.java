package com.personal.chama.member;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;

@Controller
@RequestMapping("/members")
public class MemberController {

	private final MemberService memberService;

	public MemberController(MemberService memberService) {
		this.memberService = memberService;
	}

	@GetMapping
	public String list(Model model) {
		model.addAttribute("members", memberService.listAll());
		return "members/list";
	}

	@GetMapping("/new")
	public String newForm(Model model) {
		model.addAttribute("form", new MemberForm());
		return "members/form";
	}

	@PostMapping
	public String create(@Valid @ModelAttribute("form") MemberForm form, BindingResult result,
			RedirectAttributes redirect) {
		if (result.hasErrors()) {
			return "members/form";
		}
		try {
			Member member = memberService.create(form);
			redirect.addFlashAttribute("message", member.getFullName() + " was added.");
			return "redirect:/members";
		} catch (DuplicateMemberException e) {
			result.rejectValue("fullName", "duplicate", e.getMessage());
			return "members/form";
		}
	}

	@GetMapping("/{id}/edit")
	public String editForm(@PathVariable Long id, Model model) {
		Member member = memberService.get(id);
		model.addAttribute("member", member);
		model.addAttribute("form", MemberForm.from(member));
		return "members/form";
	}

	@PostMapping("/{id}")
	public String update(@PathVariable Long id, @Valid @ModelAttribute("form") MemberForm form,
			BindingResult result, Model model, RedirectAttributes redirect) {
		if (!result.hasErrors()) {
			try {
				Member member = memberService.update(id, form);
				redirect.addFlashAttribute("message", member.getFullName() + " was updated.");
				return "redirect:/members";
			} catch (DuplicateMemberException e) {
				result.rejectValue("fullName", "duplicate", e.getMessage());
			}
		}
		model.addAttribute("member", memberService.get(id));
		return "members/form";
	}

	@PostMapping("/{id}/deactivate")
	public String deactivate(@PathVariable Long id, RedirectAttributes redirect) {
		memberService.setActive(id, false);
		redirect.addFlashAttribute("message", memberService.get(id).getFullName() + " is now inactive.");
		return "redirect:/members";
	}

	@PostMapping("/{id}/activate")
	public String activate(@PathVariable Long id, RedirectAttributes redirect) {
		memberService.setActive(id, true);
		redirect.addFlashAttribute("message", memberService.get(id).getFullName() + " is active again.");
		return "redirect:/members";
	}
}
