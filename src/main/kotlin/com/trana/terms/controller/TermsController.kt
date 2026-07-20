package com.trana.terms.controller

import com.trana.terms.dto.TermsResponse
import com.trana.terms.entity.TermsContext
import com.trana.terms.entity.TermsVersion
import com.trana.terms.service.TermsService
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/terms")
class TermsController(
    private val termsService: TermsService,
) : TermsApi {
    override fun getActiveTerms(context: TermsContext?): List<TermsResponse> =
        termsService
            .findActiveTerms()
            .let { all -> if (context == null) all else all.filter { it.type in context.types } }
            .map { it.toResponse() }
}

private fun TermsVersion.toResponse(): TermsResponse =
    TermsResponse(
        id = checkNotNull(id) { "TermsVersion id should be assigned" },
        type = type,
        version = version,
        title = title,
        contentUrl = contentUrl,
        effectiveAt = effectiveAt,
    )
