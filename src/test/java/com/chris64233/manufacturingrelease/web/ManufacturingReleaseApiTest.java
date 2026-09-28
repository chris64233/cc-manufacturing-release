package com.chris64233.manufacturingrelease.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ManufacturingReleaseApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void fullReleaseWorkflowOverHttp() throws Exception {
        // 声明批次（两个检验项目）
        mockMvc.perform(post("/api/batches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "batchNo": "B-HTTP",
                                  "productCode": "PROD-X",
                                  "quantity": 250.5,
                                  "inspectionItems": [
                                    {"itemCode": "I1", "itemName": "外观"},
                                    {"itemCode": "I2", "itemName": "含量"}
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_RELEASE"))
                .andExpect(jsonPath("$.productCode").value("PROD-X"));

        // 重复批次号 → 409
        mockMvc.perform(post("/api/batches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"batchNo":"B-HTTP","productCode":"P","quantity":1,
                                 "inspectionItems":[{"itemCode":"I1","itemName":"x"}]}
                                """))
                .andExpect(status().isConflict());

        // I1 不合格：未关联偏差 → 409
        mockMvc.perform(post("/api/batches/B-HTTP/inspection-results")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemCode":"I1","resultValue":"NG","conforming":false,"submittedBy":"t"}
                                """))
                .andExpect(status().isConflict());

        // 缺必填字段 → 400
        mockMvc.perform(post("/api/batches/B-HTTP/inspection-results")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemCode":"I1","conforming":true}
                                """))
                .andExpect(status().isBadRequest());

        // 登记偏差并提交不合格结果
        mockMvc.perform(post("/api/batches/B-HTTP/deviations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"deviationNo":"D-1","description":"外观瑕疵"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));
        mockMvc.perform(post("/api/batches/B-HTTP/inspection-results")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemCode":"I1","resultValue":"NG","conforming":false,
                                 "deviationNo":"D-1","submittedBy":"t"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.current").value(true));
        mockMvc.perform(post("/api/batches/B-HTTP/inspection-results")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemCode":"I2","resultValue":"99.2%","conforming":true,"submittedBy":"t"}
                                """))
                .andExpect(status().isOk());

        // 偏差未处置、缺审批：放行 → 409
        mockMvc.perform(post("/api/batches/B-HTTP/releases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"releaseNo":"REL-EARLY","releasedBy":"sys"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("放行条件")));

        // 偏差有条件接受
        mockMvc.perform(post("/api/batches/B-HTTP/deviations/D-1/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"disposition":"CONDITIONAL_ACCEPTANCE","decisionComment":"让步","decidedBy":"qa"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECIDED"));

        // 两个角色审批
        mockMvc.perform(post("/api/batches/B-HTTP/approvals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"QUALITY_MANAGER","approver":"qa-boss","comment":"ok"}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/batches/B-HTTP/approvals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"PRODUCTION_MANAGER","approver":"prod-boss","comment":"ok"}
                                """))
                .andExpect(status().isOk());

        // 当前结论：可放行
        mockMvc.perform(get("/api/batches/B-HTTP/conclusion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conclusion").value("RELEASABLE"))
                .andExpect(jsonPath("$.blockingReasons").isEmpty());

        // 放行
        mockMvc.perform(post("/api/batches/B-HTTP/releases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"releaseNo":"REL-HTTP","releasedBy":"sys"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchStatus").value("RELEASED"))
                .andExpect(jsonPath("$.evidences.length()").value(5));

        // 幂等重放
        mockMvc.perform(post("/api/batches/B-HTTP/releases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"releaseNo":"REL-HTTP","releasedBy":"someone-else"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.releasedBy").value("sys"));

        // 放行后补录结果 → 409
        mockMvc.perform(post("/api/batches/B-HTTP/inspection-results")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemCode":"I1","resultValue":"late","conforming":true,"submittedBy":"t"}
                                """))
                .andExpect(status().isConflict());

        // 出库闸门通过
        mockMvc.perform(post("/api/batches/B-HTTP/outbound-check"))
                .andExpect(status().isNoContent());

        // 撤销放行
        mockMvc.perform(post("/api/batches/B-HTTP/revocation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"依据有误","revokedBy":"qa-director"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revoked").value(true))
                .andExpect(jsonPath("$.batchStatus").value("RELEASE_REVOKED"));

        // 撤销后出库被阻止
        mockMvc.perform(post("/api/batches/B-HTTP/outbound-check"))
                .andExpect(status().isConflict());

        // 证据包可查询：结论 + 完整版本历史
        mockMvc.perform(get("/api/batches/B-HTTP/evidence-package"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batch.status").value("RELEASE_REVOKED"))
                .andExpect(jsonPath("$.release.releaseNo").value("REL-HTTP"))
                .andExpect(jsonPath("$.currentConclusion.conclusion").value("RELEASE_REVOKED"))
                .andExpect(jsonPath("$.versionHistory.length()").value(2));

        // 未知批次 → 404
        mockMvc.perform(get("/api/batches/NO-SUCH-BATCH/conclusion"))
                .andExpect(status().isNotFound());
    }
}
