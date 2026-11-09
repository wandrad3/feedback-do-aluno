# Manual de uso e testes da API

A aplicação está na conta AWS **315205584370**, região **us-east-2**. Para testar, use um usuário Cognito fornecido pelo responsável pelo projeto. Não são necessárias chaves IAM. **Não publique a senha nem o token recebido.**

| Recurso | Endereço ou valor |
| --- | --- |
| Swagger UI | [Abrir documentação interativa](https://vilam86911.execute-api.us-east-2.amazonaws.com/swagger-ui.html) |
| OpenAPI 3.0 | [Abrir contrato JSON](https://vilam86911.execute-api.us-east-2.amazonaws.com/openapi.json) |
| API | `https://vilam86911.execute-api.us-east-2.amazonaws.com` |
| Cliente Cognito | `7l8o6012lm01h37ositatnsn36` |
| Collection Postman | [Baixar arquivo](feedback-do-aluno.postman_collection.json) |

## 1. Obter um AccessToken

**No Swagger UI:** abra [swagger-ui.html](https://vilam86911.execute-api.us-east-2.amazonaws.com/swagger-ui.html), informe usuário e senha no quadro **Autenticação Cognito** e clique em **Entrar e autorizar**. A página envia a senha diretamente ao Cognito por HTTPS e usa o AccessToken retornado para autorizar o `POST /avaliacao`. Ela limpa o campo de senha após o login e não guarda o token após atualizar ou fechar a página.

**No Postman:** importe a collection, preencha as variáveis `usuario` e `senha` com as credenciais recebidas e execute **01 - Autenticar no Cognito**. A requisição usa `USER_PASSWORD_AUTH` e guarda `AuthenticationResult.AccessToken` na variável `access_token` da collection. Não exporte a collection depois de preenchê-la com credenciais ou token.

**No terminal:** faça a mesma autenticação no endpoint público `https://cognito-idp.us-east-2.amazonaws.com/`. Substitua `<USUARIO>` e `<SENHA>`; o ID do cliente já está preenchido:

```bash
curl -sS https://cognito-idp.us-east-2.amazonaws.com/ \
  -H 'Content-Type: application/x-amz-json-1.1' \
  -H 'X-Amz-Target: AWSCognitoIdentityProviderService.InitiateAuth' \
  -d '{"AuthFlow":"USER_PASSWORD_AUTH","ClientId":"7l8o6012lm01h37ositatnsn36","AuthParameters":{"USERNAME":"<USUARIO>","PASSWORD":"<SENHA>"}}'
```

Copie `AuthenticationResult.AccessToken` da resposta. O token expira em uma hora. Se receber `NotAuthorizedException`, confira usuário e senha. Se receber `NEW_PASSWORD_REQUIRED`, peça ao responsável para definir uma senha permanente para o usuário de teste.

## 2. Testar no Swagger UI

1. Abra [swagger-ui.html](https://vilam86911.execute-api.us-east-2.amazonaws.com/swagger-ui.html). A página deve mostrar o quadro **Autenticação Cognito** e apenas a operação `POST /avaliacao`. Se carregar uma versão antiga, use **Ctrl+F5**.
2. Entre no quadro de autenticação conforme o passo 1. A mensagem **Autenticado** indica que o token foi aplicado ao Swagger UI. Se já tiver um AccessToken, pode clicar em **Authorize** e colá-lo sem o prefixo `Bearer`.
3. Expanda `POST /avaliacao`, clique em **Try it out**, informe um JSON fictício como `{"descricao":"A aula foi clara e objetiva","nota":9}` e clique em **Execute**.
4. Confira a resposta `201` com `id`, `urgencia: "NORMAL"` e `enviadoEm`. Para testar a validação, envie uma nota `11`: a resposta esperada é `400`.

Uma nota de **0 a 3** retorna `urgencia: "CRITICA"` e envia um alerta real ao administrador. Use esse caso somente quando quiser validar o alerta.

## 3. Testar no Postman

Após **01 - Autenticar no Cognito**, execute **03 - Avaliação normal**, **05 - Nota inválida** e **06 - Sem token**. A requisição **04 - Avaliação crítica** envia um alerta real. A requisição **02 - Consultar OpenAPI** verifica o contrato publicado. Os testes da collection verificam os códigos HTTP e os campos principais das respostas.

## 4. Testar com curl

```bash
curl -i -X POST 'https://vilam86911.execute-api.us-east-2.amazonaws.com/avaliacao' \
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
curl -i -X POST 'https://vilam86911.execute-api.us-east-2.amazonaws.com/avaliacao' \
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
$clientId = '7l8o6012lm01h37ositatnsn36'
$usuario = '<USUARIO>'
$senhaSegura = Read-Host 'Senha do usuário de teste' -AsSecureString
$senha = [System.Net.NetworkCredential]::new('', $senhaSegura).Password
$authBody = @{ AuthFlow='USER_PASSWORD_AUTH'; ClientId=$clientId; AuthParameters=@{ USERNAME=$usuario; PASSWORD=$senha } } | ConvertTo-Json -Depth 4
$auth = Invoke-RestMethod 'https://cognito-idp.us-east-2.amazonaws.com/' -Method Post -ContentType 'application/x-amz-json-1.1' -Headers @{ 'X-Amz-Target'='AWSCognitoIdentityProviderService.InitiateAuth' } -Body $authBody
$token = $auth.AuthenticationResult.AccessToken
$body = @{ descricao='A aula foi clara'; nota=9 } | ConvertTo-Json
Invoke-RestMethod 'https://vilam86911.execute-api.us-east-2.amazonaws.com/avaliacao' -Method Post -ContentType 'application/json' -Headers @{ Authorization="Bearer $token" } -Body $body
```
