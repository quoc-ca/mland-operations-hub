# VPC cho môi trường staging
resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = {
    Name        = "${var.name_prefix}-vpc"
    Project     = var.project_name
    Environment = var.environment
  }
}

# Internet Gateway để kết nối ra ngoài Internet
resource "aws_internet_gateway" "gw" {
  vpc_id = aws_vpc.main.id

  tags = {
    Name        = "${var.name_prefix}-igw"
    Project     = var.project_name
    Environment = var.environment
  }
}

# Public Subnet chứa EC2 và Elastic IP
resource "aws_subnet" "public" {
  vpc_id                  = aws_vpc.main.id
  cidr_block              = var.public_subnet_cidr
  availability_zone       = var.availability_zone
  map_public_ip_on_launch = true

  tags = {
    Name        = "${var.name_prefix}-public-subnet"
    Project     = var.project_name
    Environment = var.environment
  }
}

# Bảng định tuyến (Route Table) trỏ 0.0.0.0/0 ra Internet Gateway
resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.gw.id
  }

  tags = {
    Name        = "${var.name_prefix}-public-rt"
    Project     = var.project_name
    Environment = var.environment
  }
}

# Gắn Route Table vào Public Subnet
resource "aws_route_table_association" "public" {
  subnet_id      = aws_subnet.public.id
  route_table_id = aws_route_table.public.id
}
