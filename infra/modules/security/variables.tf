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

variable "vpc_id" {
  description = "ID của VPC nơi áp dụng Security Group"
  type        = string
}
