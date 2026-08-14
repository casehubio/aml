package io.casehub.aml.supervisor;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.casehub.api.model.ai.ChatModelProvider;
import io.casehub.api.model.ai.ModelType;
import org.junit.jupiter.api.Test;

import java.util.List;

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
}
