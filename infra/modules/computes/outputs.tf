output "instance_id" {
  description = "ID của EC2 instance staging"
  value       = aws_instance.staging_ec2.id
}

output "instance_arn" {
  description = "ARN của EC2 instance staging"
  value       = aws_instance.staging_ec2.arn
}

output "elastic_ip" {
  description = "Địa chỉ Elastic IP công khai cố định của EC2"
  value       = aws_eip.staging_eip.public_ip
}

output "elastic_ip_allocation_id" {
  description = "Allocation ID của Elastic IP"
  value       = aws_eip.staging_eip.id
}
