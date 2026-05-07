package com.trana.terms

import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/terms")
class TermsController(private val termsService: TermsService) : TermsApi {
    override fun getActiveTerms(): List<TermsResponse> = termsService.getActiveTerms().map { it.toResponse() }
}

private fun TermsVersion.toResponse() = TermsResponse(
    id = id!!,
    type = type,
    version = version,
    title = title,
    contentUrl = contentUrl,
    effectiveAt = effectiveAt,
)
