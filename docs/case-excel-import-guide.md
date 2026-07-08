# 案例 Excel 导入脚本使用说明

脚本位置：

```text
scripts/import_case_excel.py
```

默认读取：

```text
data/人工智能OPC案例汇总_V2.xlsx
```

## 1. 只检查，不写数据库

```powershell
python scripts/import_case_excel.py
```

这个模式会输出：

- 有效案例数量
- 计划新增来源数量
- 计划新增案例数量
- 标签数量
- 省份、城市、案例类型、标签统计
- 可能的警告

如果出现未知省份，先不要导入，应先确认 `regions` 表是否已有该省份。

## 2. 生成 SQL 文件

```powershell
python scripts/import_case_excel.py --write-sql
```

生成文件：

```text
outputs/import_cases_case_excel_20260708.sql
```

这个 SQL 文件会写入：

- `sources`
- `case_items`
- `tags`

## 3. 生成导入核对 SQL

```powershell
python scripts/import_case_excel.py --write-check-sql
```

生成文件：

```text
outputs/check_case_import_case_excel_20260708.sql
```

导入后执行它，可以检查 Excel 中的案例是否全部进入数据库。

## 4. 执行导入 SQL

在 Windows PowerShell 中执行：

```powershell
& 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' -uroot -p123456 --default-character-set=utf8mb4 opc_platform --execute="source E:/MyCode/codex/OPC/outputs/import_cases_case_excel_20260708.sql"
```

注意：

- `--default-character-set=utf8mb4` 必须保留，避免中文乱码
- 如果你的 MySQL 密码不是 `123456`，需要替换
- 执行前建议先确认后端连接的数据库也是 `opc_platform`

## 5. 导入后核对

执行：

```powershell
& 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' -uroot -p123456 --default-character-set=utf8mb4 opc_platform --table --execute="source E:/MyCode/codex/OPC/outputs/check_case_import_case_excel_20260708.sql"
```

本批次核对结果：

```text
expected_case_count = 92
missing_case_count  = 0
matched_case_count  = 92
```

说明 Excel 里的 92 条案例都已经匹配到数据库。

## 6. 查看本批次导入数量

```powershell
& 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' -uroot -p123456 --default-character-set=utf8mb4 opc_platform --table --execute="SELECT reviewer, COUNT(*) AS count FROM case_items GROUP BY reviewer;"
```

本批次结果：

```text
case-excel-import: 92
```

## 7. 查看省份分布

```powershell
& 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' -uroot -p123456 --default-character-set=utf8mb4 opc_platform --table --execute="SELECT r.name AS province, COUNT(*) AS count FROM case_items c JOIN regions r ON c.region_id = r.id WHERE c.reviewer = 'case-excel-import' GROUP BY r.id, r.name ORDER BY count DESC, MIN(r.sort_order);"
```

本批次省份分布已记录在：

```text
docs/case-excel-field-mapping.md
```

## 8. 前端验证

后端启动后访问：

```text
http://localhost:8082/api/public/cases
```

前端启动后访问：

```text
http://localhost:5173/cases
```

如果接口返回中文乱码，优先检查：

- MySQL 表和连接是否是 `utf8mb4`
- MySQL 命令是否带了 `--default-character-set=utf8mb4`
- 后端响应 JSON 是否正常

## 9. 去重说明

重复执行导入 SQL 时，脚本会尽量避免重复插入。

`case_items` 去重规则：

```text
title + actor_name + region_id
```

`sources` 去重规则：

```text
url
```

没有 URL 时，按标题和导入批次辅助去重。
