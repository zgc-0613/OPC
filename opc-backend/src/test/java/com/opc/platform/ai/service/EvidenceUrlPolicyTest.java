package com.opc.platform.ai.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EvidenceUrlPolicyTest {

    @ParameterizedTest
    @CsvSource({
            "https://example.gov.cn/policy/1,true",
            "http://news.example.com/report?id=1,true",
            "HTTPS://EXAMPLE.COM/path,true",
            "ftp://example.com/file,false",
            "https://user@example.com/private,false",
            "https://example.com:8443/path,false",
            "https://example.com/path with space,false",
            "https:///missing-host,false",
            "https://example..gov.cn/path,false",
            "https://-bad.example/path,false",
            "https://bad-.example/path,false",
            "not-a-url,false"
    })
    void usesOneConservativeUrlGrammarForApprovalAndQueueSql(String url, boolean expected) {
        assertEquals(expected, EvidenceUrlPolicy.isSafe(url));
    }
}
