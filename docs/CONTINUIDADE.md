# Continuidade em uma nova conversa

Atualizado em **9 de novembro de 2026**. Este arquivo registra o estado verificado do projeto para retomar o trabalho sem expor credenciais.

## Objetivo

Concluir a plataforma pública de feedback do aluno em Java 21, AWS `us-east-2`, Terraform e GitHub Actions, com análise SonarQube Cloud, cobertura JaCoCo de 100% e recursos adequados a uma conta AWS de estudante. O repositório é [wandrad3/feedback-do-aluno](https://github.com/wandrad3/feedback-do-aluno).

## Estado verificado

- O código Java, testes, módulo Terraform, workflow, [README](../README.md) e [manual público da API](USO-DA-API.md) estão no repositório. As correções Sonar estão no [PR #1](https://github.com/wandrad3/feedback-do-aluno/pull/1); não fazer merge antes de preparar AWS.
- Java 21 e Maven 3.9.3 estão instalados. Em 9 de novembro, `mvn clean verify` passou com 8 testes e o JaCoCo confirmou 100% de linhas e ramos. Reexecute as verificações antes da entrega final.
- AWS CLI 2.39.5 e Terraform 1.10.5 foram instalados via WinGet. Os comandos `fmt -check`, `init -backend=false` e `validate` passaram em `terraform/bootstrap` e `terraform/app`. Uma sessão de terminal antiga pode precisar ser reiniciada para reconhecer os comandos no `PATH`.
- O [workflow do PR #1](https://github.com/wandrad3/feedback-do-aluno/actions/runs/42636758040) passou por testes, cobertura, Terraform validate e análise Sonar com o token substituído. Deploy e destroy foram ignorados.
- GitHub Actions tem as variables `AWS_ACCOUNT_ID=315205584370`, `AWS_REGION=us-east-2`, `SONAR_ORGANIZATION=wandrad3profile-projects` e `SONAR_PROJECT_KEY=wandrad3profile-projects_wandrad3-feedback-do-aluno`.
- `SONAR_TOKEN` e `EMAIL_ADMIN` estão cadastrados como secrets do GitHub. Nunca coloque valores desses secrets no Git, neste arquivo ou no chat.
- O projeto Sonar público existe com a chave real `wandrad3profile-projects_wandrad3-feedback-do-aluno`. `SONAR_PROJECT_KEY` foi corrigida. O token exposto no chat foi revogado, um novo token foi gerado e o secret `SONAR_TOKEN` foi substituído no GitHub sem divulgar o novo valor. Há outro token antigo de análise na conta Sonar que pode ser revogado pelo usuário na página de segurança, caso não esteja em uso.
- Em 9 de novembro, uma análise local no Sonar passou no segundo envio: 100% de linhas e ramos, zero vulnerabilidades, zero code smells e Quality Gate `OK`. O primeiro envio teve status `NONE`, esperado para a primeira análise de um projeto novo.
- A organização Sonar não está vinculada a uma plataforma DevOps. A API recusou vincular o projeto a `wandrad3/feedback-do-aluno` com a mensagem de que a organização não possui vínculo. É preciso conectar primeiro a organização ao GitHub e depois vincular o projeto ao repositório.
- Ainda não há deploy AWS confirmado na conta de destino. O perfil AWS `default` é de outra conta: não execute Terraform usando esse perfil.
- O AWS Identity Center foi configurado localmente na sessão `feedback-aluno`. O login pelo AWS Builder ID funcionou, mas a API do portal retornou uma lista vazia de contas atribuídas. Assim, `aws sts get-caller-identity --profile feedback-aluno` falhou com `GetRoleCredentials: No access` para a conta de destino. Uma tentativa separada de `aws login` abriu a tela genérica de usuário IAM e foi interrompida; é preciso entrar pelo acesso de projeto da AWS e verificar se a conta **315205584370** aparece no seletor. A captura anterior do console mostrava `AccountFullAccessRole`, mas não comprovava que a sessão estava nessa conta.
- A autorização `aws login --profile feedback-aluno-projeto --region us-east-1` chegou à página de sessão ativa no Chrome, mas a troca do código falhou repetidamente com `CreateOAuth2Token: TOKEN_EXPIRED`. Em 9 de novembro, o relógio local estava cerca de **126 segundos atrasado** em relação aos cabeçalhos `Date` da AWS, GitHub e Cloudflare; `w32tm /query /status` mostrou relógio não sincronizado, e `w32tm /resync` retornou `Acesso negado`. O usuário precisa usar **Configurações → Hora e idioma → Data e hora → Sincronizar agora**. Depois, confirmar que o desvio caiu e repetir o login. A equipe do AWS CLI associou esse mesmo erro à falta de sincronização do relógio em [aws/aws-cli#10330](https://github.com/aws/aws-cli/issues/10330).
- Em 9 de novembro, a automação do Chrome e do navegador integrado ainda retornou `browser access host is unavailable`. A sessão autenticada no Sonar não ficou acessível pela automação.

## Próximos passos

1. **Vincular Sonar e GitHub.** Na [organização Sonar](https://sonarcloud.io/organizations/wandrad3profile-projects/projects), conectar `wandrad3` ao GitHub e dar acesso ao repositório `wandrad3/feedback-do-aluno`. Depois, no projeto, abrir **Administration → General Settings → Repository binding** e selecionar esse repositório. A automação do Chrome continuou indisponível nesta conversa.
2. **Preparar AWS.** Sincronizar o relógio do Windows, repetir `aws login --profile feedback-aluno-projeto --region us-east-1`, escolher a sessão do projeto no Chrome e confirmar com `aws sts get-caller-identity --profile feedback-aluno-projeto` que o ID é **315205584370**. Verificar elegibilidade e uso do Free Tier na conta.
3. **Provisionar e implantar.** Seguir o [README](../README.md) para executar `terraform/bootstrap` na conta correta. Fazer merge do PR #1 em `main` somente depois disso, pois o push aciona o deploy. Confirmar o ID da conta antes do apply, validar a API e a confirmação da inscrição SNS.
5. **Preparar a entrega.** Conferir o [manual público da API](USO-DA-API.md), finalizar um manual privado para os professores com os dados de acesso necessários e guardá-lo fora do repositório público. Após a avaliação, usar o fluxo manual `destroy` do GitHub Actions somente quando a remoção tiver sido autorizada.

## Texto para iniciar a próxima conversa

> Continue o projeto em `D:\wstech\projetos\feedback-do-aluno`. Leia `docs/CONTINUIDADE.md` e `README.md` primeiro. O projeto Sonar público existe, a análise e o PR #1 passaram; `SONAR_TOKEN` e `EMAIL_ADMIN` estão configurados no GitHub. A organização Sonar ainda precisa ser conectada ao GitHub. O login pelo Identity Center não tem contas atribuídas. O relógio do Windows está cerca de 126 segundos atrasado e causa `TOKEN_EXPIRED` no `aws login`; sincronize-o nas Configurações, repita o login pelo acesso de projeto e confirme a conta `315205584370` antes de executar o bootstrap em `us-east-2` e fazer merge em `main`. Mantenha 100% de cobertura e não publique credenciais. Não execute Terraform na conta AWS padrão nem acione `destroy` antes da avaliação.
