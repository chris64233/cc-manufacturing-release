# cc-manufacturing-release

生产批次与质量资料管理服务：覆盖**生产批次在检验、偏差处置和质量审批完成后的放行**全流程，
包括版本化检验结果、偏差处置、双角色审批、放行依据快照锁定、幂等放行、撤销放行与证据追溯。

## 开发环境

- JDK 21
- Maven Wrapper 3.9.9
- Spring Boot 4.1.1（Spring Data JPA + H2）

迁移项目沿用现有 Spring Boot 版本，其他项目使用上述版本。

## 常用命令

运行测试：

    ./mvnw clean test

启动服务：

    ./mvnw spring-boot:run

## 领域模型

| 实体 | 说明 |
| --- | --- |
| `Batch` | 生产批次：批次号、产品、数量、所需检验项目；状态 `CREATED → RELEASED → SHIPPED / REVOKED` |
| `InspectionItem` | 批次声明的检验项目 |
| `InspectionResultVersion` | 检验结果版本，带项目内递增版本号；`superseded` 标记旧版本 |
| `Deviation` | 偏差单：`OPEN → IN_DISPOSITION / CLOSED`，处置决定返工/报废/有条件接受 |
| `ReleaseApproval` | 放行审批，记录角色与审批人，可撤回 |
| `Release` | 放行记录，放行业务号全局唯一（幂等键） |
| `ReleaseEvidenceItem` | 放行依据快照，锁定当时使用的结果版本与偏差版本 |
| `ReleaseRevocation` | 撤销放行事件（只追加，不修改放行记录） |
| `BatchEvent` | 追加式审计事件，构成完整版本历史 |

## 主要业务规则

### 1. 批次声明与版本化检验结果

- 批次声明产品、数量和所需检验项目，至少一个检验项目；项目代码批次内唯一。
- 每个检验项目可多次提交结果，版本号从 1 递增；提交新版本时上一版被标记为
  **已替代（superseded）**，历史完整保留，但**只有当前有效（未替代）结果能用于放行**。
- **不合格结果必须关联一张本批次的、尚未关闭的偏差单**；已关闭偏差不能再挂接新的不合格结果。

### 2. 偏差处置

偏差处置决定有三种：

- `CONDITIONAL_ACCEPT` 有条件接受：偏差直接终态关闭，允许放行。
- `SCRAP` 报废：偏差直接终态关闭，但批次**永久禁止放行**。
- `REWORK` 返工：必须指定待复验项目，偏差进入 `IN_DISPOSITION`；该项目复验提交合格结果后
  偏差自动关闭。复验仍不合格则继续阻塞。

放行的必要条件（全部满足）：

1. 每个声明的检验项目都有**当前有效结果**；
2. 当前结果不合格的，其关联偏差已终态关闭；
3. 批次上**全部偏差**都处于允许放行的终态（有条件接受，或返工复验合格关闭）；
4. 至少有**两个不同角色**的当前有效审批（撤回的审批不计）。

### 3. 审批、事务与并发一致性

- 同一角色在撤回前不能重复审批；撤回后可重新审批，历史保留。
- 放行时在**同一事务**内完成：加锁 → 依据校验 → 生成放行单 → 写入检验结果版本与偏差版本的
  **不可变快照** → 批次状态翻转为 `RELEASED`。
- 所有修改批次资料/状态的事务都先对批次行加**悲观写锁**（`PESSIMISTIC_WRITE`），
  使并发的"补录结果 / 撤回审批 / 放行"严格串行：
  - 撤回先生效 → 放行因审批不足失败，不会产生放行单；
  - 放行先生效 → 撤回被拒（已放行不可撤回），证据完整锁定；
  - 补录先生效 → 放行锁定新版本；放行先生效 → 补录因不可变被拒。
- 不会出现"依据不完整却已放行"的批次。

### 4. 幂等、不可变与撤销

- 放行业务号 `releaseNo` 全局唯一，重复提交（含并发重试）返回同一张放行单；
  同一业务号不能用于其他批次，同一批次不能用不同业务号二次放行。
- 放行后**批次数量、检验依据、偏差与审批均不可修改**，补录结果、改数量、撤回审批等一律拒绝。
- 发现错误时只能**登记撤销放行事件**（不删除/不改写放行记录与快照）：
  - 批次进入 `REVOKED`，**尚未出库的批次被阻止继续流转（禁止出库）**；
  - 已出库批次同样可登记撤销，并记录 `alreadyShipped=true` 以便追溯。
- 出库仅允许 `RELEASED` 状态；已撤销批次禁止出库。

### 5. 查询

