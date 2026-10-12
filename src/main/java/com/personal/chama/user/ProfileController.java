package com.personal.chama.user;

import java.security.Principal;
import java.time.Clock;
import java.time.LocalDate;

import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.personal.chama.member.Member;
import com.personal.chama.member.MemberService;

/** Every logged-in user can manage their own profile, so these are allowed for any role. */
@Controller
@RequestMapping("/profile")
public class ProfileController {

	private final ProfileService profiles;
	private final MemberService memberService;
	private final Clock clock;

	public ProfileController(ProfileService profiles, MemberService memberService, Clock clock) {
		this.profiles = profiles;
		this.memberService = memberService;
		this.clock = clock;
	}

	@GetMapping
	@Transactional(readOnly = true)
	public String show(Principal principal, Model model) {
		AppUser user = profiles.get(principal.getName());
		model.addAttribute("user", user);
		model.addAttribute("linkedMemberId", user.getMember() == null ? null : user.getMember().getId());
		model.addAttribute("linkedMemberName", user.getMember() == null ? null : user.getMember().getFullName());
		model.addAttribute("members", memberService.listAll().stream().filter(Member::isActive).toList());
		model.addAttribute("passwordChangedOn", user.getPasswordChangedAt() == null ? null
				: LocalDate.ofInstant(user.getPasswordChangedAt(), clock.getZone()));
		return "profile/show";
	}

	@PostMapping("/member")
	public String linkMember(Principal principal, @RequestParam(required = false) Long memberId,
			RedirectAttributes redirect) {
		return run(redirect, "Your login is linked to your member record.",
				() -> profiles.linkMember(principal.getName(), memberId));
	}

	@PostMapping("/contact")
	public String contact(Principal principal, @RequestParam(required = false) String email,
			@RequestParam(required = false) String phone, RedirectAttributes redirect) {
		return run(redirect, "Contact details saved.", () -> profiles.updateContact(principal.getName(), email, phone));
	}

	@PostMapping("/password")
	public String password(Principal principal, @RequestParam(required = false) String current,
			@RequestParam(required = false) String newPassword, @RequestParam(required = false) String repeat,
			RedirectAttributes redirect) {
		return run(redirect, "Password changed.",
				() -> profiles.changePassword(principal.getName(), current, newPassword, repeat));
	}

	private String run(RedirectAttributes redirect, String success, Runnable action) {
		try {
			action.run();
			redirect.addFlashAttribute("message", success);
		} catch (ProfileException e) {
			redirect.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/profile";
	}
}
