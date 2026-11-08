terraform {
  required_version = ">= 1.10.0"
  required_providers { aws = { source = "hashicorp/aws", version = "~> 5.0" } }
  backend "s3" {
    key          = "feedback-do-aluno/app.tfstate"
    use_lockfile = true
    encrypt      = true
  }
}

provider "aws" { region = "us-east-2" }

variable "email_admin" { type = string }
variable "artefato_jar" {
  type    = string
  default = "../../target/feedback-do-aluno.jar"
}

module "plataforma" {
  source       = "../modules/plataforma"
  nome         = "feedback-aluno"
  regiao       = "us-east-2"
  email_admin  = var.email_admin
  artefato_jar = var.artefato_jar
}

output "api_url" { value = module.plataforma.api_url }
output "cognito_client_id" { value = module.plataforma.cognito_client_id }
output "cognito_user_pool_id" { value = module.plataforma.cognito_user_pool_id }
output "topico_sns_arn" { value = module.plataforma.topico_sns_arn }
