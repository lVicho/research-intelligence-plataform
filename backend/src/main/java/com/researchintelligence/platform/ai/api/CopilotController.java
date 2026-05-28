package com.researchintelligence.platform.ai.api;

import com.researchintelligence.platform.ai.application.CopilotService;
import com.researchintelligence.platform.ai.application.CopilotAnswerEvaluationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/copilot")
public class CopilotController {

    private final CopilotService service;
    private final CopilotAnswerEvaluationService evaluationService;

    public CopilotController(CopilotService service, CopilotAnswerEvaluationService evaluationService) {
        this.service = service;
        this.evaluationService = evaluationService;
    }

    @PostMapping("/ask")
    public CopilotAskResponse ask(@Valid @RequestBody CopilotAskRequest request) {
        return service.ask(request);
    }

    @PostMapping("/retrieve")
    public CopilotRetrieveResponse retrieve(@Valid @RequestBody CopilotRetrieveRequest request) {
        return service.retrieve(request);
    }

    @PostMapping("/answer")
    public CopilotAnswerResponse answer(@Valid @RequestBody CopilotAnswerRequest request) {
        return service.answer(request);
    }

    @PostMapping("/evaluate-answer")
    public CopilotAnswerEvaluationResponse evaluateAnswer(@Valid @RequestBody CopilotAnswerEvaluationRequest request) {
        return evaluationService.evaluate(request);
    }
}
