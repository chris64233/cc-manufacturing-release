# cc-manufacturing-release

生产批次与质量资料管理服务：管理生产批次的检验结果（带版本）、偏差处置、质量审批，并在依据完整时执行批次放行、证据锁定、幂等控制与撤销。

## 开发环境

- JDK 21
- Maven Wrapper 3.9.9
- Spring Boot 4.1.1（Spring Web MVC + Spring Data JPA + Bean Validation）
- H2 数据库（默认内存模式；生产可替换为其他关系数据库，并发控制基于标准悲观行锁）

迁移项目沿用现有 Spring Boot 版本，其他项目使用上述版本。

## 常用命令

运行测试：

    ./mvnw clean test

启动服务：

    ./mvnw spring-boot:run

## 领域模型

| 模型 | 说明 |
| --- | --- |
| ProductionBatch | 生产批次：批次号、产品、数量、状态（`PENDING_RELEASE` / `RELEASED` / `RELEASE_REVOKED`）及所需检验项目 |
| InspectionItem | 批次声明的所需检验项目（同一批次内项目编码唯一） |
| InspectionResult | 检验结果，**带版本号**。同一项目可多次提交，旧版本标记 `current=false` 保留历史，仅当前版本可作为放行依据 |
| Deviation | 偏差单（`OPEN` → `DECIDED`），终态处置为返工、报废或有条件接受 |
| ReleaseApproval | 审批记录，撤回只将记录置为失效（保留历史） |
| BatchRelease + ReleaseEvidence | 放行记录及其依据快照：放行事务内逐行锁定本次使用的检验结果版本、偏差终态、有效审批 |
| ReleaseRevocation | 撤销放行事件（只追加，不修改原放行） |

## 主要业务规则

1. **批次声明**：声明产品、数量和所需检验项目；批次号唯一；同批次检验项目编码不可重复。
2. **检验结果版本化**：
   - 每次提交生成递增版本（v1, v2, …），新版本自动将旧版本置为非当前；历史结果保留可查，但**永远不能用于放行**。
   - **不合格结果必须关联本批次的偏差单**；合格结果不允许关联偏差单。
3. **偏差处置**：偏差单可决定 `REWORK`（返工）、`SCRAP`（报废）、`CONDITIONAL_ACCEPTANCE`（有条件接受）。处置为终态、不可更改。只有允许放行的处置（返工 / 有条件接受）才解除阻塞；**报废不允许放行**。
4. **放行三条件（必须同时满足）**：
   - 所有所需检验项目都有**当前有效结果**，且当前不合格结果均关联偏差单；
   - 批次下所有偏差均已作出**允许放行的终态处理**；
   - 存在**两个不同角色**（`QUALITY_MANAGER` 质量、`PRODUCTION_MANAGER` 生产）的**有效**审批。
5. **放行事务与版本锁定**：放行在单个数据库事务内完成全部条件校验，并把本次实际使用的每条检验结果版本、每个偏差终态、每条有效审批写入 `release_evidence` 快照。放行后即使再补录/替代结果、撤回审批，已放行批次的依据也以快照为准。
6. **并发安全**：批次的一切变更（补录结果、偏差处置、审批/撤回、放行、撤销）都先对批次行加悲观写锁并按固定顺序锁定依据行。因此并发补录结果或撤回审批时：要么在放行前生效（放行锁定新依据），要么在放行后被冻结拒绝——**不会出现依据不完整（缺结果、偏差未终态、仅单一审批）的已放行批次**。
7. **放行业务号幂等**：同一 `releaseNo` 重复提交（含并发提交）只产生一条放行，重复请求返回首次放行记录；同一业务号不能用于不同批次（409）。
8. **放行后冻结**：放行后批次数量与放行依据不可修改——不能再补录/替代检验结果、登记/处置偏差、授权或撤回审批。
9. **撤销放行**：发现错误时只能登记撤销事件（原因、操作人、时间），不可修改或删除原放行。撤销后批次状态为 `RELEASE_REVOKED`，**出库流转闸门**（`POST /api/batches/{batchNo}/outbound-check`）拒绝尚未出库的批次继续流转；撤销不可重复。
10. **查询能力**：
    - 当前结论：`GET /api/batches/{batchNo}/conclusion`（`RELEASABLE` / `BLOCKED` 及逐条阻塞原因 / `RELEASED` / `RELEASE_REVOKED`）；
    - 证据包：`GET /api/batches/{batchNo}/evidence-package`（批次 + 放行及锁定证据 + 当前结论 + 全部结果版本历史）；
    - 完整版本历史：`GET /api/batches/{batchNo}/history`；
    - 放行记录：`GET /api/releases/{releaseNo}`。

