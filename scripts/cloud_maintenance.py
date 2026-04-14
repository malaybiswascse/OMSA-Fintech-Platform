#!/usr/bin/env python3
"""
scripts/cloud_maintenance.py
-----------------------------
Python automation for OMSA cloud maintenance tasks:
  - Stale ECR image cleanup (keep last N images per repo)
  - CloudWatch log analysis for error patterns
  - RDS snapshot verification
  - EKS node health report
  - Transaction failure rate report from CloudWatch metrics

Usage:
  python cloud_maintenance.py ecr-cleanup --keep 20
  python cloud_maintenance.py log-analysis --hours 24
  python cloud_maintenance.py rds-snapshot-check
  python cloud_maintenance.py node-health
  python cloud_maintenance.py txn-failure-report --region za-johannesburg
"""

import argparse
import boto3
import json
import logging
import sys
from datetime import datetime, timedelta, timezone
from typing import Optional

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    handlers=[logging.StreamHandler(sys.stdout)]
)
logger = logging.getLogger(__name__)

AWS_REGION   = "af-south-1"
ECR_REPOS    = ["omsa/transaction-service", "omsa/policy-service"]
LOG_GROUPS   = [
    "/aws/eks/omsa-fintech-prod/application",
    "/aws/rds/instance/omsa-fintech-prod/postgresql"
]


# ── ECR Image Cleanup ─────────────────────────────────────────────────────────

def ecr_cleanup(keep: int = 20):
    """
    Removes old ECR images beyond the `keep` most recent.
    Runs weekly via GitHub Actions cron to prevent ECR storage bloat.
    """
    ecr = boto3.client("ecr", region_name=AWS_REGION)
    total_deleted = 0

    for repo in ECR_REPOS:
        logger.info(f"Checking ECR repo: {repo}")
        try:
            paginator = ecr.get_paginator("list_images")
            all_images = []
            for page in paginator.paginate(repositoryName=repo):
                all_images.extend(page["imageIds"])

            # Filter out untagged images
            tagged = [img for img in all_images if img.get("imageTag")]
            if len(tagged) <= keep:
                logger.info(f"  {repo}: {len(tagged)} images — no cleanup needed")
                continue

            # Sort by push date to find oldest
            details = ecr.describe_images(
                repositoryName=repo,
                imageIds=tagged
            )["imageDetails"]
            details.sort(key=lambda x: x["imagePushedAt"])

            to_delete = details[:len(details) - keep]
            image_ids = [{"imageTag": img["imageTags"][0]} for img in to_delete]

            ecr.batch_delete_image(repositoryName=repo, imageIds=image_ids)
            logger.info(f"  {repo}: deleted {len(to_delete)} old images, kept {keep}")
            total_deleted += len(to_delete)

        except ecr.exceptions.RepositoryNotFoundException:
            logger.warning(f"  Repo not found: {repo}")
        except Exception as e:
            logger.error(f"  Error cleaning {repo}: {e}")

    logger.info(f"ECR cleanup complete. Total deleted: {total_deleted}")
    return total_deleted


# ── CloudWatch Log Analysis ───────────────────────────────────────────────────

def log_analysis(hours: int = 24):
    """
    Scans CloudWatch logs for ERROR/EXCEPTION patterns.
    Produces a summary of top error types — used for daily ops review.
    """
    logs  = boto3.client("logs", region_name=AWS_REGION)
    now   = datetime.now(timezone.utc)
    start = int((now - timedelta(hours=hours)).timestamp() * 1000)
    end   = int(now.timestamp() * 1000)

    error_patterns = {
        "DB_CONNECTION":    "HikariPool.*connection.*timeout|Cannot acquire connection",
        "OOM":              "OutOfMemoryError|java.lang.OutOfMemoryError",
        "TXN_FAILED":       "TransactionStatus.FAILED|transaction.*failed",
        "AUTH_FAILURE":     "401|403|Unauthorized|Forbidden",
        "TIMEOUT":          "ReadTimeoutException|SocketTimeoutException|timeout",
        "UNHANDLED_EXCEPTION": "Unhandled exception|NullPointerException",
    }

    report = {}

    for group in LOG_GROUPS:
        logger.info(f"Analysing log group: {group}")
        for pattern_name, query_filter in error_patterns.items():
            try:
                resp = logs.filter_log_events(
                    logGroupName=group,
                    startTime=start,
                    endTime=end,
                    filterPattern=query_filter,
                    limit=100
                )
                count = len(resp.get("events", []))
                if count > 0:
                    key = f"{group.split('/')[-1]}::{pattern_name}"
                    report[key] = count
                    logger.warning(f"  [{pattern_name}] {count} occurrences in {group}")

            except logs.exceptions.ResourceNotFoundException:
                logger.warning(f"  Log group not found: {group}")
                break
            except Exception as e:
                logger.error(f"  Error querying {group}: {e}")

    if not report:
        logger.info("✅ No error patterns found in the last %d hours", hours)
    else:
        logger.warning(f"⚠️  Found {sum(report.values())} total errors across {len(report)} patterns")
        print("\n=== ERROR SUMMARY ===")
        for pattern, count in sorted(report.items(), key=lambda x: x[1], reverse=True):
            print(f"  {pattern:50s} {count:>5} occurrences")

    return report


