variable "aws_region" {
  description = "Khu vực AWS Region triển khai hạ tầng"
  type        = string
  default     = "ap-southeast-1"
}

variable "name_prefix" {
  description = "Tiền tố đặt tên và tag cho các tài nguyên hạ tầng theo mẫu: mland-test-deploy-*"
  type        = string
  default     = "mland-test-deploy"
}

variable "project_name" {
  description = "Tên định danh dự án"
  type        = string
  default     = "mland"
}

variable "environment" {
  description = "Môi trường triển khai (staging hoặc test-deploy)"
  type        = string
  default     = "test-deploy"
}

variable "instance_type" {
  description = "Loại máy chủ EC2 instance"
  type        = string
  default     = "t3.medium"
}

variable "root_volume_size" {
  description = "Dung lượng ổ đĩa Root EBS của EC2 (GiB)"
  type        = number
  default     = 30
}

variable "cors_allowed_origins" {
  description = "Danh sách URL domain nguồn được phép gọi Lambda Control API (CORS)"
  type        = list(string)
  default     = ["http://localhost:5173"]
}

variable "team_key" {
  description = "Mã bí mật xác nhận nội bộ của team (tùy chọn)"
  type        = string
  default     = ""
  sensitive   = true
}
