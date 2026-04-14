# Incident Runbook — OMSA Financial Services Platform

## SLA Reference
| Severity | Response | Resolution | Escalation |
|----------|----------|------------|------------|
| P1 — Critical | 15 min | 4 hours | On-call → Lead → CTO |
| P2 — High | 1 hour | 8 hours | On-call → Lead |
| P3 — Medium | 4 hours | 2 days | On-call |
| P4 — Low | 1 day | 1 week | Next sprint |

---

## Runbook: Transaction Service Down (P1)

**Alert:** `TransactionServiceDown`

### Step 1 — Assess (2 min)
```bash
kubectl get pods -n fintech-prod -l app=transaction-service
kubectl describe pods -n fintech-prod -l app=transaction-service | tail -30
kubectl logs -n fintech-prod -l app=transaction-service --tail=100 --previous
```

### Step 2 — Quick Fixes
```bash
# Option A: Rollback via ArgoCD (recommended — GitOps)
argocd app rollback transaction-service --revision <previous-revision>

# Option B: Rollback via kubectl
kubectl rollout undo deployment/transaction-service -n fintech-prod
kubectl rollout status deployment/transaction-service -n fintech-prod

# Option C: Force restart
kubectl rollout restart deployment/transaction-service -n fintech-prod

# Option D: Scale up (if resource issue)
kubectl scale deployment/transaction-service --replicas=6 -n fintech-prod
```

### Step 3 — Verify Recovery
```bash
kubectl get pods -n fintech-prod -l app=transaction-service
# All pods should be Running/Ready within 2 minutes
curl -f https://api.omsa.example.com/api/v1/transactions/health
```

### Common Root Causes
| Symptom | Cause | Fix |
|---------|-------|-----|
| `CrashLoopBackOff` | OOM / bad config / DB connection | Check logs, increase memory if OOM |
| `ImagePullBackOff` | Bad image tag / ECR permissions | Check ECR, verify IRSA role |
| `Pending` pods | Node resource exhaustion | Check node capacity, trigger autoscaler |
| `0/3 Ready` | Readiness probe failing | Check DB connectivity, check SG rules |

---

## Runbook: High Transaction Failure Rate (P1)

**Alert:** `HighTransactionFailureRate` (> 2% in any region)

```bash
# 1. Check which region is affected
kubectl logs -n fintech-prod -l app=transaction-service \
  --tail=500 | grep "status.*FAILED" | head -20

# 2. Check DB connectivity
kubectl exec -it -n fintech-prod \
  $(kubectl get pod -n fintech-prod -l app=transaction-service -o name | head -1) \
  -- wget -qO- http://localhost:8080/actuator/health | python3 -m json.tool

# 3. Check HikariCP pool
kubectl exec -it -n fintech-prod \
  $(kubectl get pod -n fintech-prod -l app=transaction-service -o name | head -1) \
  -- wget -qO- http://localhost:8080/actuator/metrics/hikaricp.connections.active

# 4. Run failure analysis
python3 scripts/cloud_maintenance.py txn-failure-report --region za-johannesburg
```

---

## Runbook: ArgoCD Out of Sync

**Alert:** ArgoCD app `OutOfSync` for > 10 minutes

```bash
# Check sync status
argocd app get transaction-service
argocd app get policy-service

# View diff — what's different between Git and cluster
argocd app diff transaction-service

# Manual sync if auto-sync is stuck
argocd app sync transaction-service --force

# Check ArgoCD controller logs
kubectl logs -n argocd \
  -l app.kubernetes.io/name=argocd-application-controller \
  --tail=100
```

---

## Runbook: RDS Aurora Failover

**Triggered by:** Aurora primary instance failure (automatic Multi-AZ failover)

```bash
# Monitor failover status
aws rds describe-db-clusters \
  --db-cluster-identifier omsa-fintech-prod \
  --query 'DBClusters[0].{Status:Status,Reader:ReaderEndpoint,Writer:Endpoint}'

# Expected: failover completes in ~30s
# Connection strings use cluster endpoint — apps reconnect automatically via HikariCP
# HikariCP config: connection-timeout=30000ms handles reconnect

# Verify app reconnected
kubectl logs -n fintech-prod -l app=transaction-service \
  --since=5m | grep -i "hikari\|connection\|reconnect"

# Post-failover: verify which node is now primary
aws rds describe-db-instances \
  --query 'DBInstances[?DBClusterIdentifier==`omsa-fintech-prod`].[DBInstanceIdentifier,IsClusterWriter]'
```

---

## Useful Commands Reference

```bash
# All pods in namespace
kubectl get pods -n fintech-prod -o wide

# ArgoCD app status
argocd app list

# Check HPA
kubectl get hpa -n fintech-prod

# Watch pod events
kubectl get events -n fintech-prod --sort-by='.lastTimestamp' | tail -20

# Resource usage
kubectl top pods -n fintech-prod
kubectl top nodes

# Check ingress
kubectl get ingress -n fintech-prod
kubectl describe ingress -n fintech-prod

# Exec into pod for debugging
kubectl exec -it -n fintech-prod \
  $(kubectl get pod -n fintech-prod -l app=transaction-service -o name | head -1) \
  -- /bin/sh

# Check Secrets (External Secrets sync status)
kubectl get externalsecret -n fintech-prod
kubectl describe externalsecret aurora-credentials -n fintech-prod

# Run maintenance scripts
python3 scripts/cloud_maintenance.py ecr-cleanup --keep 20
python3 scripts/cloud_maintenance.py rds-snapshot-check
python3 scripts/cloud_maintenance.py node-health
python3 scripts/cloud_maintenance.py log-analysis --hours 24
```
