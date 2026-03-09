package com.youhua.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注 AI 生成的代码，便于后续追溯和审查。
 *
 * <p>使用场景：
 * <ul>
 *   <li>逻辑复杂但未经充分人工审查</li>
 *   <li>性能未经过基准测试</li>
 *   <li>依赖了 Mock 数据的临时实现</li>
 *   <li>AI 推理链记录显示「不确定」的部分</li>
 * </ul>
 *
 * @see <a href="ai-spec/TEST-SPEC.md">TEST-SPEC.md @AiGenerated 注解规范</a>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface AiGenerated {

    /** 标注原因：为什么需要人工复核 */
    String reason();

    /** AI 生成日期，格式 yyyy-MM-dd */
    String generatedAt();

    /** 预期人工审查截止时间，如 "2026-Q2" */
    String reviewBy() default "";

    /** 关联的 SCRATCHPAD 条目引用 */
    String scratchpadRef() default "";
}
