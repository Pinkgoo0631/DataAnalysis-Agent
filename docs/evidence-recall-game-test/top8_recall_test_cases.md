# 游戏数据问答 Top-8 召回测试用例（16 条）

## 评分说明

执行每条 Query 后，从 EvidenceRecallNode 输出中提取 `BK-*` 和 `AK-*`。只要返回内容包含对应证据标识，即判定该证据命中。业务知识与智能体知识库分别计算 Recall@8；“可接受辅助证据”不计入召回分子，也不视为错误。

参考结果基于 `game_schema.sql` 当前 40 行数据。如果数据发生变化，应重新执行参考 SQL，不要继续使用旧结果。

---

## T01：限定年份的收入 TopN

**Query**：2024 年发行的游戏里，总收入最高的 3 款分别是什么？给出收入和实际销量。

- 金标准业务证据：`BK-02`、`BK-07`
- 金标准智能体证据：`AK-01`、`AK-13`
- 可接受辅助证据：`AK-04`、`AK-14`
- 参考 SQL：`SELECT name,revenue,actual_sales FROM games WHERE release_year=2024 ORDER BY revenue DESC,id ASC LIMIT 3;`
- 参考结果：未来战场 316480.00 万元/860.00 万份；都市特攻 268500.00 万元/750.00 万份；时空裂缝 152640.00 万元/480.00 万份。

## T02：按类型聚合平均销量

**Query**：按游戏类型统计平均实际销量，列出平均销量最高的前三个类型，并显示每类样本数。

- 金标准业务证据：`BK-01`、`BK-11`
- 金标准智能体证据：`AK-02`、`AK-09`、`AK-13`
- 参考 SQL：`SELECT t.name,COUNT(*) n,AVG(g.actual_sales) avg_sales FROM games g JOIN game_types t ON g.type_id=t.id GROUP BY t.id,t.name ORDER BY avg_sales DESC,t.id LIMIT 3;`
- 参考结果：休闲益智 1852.50 万份（4）；射击 936.00 万份（5）；体育竞技 746.67 万份（3）。

## T03：简化 ROI 排名

**Query**：按总投入回报率给所有游戏排名，返回前三名，并写明 ROI 的计算口径。

- 金标准业务证据：`BK-02`、`BK-03`、`BK-05`
- 金标准智能体证据：`AK-05`、`AK-13`
- 可接受辅助证据：`AK-14`
- 参考 SQL：`SELECT name,(revenue-total_investment)/NULLIF(total_investment,0)*100.0 roi_pct FROM games ORDER BY roi_pct DESC,id ASC LIMIT 3;`
- 参考结果：龙裔编年史 1850.20%；未来战场 1761.65%；都市特攻 1690.00%。

## T04：营销销量效率

**Query**：每一万元营销预算带来的销量最高的是哪些游戏？列出前 5 名，并提醒我这个指标能不能证明营销带来了销量。

- 金标准业务证据：`BK-01`、`BK-09`
- 金标准智能体证据：`AK-08`、`AK-14`
- 参考 SQL：`SELECT name,actual_sales/NULLIF(marketing_budget,0) sales_per_marketing FROM games ORDER BY sales_per_marketing DESC,id ASC LIMIT 5;`
- 参考结果：钢铁射击场 1.8667；拼图大师 1.6667；方块消消乐 1.6000；猫咪养成记 1.4444；街头滑板 1.2000。该比率只描述关联，不能证明因果。

## T05：评分分组反常结果

**Query**：高评分游戏和其他游戏的平均销量、平均收入分别是多少？高评分按项目默认口径，解释为什么结果可能与直觉不同。

- 金标准业务证据：`BK-01`、`BK-02`、`BK-14`
- 金标准智能体证据：`AK-03`、`AK-07`、`AK-09`、`AK-14`
- 参考 SQL：`SELECT CASE WHEN review_score>=8.5 THEN '高评分' ELSE '其他' END score_group,COUNT(*) n,AVG(actual_sales),AVG(revenue) FROM games GROUP BY score_group;`
- 参考结果：高评分组 18 款，平均销量 430.00 万份、平均收入 119367.78 万元；其他组 22 款，平均销量 870.32 万份、平均收入 20905.27 万元。低价手机游戏抬高了其他组的销量均值，不能据此得出评分降低销量。

