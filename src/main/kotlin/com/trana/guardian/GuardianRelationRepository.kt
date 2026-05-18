package com.trana.guardian

import org.springframework.data.jpa.repository.JpaRepository

interface GuardianRelationRepository : JpaRepository<GuardianRelation, Long> {
    /** 중복 관계 INSERT 방지용 (W3 4차 보호자 KYC 완료 흐름에서 사용) */
    fun findByGuardianIdAndMinorUserIdAndStatus(
        guardianId: Long,
        minorUserId: Long,
        status: GuardianRelationStatus,
    ): GuardianRelation?

    /** 미성년자의 활성 보호자 조회 (있어야 가입 완료) */
    fun findByMinorUserIdAndStatus(
        minorUserId: Long,
        status: GuardianRelationStatus,
    ): GuardianRelation?
}