## HTTP 接口

| 方法与路径 | 说明 |
| --- | --- |
| `POST /api/batches` | 声明批次（201） |
| `GET  /api/batches/{batchNo}` | 批次基本信息 |
| `POST /api/batches/{batchNo}/inspection-results` | 提交检验结果（自动升版本） |
| `POST /api/batches/{batchNo}/deviations` | 登记偏差单 |
| `POST /api/batches/{batchNo}/deviations/{deviationNo}/decision` | 偏差终态处置 |
| `POST /api/batches/{batchNo}/approvals` | 授予某角色审批 |
| `POST /api/batches/{batchNo}/approvals/{role}/withdraw` | 撤回某角色审批 |
| `POST /api/batches/{batchNo}/releases` | 放行（body 含幂等业务号 `releaseNo`） |
| `POST /api/batches/{batchNo}/revocation` | 撤销放行 |
| `POST /api/batches/{batchNo}/outbound-check` | 出库流转闸门（允许 204 / 阻止 409） |
| `GET  /api/batches/{batchNo}/conclusion` | 当前结论与阻塞原因 |
| `GET  /api/batches/{batchNo}/history` | 检验结果完整版本历史 |
| `GET  /api/batches/{batchNo}/evidence-package` | 批次证据包 |
| `GET  /api/releases/{releaseNo}` | 放行记录与证据快照 |

状态码：参数校验失败 `400`；对象不存在 `404`；业务规则冲突（放行条件不满足、重复/冻结操作、并发冲突、撤销后出库等）`409`。

### 典型流程示例

```bash
# 1. 声明批次
curl -XPOST localhost:8080/api/batches -H 'Content-Type: application/json' -d '{
  "batchNo":"B-001","productCode":"PROD-X","quantity":250.5,
  "inspectionItems":[{"itemCode":"I1","itemName":"外观"},{"itemCode":"I2","itemName":"含量"}]}'

# 2. 不合格结果先登记偏差，再提交结果
curl -XPOST localhost:8080/api/batches/B-001/deviations -H 'Content-Type: application/json' \
  -d '{"deviationNo":"D-1","description":"外观瑕疵"}'
curl -XPOST localhost:8080/api/batches/B-001/inspection-results -H 'Content-Type: application/json' \
  -d '{"itemCode":"I1","resultValue":"NG","conforming":false,"deviationNo":"D-1","submittedBy":"tester"}'
curl -XPOST localhost:8080/api/batches/B-001/inspection-results -H 'Content-Type: application/json' \
  -d '{"itemCode":"I2","resultValue":"99.2%","conforming":true,"submittedBy":"tester"}'

# 3. 偏差有条件接受（或返工）
curl -XPOST localhost:8080/api/batches/B-001/deviations/D-1/decision -H 'Content-Type: application/json' \
  -d '{"disposition":"CONDITIONAL_ACCEPTANCE","decisionComment":"让步接收","decidedBy":"qa"}'

# 4. 两个不同角色审批
curl -XPOST localhost:8080/api/batches/B-001/approvals -H 'Content-Type: application/json' \
  -d '{"role":"QUALITY_MANAGER","approver":"qa-boss","comment":"同意"}'
curl -XPOST localhost:8080/api/batches/B-001/approvals -H 'Content-Type: application/json' \
  -d '{"role":"PRODUCTION_MANAGER","approver":"prod-boss","comment":"同意"}'

# 5. 放行（releaseNo 幂等）
curl -XPOST localhost:8080/api/batches/B-001/releases -H 'Content-Type: application/json' \
  -d '{"releaseNo":"REL-20260928-001","releasedBy":"system"}'

# 6. 查询证据包；发现错误可撤销（撤销后出库闸门返回 409）
curl localhost:8080/api/batches/B-001/evidence-package
curl -XPOST localhost:8080/api/batches/B-001/revocation -H 'Content-Type: application/json' \
  -d '{"reason":"依据有误","revokedBy":"qa-director"}'
```

## 测试

- `ManufacturingReleaseRulesTest`：声明、版本替代、偏差各处置的阻塞/放行、双角色审批与撤回、放行证据快照、幂等、放行后冻结、撤销与出库闸门、证据包/结论/版本历史。
- `ManufacturingReleaseConcurrencyTest`：放行与补录结果并发、放行与撤回审批并发（多轮重复）、相同放行业务号并发放行仅生效一次。
- `ManufacturingReleaseApiTest`：完整 HTTP 工作流（含 400/404/409 与撤销后出库阻止）。
