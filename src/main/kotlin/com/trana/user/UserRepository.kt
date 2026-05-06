package com.trana.user

import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, Long> {
    fun findByPublicCode(publicCode: String): User?

    fun existsByEmail(email: String): Boolean
}
