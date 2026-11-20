terraform {
  required_version = ">= 1.5.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    archive = {
      source  = "hashicorp/archive"
      version = "~> 2.4"
    }
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = var.project_name
      Environment = var.environment
      ManagedBy   = "terraform"
      Prefix      = var.name_prefix
    }
  }
}

# Module 1: Mạng VPC, Public Subnet và Internet Gateway
module "network" {
  source       = "./modules/network"
  name_prefix  = var.name_prefix
  project_name = var.project_name
  environment  = var.environment
}

# Module 2: Tường lửa Security Group
module "security" {
  source       = "./modules/security"
  name_prefix  = var.name_prefix
  project_name = var.project_name
  environment  = var.environment
  vpc_id       = module.network.vpc_id
}

# Module 3: Máy chủ EC2, EBS gp3, Elastic IP tĩnh & IAM SSM Agent
module "computes" {
  source            = "./modules/computes"
  name_prefix       = var.name_prefix
  project_name      = var.project_name
  environment       = var.environment
  subnet_id         = module.network.public_subnet_id
  security_group_id = module.security.security_group_id
  instance_type     = var.instance_type
  root_volume_size  = var.root_volume_size
}

# Module 4: Lambda Control API & Function URL
module "lambda" {
  source               = "./modules/lambda"
  name_prefix          = var.name_prefix
  project_name         = var.project_name
  environment          = var.environment
  aws_region           = var.aws_region
  ec2_instance_id      = module.computes.instance_id
  cors_allowed_origins = var.cors_allowed_origins
  team_key             = var.team_key
}
