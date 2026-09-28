package com.chris64233.manufacturingrelease.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 质量放行 HTTP 接口端到端测试。
 */
@SpringBootTest
class QualityReleaseControllerTest {

    @Autowired
    private WebApplicationContext wac;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
    }

    @Test
    void fullReleaseLifecycleOverHttp() throws Exception {
        // 声明批次
        mockMvc.perform(post("/api/batches").contentType(MediaType.APPLICATION_JSON).content("""
                {
                  "batchNo": "B-API-1",
                  "productCode": "P-100",
                  "productName": "注射液",
                  "quantity": 1000,
                  "actor": "planner",
                  "items": [
                    {"itemCode": "I1", "itemName": "含量", "specification": "95-105%"},
                    {"itemCode": "I2", "itemName": "无菌"}
                  ]
                }
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.items.length()").value(2));

        // I1 不合格 → 登记偏差；I2 合格
        mockMvc.perform(post("/api/batches/B-API-1/deviations").contentType(MediaType.APPLICATION_JSON).content("""
                {"deviationNo": "D-API-1", "description": "含量偏低", "actor": "qc"}
                """)).andExpect(status().isCreated());

        mockMvc.perform(post("/api/batches/B-API-1/results").contentType(MediaType.APPLICATION_JSON).content("""
                {"itemCode": "I1", "conforming": false, "valueText": "90%", "deviationNo": "D-API-1", "actor": "qc"}
                """)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNo").value(1))
                .andExpect(jsonPath("$.conforming").value(false))
                .andExpect(jsonPath("$.deviationNo").value("D-API-1"));

        mockMvc.perform(post("/api/batches/B-API-1/results").contentType(MediaType.APPLICATION_JSON).content("""
                {"itemCode": "I2", "conforming": true, "valueText": "合格", "actor": "qc"}
                """)).andExpect(status().isCreated());

        // 偏差有条件接受
        mockMvc.perform(post("/api/deviations/D-API-1/disposition").contentType(MediaType.APPLICATION_JSON).content("""
                {"decision": "CONDITIONAL_ACCEPT", "remark": "限定用途放行", "actor": "qa"}
                """)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.decision").value("CONDITIONAL_ACCEPT"));

        // 此时缺少双角色审批，结论不可放行
        mockMvc.perform(get("/api/batches/B-API-1/conclusion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.releasable").value(false))
                .andExpect(jsonPath("$.blockers[0]").value(org.hamcrest.Matchers.containsString("审批")));

        // 直接放行 → 409
        mockMvc.perform(post("/api/batches/B-API-1/releases").contentType(MediaType.APPLICATION_JSON).content("""
                {"releaseNo": "R-API-1", "actor": "qa"}
                """)).andExpect(status().isConflict());

        // 两个不同角色审批
        mockMvc.perform(post("/api/batches/B-API-1/approvals").contentType(MediaType.APPLICATION_JSON).content("""
                {"roleCode": "QA_MANAGER", "approver": "alice", "comment": "ok"}
                """)).andExpect(status().isCreated());
        mockMvc.perform(post("/api/batches/B-API-1/approvals").contentType(MediaType.APPLICATION_JSON).content("""
                {"roleCode": "PRODUCTION_MANAGER", "approver": "bob"}
                """)).andExpect(status().isCreated());

        // 放行
        mockMvc.perform(post("/api/batches/B-API-1/releases").contentType(MediaType.APPLICATION_JSON).content("""
                {"releaseNo": "R-API-1", "actor": "qa-lead"}
                """)).andExpect(status().isOk())
                .andExpect(jsonPath("$.releaseNo").value("R-API-1"))
                .andExpect(jsonPath("$.evidence.length()").value(3));

        // 幂等重试
        mockMvc.perform(post("/api/batches/B-API-1/releases").contentType(MediaType.APPLICATION_JSON).content("""
                {"releaseNo": "R-API-1", "actor": "qa-lead"}
                """)).andExpect(status().isOk())
                .andExpect(jsonPath("$.releaseNo").value("R-API-1"));

        // 证据包
        mockMvc.perform(get("/api/releases/R-API-1/evidence"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evidence[?(@.evidenceType=='DEVIATION')].deviationNo")
                        .value(org.hamcrest.Matchers.hasItem("D-API-1")));

        // 放行后补录结果 → 409；改数量 → 409
        mockMvc.perform(post("/api/batches/B-API-1/results").contentType(MediaType.APPLICATION_JSON).content("""
                {"itemCode": "I1", "conforming": true, "actor": "qc"}
                """)).andExpect(status().isConflict());
        mockMvc.perform(put("/api/batches/B-API-1/quantity").contentType(MediaType.APPLICATION_JSON).content("""
                {"quantity": 2000, "actor": "planner"}
                """)).andExpect(status().isConflict());

        // 完整版本历史
        mockMvc.perform(get("/api/batches/B-API-1/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[?(@.type=='RELEASED')].detail")
                        .value(org.hamcrest.Matchers.hasItem(
                                org.hamcrest.Matchers.containsString("R-API-1"))));

        // 撤销后禁止出库
        mockMvc.perform(post("/api/releases/R-API-1/revocations").contentType(MediaType.APPLICATION_JSON).content("""
                {"reason": "发现依据错误", "actor": "qm"}
                """)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.alreadyShipped").value(false));

        mockMvc.perform(post("/api/batches/B-API-1/ship").contentType(MediaType.APPLICATION_JSON).content("""
                {"actor": "wh"}
                """)).andExpect(status().isConflict());

        mockMvc.perform(get("/api/batches/B-API-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOKED"));
    }

    @Test
    void reworkFlowOverHttp() throws Exception {
        mockMvc.perform(post("/api/batches").contentType(MediaType.APPLICATION_JSON).content("""
                {"batchNo": "B-API-2", "productCode": "P2", "quantity": 50, "actor": "p",
                 "items": [{"itemCode": "I1", "itemName": "外观"}]}
                """)).andExpect(status().isCreated());

        mockMvc.perform(post("/api/batches/B-API-2/deviations").contentType(MediaType.APPLICATION_JSON).content("""
                {"deviationNo": "D-API-2", "description": "外观瑕疵", "actor": "qc"}
                """)).andExpect(status().isCreated());
        mockMvc.perform(post("/api/batches/B-API-2/results").contentType(MediaType.APPLICATION_JSON).content("""
                {"itemCode": "I1", "conforming": false, "valueText": "划痕", "deviationNo": "D-API-2", "actor": "qc"}
                """)).andExpect(status().isCreated());
        mockMvc.perform(post("/api/deviations/D-API-2/disposition").contentType(MediaType.APPLICATION_JSON).content("""
                {"decision": "REWORK", "reworkItemCode": "I1", "actor": "qa"}
                """)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_DISPOSITION"));

        mockMvc.perform(post("/api/batches/B-API-2/approvals").contentType(MediaType.APPLICATION_JSON).content("""
                {"roleCode": "QA_MANAGER", "approver": "a"}
                """)).andExpect(status().isCreated());
        mockMvc.perform(post("/api/batches/B-API-2/approvals").contentType(MediaType.APPLICATION_JSON).content("""
                {"roleCode": "PRODUCTION_MANAGER", "approver": "b"}
                """)).andExpect(status().isCreated());

        // 返工未复验 → 409
        mockMvc.perform(post("/api/batches/B-API-2/releases").contentType(MediaType.APPLICATION_JSON).content("""
                {"releaseNo": "R-API-2", "actor": "qa"}
                """)).andExpect(status().isConflict());

        // 复验合格 → 偏差自动关闭 → 放行
        mockMvc.perform(post("/api/batches/B-API-2/results").contentType(MediaType.APPLICATION_JSON).content("""
                {"itemCode": "I1", "conforming": true, "valueText": "合格", "actor": "qc"}
                """)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNo").value(2));

        mockMvc.perform(get("/api/batches/B-API-2"))
                .andExpect(jsonPath("$.deviations[0].status").value("CLOSED"));

        mockMvc.perform(post("/api/batches/B-API-2/releases").contentType(MediaType.APPLICATION_JSON).content("""
                {"releaseNo": "R-API-2", "actor": "qa"}
                """)).andExpect(status().isOk());

        // 已放行 → 可出库
        mockMvc.perform(post("/api/batches/B-API-2/ship").contentType(MediaType.APPLICATION_JSON).content("""
                {"actor": "wh"}
                """)).andExpect(status().isAccepted());
    }

    @Test
    void validationAndNotFoundMappedCorrectly() throws Exception {
        // 缺必填字段 → 400
        mockMvc.perform(post("/api/batches").contentType(MediaType.APPLICATION_JSON).content("""
                {"batchNo": "B-X", "quantity": 10, "items": []}
                """)).andExpect(status().isBadRequest());

        // 未知批次 → 404
        mockMvc.perform(get("/api/batches/NO-SUCH-BATCH"))
                .andExpect(status().isNotFound());

        // 未知放行业务号证据包 → 404
        mockMvc.perform(get("/api/releases/NO-SUCH-RELEASE/evidence"))
                .andExpect(status().isNotFound());

        // 非法处置决定 → 400
        mockMvc.perform(post("/api/batches/B-X/deviations").contentType(MediaType.APPLICATION_JSON).content("""
                {"deviationNo": "D-X", "actor": "qc"}
                """)).andExpect(status().isNotFound()); // 批次不存在优先
    }
}
