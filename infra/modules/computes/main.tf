# Tra cứu AMI Ubuntu 22.04 LTS chính thức từ Canonical
data "aws_ami" "ubuntu" {
  most_recent = true

  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd/ubuntu-jammy-22.04-amd64-server-*"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }

  owners = ["099720109477"] # Canonical
}

# IAM Role cho EC2 để SSM Agent hoạt động
resource "aws_iam_role" "ec2_ssm_role" {
  name = "${var.name_prefix}-ec2-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "ec2.amazonaws.com"
        }
      }
    ]
  })

  tags = {
    Name        = "${var.name_prefix}-ec2-role"
    Project     = var.project_name
    Environment = var.environment
  }
}

# Gán policy quyền chuẩn của Systems Manager
resource "aws_iam_role_policy_attachment" "ssm_core" {
  role       = aws_iam_role.ec2_ssm_role.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

# Instance Profile để gắn IAM Role vào EC2
resource "aws_iam_instance_profile" "ec2_profile" {
  name = "${var.name_prefix}-ec2-profile"
  role = aws_iam_role.ec2_ssm_role.name
}

# Máy chủ EC2 Staging
resource "aws_instance" "staging_ec2" {
  ami                    = data.aws_ami.ubuntu.id
  instance_type          = var.instance_type
  subnet_id              = var.subnet_id
  vpc_security_group_ids = [var.security_group_id]
  iam_instance_profile   = aws_iam_instance_profile.ec2_profile.name

  # Cấu hình đĩa Root EBS gp3 30GB
  root_block_device {
    volume_size           = var.root_volume_size
    volume_type           = "gp3"
    delete_on_termination = true
    encrypted             = true

    tags = {
      Name        = "${var.name_prefix}-root-volume"
      Project     = var.project_name
      Environment = var.environment
    }
  }

  user_data                   = file("${path.module}/user_data.sh")
  user_data_replace_on_change = false

  tags = {
    Name        = "${var.name_prefix}-ec2"
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

# Cấp phát Elastic IP (EIP) tĩnh
resource "aws_eip" "staging_eip" {
  domain = "vpc"

  tags = {
    Name        = "${var.name_prefix}-eip"
    Project     = var.project_name
    Environment = var.environment
  }
}

# Gắn cố định Elastic IP vào máy chủ EC2
resource "aws_eip_association" "staging_eip_assoc" {
  instance_id   = aws_instance.staging_ec2.id
  allocation_id = aws_eip.staging_eip.id
}
