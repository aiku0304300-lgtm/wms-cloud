package com.wms.check.ai;

/**
 * AI 诊断能力的抽象：上层 AnomalyDiagnosisService 只依赖这个接口，
 * 不关心具体调的是哪家模型、走的是哪个协议。
 * <p>
 * 换供应商、接入本地私有模型、甚至换成"规则引擎+模板"的假实现（比如单测里 mock），
 * 都只需要换一个实现类，业务代码不用动——这跟 AiDiagnosisClient / OpenAiCompatibleDiagnosisClient
 * 这层拆分背后的思路，和 checkBox() 只依赖 Mapper 接口不关心底层是 MySQL 还是别的存储，是同一个道理。
 */
public interface AiDiagnosisClient {

    /**
     * @param prompt 已经拼好上下文的完整提示词
     * @return 模型给出的诊断文本
     */
    String diagnose(String prompt);
}
