package com.trana.common

import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/**
 * 캐시 설정 — test profile 에서 비활성 (refactor gg).
 *
 * @DataJpaTest 가 좁은 context 만 가져와 CacheManager 빈을 못 찾으면 NoSuchBeanDefinitionException.
 * test profile 에서 @EnableCaching 자체 안 로드 → CacheAspect 등록 X → 정상 통과.
 * @Cacheable annotation 은 그대로 두되 aspect 가 없으면 plain 호출.
 */
@Configuration
@Profile("!test")
@EnableCaching
class CacheConfig
