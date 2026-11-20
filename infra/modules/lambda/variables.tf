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

variable "aws_region" {
  description = "Khu vực AWS Region triển khai"
  type        = string
  default     = "ap-southeast-1"
}

variable "ec2_instance_id" {
  description = "ID của máy chủ EC2 Staging cần điều khiển"
  type        = string
}

variable "cors_allowed_origins" {
  description = "Danh sách domain được phép gọi Lambda Function URL (CORS)"
  type        = list(string)
  default     = ["http://localhost:5173"]
}

variable "team_key" {
  description = "Mã bí mật xác nhận nội bộ của team (tùy chọn)"
  type        = string
  default     = ""
  sensitive   = true
}
