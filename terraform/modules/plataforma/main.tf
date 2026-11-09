locals { tags = { Projeto = var.nome, GerenciadoPor = "Terraform" } }

resource "aws_dynamodb_table" "avaliacoes" {
  name         = "${var.nome}-avaliacoes"
  billing_mode = "PROVISIONED"
  read_capacity  = 1
  write_capacity = 1
  hash_key  = "semana"
  range_key = "id"
  attribute {
    name = "semana"
    type = "S"
  }
  attribute {
    name = "id"
    type = "S"
  }
  stream_enabled   = true
  stream_view_type = "NEW_IMAGE"
  server_side_encryption { enabled = true }
  point_in_time_recovery { enabled = false }
  tags = local.tags
}

resource "aws_sns_topic" "notificacoes" {
  name = "${var.nome}-notificacoes"
  tags = local.tags
}

resource "aws_sns_topic_subscription" "admin" {
  topic_arn = aws_sns_topic.notificacoes.arn
  protocol  = "email"
  endpoint  = var.email_admin
}

resource "aws_cognito_user_pool" "alunos" {
  name                = "${var.nome}-alunos"
  user_pool_tier      = "LITE"
  deletion_protection = "INACTIVE"
  admin_create_user_config { allow_admin_create_user_only = true }
  password_policy {
    minimum_length    = 12
    require_lowercase = true
    require_uppercase = true
    require_numbers   = true
    require_symbols   = true
  }
  tags = local.tags
}

resource "aws_cognito_user_pool_client" "api" {
  name                          = "${var.nome}-api"
  user_pool_id                  = aws_cognito_user_pool.alunos.id
  generate_secret               = false
  explicit_auth_flows           = ["ALLOW_USER_PASSWORD_AUTH", "ALLOW_REFRESH_TOKEN_AUTH"]
  prevent_user_existence_errors = "ENABLED"
  access_token_validity         = 1
  id_token_validity             = 1
  token_validity_units {
    access_token = "hours"
    id_token     = "hours"
  }
}

resource "aws_iam_role" "lambda" {
  for_each = toset(["receber", "notificar", "relatorio"])
  name     = "${var.nome}-${each.key}"
  assume_role_policy = jsonencode({ Version = "2012-10-17", Statement = [{ Effect = "Allow", Principal = { Service = "lambda.amazonaws.com" }, Action = "sts:AssumeRole" }] })
  tags = local.tags
}

resource "aws_iam_role_policy" "receber" {
  name = "gravar-avaliacoes"
  role = aws_iam_role.lambda["receber"].id
  policy = jsonencode({ Version = "2012-10-17", Statement = [{ Effect = "Allow", Action = ["dynamodb:PutItem"], Resource = aws_dynamodb_table.avaliacoes.arn }] })
}

resource "aws_iam_role_policy" "notificar" {
  name = "ler-stream-e-publicar"
  role = aws_iam_role.lambda["notificar"].id
  policy = jsonencode({ Version = "2012-10-17", Statement = [
    { Effect = "Allow", Action = ["dynamodb:DescribeStream", "dynamodb:GetRecords", "dynamodb:GetShardIterator", "dynamodb:ListStreams"], Resource = aws_dynamodb_table.avaliacoes.stream_arn },
    { Effect = "Allow", Action = ["sns:Publish"], Resource = aws_sns_topic.notificacoes.arn }
  ] })
}

resource "aws_iam_role_policy" "relatorio" {
  name = "consultar-e-publicar"
  role = aws_iam_role.lambda["relatorio"].id
  policy = jsonencode({ Version = "2012-10-17", Statement = [
    { Effect = "Allow", Action = ["dynamodb:Query"], Resource = aws_dynamodb_table.avaliacoes.arn },
    { Effect = "Allow", Action = ["sns:Publish"], Resource = aws_sns_topic.notificacoes.arn }
  ] })
}

resource "aws_cloudwatch_log_group" "lambda" {
  for_each          = aws_iam_role.lambda
  name              = "/aws/lambda/${var.nome}-${each.key}"
  retention_in_days = 7
  tags              = local.tags
}

resource "aws_iam_role_policy" "logs" {
  for_each = aws_iam_role.lambda
  name     = "escrever-logs"
  role     = each.value.id
  policy = jsonencode({ Version = "2012-10-17", Statement = [{ Effect = "Allow", Action = ["logs:CreateLogStream", "logs:PutLogEvents"], Resource = "${aws_cloudwatch_log_group.lambda[each.key].arn}:*" }] })
}

resource "aws_lambda_function" "funcao" {
  for_each         = { receber = "ReceberAvaliacao", notificar = "NotificarUrgencia", relatorio = "RelatorioSemanal" }
  function_name    = "${var.nome}-${each.key}"
  filename         = var.artefato_jar
  source_code_hash = filebase64sha256(var.artefato_jar)
  runtime          = "java21"
  handler          = "br.com.wstech.feedback.${each.value}::handleRequest"
  role             = aws_iam_role.lambda[each.key].arn
  memory_size      = 512
  timeout          = each.key == "relatorio" ? 60 : 15
  architectures    = ["x86_64"]
  environment {
    variables = {
      TABELA_AVALIACOES  = aws_dynamodb_table.avaliacoes.name
      TOPICO_NOTIFICACOES = aws_sns_topic.notificacoes.arn
    }
  }
  depends_on = [aws_iam_role_policy.logs, aws_cloudwatch_log_group.lambda]
  tags = local.tags
}

