package com.trana.identity.service

import com.trana.common.storage.StorageService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class IdCardImageGatewayTest {
    private val storageService: StorageService = mockk()
    private val gateway = IdCardImageGateway(storageService)

    @Test
    fun deleteSwallowSkipsStorageWhenKeyIsNull() {
        gateway.deleteSwallow(null)

        verify(exactly = 0) { storageService.delete(any()) }
    }

    @Test
    fun deleteSwallowSwallowsStorageException() {
        every { storageService.delete("identity/req-1/id-card.jpg") } throws
            RuntimeException("S3 unreachable")

        gateway.deleteSwallow("identity/req-1/id-card.jpg") // 예외 누설 X — swallow contract

        verify { storageService.delete("identity/req-1/id-card.jpg") }
    }
}
