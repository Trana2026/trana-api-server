package com.trana.common.storage

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client

/**
 * AWS S3 클라이언트 Bean.
 *
 * - 정적 자격증명 (StaticCredentialsProvider) — properties로 주입
 * - 기동 시 network call X (build()만 — 실제 호출 시 권한 검증)
 * - 추후 NCP Object Storage 전환 시 endpointOverride(URI) 추가만으로 가능
 */
@Configuration
class S3Config {
    @Bean
    fun s3Client(props: StorageProperties): S3Client =
        S3Client
            .builder()
            .region(Region.of(props.region))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(props.accessKey, props.secretKey),
                ),
            ).build()
}
