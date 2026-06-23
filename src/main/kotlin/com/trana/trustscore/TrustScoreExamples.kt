package com.trana.trustscore

/**
 * Swagger `@ExampleObject` value 모음 — TrustScoreApi 에서 참조.
 */
object TrustScoreExamples {
    const val TRUST_SCORE_RESPONSE = """
  {
    "trustScore": 67,
    "trustGrade": "TRUST",
    "trustGradeLabel": "신뢰",
    "completedContractCount": 24,
    "warrantyProvidedCount": 7,
    "fraudReportReceivedCount": 1
  }
  """

    const val TRUST_SCORE_RESPONSE_NEWBIE = """
  {
    "trustScore": 12,
    "trustGrade": "NEWBIE",
    "trustGradeLabel": "새내기",
    "completedContractCount": 0,
    "warrantyProvidedCount": 0,
    "fraudReportReceivedCount": 0
  }
  """

    const val TRUST_SCORE_RESPONSE_BEST = """
  {
    "trustScore": 98,
    "trustGrade": "BEST",
    "trustGradeLabel": "최우수",
    "completedContractCount": 120,
    "warrantyProvidedCount": 45,
    "fraudReportReceivedCount": 0
  }
  """
}
