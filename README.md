# 🏦 Integrated Financial Services Platform — Old Mutual South Africa

[![CI](https://github.com/malaybiswascse/omsa-fintech-platform/actions/workflows/ci.yml/badge.svg)](https://github.com/malaybiswascse/omsa-fintech-platform/actions)
[![Security Scan](https://img.shields.io/badge/trivy-passing-brightgreen)](https://github.com/malaybiswascse/omsa-fintech-platform/actions)
[![Terraform](https://img.shields.io/badge/terraform-1.6-623CE4)](https://terraform.io)
[![Kubernetes](https://img.shields.io/badge/kubernetes-1.28-326CE5)](https://kubernetes.io)
[![ArgoCD](https://img.shields.io/badge/argocd-gitops-orange)](https://argoproj.github.io)
[![License](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

A **production-grade, cloud-native financial services platform** serving banking and insurance workloads across **14 regions** for Old Mutual South Africa. Built on AWS EKS with GitOps-driven deployments via ArgoCD, full DevSecOps pipeline, and real-time observability across all transaction and policy services.

---

## 📐 Architecture Overview

```
                         ┌──────────────────────────────────────────────┐
                         │              AWS Cloud (af-south-1)           │
                         │                                               │
 Clients ──► Route53 ──► ALB ──► NGINX Ingress                        │
                         │            │                                  │
                         │   ┌────────▼──────────────────────────┐      │
                         │   │          EKS Cluster               │      │
                         │   │                                     │      │
                         │   │  ┌─────────────┐ ┌─────────────┐  │      │
                         │   │  │ Transaction  │ │   Policy    │  │      │
                         │   │  │  Service     │ │   Service   │  │      │
                         │   │  │ (Java/SB)   │ │ (Java/SB)   │  │      │
                         │   │  └──────┬──────┘ └──────┬───────┘  │      │
                         │   │         └───────┬────────┘          │      │
                         │   │      ┌──────────▼─────────┐         │      │
                         │   │      │  RDS Aurora (PG)    │         │      │
                         │   │      │  Multi-AZ, 14 read  │         │      │
                         │   │      │  replicas (regions) │         │      │
                         │   │      └────────────────────┘         │      │
                         │   └─────────────────────────────────────┘      │
                         │                                                 │
                         │  GitOps: ArgoCD ← GitHub (main branch)        │
                         │  Secrets: AWS Secrets Manager                  │
                         │  Monitoring: Prometheus + Grafana               │
                         │  Security: Trivy + OWASP in GitHub Actions     │
                         └─────────────────────────────────────────────────┘
```

---

## 🛠 Tech Stack

| Layer | Technology |
|-------|-----------|
| **Languages** | Java 17 (Spring Boot 3), Python 3.11 |
| **Cloud** | AWS (EKS, RDS Aurora, VPC, ALB, Secrets Manager, CloudWatch) |
| **IaC** | Terraform 1.6 (modular: VPC, EKS, RDS) |
| **Containers** | Docker (multi-stage, non-root) |
| **Orchestration** | Kubernetes 1.28, AWS EKS |
| **GitOps** | ArgoCD (Application CRDs, auto-sync) |
| **CI/CD** | GitHub Actions (build → scan → push → deploy) |
| **DevSecOps** | Trivy (container scan), OWASP Dependency Check |
| **Monitoring** | Prometheus, Grafana, AlertManager |
| **Database** | AWS RDS Aurora PostgreSQL (Multi-AZ) |
| **Secrets** | AWS Secrets Manager + External Secrets Operator |
| **DNS** | Route53 + ACM wildcard TLS |

---

## 📁 Repository Structure

```
omsa-fintech-platform/
├── services/
│   ├── transaction-service/     # Java Spring Boot — payment processing
│   └── policy-service/          # Java Spring Boot — insurance policy mgmt
├── infrastructure/
│   ├── terraform/
│   │   ├── modules/             # Reusable: vpc, eks, rds
│   │   └── environments/        # dev / prod configs
│   └── ansible/                 # Server hardening playbooks
├── kubernetes/
│   ├── base/                    # Base manifests (Kustomize)
│   └── overlays/                # dev / prod environment overlays
├── argocd/                      # ArgoCD Application + AppProject CRDs
├── monitoring/
│   ├── prometheus/rules/        # Alert rules (transaction health, latency)
│   └── grafana/dashboards/      # Financial services dashboards
├── scripts/                     # Python automation (maintenance, log analysis)
├── docs/                        # ADRs, runbooks, DR plan
└── .github/workflows/           # GitHub Actions CI/CD pipelines
```

---

## 🚀 Quick Start

### 1. Provision Infrastructure
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

# Apply our ArgoCD applications
kubectl apply -f argocd/
```

### 4. ArgoCD syncs everything automatically
```
GitHub push → ArgoCD detects diff → applies to EKS
```

---

## 🔄 GitOps Deployment Flow

```
Developer pushes code
        │
        ▼
GitHub Actions CI:
  ├── Unit Tests (JUnit + JaCoCo)
  ├── OWASP Dependency Check
  ├── SonarQube Analysis
  ├── Docker Build (multi-stage)
  ├── Trivy Container Scan (fail on CRITICAL)
  ├── Push to Amazon ECR
  └── Update image tag in kubernetes/overlays/prod/

ArgoCD (polling every 3 min):
  ├── Detects diff in Git
  ├── Validates manifests
  ├── Applies to EKS (RollingUpdate)
  └── Health check → Slack notification
```

---

## 📊 Observability

- **Grafana**: Transaction rate, P95 latency, error rate per region
- **Prometheus**: Scrapes all pods, alert rules for SLA breaches
- **AlertManager**: PagerDuty (P1/P2) + Slack (#fintech-alerts)
- **CloudWatch**: VPC Flow Logs, EKS control plane logs, RDS slow queries

---

## 🌍 Multi-Region Architecture

Services deployed across **14 AWS regions** using:
- Aurora Global Database with read replicas per region
- Route53 Latency-Based Routing → nearest healthy endpoint
- CloudFront for static assets
- Active-Passive failover (RPO: 15min, RTO: 30min)

---

## 📖 Documentation

- [Architecture Decision Records](docs/ADR.md)
- [Incident Runbook](docs/runbook.md)
- [Disaster Recovery Plan](docs/disaster-recovery.md)

---

## 📝 License
MIT — see [LICENSE](LICENSE)
