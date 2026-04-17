# 🏦 Integrated Financial Services Platform — Old Mutual South Africa (OMSA)

[![Jenkins](https://img.shields.io/badge/CI-Jenkins-D24939?style=flat&logo=jenkins&logoColor=white)](https://jenkins.io)
[![ArgoCD](https://img.shields.io/badge/CD-ArgoCD-EF7B4D?style=flat&logo=argo&logoColor=white)](https://argoproj.github.io)
[![Kubernetes](https://img.shields.io/badge/Kubernetes-1.28-326CE5?style=flat&logo=kubernetes&logoColor=white)](https://kubernetes.io)
[![Terraform](https://img.shields.io/badge/Terraform-1.6-623CE4?style=flat&logo=terraform&logoColor=white)](https://terraform.io)
[![AWS](https://img.shields.io/badge/AWS-EKS-FF9900?style=flat&logo=amazon-aws&logoColor=white)](https://aws.amazon.com)
[![Trivy](https://img.shields.io/badge/Security-Trivy-1904DA?style=flat&logo=aqua&logoColor=white)](https://trivy.dev)

A **production-grade, cloud-native financial services platform** built for Old Mutual South Africa — serving banking and insurance workloads across **14 regions**. Deployed on AWS EKS with GitOps-driven continuous delivery via ArgoCD, a full DevSecOps pipeline through Jenkins, real-time observability with Prometheus and Grafana, and multi-region high availability using AWS Aurora Global Database.

---

## 📐 Architecture Overview

```
                     ┌────────────────────────────────────────────────────────┐
                     │                  AWS Cloud (af-south-1 primary)         │
                     │                                                          │
  Clients ──► Route53 ──► ALB ──► NGINX Ingress Controller                  │
                     │                    │                                    │
                     │         ┌──────────▼──────────────────────────┐        │
                     │         │          EKS Cluster                  │        │
                     │         │  Namespace: fintech-prod              │        │
                     │         │                                        │        │
                     │         │  ┌──────────────┐ ┌──────────────┐   │        │
                     │         │  │  Transaction  │ │    Policy    │   │        │
                     │         │  │   Service     │ │   Service    │   │        │
                     │         │  │ (Java SB 3.2) │ │ (Java SB 3.2│   │        │
                     │         │  │  3 replicas   │ │  2 replicas )│   │        │
                     │         │  │  HPA: 3-20    │ │  HPA: 2-10  │   │        │
                     │         └──┴──────┬─────────┴──────┬─────────┴──┘        │
                     │                   └────────┬────────┘                    │
                     │              ┌─────────────▼──────────────┐             │
                     │              │  RDS Aurora PostgreSQL       │             │
                     │              │  Multi-AZ + 14 read replicas│             │
                     │              │  (one per region)            │             │
                     │              └────────────────────────────┘             │
                     │                                                          │
                     │  GitOps:    ArgoCD ← Git (kubernetes/overlays/prod)    │
                     │  CI/CD:     Jenkins (10-stage declarative pipeline)     │
                     │  Secrets:   AWS Secrets Manager + External Secrets Op  │
                     │  Monitor:   Prometheus + Grafana + AlertManager         │
                     │  Security:  Trivy + OWASP Dependency Check             │
                     └──────────────────────────────────────────────────────────┘

  14-Region Setup:
  af-south-1 (primary write) ──► 13 regional read replicas via Aurora Global
  Route53 Latency-Based Routing → nearest healthy endpoint per client region
```

---

## 🛠 Tech Stack

| Layer | Technology | Purpose |
|-------|-----------|---------|
| **Languages** | Java 17 (Spring Boot 3.2) | Transaction & Policy microservices |
| **Cloud** | AWS (EKS, Aurora, VPC, ALB, ECR, SES, Secrets Manager) | All infrastructure |
| **IaC** | Terraform 1.6 (modular) | VPC, EKS, RDS provisioning |
| **Containers** | Docker (multi-stage, non-root, Alpine) | Service packaging |
| **Orchestration** | Kubernetes 1.28, AWS EKS | Container orchestration |
| **GitOps / CD** | ArgoCD (auto-sync, selfHeal) | Drift-free deployments |
| **CI** | Jenkins (Declarative Pipeline, K8s agents) | Build & quality pipeline |
| **Code Quality** | SonarQube (quality gate, 70% coverage min) | Technical debt control |
| **Security** | Trivy (container scan) + OWASP Dependency Check | DevSecOps gates |
| **Monitoring** | Prometheus, Grafana, AlertManager | Observability |
| **Database** | AWS Aurora PostgreSQL (Multi-AZ + Global) | Persistent storage |
| **Secrets** | AWS Secrets Manager + External Secrets Operator | Secret management |
| **DNS** | Route53 + ACM (wildcard TLS) | Traffic routing |

---

## 📁 Repository Structure

```
omsa-fintech-platform/
├── services/
│   ├── transaction-service/          # Payment & fund transfer microservice
│   │   ├── src/main/java/            # Spring Boot application code
│   │   ├── src/test/java/            # Testcontainers integration tests
│   │   ├── src/main/resources/
│   │   │   └── db/migration/         # Flyway SQL migrations
│   │   ├── Dockerfile                # Multi-stage, non-root container
│   │   └── pom.xml
│   └── policy-service/               # Insurance policy lifecycle microservice
│       ├── src/main/java/
│       ├── src/test/java/
│       ├── src/main/resources/
│       │   └── db/migration/
│       ├── Dockerfile
│       └── pom.xml
├── infrastructure/
│   ├── terraform/
│   │   ├── modules/
│   │   │   ├── eks/                  # EKS cluster + ECR + OIDC/IRSA
│   │   │   ├── vpc/                  # Multi-AZ VPC, subnets, NAT
│   │   │   └── rds/                  # Aurora PostgreSQL + Secrets Manager
│   │   └── environments/
│   │       └── prod/                 # Production environment config
│   └── ansible/                      # Server hardening playbooks
├── kubernetes/
│   ├── base/                         # Base Kustomize manifests
│   │   ├── transaction-service.yaml  # Deployment, Service, HPA, PDB
│   │   └── policy-service.yaml
│   └── overlays/
│       ├── dev/                      # Dev environment patches
│       └── prod/                     # Prod environment (ArgoCD sync target)
│           ├── kustomization.yaml    # Image tags updated by Jenkins
│           └── namespace.yaml
├── argocd/
│   └── applications.yaml             # ArgoCD Application + AppProject CRDs
├── jenkins/
│   └── Jenkinsfile                   # 10-stage declarative CI/CD pipeline
├── monitoring/
│   ├── prometheus/rules/             # Alert rules (P1/P2/P3 severity)
│   └── grafana/dashboards/           # Transaction health dashboards
├── scripts/
│   └── cloud_maintenance.py          # Python: ECR cleanup, log analysis, RDS checks
└── docs/
    ├── ADR.md                        # Architecture Decision Records
    ├── runbook.md                    # Incident response (P1=4h SLA)
    └── ci-cd-pipeline.md             # Jenkins + ArgoCD setup guide
```

---

## 🔄 CI/CD Pipeline — Jenkins + ArgoCD

```
Git push to main branch
        │
        ▼
Jenkins (webhook triggered from GitHub)
  ├── Stage 1:  Checkout + metadata extraction
  ├── Stage 2:  Parallel unit + integration tests
  │              ├── Transaction Service (Maven + Testcontainers + real PostgreSQL)
  │              └── Policy Service     (Maven + Testcontainers + real PostgreSQL)
  ├── Stage 3:  SonarQube static analysis (both services)
  ├── Stage 4:  SonarQube Quality Gate (min 60% coverage, aborts on fail)
  ├── Stage 5:  OWASP Dependency Check (CVSS ≥ 8 fails build)
  ├── Stage 6:  Docker multi-stage build (transaction + policy images)
  ├── Stage 7:  Trivy container scan (CRITICAL CVE = build failure)
  ├── Stage 8:  Push to Amazon ECR (tagged: build-number + git-sha)
  ├── Stage 9:  Update kustomization.yaml image tag → Git commit
  └── Stage 10: Manual approval gate → Production sign-off
        │
        ▼
ArgoCD (polling every 3 minutes)
  ├── Detects new image tag in kubernetes/overlays/prod/kustomization.yaml
  ├── Validates manifests (dry-run)
  ├── Applies RollingUpdate to EKS (maxUnavailable: 0 = zero downtime)
  ├── selfHeal: true → reverts any manual kubectl changes (drift prevention)
  └── Slack notification → #omsa-deployments
```

---

## 🚀 Quick Start

### 1. Provision AWS Infrastructure
```bash
cd infrastructure/terraform/environments/prod
terraform init
terraform plan -out=tfplan
terraform apply tfplan
```

### 2. Configure kubectl
```bash
aws eks update-kubeconfig \
  --name omsa-fintech-prod \
  --region af-south-1
```

### 3. Install ArgoCD
```bash
kubectl create namespace argocd
kubectl apply -n argocd \
  -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml

# Apply platform applications
kubectl apply -f argocd/applications.yaml
```

### 4. ArgoCD syncs automatically from this point
```bash
# ArgoCD detects kubernetes/overlays/prod/ and deploys everything
argocd app list
argocd app get transaction-service
argocd app get policy-service
```

### 5. Jenkins (manual trigger for first run)
```
Jenkins → omsa-fintech-platform → Build Now
```

---

## 📊 Observability

| Tool | URL | Purpose |
|------|-----|---------|
| Grafana | https://grafana.omsa.internal | Transaction rate, P95 latency, error rate |
| Prometheus | https://prometheus.omsa.internal | Metrics collection + alert rules |
| AlertManager | https://alertmanager.omsa.internal | PagerDuty (P1/P2), Slack (P3/P4) |
| ArgoCD | https://argocd.omsa.internal | GitOps deployment status |
| Jenkins | https://jenkins.omsa.internal | Pipeline runs and build history |
| SonarQube | https://sonar.omsa.internal | Code quality and coverage |

### Key Metrics Monitored
- Transaction failure rate per region (alert at > 2%)
- P95 API latency (alert at > 3 seconds)
- JVM heap utilisation per pod
- HikariCP connection pool saturation
- RDS Aurora replica lag (alert at > 100ms)
- Pod restart count (CrashLoopBackOff detection)

---

## 🌍 Multi-Region High Availability

| Component | Strategy | RTO | RPO |
|-----------|----------|-----|-----|
| EKS Pods | Multi-AZ, topology spread | < 2 min | 0 |
| Aurora DB | Multi-AZ auto-failover | ~30 sec | ~5 sec |
| Aurora Read | Global DB, 14 regional replicas | N/A (reads only) | ~5 sec |
| DNS Routing | Route53 latency-based + health checks | < 60 sec | 0 |
| Secrets | AWS Secrets Manager + auto-rotation | Immediate | 0 |

---

## 🔒 Security Posture

- **Container security**: non-root user, `readOnlyRootFilesystem`, drop ALL capabilities
- **Network isolation**: Kubernetes NetworkPolicy — pods only accept traffic from Ingress + monitoring namespace
- **IRSA**: Pod-level AWS permissions via IAM Roles for Service Accounts (no static credentials)
- **Secrets**: AWS Secrets Manager → External Secrets Operator → Kubernetes Secrets (never in Git)
- **Scanning**: Trivy on every build (CRITICAL = pipeline failure), OWASP on every dependency update
- **Encryption**: KMS for EKS secrets at rest, TLS everywhere in transit, Aurora encrypted at rest

---

## 🧪 Testing Strategy

| Level | Framework | Coverage |
|-------|----------|---------|
| Unit tests | JUnit 5 + Mockito | Service layer, validation, state transitions |
| Integration tests | Testcontainers (real PostgreSQL) | Full HTTP request → DB → response cycle |
| Contract tests | Spring MVC MockMvc | All REST endpoints, error responses |
| Quality gate | JaCoCo + SonarQube | Min 60% instruction coverage enforced in Jenkins |

---

## 📖 Documentation

- [Architecture Decision Records](docs/ADR.md) — 6 key architectural choices with rationale
- [Incident Runbook](docs/runbook.md) — P1/P2 response procedures, rollback steps
- [CI/CD Pipeline Guide](docs/ci-cd-pipeline.md) — Jenkins setup, ArgoCD configuration

---

## 🐍 Python Automation Scripts

```bash
# Clean up old ECR images (keep last 20 per repo)
python3 scripts/cloud_maintenance.py ecr-cleanup --keep 20

# Scan CloudWatch logs for error patterns (last 24h)
python3 scripts/cloud_maintenance.py log-analysis --hours 24

# Verify RDS automated snapshot is within 26 hours
python3 scripts/cloud_maintenance.py rds-snapshot-check

# EKS node health report
python3 scripts/cloud_maintenance.py node-health

# Transaction failure rate report per region
python3 scripts/cloud_maintenance.py txn-failure-report --region za-johannesburg
```

---

## 📝 License

MIT — see [LICENSE](LICENSE)
