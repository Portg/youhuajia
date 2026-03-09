# VIBE-CHECKLIST.md — AI 输出六关验收门禁

> 每次 AI 生成代码后，逐关检查。全部通过方可交付。

---

## 【第一关】意图对齐

- [ ] AI 输出是否解决了 `intent.md` 里的核心业务问题？
- [ ] `intent.md` 中的 Anti-Goals 有没有被违反？（逐条核对）
- [ ] AI 有没有发明 `intent.md` 未定义的业务规则？

## 【第二关】上下文遗漏

- [ ] `state-machines.yaml` 中所有状态都被正确处理了？
- [ ] 命名与 `CLAUDE.md` 完全一致？（camelCase/snake_case/kebab-case）
- [ ] 没有多出 `openapi.yaml` 未定义的字段？

## 【第三关】AI 幻觉风险

- [ ] 没有自行发明 `openapi.yaml` 里没有的字段或接口？
- [ ] 没有硬编码的魔法数字（应引用 `rules.md` 的 Rule Key 或 `application.yml`）？
- [ ] 注释描述的逻辑和代码实际实现一致？

## 【第四关】金融场景专项

- [ ] 金额计算全部使用 BigDecimal？
- [ ] 状态流转操作有幂等保护？
- [ ] 高敏字段（身份证、银行卡、手机号）没有出现在任何日志语句中？
- [ ] 并发修改场景有乐观锁（`@Version`）？

## 【第五关】代码质量底线

- [ ] 没有 `e.printStackTrace()`？
- [ ] 所有 TODO 注释有对应 Issue 编号？
- [ ] 没有 `@SuppressWarnings("all")`？
- [ ] Controller 不含 if/else 业务判断（必须在 Service 层）？
- [ ] 异常处理使用 `BizException(ErrorCode)`，非裸 RuntimeException？

## 【第六关】重构信号检测

- [ ] 这个类的职责超过一个了吗？（单一职责检查）
- [ ] 有没有超过 20 行的 Service 方法？（方法过长信号）
- [ ] 有没有超过 3 层的 if 嵌套？（复杂度过高信号）
- [ ] 有没有重复出现 3 次以上的代码模式？（重复代码信号）
- [ ] 复杂逻辑是否已标注 `@AiGenerated` 注解？
- [ ] Scratchpad 推理链中标注为「不确定」的部分是否已标注？

## 【附加】测试覆盖验收

- [ ] 是否同步生成了测试文件？
- [ ] Anti-Goals 中每条规则是否有对应 @Test 方法？
- [ ] 所有非法状态跳跃是否有 assertThrows 测试？
- [ ] 测试使用 @DisplayName 包含中文业务描述？

---

检查结果：日期：____ 版本：____ 检查人：____
问题数：严重 __ / 一般 __ / 建议 __ | 测试通过：__/__
