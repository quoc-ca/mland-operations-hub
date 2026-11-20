output "security_group_id" {
  description = "ID của Security Group EC2"
  value       = aws_security_group.staging_ec2.id
}

output "security_group_name" {
  description = "Tên của Security Group EC2"
  value       = aws_security_group.staging_ec2.name
}
