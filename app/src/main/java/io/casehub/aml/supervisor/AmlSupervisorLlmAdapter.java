package io.casehub.aml.supervisor;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import io.casehub.api.context.CaseContext;
import io.casehub.api.engine.PlanExecutionContext;
import io.casehub.api.model.Binding;
import io.casehub.api.model.ai.ChatModelProvider;
import io.casehub.api.model.ai.anthropic.AnthropicChatModelProvider;
import io.casehub.engine.planning.plan.CasePlanModel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class AmlSupervisorLlmAdapter {

    private static final Pattern JSON_FENCE =
            Pattern.compile("```(?:json)?\\s*\\n(\\{.*?})\\s*\\n```", Pattern.DOTALL);

    private final ChatModelProvider chatModelProvider;
    private final ObjectMapper objectMapper;

    @Inject
    public AmlSupervisorLlmAdapter(
            ObjectMapper objectMapper,
            @ConfigProperty(name = "casehub.aml.supervisor.api-key") Optional<String> apiKey,
            @ConfigProperty(name = "casehub.aml.supervisor.model", defaultValue = "claude-sonnet-4-20250514") String modelName) {
        this.objectMapper = objectMapper;
        this.chatModelProvider = apiKey
                .filter(k -> !k.isBlank())
                .map(k -> (ChatModelProvider) AnthropicChatModelProvider.builder()
                        .apiKey(k)
                        .modelName(modelName)
                        .build())
                .orElse(null);
    }

    AmlSupervisorLlmAdapter(ChatModelProvider provider, ObjectMapper objectMapper) {
        this.chatModelProvider = provider;
        this.objectMapper = objectMapper;
    }

    public boolean isAvailable() {
        return chatModelProvider != null;
    }

    public SupervisorDecision consult(
            CasePlanModel plan, PlanExecutionContext ctx, List<Binding> eligible) {
        String prompt = buildPrompt(plan, ctx, eligible);
        return callLlm(prompt);
    }

    String buildPrompt(CasePlanModel plan, PlanExecutionContext ctx, List<Binding> eligible) {
        var sb = new StringBuilder();
        sb.append("""
                  You are an AML investigation supervisor. Decide which investigation \
                  steps should fire next based on accumulated evidence.
                  
                  ## Case Context
                  """);
        if (ctx != null && ctx.caseContext() != null) {
            sb.append(projectCaseContext(ctx.caseContext()));
        } else {
            sb.append("No context available yet.\n");
        }

        if (ctx != null && ctx.caseContext() != null && ctx.caseContext().asJsonNode() != null) {
            var node = ctx.caseContext().asJsonNode();
            if (node.has("rejectionReview") && !node.get("rejectionReview").isNull()) {
                sb.append("""
                          
                          ## Rejection Routing Context
                          This case has been through rejection routing. The rejection rationale and \
                          senior analyst review are included in the context above. Use this to inform \
                          selection of non-rejection bindings that may also be eligible in this cycle.
                          """);
            }
        }

        sb.append("\n## Eligible Bindings (select a subset to fire NOW)\n");
        if (eligible != null) {
            for (Binding b : eligible) {
                sb.append("- ").append(b.getName()).append("\n");
            }
        }
        sb.append("""
                  
                  ## Instructions
                  - Select which bindings to fire NOW. Suppress bindings not yet useful.
                  - You MUST select at least one binding.
                  - If evidence is sufficient for triage, select investigation-triage \
                  and suppress remaining specialists (early termination).
                  - Do not reason about the triage outcome — that is handled by the \
                  deterministic evaluator.
                  - Respond with JSON only:
                  {"selectedBindings": ["name1"], "suppressedBindings": ["name2"], \
                  "rationale": "brief explanation", "earlyTermination": false}
                  """);
        return sb.toString();}

    private String projectCaseContext(CaseContext ctx) {
        if (ctx == null || ctx.asJsonNode() == null) {return "Empty\n";}
        var node = ctx.asJsonNode();
        var sb   = new StringBuilder();
        appendIfPresent(sb, node, "entityResolution");
        appendIfPresent(sb, node, "patternAnalysis");
        appendIfPresent(sb, node, "osintScreening");
        appendIfPresent(sb, node, "cbrPathAdvice");
        appendIfPresent(sb, node, "investigationTriage");
        appendIfPresent(sb, node, "priorEntityContext");
        appendIfPresent(sb, node, "seniorAnalystReview");

        if (node.has("actionGateRejected") && !node.get("actionGateRejected").isNull()) {
            var rejection = node.get("actionGateRejected");
            sb.append("\n## Rejection Context\n");
            sb.append("- Gate type: ").append(textOf(rejection, "actionType")).append("\n");
            sb.append("- Rejected by: ").append(textOf(rejection, "rejectedBy")).append("\n");
            sb.append("- Rationale: ").append(textOf(rejection, "resolution")).append("\n");
        }
        if (node.has("rejectionReview") && !node.get("rejectionReview").isNull()) {
            var review = node.get("rejectionReview");
            sb.append("\n## Senior Analyst Review\n");
            sb.append("- Risk adjustment: ").append(textOf(review, "riskAdjustment")).append("\n");
            sb.append("- Finding: ").append(textOf(review, "finding")).append("\n");
            sb.append("- Recommended action: ").append(textOf(review, "recommendedAction")).append("\n");
        }
        if (node.has("postRejectionTriage") && !node.get("postRejectionTriage").isNull()) {
            appendIfPresent(sb, node, "postRejectionTriage");
        }

        if (sb.isEmpty()) {sb.append("No specialist findings yet.\n");}
        return sb.toString();}

    private void appendIfPresent(StringBuilder sb, com.fasterxml.jackson.databind.JsonNode node,
                                 String field) {
        if (node.has(field) && !node.get(field).isNull()) {
            sb.append("- ").append(field).append(": ").append(node.get(field)).append("\n");
        }
    }

    private static String textOf(com.fasterxml.jackson.databind.JsonNode parent, String field) {
        return parent.has(field) && !parent.get(field).isNull() ? parent.get(field).asText() : "N/A";
    }


    SupervisorDecision callLlm(String prompt) {
        try {
            ChatModel chatModel = chatModelProvider.get();
            var request = ChatRequest.builder()
                    .messages(List.of(UserMessage.from(prompt)))
                    .build();
            var response = chatModel.chat(request);
            String text = response.aiMessage().text();
            String json = extractJsonBlock(text);
            return objectMapper.readValue(json, SupervisorDecision.class);
        } catch (InvalidSupervisorResponseException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidSupervisorResponseException(
                    "Failed to parse LLM response: " + e.getMessage());
        }
    }

    static String extractJsonBlock(String text) {
        if (text == null || text.isBlank()) {
            throw new InvalidSupervisorResponseException("Empty LLM response");
        }
        Matcher m = JSON_FENCE.matcher(text);
        if (m.find()) return m.group(1);
        String trimmed = text.trim();
        if (trimmed.startsWith("{")) return trimmed;
        throw new InvalidSupervisorResponseException(
                "No JSON block found in LLM response");
    }
}