- **当前结论** `GET /api/batches/{batchNo}/conclusion`：状态、是否可放行、具体阻碍项、放行/撤销信息。
- **证据包** `GET /api/releases/{releaseNo}/evidence`：放行时锁定的每个结果版本与偏差版本快照。
- **完整版本历史** `GET /api/batches/{batchNo}/history`：批次现状、全部结果版本、审批记录与审计事件。

## HTTP 接口

写接口的操作人通过请求体 `actor` 传递。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/batches` | 声明批次（含检验项目） |
| GET | `/api/batches/{batchNo}` | 批次详情（含项目/偏差/审批） |
| PUT | `/api/batches/{batchNo}/quantity` | 修改数量（放行前） |
| POST | `/api/batches/{batchNo}/results` | 提交检验结果新版本 |
| POST | `/api/batches/{batchNo}/deviations` | 登记偏差单 |
| POST | `/api/deviations/{deviationNo}/disposition` | 偏差处置（REWORK/SCRAP/CONDITIONAL_ACCEPT） |
| POST | `/api/batches/{batchNo}/approvals` | 授予角色审批 |
| POST | `/api/batches/{batchNo}/approvals/withdrawals` | 撤回角色审批 |
| POST | `/api/batches/{batchNo}/releases` | 放行（按 `releaseNo` 幂等） |
| POST | `/api/releases/{releaseNo}/revocations` | 登记撤销放行 |
| POST | `/api/batches/{batchNo}/ship` | 批次出库 |
| GET | `/api/batches/{batchNo}/conclusion` | 当前结论与阻碍项 |
| GET | `/api/releases/{releaseNo}/evidence` | 放行证据包 |
| GET | `/api/batches/{batchNo}/history` | 完整版本历史 |

错误码：`400` 参数校验失败、`404` 资源不存在、`409` 业务规则冲突
（依据不完整、状态非法、对象不可变、业务号冲突、并发冲突等）。

### 典型放行流程示例

```bash
# 1. 声明批次
curl -X POST localhost:8080/api/batches -H 'Content-Type: application/json' -d '{
  "batchNo":"B-001","productCode":"P-100","quantity":1000,"actor":"planner",
  "items":[{"itemCode":"I1","itemName":"含量"},{"itemCode":"I2","itemName":"无菌"}]}'

# 2. 登记偏差并提交结果
curl -X POST localhost:8080/api/batches/B-001/deviations -H 'Content-Type: application/json' -d \
  '{"deviationNo":"D-001","description":"含量偏低","actor":"qc"}'
curl -X POST localhost:8080/api/batches/B-001/results -H 'Content-Type: application/json' -d \
  '{"itemCode":"I1","conforming":false,"valueText":"90%","deviationNo":"D-001","actor":"qc"}'
curl -X POST localhost:8080/api/batches/B-001/results -H 'Content-Type: application/json' -d \
  '{"itemCode":"I2","conforming":true,"valueText":"合格","actor":"qc"}'

# 3. 偏差有条件接受（或 REWORK 后复验合格）
curl -X POST localhost:8080/api/deviations/D-001/disposition -H 'Content-Type: application/json' -d \
  '{"decision":"CONDITIONAL_ACCEPT","remark":"限定用途","actor":"qa"}'

# 4. 两个不同角色审批
curl -X POST localhost:8080/api/batches/B-001/approvals -H 'Content-Type: application/json' -d \
  '{"roleCode":"QA_MANAGER","approver":"alice"}'
curl -X POST localhost:8080/api/batches/B-001/approvals -H 'Content-Type: application/json' -d \
  '{"roleCode":"PRODUCTION_MANAGER","approver":"bob"}'

# 5. 幂等放行
curl -X POST localhost:8080/api/batches/B-001/releases -H 'Content-Type: application/json' -d \
  '{"releaseNo":"R-001","actor":"qa-lead"}'

# 6. 查询证据包 / 当前结论 / 历史
curl localhost:8080/api/releases/R-001/evidence
curl localhost:8080/api/batches/B-001/conclusion
curl localhost:8080/api/batches/B-001/history
```

## 测试

- `QualityReleaseServiceTest`：声明、版本化结果、三种偏差处置、双角色审批、放行条件、
  幂等、快照锁定、放行后不可变、撤销、阻止出库、结论与历史查询等业务规则。
- `QualityReleaseConcurrencyTest`：放行与撤回审批、放行与并发补录结果（多轮栅栏并发）、
  同号并发重试幂等、异号并发放行互斥，验证不会产生依据不完整的已放行批次。
- `QualityReleaseControllerTest`：HTTP 全生命周期、返工流程、参数校验与 400/404/409 映射。
