package com.personal.chama.web;

import java.security.Principal;
import java.time.Clock;
import java.time.LocalTime;
import java.time.YearMonth;

import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.personal.chama.member.MemberService;
import com.personal.chama.user.AppUser;
import com.personal.chama.user.AppUserRepository;

@Controller
public class HomeController {

	private final Clock clock;
	private final AppUserRepository users;
	private final MemberService memberService;

	public HomeController(Clock clock, AppUserRepository users, MemberService memberService) {
		this.clock = clock;
		this.users = users;
		this.memberService = memberService;
	}

	@GetMapping("/")
	@Transactional(readOnly = true)
	public String dashboard(Principal principal, Model model) {
		model.addAttribute("greeting", greeting(LocalTime.now(clock)));
		model.addAttribute("name", displayName(principal.getName()));
		model.addAttribute("month", YearMonth.now(clock));
		model.addAttribute("activeMembers", memberService.listAll().stream().filter(m -> m.isActive()).count());
		return "dashboard";
	}

	@GetMapping("/login")
	public String login() {
		return "login";
	}

	static String greeting(LocalTime time) {
		if (time.isBefore(LocalTime.NOON)) {
			return "Good morning";
		}
		if (time.isBefore(LocalTime.of(17, 0))) {
			return "Good afternoon";
		}
		return "Good evening";
	}

	/** The linked member's first name if there is one, otherwise the login name. */
	private String displayName(String username) {
		return users.findByUsername(username)
				.map(AppUser::getMember)
				.map(member -> member.getFirstName())
				.orElse(username);
	}
}
