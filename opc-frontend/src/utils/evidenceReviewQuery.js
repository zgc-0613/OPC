export function buildEvidenceReviewQuery(params = {}) {
  return Object.fromEntries(
    Object.entries(params).filter(([, value]) => (
      value !== null
      && value !== undefined
      && (typeof value !== 'string' || value.trim() !== '')
    )),
  )
}
