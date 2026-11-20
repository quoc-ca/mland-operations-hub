# Đóng gói tự động mã nguồn Node.js handler thành file ZIP
data "archive_file" "lambda_payload" {
  type        = "zip"
  source_dir  = "${path.module}/src"
  output_path = "${path.module}/dist/control-api.zip"
}

# IAM Role thực thi cho Lambda
resource "aws_iam_role" "lambda_role" {
  name = "${var.name_prefix}-lambda-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "lambda.amazonaws.com"
        }
      }
    ]
  })

  tags = {
    Name        = "${var.name_prefix}-lambda-role"
    Project     = var.project_name
    Environment = var.environment
  }
}

# Policy cấp quyền điều khiển EC2 và SSM tối thiểu
resource "aws_iam_policy" "lambda_control_policy" {
  name        = "${var.name_prefix}-lambda-policy"
  description = "Quyền tối thiểu cho Lambda để bật/tắt EC2 và gửi lệnh SSM"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "ec2:StartInstances",
          "ec2:StopInstances",
          "ec2:DescribeInstances",
          "ec2:DescribeInstanceStatus"
        ]
        Resource = "*"
      },
      {
        Effect = "Allow"
        Action = [
          "ssm:SendCommand",
          "ssm:GetCommandInvocation",
          "ssm:DescribeInstanceInformation",
          "ssm:ListCommands"
        ]
        Resource = "*"
      }
    ]
  })
}

# Gán Policy điều khiển EC2/SSM vào Lambda Role
resource "aws_iam_role_policy_attachment" "lambda_control_attach" {
  role       = aws_iam_role.lambda_role.name
  policy_arn = aws_iam_policy.lambda_control_policy.arn
}

# Gán Policy ghi logs cơ bản của AWS Lambda
resource "aws_iam_role_policy_attachment" "lambda_basic_execution" {
  role       = aws_iam_role.lambda_role.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

# CloudWatch Log Group để lưu trữ nhật ký thực thi của Lambda
resource "aws_cloudwatch_log_group" "lambda_logs" {
  name              = "/aws/lambda/${var.name_prefix}-control-api"
  retention_in_days = 7

  tags = {
    Name        = "${var.name_prefix}-lambda-logs"
    Project     = var.project_name
    Environment = var.environment
  }
}

# Hàm AWS Lambda Control API
resource "aws_lambda_function" "control_api" {
  function_name    = "${var.name_prefix}-control-api"
  role             = aws_iam_role.lambda_role.arn
  handler          = "index.handler"
  runtime          = "nodejs20.x"
  filename         = data.archive_file.lambda_payload.output_path
  source_code_hash = data.archive_file.lambda_payload.output_base64sha256
  timeout          = 30
  memory_size      = 256

  environment {
    variables = {
      STAGING_EC2_ID  = var.ec2_instance_id
      ALLOWED_ORIGINS = join(",", var.cors_allowed_origins)
      TEAM_KEY        = var.team_key
    }
  }

  depends_on = [
    aws_iam_role_policy_attachment.lambda_control_attach,
    aws_iam_role_policy_attachment.lambda_basic_execution,
    aws_cloudwatch_log_group.lambda_logs
  ]

  tags = {
    Name        = "${var.name_prefix}-control-api"
    Project     = var.project_name
    Environment = var.environment
  }
}

# Mở cổng HTTPS endpoint qua Lambda Function URL có kiểm soát nguồn CORS
resource "aws_lambda_function_url" "control_api_url" {
  function_name      = aws_lambda_function.control_api.function_name
  authorization_type = "NONE"

  cors {
    allow_credentials = false
    allow_headers     = ["content-type", "authorization", "x-team-key"]
    allow_methods     = ["GET", "POST", "OPTIONS"]
    allow_origins     = var.cors_allowed_origins
    max_age           = 3600
  }
}
