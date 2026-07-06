# OPC 政策 Excel 导入字段映射表

本文档用于说明 `data/人工智能OPC政策汇总_2026_完善版_豆包AI生成.xlsx` 导入后端数据库时的字段映射规则。

当前 Excel 是政策数据，主要导入三类表：

- `sources`：资料来源表
- `policies`：政策主表
- `tags`：标签字典表
- `policy_tags`：政策和标签关系表

暂不导入 `case_items`，因为当前 Excel 不包含案例数据。

## 一、地区处理规则

当前 MVP 推荐方案：

- `policies.region_id` 先按 `region省` 匹配 `regions.name`
- `region市`、`region区` 暂时不进入 `region_id`
- 市区信息先合并保存到 `policies.key_points` 中，便于详情页追溯

这样做的原因：

- 目前 `regions` 表主要已初始化 34 个省级地区
- 直接扩展全国城市/区县数据会增加清洗工作量
- 现阶段政策筛选以省级为主，已经能满足 MVP 展示和会议材料整理

后续升级方案：

- 继续使用 `regions` 表，不新建 `cities` 表
- 在 `regions` 表中补充城市、区县数据
- 使用 `level` 区分 `province`、`city`、`district`
- 使用 `parent_id` 建立省、市、区层级关系

不建议单独新建 `cities` 表。因为现在的 `regions` 表本身就是为了做通用地区层级设计的，后续直接扩展它更干净。

## 二、sources 来源表映射

每一条政策导入时，同时生成一条来源记录。

| Excel 字段 | sources 字段 | 转换规则 | 备注 |
|---|---|---|---|
| `title政策标题` | `title` | 原样写入 | 来源标题先使用政策标题 |
| `url政策原文网页链接` | `url` | 原样写入 | 原始政策网页 |
| 空 | `source_type` | 固定为 `government_site` | 当前数据大多来自政府官网 |
| `发文单位` | `publisher` | 原样写入 | 发布机构 |
| 空 | `local_file` | 空字符串或 `NULL` | 当前 Excel 暂无本地文件 |
| 导入当天日期 | `accessed_at` | 使用导入日期 | 建议不要用发布日期代替访问日期 |
| 空 | `notes` | 拼接备注 | 记录 Excel 来源和导入批次 |
| `政策状态` | `status` | 转换为系统状态 | 见状态转换表 |

`sources.notes` 建议格式：

```text
Imported from 人工智能OPC政策汇总_2026_完善版_豆包AI生成.xlsx; import_batch=policy_excel_20260704
```

## 三、policies 政策表映射

| Excel 字段 | policies 字段 | 转换规则 | 备注 |
|---|---|---|---|
| `title政策标题` | `title` | 原样写入 | 必填 |
| `region省` | `region_id` | 按省名匹配 `regions.id` | MVP 阶段按省匹配 |
| `发文单位` | `issuing_body` | 原样写入 | 必填 |
| `文号` | `document_no` | 原样写入 | 政策正式文号 |
| `发布日期` | `publish_date` | 转成 `YYYY-MM-DD` | 优先使用发布日期 |
| `开始实施时间` | `effective_date` | 能识别为日期时转成 `YYYY-MM-DD` | 无法识别时留空 |
| `政策有效时长` | `valid_period` | 原样写入 | 如 `长期有效`、`至2028年12月31日` |
| 导入生成的来源 ID | `source_id` | 插入 `sources` 后回填 | 必填 |
| `policy_level政策等级` | `policy_level` | 转成英文枚举 | 见政策等级转换表 |
| 空 | `policy_type` | 固定为 `comprehensive` | 当前 Excel 多为综合政策 |
| `summary摘要（100字左右）` | `summary` | 原样写入 | 必填 |
| `政策要点(多值)` + 基础信息 | `key_points` | 拼接文本 | 保存市区等 |
| 具体政策字段 | `support_measures` | 分段拼接 | 保存算力、资金、场地等措施 |
| `政策要点(多值)` | `tags` | 原样写入，英文逗号分隔 | 同时可写入 `tags` 字典 |
| `url政策原文网页链接` | `original_url` | 原样写入 | 政策原文 |
| `辅证链接` | `evidence_url` | 原样写入 | 政策相关佐证网页 |
| 空 | `local_file` | 空字符串或 `NULL` | 当前 Excel 暂无本地文件 |
| 导入当天日期 | `accessed_at` | 使用导入日期 | 表示资料访问/导入日期 |
| `政策状态` | `status` | 转换为系统状态 | 见状态转换表 |
| 空 | `reviewer` | 固定为 `excel-import` | 标记导入来源 |

