# Architecture Decision Records — OMSA Financial Services Platform

## ADR-001: GitOps with ArgoCD over Jenkins push-based deploys

**Status:** Accepted | **Date:** 2023-03-01

### Context
Legacy Jenkins pipelines pushed directly to EKS clusters. This created drift — manual `kubectl apply` commands by engineers caused differences between what was in Git and what was running in production.

### Decision
GitOps model with ArgoCD:
- Kubernetes manifests live in Git (single source of truth)
- ArgoCD polls every 3 minutes and applies any diff automatically
- `selfHeal: true` reverts any manual changes — preventing drift
- GitHub Actions CI only updates image tags in Git; ArgoCD does the actual deployment

### Consequences
- **Eliminated configuration drift** across 14 regions
- Full audit trail: every change is a Git commit with author and timestamp
- Engineers can no longer `kubectl apply` in production without it being reverted
- Rollback = `git revert` + push → ArgoCD applies within 3 minutes

---

## ADR-002: AWS EKS over self-managed Kubernetes

**Status:** Accepted | **Date:** 2023-02-15

### Context
Financial services require high availability and minimal operational overhead on control plane management.

### Decision
AWS EKS with:
- Managed node groups in private subnets across 3 AZs
- Cluster Autoscaler for cost-efficient node scaling
- EKS Managed Add-ons (vpc-cni, coredns, kube-proxy, ebs-csi)
- IRSA (IAM Roles for Service Accounts) for pod-level AWS permissions
- KMS encryption for Kubernetes secrets at rest

### Consequences
- AWS manages control plane HA — no manual etcd management
- Node upgrades handled by EKS (rolling, with PodDisruptionBudgets)
- IRSA eliminates need for long-lived AWS credentials in pods

---

## ADR-003: Trivy + OWASP as DevSecOps gates in GitHub Actions

**Status:** Accepted | **Date:** 2023-03-10

### Context
Financial services are high-value targets. Container and library vulnerabilities must be caught before production.

### Decision
Two mandatory security gates in every CI run:
1. **OWASP Dependency Check** — scans Java dependencies for CVEs with CVSS ≥ 8 failing the build
2. **Trivy** — scans the built Docker image for CRITICAL/HIGH CVEs before ECR push

Neither gate can be bypassed. SARIF output uploaded to GitHub Security tab for tracking.

### Consequences
- Zero CRITICAL CVEs shipped to production since implementation
- Added ~4 minutes to CI pipeline (acceptable trade-off)
- Some false positives managed via suppression file (reviewed quarterly)

---

## ADR-004: Aurora PostgreSQL over standard RDS

**Status:** Accepted | **Date:** 2023-02-20

### Context
Transaction data requires both high write throughput and low-latency reads across 14 regions.

### Decision
AWS Aurora PostgreSQL with:
- Global Database: primary in af-south-1, read replicas in all 14 regions
- Multi-AZ: automatic failover within primary region (RTO: ~30s)
- Aurora Serverless v2 considered but rejected — unpredictable cold start latency for financial transactions
- Connection pooling via RDS Proxy to handle burst traffic from HPA scale-outs

### Consequences
- **RPO: ~5s** (Aurora replication lag)
- **RTO: ~30s** (automated Multi-AZ failover)
- Read replicas in each region reduce cross-region latency for reporting queries
- RDS Proxy absorbs connection spikes from pod autoscaling (avoids "Too many connections")

---

## ADR-005: External Secrets Operator for secret management

**Status:** Accepted | **Date:** 2023-04-01

### Context
Database credentials, API keys, and TLS certificates must not be stored in Git or Kubernetes Secrets directly (plaintext base64).

### Decision
- **AWS Secrets Manager**: source of truth for all credentials
- **External Secrets Operator**: syncs Secrets Manager values → Kubernetes Secrets on a schedule
- Secrets rotated automatically (RDS: 30 days, API keys: 90 days) via Lambda rotation
- No secrets in Git — `kubernetes/base/*.yaml` reference secret names, not values

### Consequences
- Kubernetes Secrets stay in sync with rotation automatically
- If a secret is compromised, rotation in Secrets Manager propagates within minutes
- Slight latency on rotation propagation (max sync interval: 1h)

---

## ADR-006: Kustomize overlays for environment management

**Status:** Accepted | **Date:** 2023-03-15

### Decision
Single `kubernetes/base/` with `kubernetes/overlays/dev/` and `kubernetes/overlays/prod/` using Kustomize:
- `base/` contains deployment structure, probes, security context
- `overlays/prod/` patches replica count, image tags, resource limits
- GitHub Actions updates only `overlays/prod/kustomization.yaml` (image tag)
- ArgoCD reads `overlays/prod/` as the sync path

### Consequences
- DRY: no duplicate YAML between environments
- ArgoCD diff is minimal and predictable (only image tag changes on most deploys)
- Helm considered but rejected — Kustomize is simpler for this scope
