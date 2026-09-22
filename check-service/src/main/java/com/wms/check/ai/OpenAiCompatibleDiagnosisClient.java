package com.wms.check.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * 对接任意"OpenAI 兼容协议"的 chat/completions 接口（阿里云 DashScope、DeepSeek、
 * Moonshot、OpenAI 官方等都兼容这套协议，换供应商只用改 application.yml 里的 ai.base-url）。
 * <p>
 * 没配置 apiKey 时直接走规则兜底，不发真实请求——这是"优雅降级"：AI 诊断只是运维辅助功能，
 * 不是核对主流程的一部分，它不可用不该影响任何核心业务，也不该逼着每个跑这个项目的人
 * 都去申请一个 API key。真调用失败（网络、超时、限流）时同样兜底，只打日志不往上抛异常。
 * <p>
 * 用 Spring 6.1 自带的 RestClient，不用额外引入 HTTP 客户端依赖
 * （spring-boot-starter-web 已经带了它）。
 */
@Component
public class OpenAiCompatibleDiagnosisClient implements AiDiagnosisClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleDiagnosisClient.class);

    private static final String SYSTEM_PROMPT =
            "你是仓库出库核对系统的运维顾问。根据给出的核对失败/重试记录，用简洁的中文说明可能的根因，" +
            "并给出 1~2 条可执行的排查建议。不要编造记录里没有的信息。";

    private final AiProperties properties;
    private final RestClient restClient;

    @Autowired
    public OpenAiCompatibleDiagnosisClient(AiProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder().baseUrl(properties.getBaseUrl()).build();
    }

    @Override
    public String diagnose(String prompt) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            log.info("未配置 ai.api-key，AI 诊断走规则兜底");
            return fallback(prompt);
        }
        try {
            ChatCompletionResponse response = restClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .body(new ChatCompletionRequest(
                            properties.getModel(),
                            List.of(
                                    new ChatMessage("system", SYSTEM_PROMPT),
                                    new ChatMessage("user", prompt)
                            ),
                            0.3
                    ))
                    .retrieve()
                    .body(ChatCompletionResponse.class);

            if (response == null || response.choices() == null || response.choices().isEmpty()) {
                log.warn("AI 诊断接口返回了空结果，走规则兜底");
                return fallback(prompt);
            }
            return response.choices().get(0).message().content();
        } catch (Exception e) {
            // 下游 AI 服务超时/限流/网络抖动，不应该影响这个只读诊断接口本身的可用性
            log.warn("调用 AI 诊断接口失败，走规则兜底：{}", e.getMessage());
            return fallback(prompt);
        }
    }

    /**
     * 规则兜底：没配 key、或者真实调用失败时，至少把已经拼好的原始上下文原样返回，
     * 好过整个接口直接报错——对使用者来说，"看到原始数据自己判断"也比"什么都看不到"强。
     */
    private String fallback(String prompt) {
        return "[AI 诊断不可用，以下是原始核对记录，请人工判断]\n" + prompt;
    }

    private record ChatCompletionRequest(String model, List<ChatMessage> messages, double temperature) {
    }

    private record ChatMessage(String role, String content) {
    }

    private record ChatCompletionResponse(List<Choice> choices) {
    }

    private record Choice(ChatMessage message) {
    }
}