## 四、key_points 拼接规则

`key_points` 用于保存当前数据库没有单独字段，但政策详情页可能需要展示的信息。

时间类字段保留三类：

- `发布日期 -> publish_date`
- `开始实施时间 -> effective_date`
- `政策有效时长 -> valid_period`

`成文日期`、`废止日期` 不进入数据库。

建议格式：

```text
政策要点：算力支持,资金补贴,场地工位
地区：广西壮族自治区 / 南宁市 / 
```

## 五、support_measures 拼接规则

将 Excel 中的具体政策字段合并到 `support_measures`。

建议格式：

```text
【算力支持】
对OPC企业实际使用算力结算金额的30%给予每年最高200万元的算力支持。

【资金补贴】
正常经营满1年的OPC给予5000元创业奖励。

【场地工位】
提供免费创业工位或最高100平方米免费创业空间。

【场景需求】
支持OPC面向东盟应用场景落地。

【人才服务】
给予优秀创业团队项目资助、青年人才生活补助等。

【投资融资】
可申请最高30万元、最长3年政府贴息创业担保贷款。

【其他】
组建OPC创业导师队伍提供全链条服务。
```

涉及的 Excel 字段：

- `具体政策·算力支持`
- `具体政策·资金补贴`
- `具体政策·场地工位`
- `具体政策·场景需求`
- `具体政策·人才服务`
- `具体政策·投资融资`
- `具体政策·其他`

## 六、tags 标签表映射

从 `政策要点(多值)` 中拆分标签，写入 `tags` 字典表。

| Excel 值 | tags.name | tags.tag_type | 备注 |
|---|---|---|---|
| `算力支持` | `算力支持` | `policy` | 政策标签 |
| `资金补贴` | `资金补贴` | `policy` | 政策标签 |
| `场地工位` | `场地工位` | `policy` | 政策标签 |
| `场景需求` | `场景需求` | `policy` | 政策标签 |
| `人才服务` | `人才服务` | `policy` | 政策标签 |
| `投资融资` | `投资融资` | `policy` | 政策标签 |
| `其他` | `其他` | `policy` | 政策标签 |

导入时应按 `(name, tag_type)` 去重，已存在则跳过。

## 七、policy_tags 政策标签关系表映射

导入政策时，从 `政策要点(多值)` 拆分标签，并建立政策和标签之间的关系。

示例：

```text
policies.id = 1
policies.tags = 算力支持,资金补贴

tags:
1 算力支持 policy
2 资金补贴 policy

policy_tags:
policy_id=1, tag_id=1
policy_id=1, tag_id=2
```

导入时应按 `(policy_id, tag_id)` 去重。

## 八、枚举转换规则

### 政策等级

| Excel 值 | 数据库值 |
|---|---|
| `国家级` | `national` |
| `省级` | `provincial` |
| `市级` | `city` |
| `区级` | `district` |

### 政策状态

| Excel 值 | 数据库值 |
|---|---|
| `现行有效` | `published` |
| `征求意见稿` | `draft` |
| 其他或空值 | `draft` |

### 来源类型

当前批次统一为：

```text
government_site
```

### 政策类型

当前批次统一为：

```text
comprehensive
```

## 九、去重规则

导入 `sources` 时，建议按以下优先级去重：

1. `url` 完全相同
2. `title` + `publisher` 完全相同

导入 `policies` 时，建议按以下优先级去重：

1. `original_url` 完全相同
2. `title` + `issuing_body` 完全相同

导入 `tags` 时，按已有唯一规则去重：

```text
name + tag_type
```

导入 `policy_tags` 时，按已有唯一规则去重：

```text
policy_id + tag_id
```

## 十、导入批次标记

为避免后续重复导入混乱，建议导入脚本统一加入批次标记。

`sources.notes` 中加入：

```text
import_batch=policy_excel_20260704
```

`policies.reviewer` 中写入：

```text
excel-import
```

后续如果需要回滚，可按这些标记定位本批次数据。

## 十一、暂不处理字段

以下字段暂不单独建数据库字段，先进入 `key_points`：

- `region市`
- `region区`

以下时间字段导入时直接忽略：

- `成文日期`
- `废止日期`

如果后续老师明确需要按有效期、文号、市区筛选，再考虑给 `policies` 表补充字段，或扩展 `regions` 市区数据。