## T06：IP 组与非 IP 组比较

**Query**：知名 IP 或续作与原创游戏相比，平均销量和平均收入有多大差异？请同时给样本量，不要把相关性说成因果。

- 金标准业务证据：`BK-01`、`BK-02`、`BK-13`、`BK-19`
- 金标准智能体证据：`AK-06`、`AK-09`、`AK-14`
- 参考 SQL：`SELECT is_ip,COUNT(*) n,AVG(actual_sales) avg_sales,AVG(revenue) avg_revenue FROM games GROUP BY is_ip;`
- 参考结果：IP 组 12 款，平均销量 644.17 万份、平均收入 119395.00 万元；非 IP 组 28 款，平均销量 684.18 万份、平均收入 41992.71 万元。IP 组平均收入约高 184.32%，但不能解释为因果提升。

## T07：平台年度收入

**Query**：2023 年各主要发行平台的总收入是多少？按收入降序，并说明这里的平台字段有什么限制。

- 金标准业务证据：`BK-02`、`BK-12`
- 金标准智能体证据：`AK-03`、`AK-13`
- 参考 SQL：`SELECT p.name,SUM(g.revenue) total_revenue,SUM(g.actual_sales) total_sales,COUNT(*) n FROM games g JOIN game_platforms p ON g.platform_id=p.id WHERE g.release_year=2023 GROUP BY p.id,p.name ORDER BY total_revenue DESC;`
- 参考结果：Xbox 281920.00 万元；PlayStation 263500.00 万元；PC 159000.00 万元；Switch 40200.00 万元。每款游戏只有一个主要平台，不能解释为同一作品的跨平台拆分。

## T08：预算档位表现

**Query**：按小型、中型、大型投入三个档位，比较游戏数量、平均销量和平均收入。预算边界按项目知识定义。

- 金标准业务证据：`BK-01`、`BK-02`、`BK-15`
- 金标准智能体证据：`AK-05`、`AK-09`
- 参考 SQL：`SELECT CASE WHEN total_investment<3000 THEN '小型' WHEN total_investment<10000 THEN '中型' ELSE '大型' END tier,COUNT(*) n,AVG(actual_sales),AVG(revenue) FROM games GROUP BY tier;`
- 参考结果：小型 15 款，平均销量 1070.47 万份、平均收入 10959.73 万元；中型 15 款，330.67 万份、42005.33 万元；大型 10 款，587.00 万份、181406.00 万元。

## T09：销量冠军与收入冠军

**Query**：销量最高的游戏和收入最高的游戏是同一款吗？分别给出冠军及其销量、售价、收入，并解释差异。

- 金标准业务证据：`BK-01`、`BK-02`、`BK-07`
- 金标准智能体证据：`AK-03`、`AK-04`、`AK-10`
- 参考 SQL：分别按 `actual_sales DESC` 与 `revenue DESC` 各取一条。
- 参考结果：销量冠军是方块消消乐（3200.00 万份、6.00 元、19200.00 万元）；收入冠军是龙裔编年史（980.00 万份、398.00 元、390040.00 万元）。二者不是同一款，主要差异来自售价与平台结构。

## T10：类型收入贡献

**Query**：角色扮演 RPG 对全库总收入贡献了多少？给出该类型收入、全库收入和占比。

- 金标准业务证据：`BK-02`、`BK-11`、`BK-18`
- 金标准智能体证据：`AK-02`、`AK-09`、`AK-13`
- 参考 SQL：`SELECT SUM(CASE WHEN t.name='角色扮演RPG' THEN g.revenue ELSE 0 END) rpg_revenue,SUM(g.revenue) total_revenue,SUM(CASE WHEN t.name='角色扮演RPG' THEN g.revenue ELSE 0 END)/SUM(g.revenue)*100.0 share_pct FROM games g JOIN game_types t ON g.type_id=t.id;`
- 参考结果：RPG 收入 635176.00 万元，全库收入 2608536.00 万元，占 24.35%。

## T11：销量加权平均售价

