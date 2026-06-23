package com.trana.trustscore.repository

import com.trana.trustscore.entity.TrustScoreEvent
import org.springframework.data.jpa.repository.JpaRepository

interface TrustScoreEventRepository : JpaRepository<TrustScoreEvent, Long>
