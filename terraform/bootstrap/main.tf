terraform {
  required_version = ">= 1.10.0"
  required_providers { aws = { source = "hashicorp/aws", version = "~> 5.0" } }
}

provider "aws" { region = "us-east-2" }

variable "github_repository_id" { type = number }
variable "github_owner_id" {
  type    = number
  default = 59511225
}

data "aws_caller_identity" "atual" {}

locals { bucket = "feedback-aluno-tfstate-${data.aws_caller_identity.atual.account_id}" }

resource "aws_s3_bucket" "state" {
  bucket = local.bucket
  tags   = { Projeto = "feedback-aluno", GerenciadoPor = "Terraform" }
}

resource "aws_s3_bucket_versioning" "state" {
  bucket = aws_s3_bucket.state.id
  versioning_configuration { status = "Enabled" }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "state" {
  bucket = aws_s3_bucket.state.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "state" {
  bucket                  = aws_s3_bucket.state.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_iam_openid_connect_provider" "github" {
  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]
  tags           = { Projeto = "feedback-aluno", GerenciadoPor = "Terraform" }
}

resource "aws_iam_role" "github" {
  name = "feedback-aluno-github-deploy"
  assume_role_policy = jsonencode({ Version = "2012-10-17", Statement = [{
    Effect    = "Allow", Action = "sts:AssumeRoleWithWebIdentity",
    Principal = { Federated = aws_iam_openid_connect_provider.github.arn },
    Condition = { StringEquals = {
      "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com",
      "token.actions.githubusercontent.com:sub" = "repo:wandrad3@${var.github_owner_id}/feedback-do-aluno@${var.github_repository_id}:ref:refs/heads/main"
    } }
  }] })
  tags = { Projeto = "feedback-aluno", GerenciadoPor = "Terraform" }
}

resource "aws_iam_role_policy" "deploy" {
  name = "feedback-aluno-deploy"
  role = aws_iam_role.github.id
  policy = jsonencode({ Version = "2012-10-17", Statement = [
    { Effect = "Allow", Action = ["apigateway:*", "cognito-idp:*", "dynamodb:*", "lambda:*", "sns:*", "ses:*", "scheduler:*", "cloudwatch:*", "logs:*"], Resource = "*" },
    { Effect = "Allow", Action = ["iam:CreateRole", "iam:GetRole", "iam:DeleteRole", "iam:UpdateAssumeRolePolicy", "iam:TagRole", "iam:UntagRole", "iam:PassRole", "iam:PutRolePolicy", "iam:GetRolePolicy", "iam:DeleteRolePolicy", "iam:ListRolePolicies", "iam:ListAttachedRolePolicies"], Resource = "arn:aws:iam::${data.aws_caller_identity.atual.account_id}:role/feedback-aluno-*" },
    { Effect = "Allow", Action = ["iam:DeleteOpenIDConnectProvider"], Resource = aws_iam_openid_connect_provider.github.arn },
    { Effect = "Allow", Action = ["s3:ListBucket", "s3:ListBucketVersions", "s3:GetBucketLocation", "s3:DeleteBucket"], Resource = aws_s3_bucket.state.arn },
    { Effect = "Allow", Action = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject", "s3:DeleteObjectVersion"], Resource = "${aws_s3_bucket.state.arn}/feedback-do-aluno/*" }
  ] })
}

output "bucket_state" { value = aws_s3_bucket.state.bucket }
output "role_deploy_arn" { value = aws_iam_role.github.arn }
