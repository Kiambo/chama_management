package com.personal.chama.member;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MemberService {

	private final MemberRepository members;

	public MemberService(MemberRepository members) {
		this.members = members;
	}

	public List<Member> listAll() {
		return members.findAllByOrderByActiveDescFullNameAsc();
	}

	public Member get(Long id) {
		return members.findById(id).orElseThrow(() -> new MemberNotFoundException(id));
	}

	@Transactional
	public Member create(MemberForm form) {
		String name = form.getFullName().strip();
		if (members.existsByFullNameIgnoreCase(name)) {
			throw new DuplicateMemberException(name);
		}
		return members.save(new Member(name, form.getJoinDate(), form.getMgrStartDate()));
	}

	@Transactional
	public Member update(Long id, MemberForm form) {
		String name = form.getFullName().strip();
		if (members.existsByFullNameIgnoreCaseAndIdNot(name, id)) {
			throw new DuplicateMemberException(name);
		}
		Member member = get(id);
		member.update(name, form.getJoinDate(), form.getMgrStartDate());
		return member;
	}

	@Transactional
	public void setActive(Long id, boolean active) {
		Member member = get(id);
		if (active) {
			member.activate();
		} else {
			member.deactivate();
		}
	}
}
