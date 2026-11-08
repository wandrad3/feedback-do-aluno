output "api_url" { value = aws_apigatewayv2_api.api.api_endpoint }
output "cognito_client_id" { value = aws_cognito_user_pool_client.api.id }
output "cognito_user_pool_id" { value = aws_cognito_user_pool.alunos.id }
output "topico_sns_arn" { value = aws_sns_topic.notificacoes.arn }
