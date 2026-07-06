# 政策 Excel 导入脚本使用说明

脚本位置：

```text
scripts/import_policy_excel.py
```

默认读取：

```text
data/人工智能OPC政策汇总_2026_完善版_豆包AI生成.xlsx
```

## 1. 只检查，不写数据库

```powershell
python scripts/import_policy_excel.py
```

这个模式会输出：

- 有效政策数量
- 计划新增来源数量
- 计划新增政策数量
- 标签数量
- `policy_tags` 关系数量
- 地区、政策等级、状态、标签统计
- 可能的警告

## 2. 生成 SQL 文件

```powershell
python scripts/import_policy_excel.py --write-sql
```

生成文件：

```text
outputs/import_policies_policy_excel_20260704.sql
```

这个 SQL 文件不会自动执行，需要先人工检查，再在 MySQL 的 `opc_platform` 数据库中执行。

## 3. 只处理前 N 条

```powershell
python scripts/import_policy_excel.py --limit 5
```

生成前 5 条的 SQL：

```powershell
python scripts/import_policy_excel.py --limit 5 --write-sql --output outputs/import_policies_sample.sql
```

## 4. 导入逻辑

脚本会生成以下数据：

- `sources`
- `policies`
- `tags`
- `policy_tags`

主要去重规则：

- `sources`：按 `url` 或 `title + publisher`
- `policies`：按 `original_url` 或 `title + issuing_body`
- `tags`：按 `name + tag_type`
- `policy_tags`：按 `policy_id + tag_id`

导入批次标记：

```text
policy_excel_20260704
```
