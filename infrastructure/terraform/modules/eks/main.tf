# infrastructure/terraform/modules/eks/main.tf
# EKS cluster for OMSA Integrated Financial Services Platform
# Deployed in af-south-1 (Cape Town) — primary region

locals {
  tags = merge(var.tags, { Module = "eks", ManagedBy = "terraform" })
}

resource "aws_iam_role" "cluster" {
  name = "${var.cluster_name}-cluster-role"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{ Action = "sts:AssumeRole", Effect = "Allow",
      Principal = { Service = "eks.amazonaws.com" } }]
  })
  tags = local.tags
}
resource "aws_iam_role_policy_attachment" "cluster_policy" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSClusterPolicy"
  role       = aws_iam_role.cluster.name
}

resource "aws_iam_role" "nodes" {
  name = "${var.cluster_name}-nodes-role"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{ Action = "sts:AssumeRole", Effect = "Allow",
      Principal = { Service = "ec2.amazonaws.com" } }]
  })
  tags = local.tags
}
resource "aws_iam_role_policy_attachment" "nodes_worker" { policy_arn = "arn:aws:iam::aws:policy/AmazonEKSWorkerNodePolicy";           role = aws_iam_role.nodes.name }
resource "aws_iam_role_policy_attachment" "nodes_cni"    { policy_arn = "arn:aws:iam::aws:policy/AmazonEKS_CNI_Policy";               role = aws_iam_role.nodes.name }
resource "aws_iam_role_policy_attachment" "nodes_ecr"    { policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"; role = aws_iam_role.nodes.name }

resource "aws_kms_key" "eks" {
  description             = "${var.cluster_name} secrets encryption"
  deletion_window_in_days = 7
  enable_key_rotation     = true
  tags                    = local.tags
}

resource "aws_eks_cluster" "main" {
  name     = var.cluster_name
  version  = var.kubernetes_version
  role_arn = aws_iam_role.cluster.arn

  vpc_config {
    subnet_ids              = concat(var.public_subnet_ids, var.private_subnet_ids)
    security_group_ids      = [var.cluster_sg_id]
    endpoint_private_access = true
    endpoint_public_access  = var.public_access
    public_access_cidrs     = var.public_access_cidrs
  }

  enabled_cluster_log_types = ["api", "audit", "authenticator", "controllerManager", "scheduler"]

  encryption_config {
    resources = ["secrets"]
    provider  { key_arn = aws_kms_key.eks.arn }
  }

  tags = merge(local.tags, { Name = var.cluster_name })
  depends_on = [aws_iam_role_policy_attachment.cluster_policy]
}

resource "aws_eks_node_group" "fintech" {
  cluster_name    = aws_eks_cluster.main.name
  node_group_name = "${var.cluster_name}-fintech"
  node_role_arn   = aws_iam_role.nodes.arn
  subnet_ids      = var.private_subnet_ids
  instance_types  = var.node_instance_types
  capacity_type   = "ON_DEMAND"

  scaling_config {
    desired_size = var.desired_nodes
    min_size     = var.min_nodes
    max_size     = var.max_nodes
  }
  update_config { max_unavailable_percentage = 20 }
  labels = { role = "fintech-workloads", compliance = "pci-dss" }

  tags = merge(local.tags, {
    Name                                              = "${var.cluster_name}-nodes"
    "k8s.io/cluster-autoscaler/enabled"              = "true"
    "k8s.io/cluster-autoscaler/${var.cluster_name}"  = "owned"
  })
  depends_on = [aws_iam_role_policy_attachment.nodes_worker, aws_iam_role_policy_attachment.nodes_cni]
}

resource "aws_eks_addon" "vpc_cni"    { cluster_name = aws_eks_cluster.main.name; addon_name = "vpc-cni";    resolve_conflicts_on_update = "OVERWRITE"; tags = local.tags }
resource "aws_eks_addon" "coredns"    { cluster_name = aws_eks_cluster.main.name; addon_name = "coredns";    resolve_conflicts_on_update = "OVERWRITE"; tags = local.tags; depends_on = [aws_eks_node_group.fintech] }
resource "aws_eks_addon" "kube_proxy" { cluster_name = aws_eks_cluster.main.name; addon_name = "kube-proxy"; resolve_conflicts_on_update = "OVERWRITE"; tags = local.tags }
resource "aws_eks_addon" "ebs_csi"   { cluster_name = aws_eks_cluster.main.name; addon_name = "aws-ebs-csi-driver"; resolve_conflicts_on_update = "OVERWRITE"; tags = local.tags }

data "tls_certificate" "eks" { url = aws_eks_cluster.main.identity[0].oidc[0].issuer }
resource "aws_iam_openid_connect_provider" "eks" {
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = [data.tls_certificate.eks.certificates[0].sha1_fingerprint]
  url             = aws_eks_cluster.main.identity[0].oidc[0].issuer
  tags            = local.tags
}

resource "aws_ecr_repository" "services" {
  for_each             = toset(["transaction-service", "policy-service"])
  name                 = "omsa/${each.key}"
  image_tag_mutability = "MUTABLE"
  image_scanning_configuration { scan_on_push = true }
  encryption_configuration { encryption_type = "KMS"; kms_key = aws_kms_key.eks.arn }
  tags = merge(local.tags, { Name = each.key })
}

output "cluster_endpoint"  { value = aws_eks_cluster.main.endpoint }
output "cluster_name"      { value = aws_eks_cluster.main.name }
output "oidc_provider_arn" { value = aws_iam_openid_connect_provider.eks.arn }
output "ecr_urls"          { value = { for k, v in aws_ecr_repository.services : k => v.repository_url } }