**Query**：全库按销量加权的平均每份收入是多少？不要用游戏标价的简单平均。

- 金标准业务证据：`BK-08`
- 金标准智能体证据：`AK-01`、`AK-04`
- 参考 SQL：`SELECT SUM(revenue)/NULLIF(SUM(actual_sales),0) weighted_price FROM games;`
- 参考结果：97.02 元/份。

## T12：回本数量与口径局限

**Query**：按本项目的简化回本标准，有多少款游戏已经回本？这个结果为什么不能当作真实利润结论？

- 金标准业务证据：`BK-02`、`BK-03`、`BK-04`、`BK-06`
- 金标准智能体证据：`AK-04`、`AK-05`、`AK-14`
- 参考 SQL：`SELECT SUM(CASE WHEN revenue>=total_investment THEN 1 ELSE 0 END) break_even_count,COUNT(*) total_count FROM games;`
- 参考结果：40/40 款按简化口径回本；因未计渠道分成、税费、退款和持续运营等成本，不能称为真实盈利。

## T13：上线前销量预测方案

**Query**：我要预测下一款 PC 动作游戏的销量。请说明目标变量、可以使用的特征、必须排除的泄漏字段，以及验证方案。

- 金标准业务证据：`BK-20`
- 金标准智能体证据：`AK-07`、`AK-11`、`AK-12`
- 可接受辅助证据：`AK-08`、`AK-09`
- 参考要点：目标为 `actual_sales`；上线前排除 `revenue` 和最终 `review_score`；使用可获得的类型、平台、预算、团队、周期、IP、售价和年份；以同类基线加简单模型开始，采用时间切分或交叉验证，报告误差和预测区间。

## T14：营销预算与销量是否存在相关性

**Query**：分析营销预算和实际销量的关系，并判断“增加营销预算会导致销量增长”这个结论是否成立。

- 金标准业务证据：`BK-01`、`BK-09`
- 金标准智能体证据：`AK-08`、`AK-09`、`AK-14`
- 参考要点：可以计算相关系数、画散点并做分层描述，但观察数据存在平台、类型、IP、价格、年份和预算选择等混杂因素，不能由相关分析推出因果结论。

## T15：不可回答的留存问题（负向控制）

**Query**：比较各游戏类型的次日留存率和七日留存率，告诉我哪个类型的玩家最活跃。

- 金标准业务证据：无
- 金标准智能体证据：`AK-01`、`AK-15`
- 参考要点：当前 schema 没有用户、登录、会话或按日行为数据，累计销量不能代替活跃或留存；应明确不可回答并说明需要的字段。

## T16：不可回答的真实净利润问题（负向控制）

**Query**：扣除退款、税费、平台分成和持续运营成本后，哪款游戏的真实净利润最高？

- 金标准业务证据：`BK-02`、`BK-04`
- 金标准智能体证据：`AK-04`、`AK-05`、`AK-21`
- 参考要点：当前 `revenue` 是简化总收入，且相关扣减字段缺失；不得把 `revenue-total_investment` 称为真实净利润，也不能擅自假设固定平台分成。

---

## 建议的结果记录表

| 测试ID | 重写后查询 | 返回BK排名 | 返回AK排名 | BK Recall@8 | AK Recall@8 | 第一相关证据排名 | 备注 |
|---|---|---|---|---:|---:|---:|---|
| T01 |  |  |  |  |  |  |  |
| T02 |  |  |  |  |  |  |  |
| T03 |  |  |  |  |  |  |  |
| T04 |  |  |  |  |  |  |  |
| T05 |  |  |  |  |  |  |  |
| T06 |  |  |  |  |  |  |  |
| T07 |  |  |  |  |  |  |  |
| T08 |  |  |  |  |  |  |  |
| T09 |  |  |  |  |  |  |  |
| T10 |  |  |  |  |  |  |  |
| T11 |  |  |  |  |  |  |  |
| T12 |  |  |  |  |  |  |  |
| T13 |  |  |  |  |  |  |  |
| T14 |  |  |  |  |  |  |  |
| T15 |  |  |  |  |  |  |  |
| T16 |  |  |  |  |  |  |  |
