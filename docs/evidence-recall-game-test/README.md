# EvidenceRecallNode 游戏领域 Top-8 召回测试包

本目录用于测试游戏数据智能体的证据召回效果，内容与 `data-agent-management/src/main/resources/sql/game_schema.sql` 完全对齐。

## 文件

- `business_knowledge.csv`：20 条业务知识。每行可按“业务名词、说明、同义词、是否召回”录入业务知识页面。
- `game_agent_knowledge.md`：可直接上传到“智能体知识库”的 Markdown 文档。
- `top8_recall_test_cases.md`：16 个测试问题（含 2 个不可回答负向控制）、金标准证据 ID、参考 SQL/结果与评分方法。

## 推荐导入方式

1. 将 `business_knowledge.csv` 中的 20 行分别创建为业务知识，并保持“是否召回”为开启。
2. 将 `game_agent_knowledge.md` 作为 `DOCUMENT` 类型上传，建议选择“递归分块”。
3. 等待向量化成功后，逐条执行 `top8_recall_test_cases.md` 中的 query。
4. 从 EvidenceRecallNode 输出中记录召回的 `BK-*` 与 `AK-*` 标识，和金标准比较。

## 当前实现的 Top-8 口径

当前 `EvidenceRecallNode` 会分别检索业务知识与智能体知识库；两次检索各自使用 `default-topk-limit: 8`，默认相似度阈值为 `0.4`。所以一次查询最多得到 8 条业务知识加 8 个知识库文档块，而不是把两类证据合并后统一取 8 条。

建议分别计算：

```text
Business Recall@8 = 命中的金标准 BK 数 / 该问题全部金标准 BK 数
Agent Recall@8    = 命中的金标准 AK 数 / 该问题全部金标准 AK 数
Macro Recall@8    = 所有测试问题 Recall@8 的算术平均
```

若还要衡量噪声，可同时记录：

```text
Precision@8 = Top-8 中命中的金标准证据数 / 实际返回证据数
MRR         = 1 / 第一条相关证据的排名
```

## 注意事项

- `game_schema.sql` 中的游戏和数值均为虚构数据；本测试包中的外部资料只用于补充行业概念，不能把虚构结果解释成真实市场结论。
- `revenue` 是测试库内的简化“总收入”，不是扣除渠道分成、税费、退款和运营成本后的净收入。
- 如果改变 embedding 模型、混合检索开关、分块器、阈值或查询重写模型，应保留一份独立实验记录，不要把不同配置的结果直接混算。