# ── RDS Snapshot Verification ─────────────────────────────────────────────────

def rds_snapshot_check():
    """
    Verifies latest RDS automated snapshot is within 24h.
    Alerts if snapshot is missing or older than expected — critical for DR compliance.
    """
    rds          = boto3.client("rds", region_name=AWS_REGION)
    cluster_id   = "omsa-fintech-prod"
    max_age_hours = 26  # Allow 2h buffer beyond 24h snapshot frequency

    logger.info(f"Checking RDS snapshots for cluster: {cluster_id}")

    try:
        snapshots = rds.describe_db_cluster_snapshots(
            DBClusterIdentifier=cluster_id,
            SnapshotType="automated"
        )["DBClusterSnapshots"]

        if not snapshots:
            logger.error(f"❌ No automated snapshots found for {cluster_id}!")
            return False

        # Most recent snapshot
        latest = max(snapshots, key=lambda s: s["SnapshotCreateTime"])
        age    = datetime.now(timezone.utc) - latest["SnapshotCreateTime"]
        age_h  = age.total_seconds() / 3600

        logger.info(f"Latest snapshot: {latest['DBClusterSnapshotIdentifier']}")
        logger.info(f"Created at:      {latest['SnapshotCreateTime']}")
        logger.info(f"Status:          {latest['Status']}")
        logger.info(f"Age:             {age_h:.1f} hours")

        if age_h > max_age_hours:
            logger.error(f"❌ Snapshot is {age_h:.1f}h old — exceeds {max_age_hours}h threshold!")
            return False
        elif latest["Status"] != "available":
            logger.error(f"❌ Latest snapshot status is '{latest['Status']}' — not available!")
            return False
        else:
            logger.info(f"✅ RDS snapshot OK — {age_h:.1f}h old, status: {latest['Status']}")
            return True

    except Exception as e:
        logger.error(f"RDS snapshot check failed: {e}")
        return False


# ── EKS Node Health Report ────────────────────────────────────────────────────

def node_health():
    """
    Lists EKS nodes and their condition status.
    Used for daily ops check — identifies NotReady or memory-pressured nodes.
    """
    import subprocess
    import json as _json

    logger.info("Fetching EKS node health...")

    try:
        result = subprocess.run(
            ["kubectl", "get", "nodes", "-o", "json"],
            capture_output=True, text=True, timeout=30, check=True
        )
        nodes = _json.loads(result.stdout)["items"]
        issues = []

        print(f"\n{'NODE':<40} {'STATUS':<12} {'CPU':<10} {'MEMORY':<10} {'AGE':<10}")
        print("-" * 85)

        for node in nodes:
            name = node["metadata"]["name"]
            conditions = {c["type"]: c["status"] for c in node["status"]["conditions"]}
            ready      = conditions.get("Ready", "Unknown")
            mem_press  = conditions.get("MemoryPressure", "False")
            disk_press = conditions.get("DiskPressure", "False")

            created = datetime.fromisoformat(
                node["metadata"]["creationTimestamp"].replace("Z", "+00:00"))
            age_days = (datetime.now(timezone.utc) - created).days

            status = "✅ Ready" if ready == "True" else "❌ NotReady"
            if mem_press == "True":
                status = "⚠️ MemPressure"
                issues.append(f"{name}: MemoryPressure")
            if disk_press == "True":
                status = "⚠️ DiskPressure"
                issues.append(f"{name}: DiskPressure")
            if ready != "True":
                issues.append(f"{name}: NotReady")

            alloc = node["status"]["allocatable"]
            print(f"{name:<40} {status:<12} {alloc.get('cpu','?'):<10} "
                  f"{alloc.get('memory','?'):<10} {age_days}d")

        if issues:
            logger.warning(f"\n⚠️  {len(issues)} node issues found:")
            for issue in issues:
                logger.warning(f"  - {issue}")
        else:
            logger.info(f"\n✅ All {len(nodes)} nodes healthy")

        return issues

    except subprocess.CalledProcessError as e:
        logger.error(f"kubectl failed: {e.stderr}")
        return []
    except FileNotFoundError:
        logger.error("kubectl not found in PATH")
        return []


