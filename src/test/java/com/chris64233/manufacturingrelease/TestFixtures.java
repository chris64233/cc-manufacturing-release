package com.chris64233.manufacturingrelease;

import java.math.BigDecimal;
import java.util.List;

import com.chris64233.manufacturingrelease.domain.ApprovalRole;
import com.chris64233.manufacturingrelease.service.ManufacturingReleaseService;
import com.chris64233.manufacturingrelease.web.ApiRequests;

/**
 * 测试夹具：构造批次并快速推进到指定状态。
 */
public final class TestFixtures {

    private TestFixtures() {
    }

    public static ApiRequests.DeclareBatchRequest declareRequest(String batchNo, String... itemCodes) {
        List<ApiRequests.InspectionItemSpec> items = java.util.Arrays.stream(itemCodes)
                .map(code -> new ApiRequests.InspectionItemSpec(code, "检验项-" + code))
                .toList();
        return new ApiRequests.DeclareBatchRequest(batchNo, "PROD-1", new BigDecimal("100.0000"), items);
    }

    public static ApiRequests.SubmitResultRequest conforming(String itemCode) {
        return new ApiRequests.SubmitResultRequest(itemCode, "OK", true, null, "tester");
    }

    public static ApiRequests.SubmitResultRequest conforming(String itemCode, String value) {
        return new ApiRequests.SubmitResultRequest(itemCode, value, true, null, "tester");
    }

    public static ApiRequests.SubmitResultRequest nonconforming(String itemCode, String deviationNo) {
        return new ApiRequests.SubmitResultRequest(itemCode, "NG", false, deviationNo, "tester");
    }

    public static void submitAllConforming(ManufacturingReleaseService service, String batchNo,
                                           String... itemCodes) {
        for (String code : itemCodes) {
            service.submitResult(batchNo, conforming(code));
        }
    }

    public static void grantBothApprovals(ManufacturingReleaseService service, String batchNo) {
        service.grantApproval(batchNo, new ApiRequests.GrantApprovalRequest(
                ApprovalRole.QUALITY_MANAGER, "qa-boss", "同意"));
        service.grantApproval(batchNo, new ApiRequests.GrantApprovalRequest(
                ApprovalRole.PRODUCTION_MANAGER, "prod-boss", "同意"));
    }

    /** 构造一个满足全部放行条件的批次（仅未执行放行）。 */
    public static void prepareReleasableBatch(ManufacturingReleaseService service, String batchNo,
                                              String... itemCodes) {
        service.declareBatch(declareRequest(batchNo, itemCodes));
        submitAllConforming(service, batchNo, itemCodes);
        grantBothApprovals(service, batchNo);
    }
}
