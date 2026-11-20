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

variable "subnet_id" {
  description = "ID của Public Subnet để đặt EC2"
  type        = string
}

variable "security_group_id" {
  description = "ID của Security Group áp dụng cho EC2"
  type        = string
}

variable "instance_type" {
  description = "Loại máy chủ EC2 instance"
  type        = string
  default     = "t3.medium"
}

variable "root_volume_size" {
  description = "Dung lượng ổ đĩa Root EBS (GiB)"
  type        = number
  default     = 30
}
