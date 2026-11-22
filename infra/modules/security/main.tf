# Security Group cho EC2 Staging
resource "aws_security_group" "staging_ec2" {
  name        = "${var.name_prefix}-sg"
  description = "Firewall controlling access to the staging EC2 server"
  vpc_id      = var.vpc_id

  # Vercel proxies API requests to the backend over HTTP on the EIP.
  ingress {
    description      = "Public HTTP backend endpoint used by the Vercel API proxy"
    from_port        = 80
    to_port          = 80
    protocol         = "tcp"
    cidr_blocks      = ["0.0.0.0/0"]
    ipv6_cidr_blocks = ["::/0"]
  }

  # Outbound All (Cho SSM Agent, Git pull, Docker Hub, Package update)
  egress {
    description      = "Cho phep outbound toan bo ra Internet cho SSM, GitHub, Docker"
    from_port        = 0
    to_port          = 0
    protocol         = "-1"
    cidr_blocks      = ["0.0.0.0/0"]
    ipv6_cidr_blocks = ["::/0"]
  }

  tags = {
    Name        = "${var.name_prefix}-sg"
    Project     = var.project_name
    Environment = var.environment
  }
}
