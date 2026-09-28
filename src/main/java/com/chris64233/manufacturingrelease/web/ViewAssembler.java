package com.chris64233.manufacturingrelease.web;

import com.chris64233.manufacturingrelease.domain.Batch;
import com.chris64233.manufacturingrelease.domain.BatchEvent;
import com.chris64233.manufacturingrelease.domain.Deviation;
import com.chris64233.manufacturingrelease.domain.InspectionItem;
import com.chris64233.manufacturingrelease.domain.InspectionResultVersion;
import com.chris64233.manufacturingrelease.domain.Release;
import com.chris64233.manufacturingrelease.domain.ReleaseApproval;
import com.chris64233.manufacturingrelease.domain.ReleaseEvidenceItem;
import com.chris64233.manufacturingrelease.domain.ReleaseRevocation;
import com.chris64233.manufacturingrelease.service.QualityReleaseService.CurrentConclusion;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 领域对象 → 对外视图的装配。只读拷贝，避免直接暴露 JPA 实体。
 */
@Component
public class ViewAssembler {

    public Views.BatchView batch(Batch b) {
        return new Views.BatchView(
                b.getBatchNo(), b.getProductCode(), b.getProductName(), b.getQuantity(),
                b.getStatus().name(), b.getDeclaredAt(),
                b.getItems().stream().map(this::item).toList(),
                b.getDeviations().stream().map(this::deviation).toList(),
                b.getApprovals().stream().map(this::approval).toList());
    }

    public Views.ItemView item(InspectionItem i) {
        InspectionResultVersion current = i.currentResult().orElse(null);
        return new Views.ItemView(
                i.getItemCode(), i.getItemName(), i.getSpecification(),
                current == null ? null : result(current),
                i.getResults().stream().map(this::result).toList());
    }

    public Views.ResultView result(InspectionResultVersion r) {
        return new Views.ResultView(
                r.getVersionNo(), r.isConforming(), r.getValueText(),
                r.getDeviation() == null ? null : r.getDeviation().getDeviationNo(),
                r.isSuperseded(), r.getSubmittedBy(), r.getSubmittedAt());
    }

    public Views.DeviationView deviation(Deviation d) {
        return new Views.DeviationView(
                d.getDeviationNo(), d.getDescription(), d.getStatus().name(),
                d.getDecision() == null ? null : d.getDecision().name(),
                d.getDispositionRemark(), d.getReworkItemCode(),
                d.getDispositionBy(), d.getOpenedAt(), d.getDispositionedAt(), d.getClosedAt());
    }

    public Views.ApprovalView approval(ReleaseApproval a) {
        return new Views.ApprovalView(
                a.getRoleCode(), a.getApprover(), a.getComment(),
                a.isWithdrawn(), a.getGrantedAt(), a.getWithdrawnAt());
    }

    public Views.EventView event(BatchEvent e) {
        return new Views.EventView(e.getType().name(), e.getDetail(), e.getActor(), e.getOccurredAt());
    }

    public Views.ReleaseView release(Release r) {
        return new Views.ReleaseView(
                r.getReleaseNo(), r.getBatch().getBatchNo(), r.getReleasedBy(), r.getReleasedAt(),
                r.getEvidenceItems().stream().map(this::evidence).toList());
    }

    public Views.EvidenceView evidence(ReleaseEvidenceItem e) {
        return new Views.EvidenceView(
                e.getEvidenceType(), e.getItemCode(),
                e.getResultVersionId(), e.getResultVersionNo(),
                e.getConforming(), e.getValueText(),
                e.getDeviationId(), e.getDeviationNo(),
                e.getDeviationStatus() == null ? null : e.getDeviationStatus().name(),
                e.getDeviationDecision() == null ? null : e.getDeviationDecision().name(),
                e.getSubmittedBy(), e.getSubmittedAt());
    }

    public Views.RevocationView revocation(ReleaseRevocation r) {
        return new Views.RevocationView(
                r.getRelease().getReleaseNo(), r.getReason(), r.getRevokedBy(),
                r.isAlreadyShipped(), r.getRevokedAt());
    }

    public Views.ConclusionView conclusion(CurrentConclusion c) {
        return new Views.ConclusionView(
                batch(c.batch()), c.releasable(), c.blockers(),
                c.release() == null ? null : new Views.ReleaseRef(
                        c.release().getReleaseNo(), c.release().getReleasedAt()),
                c.revocation() == null ? null : revocation(c.revocation()));
    }

    public Views.HistoryView history(Batch b) {
        List<Views.EventView> events = b.getEvents().stream().map(this::event).toList();
        return new Views.HistoryView(batch(b), events);
    }
}
