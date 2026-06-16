package com.trana.notification.adapter.fcm

import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.Base64

/**
 * FirebaseApp 초기화.
 *
 * trana.fcm.service-account-base64 가 비어있지 않을 때만 활성화.
 * 빈 값 (local/test 디폴트) → 빈 등록 skip → MockFcmClient 가 사용됨 (Step C-2).
 *
 * FirebaseApp 은 JVM 단 singleton — 중복 초기화 방지 위해 default name 만 사용.
 * 테스트 / devtools restart 시 기존 인스턴스 재사용.
 */
@Configuration
@ConditionalOnExpression("'\${trana.fcm.service-account-base64:}'.length() > 0")
class FirebaseConfig(
    private val props: FcmProperties,
) {
    private val log = LoggerFactory.getLogger(FirebaseConfig::class.java)

    @Bean
    fun firebaseApp(): FirebaseApp {
        val jsonBytes = Base64.getDecoder().decode(props.serviceAccountBase64)
        val credentials = ServiceAccountCredentials.fromStream(jsonBytes.inputStream())
        val options =
            FirebaseOptions
                .builder()
                .setCredentials(credentials)
                .setProjectId(credentials.projectId)
                .build()

        FirebaseApp.getApps().firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }?.let {
            log.info("FirebaseApp already initialized — reusing")
            return it
        }

        return FirebaseApp.initializeApp(options).also {
            log.info("FirebaseApp initialized: projectId={}", it.options.projectId)
        }
    }
}
