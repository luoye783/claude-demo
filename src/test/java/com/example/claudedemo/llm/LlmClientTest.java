package com.example.claudedemo.llm;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@SpringBootTest
class LlmClientTest {

    @Autowired
    LlmClient llmClient;

    @Test
    public void testLLM(){
        LlmResponse llmResponse = llmClient.chat(List.of(new ChatMessage("user","你好")));
        System.out.printf(llmResponse.content());
        System.out.printf(llmResponse.finishReason());
    }
    @Test
    void should_call_chat_completions_and_return_first_choice() {
        // 1. 准备 Builder 并绑定 MockRestServiceServer
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer mockServer = MockRestServiceServer.bindTo(builder).build();

        // 2. 配置属性
        LlmProperties props = new LlmProperties();
        props.setBaseUrl("http://localhost");
        props.setApiKey("test-key");
        props.setModel("minimax-m3");

        // 3. 创建客户端
        LlmClient client = new LlmClient(props, builder);

        // 4. 注入 mock 响应 + 验证请求
        mockServer.expect(once(), requestToUriTemplate("http://localhost/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(jsonPath("$.model").value("minimax-m3"))
                .andExpect(jsonPath("$.messages[0].role").value("user"))
                .andExpect(jsonPath("$.messages[0].content").value("Hello"))
                .andExpect(jsonPath("$.messages[1].role").value("assistant"))
                .andExpect(jsonPath("$.messages[1].content").value("Hi"))
                .andRespond(withSuccess("""
                        {
                          "choices": [
                            {
                              "message": {"role": "assistant", "content": "How can I help?"},
                              "finish_reason": "stop"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        // 5. 调用 + 断言
        List<ChatMessage> messages = List.of(
                new ChatMessage("user", "Hello"),
                new ChatMessage("assistant", "Hi")
        );
        LlmResponse response = client.chat(messages);

        assertEquals("How can I help?", response.content());
        assertEquals("stop", response.finishReason());
        mockServer.verify();
    }

    @Test
    void should_use_correct_authorization_header_per_api_key() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer mockServer = MockRestServiceServer.bindTo(builder).build();

        LlmProperties props = new LlmProperties();
        props.setBaseUrl("http://localhost");
        props.setApiKey("my-secret-key");
        props.setModel("minimax-m3");

        LlmClient client = new LlmClient(props, builder);

        mockServer.expect(once(), requestToUriTemplate("http://localhost/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer my-secret-key"))
                .andRespond(withSuccess("""
                        {
                          "choices": [
                            {"message": {"role": "assistant", "content": "ok"}, "finish_reason": "stop"}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        LlmResponse response = client.chat(List.of(new ChatMessage("user", "ping")));
        assertEquals("ok", response.content());
        mockServer.verify();
    }
}
