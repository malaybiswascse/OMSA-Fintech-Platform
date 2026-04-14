terraform {
  required_version = ">= 1.6.0"
  required_providers {
    aws    = { source = "hashicorp/aws",  version = "~> 5.0" }
    random = { source = "hashicorp/random", version = "~> 3.6" }
    tls    = { source = "hashicorp/tls",  version = "~> 4.0" }
  }
  backend "s3" {
    bucket         = "omsa-terraform-state-prod"
    key            = "prod/terraform.tfstate"
    region         = "af-south-1"
    dynamodb_table = "omsa-terraform-lock"
    encrypt        = true
  }
}

provider "aws" {
  region = "af-south-1"
  default_tags {
    tags = {
      Project     = "omsa-fintech-platform"
      Environment = "prod"
      ManagedBy   = "terraform"
      Owner       = "devops-team"
      Compliance  = "pci-dss"
    }
  }
}

module "vpc" {
  source       = "../../modules/vpc"
  cluster_name = "omsa-fintech-prod"
  vpc_cidr     = "10.0.0.0/16"
  az_count     = 3
  tags         = {}
}

module "eks" {
  source              = "../../modules/eks"
  cluster_name        = "omsa-fintech-prod"
  kubernetes_version  = "1.28"
  public_subnet_ids   = module.vpc.public_subnet_ids
  private_subnet_ids  = module.vpc.private_subnet_ids
  cluster_sg_id       = module.vpc.eks_cluster_sg_id
  node_instance_types = ["t3.large"]
  desired_nodes       = 3
  min_nodes           = 3
  max_nodes           = 20
  public_access       = true
  public_access_cidrs = ["0.0.0.0/0"]
  tags                = {}
}

module "rds" {
  source                = "../../modules/rds"
  cluster_name          = "omsa-fintech-prod"
  vpc_id                = module.vpc.vpc_id
  private_subnet_ids    = module.vpc.private_subnet_ids
  eks_nodes_sg_id       = module.vpc.eks_nodes_sg_id
  db_instance_class     = "db.r6g.large"
  allocated_storage     = 500
  multi_az              = true
  deletion_protection   = true
  backup_retention_days = 35
  create_read_replica   = true
  tags                  = {}
}

# External Secrets Operator — syncs AWS Secrets Manager → Kubernetes Secrets
resource "helm_release" "external_secrets" {
  name       = "external-secrets"
  repository = "https://charts.external-secrets.io"
  chart      = "external-secrets"
  version    = "0.9.13"
  namespace  = "external-secrets"
  create_namespace = true
}

# ArgoCD
resource "helm_release" "argocd" {
  name             = "argocd"
  repository       = "https://argoproj.github.io/argo-helm"
  chart            = "argo-cd"
  version          = "6.4.0"
  namespace        = "argocd"
  create_namespace = true

  set { name = "server.service.type"; value = "LoadBalancer" }
  set { name = "server.ingress.enabled"; value = "true" }
  set { name = "configs.params.server.insecure"; value = "false" }
}
