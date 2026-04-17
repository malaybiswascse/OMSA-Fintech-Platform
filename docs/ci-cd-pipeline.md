# CI/CD Pipeline — Jenkins + ArgoCD

This project uses **Jenkins** for CI and **ArgoCD** for GitOps-driven CD.
GitHub Actions is **not used** — the `.github/workflows/ci.yml` file is a
placeholder that simply prints a message.

---

## Pipeline Architecture

```
Developer pushes code
        │
        ▼
Jenkins (webhook triggered)
  ├── Stage 1: Checkout
  ├── Stage 2: Unit + Integration Tests (parallel)
  │     ├── Transaction Service (Maven + Testcontainers)
  │     └── Policy Service     (Maven + Testcontainers)
  ├── Stage 3: SonarQube Code Quality Analysis
  ├── Stage 4: SonarQube Quality Gate (blocks if fails)
  ├── Stage 5: OWASP Dependency Check (CVSS ≥ 8 fails)
  ├── Stage 6: Docker Build (multi-stage, non-root)
  ├── Stage 7: Trivy Container Scan (CRITICAL = fail)
  ├── Stage 8: Push to Amazon ECR
  ├── Stage 9: Update kustomization.yaml image tag (Git commit)
  └── Stage 10: Manual approval gate → Production

ArgoCD (polling every 3 minutes)
  ├── Detects new image tag in kubernetes/overlays/prod/
  ├── Applies diff to EKS cluster
  ├── selfHeal: true  → reverts any manual kubectl changes
  └── Slack notification on sync success/failure
```

---

## Jenkins Setup

### Prerequisites
- Jenkins 2.440+ with plugins: Kubernetes, Pipeline, SonarQube, Docker, Slack, JaCoCo, OWASP
- Kubernetes plugin configured to use EKS cluster
- Persistent Volume Claim `maven-cache-pvc` for Maven dependency caching

### Credentials to configure in Jenkins

| Credential ID | Type | Description |
|---|---|---|
| `ecr-registry-url` | Secret Text | AWS ECR registry URL |
| `sonarqube-url` | Secret Text | SonarQube server URL |
| `sonarqube-token` | Secret Text | SonarQube auth token |
| `github-credentials` | Username/Password | GitHub PAT for manifest update |
| `aws-credentials` | AWS Credentials | For ECR login |

### Configure the pipeline
```
1. Jenkins → New Item → Pipeline
2. Name: omsa-fintech-platform
3. Pipeline → Definition: Pipeline script from SCM
4. SCM: Git → URL: https://github.com/malaybiswascse/omsa-fintech-platform.git
5. Branch: */main
6. Script Path: jenkins/Jenkinsfile
7. Save → Build Now
```

---

## ArgoCD Setup

### Install ArgoCD
```bash
kubectl create namespace argocd
kubectl apply -n argocd \
  -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml

# Get initial admin password
kubectl get secret argocd-initial-admin-secret \
  -n argocd -o jsonpath="{.data.password}" | base64 -d
```

### Apply Applications
```bash
kubectl apply -f argocd/applications.yaml
```

### ArgoCD will automatically:
- Poll this repo every 3 minutes
- Detect image tag changes in `kubernetes/overlays/prod/kustomization.yaml`
- Apply changes to `fintech-prod` namespace in EKS
- Revert any manual `kubectl` changes (`selfHeal: true`)
- Send Slack notification on sync success or failure

---

## Triggering a Build

### Automatic (recommended)
Configure GitHub webhook:
```
Payload URL: https://jenkins.omsa.internal/github-webhook/
Content type: application/json
Events: Push events
```

### Manual
```bash
# Trigger via Jenkins CLI
java -jar jenkins-cli.jar -s https://jenkins.omsa.internal/ \
  build omsa-fintech-platform -p BRANCH=main
```

---

## Monitoring Builds
- **Jenkins**: https://jenkins.omsa.internal/job/omsa-fintech-platform/
- **ArgoCD**: https://argocd.omsa.internal/applications
- **SonarQube**: https://sonar.omsa.internal/dashboard?id=omsa-transaction-service
- **Slack**: `#omsa-deployments`
