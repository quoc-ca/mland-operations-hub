output "function_name" {
  description = "Tên hàm Lambda Control API"
  value       = aws_lambda_function.control_api.function_name
}

output "function_arn" {
  description = "ARN của hàm Lambda Control API"
  value       = aws_lambda_function.control_api.arn
}

output "function_url" {
  description = "HTTPS Endpoint Function URL dùng để gọi Lambda từ web console"
  value       = aws_lambda_function_url.control_api_url.function_url
}
