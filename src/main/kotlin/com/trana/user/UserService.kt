package com.trana.user

import com.trana.common.util.PublicCodeGenerator
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class UserService(
    private val userRepository: UserRepository,
    private val socialAccountRepository: SocialAccountRepository,
    private val publicCodeGenerator: PublicCodeGenerator,
) {

    /**
     * 소셜 로그인: 기존 매핑이 있으면 기존 사용자 반환, 없으면 신규 생성.
     *
     * @param provider 소셜 공급자 (KAKAO/GOOGLE/APPLE)
     * @param providerUserId 공급자가 발급한 사용자 ID
     * @param email 공급자 제공 이메일 (선택)
     * @param nickname 공급자 제공 닉네임 (선택)
     * @return 기존 또는 신규 User
     */
    fun findOrCreateBySocial(
        provider: SocialProvider,
        providerUserId: String,
        email: String? = null,
        nickname: String? = null,
    ): User {
        // 1. 기존 소셜 매핑 조회
        val existingSocial =
            socialAccountRepository.findByProviderAndProviderUserId(provider, providerUserId)

        if (existingSocial != null) {
            return userRepository.findById(existingSocial.userId).orElseThrow {
                IllegalStateException(
                    "Orphan social_account: id=${existingSocial.id}, userId=${existingSocial.userId}",
                )
            }
        }

        // 2. 신규 사용자 생성
        val newUser = User(
            publicCode = publicCodeGenerator.generate(),
            email = email,
            nickname = nickname,
        )
        userRepository.save(newUser)

        // 3. 소셜 매핑 생성
        socialAccountRepository.save(
            SocialAccount(
                userId = checkNotNull(newUser.id) { "User id should be assigned after save" },
                provider = provider,
                providerUserId = providerUserId,
            ),
        )

        return newUser
    }

    @Transactional(readOnly = true)
    fun getByPublicCode(publicCode: String): User = userRepository.findByPublicCode(publicCode)
        ?: throw UserException.NotFound(publicCode)
}
