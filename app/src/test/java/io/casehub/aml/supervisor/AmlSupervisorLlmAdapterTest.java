package io.casehub.aml.supervisor;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.casehub.api.context.CaseContext;
import io.casehub.api.engine.PlanExecutionContext;
import io.casehub.api.model.ai.ChatModelProvider;
import io.casehub.api.model.ai.ModelType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AmlSupervisorLlmAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void isAvailable_true_when_provider_present() {
        var adapter = createAdapter(mockProvider());
        assertThat(adapter.isAvailable()).isTrue();
    }

    @Test
    void isAvailable_false_when_no_provider() {
        var adapter = new AmlSupervisorLlmAdapter((ChatModelProvider) null, objectMapper);
        assertThat(adapter.isAvailable()).isFalse();
    }

    @Test
    void consult_parses_json_response() {
        String json = """
                {"selectedBindings": ["pattern-analysis"],
                 "suppressedBindings": ["osint-screening"],
                 "rationale": "pattern first",
                 "earlyTermination": false}
                """;
        var adapter = createAdapter(mockProviderReturning(json));
        var decision = adapter.consult(null, null, List.of());
        assertThat(decision.selectedBindings()).containsExactly("pattern-analysis");
        assertThat(decision.suppressedBindings()).containsExactly("osint-screening");
        assertThat(decision.rationale()).isEqualTo("pattern first");
    }

    @Test
    void consult_extracts_json_from_markdown_fence() {
        String response = """
                Here is my decision:
                ```json
                {"selectedBindings": ["investigation-triage"],
                 "suppressedBindings": [],
                 "rationale": "sufficient evidence",
                 "earlyTermination": true}
                ```
                """;
        var adapter = createAdapter(mockProviderReturning(response));
        var decision = adapter.consult(null, null, List.of());
        assertThat(decision.earlyTermination()).isTrue();
        assertThat(decision.selectedBindings()).containsExactly("investigation-triage");
    }

    @Test
    void consult_throws_on_malformed_json() {
        var adapter = createAdapter(mockProviderReturning("not json at all"));
        assertThatThrownBy(() -> adapter.consult(null, null, List.of()))
                .isInstanceOf(InvalidSupervisorResponseException.class);
    }

    @Test
    void consult_throws_on_empty_response() {
        var adapter = createAdapter(mockProviderReturning(""));
        assertThatThrownBy(() -> adapter.consult(null, null, List.of()))
                .isInstanceOf(InvalidSupervisorResponseException.class);
    }

    @Test
    void extractJsonBlock_plain_json() {
        String json = "{\"selectedBindings\":[\"a\"],\"suppressedBindings\":[],\"rationale\":\"r\",\"earlyTermination\":false}";
        assertThat(AmlSupervisorLlmAdapter.extractJsonBlock(json)).isEqualTo(json);
    }

    @Test
    void extractJsonBlock_fenced_json() {
        String fenced = """
                Some preamble
                ```json
                {"key": "value"}
                ```
                """;
        assertThat(AmlSupervisorLlmAdapter.extractJsonBlock(fenced)).isEqualTo("{\"key\": \"value\"}");
    }

    @Test
    void extractJsonBlock_null_throws() {
        assertThatThrownBy(() -> AmlSupervisorLlmAdapter.extractJsonBlock(null))
                .isInstanceOf(InvalidSupervisorResponseException.class);
    }

    @Test
    void prompt_includes_rejection_context_when_actionGateRejected_present() {
        var node = objectMapper.createObjectNode();
        node.putObject("actionGateRejected")
            .put("actionType", "sar.filing")
            .put("workerId", "sar-drafting-agent-senior")
            .put("rejectedBy", "test-mlro")
            .put("resolution", "Insufficient evidence for SAR filing");

        var ctx     = mockCaseContext(node);
        var execCtx = new PlanExecutionContext(java.util.UUID.randomUUID(), null, ctx, null, null, null, null, null);

        var    adapter = createAdapter(mockProvider());
        String prompt  = adapter.buildPrompt(null, execCtx, List.of());

        assertThat(prompt).contains("## Rejection Context");
        assertThat(prompt).contains("Gate type: sar.filing");
        assertThat(prompt).contains("Rejected by: test-mlro");
        assertThat(prompt).contains("Rationale: Insufficient evidence for SAR filing");
    }

    @Test
    void prompt_includes_senior_analyst_review_when_rejectionReview_present() {
        var node = objectMapper.createObjectNode();
        node.putObject("actionGateRejected")
            .put("actionType", "sar.filing");
        node.putObject("rejectionReview")
            .put("riskAdjustment", -0.15)
            .put("finding", "Entity structure legitimate")
            .put("recommendedAction", "LOWER_RISK");

        var ctx     = mockCaseContext(node);
        var execCtx = new PlanExecutionContext(java.util.UUID.randomUUID(), null, ctx, null, null, null, null, null);

        var    adapter = createAdapter(mockProvider());
        String prompt  = adapter.buildPrompt(null, execCtx, List.of());

        assertThat(prompt).contains("## Senior Analyst Review");
        assertThat(prompt).contains("Risk adjustment: -0.15");
        assertThat(prompt).contains("Finding: Entity structure legitimate");
    }

    @Test
    void prompt_includes_layer_c_instructions_when_rejection_review_present() {
        var node = objectMapper.createObjectNode();
        node.putObject("actionGateRejected").put("actionType", "sar.filing");
        node.putObject("rejectionReview").put("riskAdjustment", -0.15).put("finding", "f").put("recommendedAction", "a");

        var ctx     = mockCaseContext(node);
        var execCtx = new PlanExecutionContext(java.util.UUID.randomUUID(), null, ctx, null, null, null, null, null);

        var    adapter = createAdapter(mockProvider());
        String prompt  = adapter.buildPrompt(null, execCtx, List.of());

        assertThat(prompt).contains("rejection routing");
        assertThat(prompt).contains("senior analyst review");
    }

    @Test
    void prompt_does_not_include_rejection_sections_without_rejection_fields() {
        var node = objectMapper.createObjectNode();
        node.putObject("entityResolution").put("riskScore", 0.5);

        var ctx     = mockCaseContext(node);
        var execCtx = new PlanExecutionContext(java.util.UUID.randomUUID(), null, ctx, null, null, null, null, null);

        var    adapter = createAdapter(mockProvider());
        String prompt  = adapter.buildPrompt(null, execCtx, List.of());

        assertThat(prompt).doesNotContain("## Rejection Context");
        assertThat(prompt).doesNotContain("## Senior Analyst Review");
        assertThat(prompt).doesNotContain("rejection routing");
    }


    private ChatModelProvider mockProvider() {
        return mockProviderReturning(
                "{\"selectedBindings\":[\"a\"],\"suppressedBindings\":[],\"rationale\":\"r\",\"earlyTermination\":false}");
    }

    private ChatModelProvider mockProviderReturning(String text) {
        ChatModel chatModel = mock(ChatModel.class);
        var response = ChatResponse.builder()
                .aiMessage(AiMessage.from(text))
                .build();
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(response);
        ChatModelProvider provider = mock(ChatModelProvider.class);
        when(provider.type()).thenReturn(ModelType.ANTHROPIC);
        when(provider.get()).thenReturn(chatModel);
        return provider;
    }

    private AmlSupervisorLlmAdapter createAdapter(ChatModelProvider provider) {
        return new AmlSupervisorLlmAdapter(provider, objectMapper);
    }

    private CaseContext mockCaseContext(com.fasterxml.jackson.databind.JsonNode node) {
        CaseContext ctx = mock(CaseContext.class);
        when(ctx.asJsonNode()).thenReturn(node);
        return ctx;
    }

}
