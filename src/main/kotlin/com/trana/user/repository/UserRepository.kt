package com.trana.user.repository

import com.trana.user.entity.User
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, Long> {
    fun findByPublicCode(publicCode: String): User?
}