resource "aws_lambda_event_source_mapping" "stream" {
  event_source_arn        = aws_dynamodb_table.avaliacoes.stream_arn
  function_name           = aws_lambda_function.funcao["notificar"].arn
  starting_position       = "LATEST"
  batch_size              = 1
  maximum_retry_attempts  = 2
  function_response_types = []
  depends_on              = [aws_iam_role_policy.notificar]
}

resource "aws_iam_role" "scheduler" {
  name = "${var.nome}-scheduler"
  assume_role_policy = jsonencode({ Version = "2012-10-17", Statement = [{ Effect = "Allow", Principal = { Service = "scheduler.amazonaws.com" }, Action = "sts:AssumeRole" }] })
  tags = local.tags
}

resource "aws_iam_role_policy" "scheduler" {
  name = "invocar-relatorio"
  role = aws_iam_role.scheduler.id
  policy = jsonencode({ Version = "2012-10-17", Statement = [{ Effect = "Allow", Action = ["lambda:InvokeFunction"], Resource = aws_lambda_function.funcao["relatorio"].arn }] })
}

resource "aws_scheduler_schedule" "semanal" {
  name                         = "${var.nome}-semanal"
  schedule_expression          = "cron(0 9 ? * MON *)"
  schedule_expression_timezone = "America/Sao_Paulo"
  flexible_time_window { mode = "OFF" }
  target {
    arn      = aws_lambda_function.funcao["relatorio"].arn
    role_arn = aws_iam_role.scheduler.arn
    input    = "{}"
  }
  depends_on = [aws_iam_role_policy.scheduler]
}

resource "aws_apigatewayv2_api" "api" {
  name          = "${var.nome}-api"
  protocol_type = "HTTP"
  tags          = local.tags
}

resource "aws_apigatewayv2_authorizer" "cognito" {
  api_id           = aws_apigatewayv2_api.api.id
  authorizer_type  = "JWT"
  name             = "alunos-cognito"
  identity_sources = ["$request.header.Authorization"]
  jwt_configuration {
    audience = [aws_cognito_user_pool_client.api.id]
    issuer   = "https://cognito-idp.${var.regiao}.amazonaws.com/${aws_cognito_user_pool.alunos.id}"
  }
}

resource "aws_apigatewayv2_integration" "receber" {
  api_id                 = aws_apigatewayv2_api.api.id
  integration_type       = "AWS_PROXY"
  integration_uri        = aws_lambda_function.funcao["receber"].invoke_arn
  payload_format_version = "2.0"
}

resource "aws_apigatewayv2_integration" "openapi" {
  api_id                 = aws_apigatewayv2_api.api.id
  integration_type       = "HTTP_PROXY"
  integration_method     = "GET"
  integration_uri        = "https://raw.githubusercontent.com/wandrad3/feedback-do-aluno/main/docs/openapi.json"
  payload_format_version = "1.0"
  response_parameters {
    status_code = 200
    mappings = {
      "overwrite:header.Content-Type" = "application/json"
    }
  }
}

resource "aws_apigatewayv2_route" "openapi" {
  api_id    = aws_apigatewayv2_api.api.id
  route_key = "GET /openapi.json"
  target    = "integrations/${aws_apigatewayv2_integration.openapi.id}"
}

resource "aws_apigatewayv2_route" "avaliacao" {
  api_id             = aws_apigatewayv2_api.api.id
  route_key          = "POST /avaliacao"
  target             = "integrations/${aws_apigatewayv2_integration.receber.id}"
  authorization_type = "JWT"
  authorizer_id      = aws_apigatewayv2_authorizer.cognito.id
}

resource "aws_apigatewayv2_stage" "default" {
  api_id      = aws_apigatewayv2_api.api.id
  name        = "$default"
  auto_deploy = true
  default_route_settings {
    throttling_burst_limit = 10
    throttling_rate_limit  = 5
  }
  tags = local.tags
}

resource "aws_lambda_permission" "api" {
  statement_id  = "AllowApiGateway"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.funcao["receber"].function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.api.execution_arn}/*/POST/avaliacao"
}

resource "aws_cloudwatch_metric_alarm" "lambda_erros" {
  for_each            = aws_lambda_function.funcao
  alarm_name          = "${var.nome}-${each.key}-erros"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "Errors"
  namespace           = "AWS/Lambda"
  period              = 300
  statistic           = "Sum"
  threshold           = 0
  treat_missing_data  = "notBreaching"
  dimensions          = { FunctionName = each.value.function_name }
  alarm_actions       = [aws_sns_topic.notificacoes.arn]
  tags                = local.tags
}
