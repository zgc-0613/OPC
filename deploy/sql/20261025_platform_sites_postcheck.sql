-- The 2026-10-25 source batch should contain 37 published, pending-review rows.
SELECT COUNT(*) AS imported_sites,
       COUNT(DISTINCT source_key) AS distinct_source_keys,
       SUM(status = 'published') AS published_sites,
       SUM(verification_status = 'pending') AS pending_review_sites
FROM platform_sites
WHERE source_batch = 'opc-nav-20261025';

SELECT category, COUNT(*) AS site_count
FROM platform_sites
WHERE source_batch = 'opc-nav-20261025'
GROUP BY category
ORDER BY MIN(sort_order);

SELECT url, COUNT(*) AS duplicate_count
FROM platform_sites
WHERE source_batch = 'opc-nav-20261025'
GROUP BY url
HAVING COUNT(*) > 1;
