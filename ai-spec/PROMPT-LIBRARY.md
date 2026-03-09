# PROMPT-LIBRARY.md — Prompt 索引

> 本文件为索引，具体 Prompt 内容见 `PROMPT-VERSIONS/` 对应目录。

---

## 使用规则

1. 每次使用前，先确认使用的是 `PROMPT-VERSIONS/` 中的最新推荐版本
2. 使用后在 `SCRATCHPAD.md` 记录版本号和效果反馈
3. 发现 Prompt 问题时，在对应 `CHANGELOG.md` 中记录，不要直接修改 Library

---

## Prompt 索引

### P-01 生成 API 接口层
当前推荐版本：`PROMPT-VERSIONS/generate-api/v1.0.md`
Golden Test 最新通过率：待测试
上次更新：2026-03-09 | 初始版本

### P-02 生成数据层（Entity + Mapper + 迁移脚本）
当前推荐版本：`PROMPT-VERSIONS/generate-entity/v1.0.md`
Golden Test 最新通过率：待测试
上次更新：2026-03-09 | 初始版本

### P-03 生成测试套件
当前推荐版本：`PROMPT-VERSIONS/generate-test/v1.0.md`
Golden Test 最新通过率：待测试
上次更新：2026-03-09 | 初始版本

### P-04 AI Code Review
当前推荐版本：`PROMPT-VERSIONS/code-review/v1.0.md`
Golden Test 最新通过率：待测试
上次更新：2026-03-09 | 初始版本

### P-05 SPEC 生成（Phase 1~3）
当前推荐版本：见 `SPEC-DISCOVERY.md`（不做版本化，每次对话定制）

### P-06 OCR 字段抽取
当前推荐版本：`prompts/ocr-extract.md`（直接使用，未版本化）

### P-07 优化建议生成
当前推荐版本：`prompts/suggestion-gen.md`（直接使用，未版本化）

---

## 版本降级程序

如果最新版本 Golden Test 通过率低于上一版本：
1. 在 `CHANGELOG.md` 中标注「回退」
2. 将上一版本设为当前推荐版本
3. 分析原因后再尝试修复
