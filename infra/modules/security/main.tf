# Security Group cho EC2 Staging
resource "aws_security_group" "staging_ec2" {
  name        = "${var.name_prefix}-sg"
  description = "Firewall controlling access to the staging EC2 server"
  vpc_id      = var.vpc_id

  # Inbound HTTPS (Caddy TLS)
  ingress {
    description      = "Cho phep HTTPS public vao Caddy"
    from_port        = 443
    to_port          = 443
    protocol         = "tcp"
    cidr_blocks      = ["0.0.0.0/0"]
    ipv6_cidr_blocks = ["::/0"]
  }

  # Inbound HTTP (Redirect HTTPS & ACME challenge)
  ingress {
    description      = "Cho phep HTTP public de redirect sang HTTPS va ACME challenge"
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
