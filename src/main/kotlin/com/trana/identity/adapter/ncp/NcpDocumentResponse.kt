package com.trana.identity.adapter.ncp

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class NcpDocumentResponse(
    val requestId: String?, // ← 추가 (NCP가 발급, Verify API의 키)
    val images: List<NcpImage>,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class NcpImage(
    val inferResult: String,
    val message: String?,
    val idCard: NcpIdCard?,
    val face: NcpFace?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class NcpIdCard(
    val result: NcpIdCardResult,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class NcpIdCardResult(
    val isConfident: Boolean?,
    val idtype: String?,
    val ic: NcpIdCardSubject?,
    val dl: NcpIdCardSubject?,
    val ac: NcpAlienRegistration?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class NcpIdCardSubject(
    val name: List<NcpText>?,
    val personalNum: List<NcpText>?,
    val address: List<NcpText>?,
    val issueDate: List<NcpText>?,
    val num: List<NcpText>?, // dl 면허번호 (ic는 null)
    val code: List<NcpText>?, // ← 추가 (dl 암호일련번호, Verify 필요)
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class NcpAlienRegistration(
    val alienRegNum: List<NcpText>?,
    val name: List<NcpText>?,
    val sex: List<NcpText>?,
    val nationality: List<NcpText>?,
    val visaType: List<NcpText>?,
    val issueDate: List<NcpText>?,
    val serialNum: List<NcpText>?, // ← 추가 (ac 시리얼, Verify 필수)
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class NcpText(
    val text: String,
    val maskingPolys: List<NcpMaskingPoly>? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class NcpMaskingPoly(
    val vertices: List<NcpVertex>,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class NcpVertex(
    val x: Double,
    val y: Double,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class NcpFace(
    val faces: List<NcpFaceDetail>?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class NcpFaceDetail(
    val alignedImage: String?,
)