# ── Transaction Failure Rate Report ──────────────────────────────────────────

def txn_failure_report(region: str = "za-johannesburg", hours: int = 24):
    """
    Pulls transaction failure metrics from CloudWatch.
    Produces a per-hour breakdown used for executive reporting.
    """
    cw   = boto3.client("cloudwatch", region_name=AWS_REGION)
    now  = datetime.now(timezone.utc)
    start = now - timedelta(hours=hours)

    logger.info(f"Transaction failure report — region: {region}, last {hours}h")

    metrics = {
        "Initiated": {
            "MetricName": "transactions_initiated_total",
            "Dimensions": [{"Name": "region", "Value": region}]
        },
        "Failed": {
            "MetricName": "transactions_status_changed_total",
            "Dimensions": [
                {"Name": "to",     "Value": "FAILED"},
                {"Name": "region", "Value": region}
            ]
        },
        "Completed": {
            "MetricName": "transactions_status_changed_total",
            "Dimensions": [
                {"Name": "to",     "Value": "COMPLETED"},
                {"Name": "region", "Value": region}
            ]
        }
    }

    results = {}
    for metric_label, query in metrics.items():
        try:
            resp = cw.get_metric_statistics(
                Namespace="OMSA/FinTech",
                MetricName=query["MetricName"],
                Dimensions=query["Dimensions"],
                StartTime=start,
                EndTime=now,
                Period=3600,
                Statistics=["Sum"]
            )
            total = sum(dp["Sum"] for dp in resp["Datapoints"])
            results[metric_label] = total
        except Exception as e:
            logger.error(f"CloudWatch error for {metric_label}: {e}")
            results[metric_label] = 0

    initiated = results.get("Initiated", 0)
    failed    = results.get("Failed",    0)
    completed = results.get("Completed", 0)
    fail_rate = (failed / initiated * 100) if initiated > 0 else 0

    print(f"\n{'='*50}")
    print(f"TRANSACTION REPORT — {region.upper()}")
    print(f"Period: Last {hours}h ({start.strftime('%Y-%m-%d %H:%M')} UTC → now)")
    print(f"{'='*50}")
    print(f"  Initiated:  {initiated:>8,.0f}")
    print(f"  Completed:  {completed:>8,.0f}")
    print(f"  Failed:     {failed:>8,.0f}")
    print(f"  Fail Rate:  {fail_rate:>7.2f}%")
    print(f"{'='*50}")

    if fail_rate > 2.0:
        logger.warning(f"⚠️  Failure rate {fail_rate:.2f}% exceeds 2% SLA threshold!")
    else:
        logger.info(f"✅ Failure rate {fail_rate:.2f}% within SLA")

    return {"initiated": initiated, "failed": failed, "completed": completed, "fail_rate": fail_rate}


# ── CLI ───────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description="OMSA Cloud Maintenance Automation")
    subparsers = parser.add_subparsers(dest="command")

    p1 = subparsers.add_parser("ecr-cleanup", help="Delete old ECR images")
    p1.add_argument("--keep", type=int, default=20, help="Images to keep per repo")

    p2 = subparsers.add_parser("log-analysis", help="Analyse CloudWatch logs for errors")
    p2.add_argument("--hours", type=int, default=24, help="Hours of logs to scan")

    subparsers.add_parser("rds-snapshot-check", help="Verify RDS automated snapshots")
    subparsers.add_parser("node-health",         help="EKS node health report")

    p5 = subparsers.add_parser("txn-failure-report", help="Transaction failure rate from CloudWatch")
    p5.add_argument("--region", default="za-johannesburg", help="AWS region filter")
    p5.add_argument("--hours",  type=int, default=24)

    args = parser.parse_args()

    if args.command == "ecr-cleanup":
        ecr_cleanup(args.keep)
    elif args.command == "log-analysis":
        log_analysis(args.hours)
    elif args.command == "rds-snapshot-check":
        ok = rds_snapshot_check()
        sys.exit(0 if ok else 1)
    elif args.command == "node-health":
        issues = node_health()
        sys.exit(0 if not issues else 1)
    elif args.command == "txn-failure-report":
        txn_failure_report(args.region, args.hours)
    else:
        parser.print_help()


if __name__ == "__main__":
    main()
