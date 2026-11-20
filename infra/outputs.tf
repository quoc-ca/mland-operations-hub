output "vpc_id" {
  description = "ID của VPC staging"
  value       = module.network.vpc_id
}

output "public_subnet_id" {
  description = "ID của Public Subnet nơi đặt EC2"
  value       = module.network.public_subnet_id
}

output "security_group_id" {
  description = "ID của Security Group EC2"
  value       = module.security.security_group_id
}

output "ec2_instance_id" {
  description = "ID của máy chủ EC2 Staging"
  value       = module.computes.instance_id
}

output "elastic_ip" {
  description = "Địa chỉ Elastic IP tĩnh công khai (dùng cấu hình DNS trỏ vào Caddy)"
  value       = module.computes.elastic_ip
}

output "lambda_control_api_url" {
  description = "HTTPS Endpoint Function URL để tích hợp vào Web Console infra-management"
  value       = module.lambda.function_url
}
