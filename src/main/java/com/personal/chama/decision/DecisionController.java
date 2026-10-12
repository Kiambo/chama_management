package com.personal.chama.decision;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/decisions")
public class DecisionController {

	private final DecisionService decisions;

	public DecisionController(DecisionService decisions) {
		this.decisions = decisions;
	}

	@GetMapping
	public String list(Model model) {
		model.addAttribute("decisions", decisions.all());
		return "decisions/list";
	}

	@GetMapping("/{id}/evidence")
	@Transactional(readOnly = true)
	public ResponseEntity<byte[]> evidence(@PathVariable Long id) {
		GroupDecision decision = decisions.get(id);
		if (!decision.hasEvidence()) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(decision.getEvidenceContentType()))
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.inline().filename(decision.getEvidenceFileName()).build().toString())
				.body(decision.getEvidenceFile());
	}
}
