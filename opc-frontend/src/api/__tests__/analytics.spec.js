import { beforeEach, describe, expect, it, vi } from 'vitest'

const request = vi.hoisted(() => ({ get: vi.fn() }))

vi.mock('../request', () => ({ default: request }))

import {
  getAnalyticsDrilldown,
  getAnalyticsRegions,
  getAnalyticsRevenue,
  getAnalyticsTechnologies,
  getAnalyticsTrends,
} from '../analytics'

describe('analytics API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('serializes the five analytics resource contracts without inventing filters', () => {
    getAnalyticsTechnologies({
      metricId: 'technology.case_count',
      technologyTagIds: [7, 9],
    })
    getAnalyticsRevenue({
      metricId: 'revenue.range_distribution',
      currency: 'CNY',
      revenuePeriod: 'annual',
      revenueType: 'revenue',
    })
    getAnalyticsRegions({ metricId: 'region.case_count', regionRole: 'operation' })
    getAnalyticsTrends({
      metricId: 'trend.case_business_time',
      dateFrom: '2026-01-01',
      dateTo: '2026-06-30',
      granularity: 'month',
    })
    getAnalyticsDrilldown({
      metricId: 'industry.case_count',
      dataVersion: 'analytics-v1:test',
      entityType: 'case',
      bucketId: 'industry:7',
      limit: 25,
    })

    expect(request.get.mock.calls.map(([path]) => path)).toEqual([
      '/analytics/technologies',
      '/analytics/revenue',
      '/analytics/regions',
      '/analytics/trends',
      '/analytics/drilldown',
    ])
    expect(request.get.mock.calls.map(([, config]) => config.params.toString())).toEqual([
      'metricId=technology.case_count&technologyTagIds=7&technologyTagIds=9',
      'metricId=revenue.range_distribution&currency=CNY&revenuePeriod=annual&revenueType=revenue',
      'metricId=region.case_count&regionRole=operation',
      'metricId=trend.case_business_time&dateFrom=2026-01-01&dateTo=2026-06-30&granularity=month',
      'metricId=industry.case_count&dataVersion=analytics-v1%3Atest&entityType=case&bucketId=industry%3A7&limit=25',
    ])
  })
})
