package com.wms.check.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 诊断能力的外部化配置，对应 application.yml 里的 ai.* 节点。
 * 用 @ConfigurationProperties 做类型安全绑定，而不是在每个用到的地方散落 @Value("${ai.xxx}")——
 * 换一个供应商（比如从阿里云 DashScope 换成 OpenAI 官方）只需要改配置文件/环境变量，
 * 不用改一行 Java 代码。
 * <p>
 * apiKey 默认空字符串：没配置真实 key 时，OpenAiCompatibleDiagnosisClient 会直接走
 * 规则兜底、不发真实网络请求——这样这个仓库任何人 clone 下来不用申请 API key 也能跑起来。
 */
@Data
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    /**
     * OpenAI 兼容协议的 base url。默认指向阿里云 DashScope 的兼容模式端点，
     * 换成 OpenAI/DeepSeek/Moonshot 等任何兼容 /chat/completions 协议的服务都行。
     */
    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";

    private String apiKey = "";

    private String model = "qwen-plus";
}
