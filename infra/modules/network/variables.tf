variable "name_prefix" {
  description = "Tiền tố đặt tên và tag cho tài nguyên: mland-test-deploy-*"
  type        = string
  default     = "mland-test-deploy"
}

variable "project_name" {
  description = "Tên định danh dự án"
  type        = string
}

variable "environment" {
  description = "Môi trường triển khai"
  type        = string
}

variable "vpc_cidr" {
  description = "Dải CIDR block cho VPC"
  type        = string
  default     = "10.0.0.0/16"
}

variable "public_subnet_cidr" {
  description = "Dải CIDR block cho Public Subnet"
  type        = string
  default     = "10.0.1.0/24"
}

variable "availability_zone" {
  description = "Availability Zone để đặt Public Subnet"
  type        = string
  default     = "ap-southeast-1a"
}
