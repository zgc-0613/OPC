# 高校 OPC 数据集核验记录

核验日期：2026-08-25  
核验范围：`高校OPC数据集.xlsx`、四张同名 CSV、`高校OPC数据集_研究报告.md`

## 结论

四张 CSV 是当前可用的事实底稿，共 42 条记录：社区 12 条、支持措施 11 条、竞赛与活动 7 条、创业案例 12 条。四表合计核验状态为 `verified=30`、`partially_verified=5`、`pending=7`，与研究报告第二、四节的汇总一致。

按省份的四类记录数量也与报告第三节一致：北京 2/2/2/2，吉林 1/1/0/0，广东 0/0/0/2，江苏 4/4/5/7，浙江 2/2/0/0，湖北 2/1/0/1，福建 1/1/0/0。四类对象不能相加解释为高校 OPC 规模。

## 必须处理的问题

1. **显示编码需要区分。** 原始 Excel 内部 XML 中的中文和工作表名称正常；此前在 PowerShell/Python 输出中出现的 `����` 属于终端编码显示问题，不是工作簿损坏。四张 CSV 也可正常按 UTF-8 读取。整理版保留原工作表，不覆盖原始文件。
2. **需要区分主表状态和排除线索。** 四张明细表的主表状态只有 `verified`、`partially_verified`、`pending`；工作簿“10 待核验记录清单”另列 1 条排除线索（`rejected`），它不属于四张主表，因此不计入 42 条主表记录。报告中的 13 条清单是 12 条主表待核验/部分核验记录加 1 条排除线索，口径可以保留，但应在网站导入时排除该线索。
3. **案例来源存在复用，不等于案例重复。** 4 条案例共享 `smartcity.team` 报告，2 条共享科学网报道。当前 `case_id`、案例名称和主体并不重复，因此暂不删除；需要逐条确认是否为不同事件后再决定去重。
4. **日期字段不是统一日期类型。** `launch_date`、`start_date`、`activity_date`、`publish_date` 同时存在精确日期、月份、年份、时间范围和“未明确”。建议保留原文字段，并增加 `date_precision`（day/month/year/range/unknown）和可选的 `date_start`、`date_end`，不要把不完整日期强行改成某一天。

## 可直接自动整理的规则

- 保留四表现有主键：`Cxx`、`Sxx`、`Axx`、`Kxx`，不重新编号。
- 保留 `source_url` 原文，同时将多个链接按分号拆为独立来源行或 `source_url_1`、`source_url_2`，不删除任何链接。
- 将空字符串统一为真正空值；`未明确`、`不明确`、`未公布`保留为文本事实，不改成空值。
- 保留 `verification_status` 原值，`pending` 和 `partially_verified` 不自动升级。
- 统一关联字段校验：当前支持措施、案例的 `community_id` 和 `activity_id` 均指向现有 ID，没有发现悬空引用。
- 案例摘要目前 12 条均在 100–300 字范围内；`district`、`community_id`、`activity_id` 的空值主要是“无关联对象”或原文未提供，不能按缺失事实补写。
- 省份、城市、机构名称应保留当前中文原文；网站导入时另设规范化字段，不覆盖原始字段。

## 建议的导入字段边界

网站高校板块继续按四类对象分表或分类型导入，统一增加：`record_type`、`record_id`、`verification_status`、`evidence_grade`、`source_url`、`source_title`、`source_unit`、`collected_at`、`notes`。其中 `record_type` 取 `community`、`support`、`activity`、`case`，高校创业案例不要并入普通案例总量，社区和活动也分别统计。

本轮只完成只读核验和审计记录，没有覆盖原始 Excel、没有导入数据库，也没有进行网站部署；来源链接的可访问性尚未在本轮联网逐条复核。
