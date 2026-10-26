# 平台集合：2026-10-25 网站清单接入

## 页面与数据

- `/platforms` 保留资源包的搜索、分类筛选、按分类分组和三列卡片布局，并使用当前 OPC 站点的颜色、字体与导航。
- 数据来源为用户提供的 `10.25_OPC数据·网站.csv`，共 37 条、9 个原始分类。压缩包中的 `sites.csv` 与该 CSV 内容一致。
- 网站图标使用清单提供的 HTTPS favicon 地址；加载失败或没有图标地址时显示网站名称前三个字符。压缩包内的 SVG 均为统一 OPC 占位图，未作为官方 Logo 展示。
- 名称、运营主体、服务和网址尚未逐站核验。数据库记录以 `verification_status='pending'` 标记，页面也展示核验提示。

## 数据库

平台网站使用独立的 MySQL `platform_sites` 表，不与政策、案例或来源台账混用。表中保留网站分类、主体、服务、简介、网址、favicon 地址、展示顺序、发布状态及核验状态。

迁移顺序：

1. 在**目标数据库**运行 `deploy/sql/20261025_platform_sites_precheck.sql`，确认库名和同名表状态。
2. 运行 `deploy/sql/20261025_platform_sites.sql` 创建表。
3. 运行 `deploy/sql/20261025_platform_sites_seed.sql` 导入 37 条清单。再次运行不会覆盖已修改的同源记录。
4. 运行 `deploy/sql/20261025_platform_sites_postcheck.sql`，核对 37 条、9 个分类、发布与待核验状态，并检查重复网址。

`opc-backend/src/main/resources/db/schema.sql` 同步了新表定义，供全新安装使用。`scripts/build_platform_sites_import_20261025.py` 可从原始 CSV 重新生成 SQL 种子。

公开接口为 `GET /api/public/platform-sites`，只返回 `status='published'` 的记录，按 `sort_order` 排列。前端只展示接口从数据库返回的数据；接口不可用时显示读取失败，返回空列表时显示空状态，不自动恢复已下架站点。

本机 `opc_platform` 已执行新增表与种子迁移；这不代表生产数据库已迁移或网站已部署。
