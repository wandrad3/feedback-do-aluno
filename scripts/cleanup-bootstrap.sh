#!/usr/bin/env bash
set -euo pipefail

account="$(aws sts get-caller-identity --query Account --output text)"
test "$account" = "158363271463"
bucket="feedback-aluno-tfstate-${account}"
role="feedback-aluno-github-deploy"

# O bucket tem versionamento: remover cada versão e marcador antes de apagar o bucket.
while true; do
  versions="$(aws s3api list-object-versions --bucket "$bucket" --output json | jq -c '[.Versions[]?, .DeleteMarkers[]?] | map({Key,VersionId})')"
  test "$versions" != "[]" || break
  aws s3api delete-objects --bucket "$bucket" --delete "$(jq -cn --argjson objects "$versions" '{Objects:$objects,Quiet:true}')" >/dev/null
done
aws s3api delete-bucket --bucket "$bucket" --region us-east-2
aws iam delete-role-policy --role-name "$role" --policy-name feedback-aluno-deploy
aws iam delete-role --role-name "$role"
aws iam delete-open-id-connect-provider --open-id-connect-provider-arn "arn:aws:iam::${account}:oidc-provider/token.actions.githubusercontent.com"
