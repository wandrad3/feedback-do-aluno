# Manual público de uso da API

Este guia permite testar a API sem chaves IAM da AWS. Peça ao responsável pelo projeto a URL da API, o ID do cliente Cognito e um usuário de teste. **Não publique a senha nem o token recebido.**

## 1. Autenticar

O cliente Cognito está configurado para `USER_PASSWORD_AUTH`. O endpoint público de autenticação é `https://cognito-idp.us-east-2.amazonaws.com/`. Substitua os valores entre `<...>`:

```bash
curl -sS https://cognito-idp.us-east-2.amazonaws.com/ \
  -H 'Content-Type: application/x-amz-json-1.1' \
  -H 'X-Amz-Target: AWSCognitoIdentityProviderService.InitiateAuth' \
  -d '{"AuthFlow":"USER_PASSWORD_AUTH","ClientId":"<CLIENT_ID>","AuthParameters":{"USERNAME":"<USUARIO>","PASSWORD":"<SENHA>"}}'
```

Copie o campo `AuthenticationResult.AccessToken` da resposta para a variável `TOKEN`. O token expira em uma hora. Se receber `NotAuthorizedException`, confira usuário, senha e ID do cliente. Se receber `NEW_PASSWORD_REQUIRED`, peça ao responsável para definir uma senha permanente para o usuário de teste.

## 2. Enviar uma avaliação

```bash
curl -i -X POST '<API_URL>/avaliacao' \
  -H "Authorization: Bearer <ACCESS_TOKEN>" \
  -H 'Content-Type: application/json' \
  -d '{"descricao":"A aula foi clara e objetiva","nota":9}'
```

Resposta `201`:

```json
{"id":"<UUID>","urgencia":"NORMAL","enviadoEm":"<DATA_UTC>"}
```

Para testar o alerta, envie uma nota de 0 a 3:

```bash
curl -i -X POST '<API_URL>/avaliacao' \
  -H "Authorization: Bearer <ACCESS_TOKEN>" \
  -H 'Content-Type: application/json' \
  -d '{"descricao":"Não consegui acompanhar a explicação","nota":2}'
```

A resposta será `201` com `urgencia` igual a `CRITICA`. O SNS envia o aviso ao administrador que confirmou a assinatura. A entrega por e-mail pode levar alguns instantes.

## Respostas e limites

| Situação | HTTP | O que fazer |
| --- | ---: | --- |
| Avaliação criada | 201 | Guarde o ID retornado. |
| JSON inválido, descrição vazia ou maior que 1000 caracteres, nota fora de 0 a 10 ou não inteira | 400 | Corrija o corpo JSON. |
| Token ausente, inválido ou expirado | 401 | Autentique novamente e use o novo access token. |
| Muitas chamadas em pouco tempo | 429 | Aguarde e tente novamente. |

Use somente dados fictícios na descrição. Não envie nomes, e-mails ou outros dados pessoais.

## Exemplo em PowerShell

```powershell
$clientId = '<CLIENT_ID>'
$usuario = '<USUARIO>'
$senha = Read-Host 'Senha do usuário de teste'
$authBody = @{ AuthFlow='USER_PASSWORD_AUTH'; ClientId=$clientId; AuthParameters=@{ USERNAME=$usuario; PASSWORD=$senha } } | ConvertTo-Json -Depth 4
$auth = Invoke-RestMethod 'https://cognito-idp.us-east-2.amazonaws.com/' -Method Post -ContentType 'application/x-amz-json-1.1' -Headers @{ 'X-Amz-Target'='AWSCognitoIdentityProviderService.InitiateAuth' } -Body $authBody
$token = $auth.AuthenticationResult.AccessToken
$body = @{ descricao='A aula foi clara'; nota=9 } | ConvertTo-Json
Invoke-RestMethod '<API_URL>/avaliacao' -Method Post -ContentType 'application/json' -Headers @{ Authorization="Bearer $token" } -Body $body
```
