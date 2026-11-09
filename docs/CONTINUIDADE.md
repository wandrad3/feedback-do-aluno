# Continuidade em uma nova conversa

Atualizado em **9 de novembro de 2026**. Este arquivo registra o estado verificado do projeto para retomar o trabalho sem expor credenciais.

## Objetivo

Concluir a plataforma pública de feedback do aluno em Java 21, AWS `us-east-2`, Terraform e GitHub Actions, com análise SonarQube Cloud, cobertura JaCoCo de 100% e recursos adequados a uma conta AWS de estudante. O repositório é [wandrad3/feedback-do-aluno](https://github.com/wandrad3/feedback-do-aluno).

## Estado verificado

- O código Java, testes, módulo Terraform, workflow, [README](../README.md) e [manual público da API](USO-DA-API.md) estão no repositório. As correções Sonar estão no [PR #1](https://github.com/wandrad3/feedback-do-aluno/pull/1); não fazer merge antes de preparar AWS.
- Java 21 e Maven 3.9.3 estão instalados. Em 9 de novembro, `mvn clean verify` passou com 8 testes e o JaCoCo confirmou 100% de linhas e ramos. Reexecute as verificações antes da entrega final.
- AWS CLI 2.39.5 e Terraform 1.10.5 foram instalados via WinGet. Os comandos `fmt -check`, `init -backend=false` e `validate` passaram em `terraform/bootstrap` e `terraform/app`. Uma sessão de terminal antiga pode precisar ser reiniciada para reconhecer os comandos no `PATH`.
- O [workflow do PR #1](https://github.com/wandrad3/feedback-do-aluno/actions/runs/42636233047) passou por testes, cobertura, Terraform validate e análise Sonar. Deploy e destroy foram ignorados. Uma reexecução foi solicitada após a troca do token para validar o novo secret.
- GitHub Actions tem as variables `AWS_ACCOUNT_ID=158363271463`, `AWS_REGION=us-east-2`, `SONAR_ORGANIZATION=wandrad3profile-projects` e `SONAR_PROJECT_KEY=wandrad3profile-projects_wandrad3-feedback-do-aluno`.
- `SONAR_TOKEN` está cadastrado como secret; `EMAIL_ADMIN` ainda falta. Nunca coloque valores desses secrets no Git, neste arquivo ou no chat.
- O projeto Sonar público existe com a chave real `wandrad3profile-projects_wandrad3-feedback-do-aluno`. `SONAR_PROJECT_KEY` foi corrigida. O token exposto no chat foi revogado, um novo token foi gerado e o secret `SONAR_TOKEN` foi substituído no GitHub sem divulgar o novo valor. Há outro token antigo de análise na conta Sonar que pode ser revogado pelo usuário na página de segurança, caso não esteja em uso.
- Em 9 de novembro, uma análise local no Sonar passou no segundo envio: 100% de linhas e ramos, zero vulnerabilidades, zero code smells e Quality Gate `OK`. O primeiro envio teve status `NONE`, esperado para a primeira análise de um projeto novo.
- A organização Sonar não está vinculada a uma plataforma DevOps. A API recusou vincular o projeto a `wandrad3/feedback-do-aluno` com a mensagem de que a organização não possui vínculo. É preciso conectar primeiro a organização ao GitHub e depois vincular o projeto ao repositório.
- Ainda não há deploy AWS confirmado na conta de destino. Apenas o perfil AWS `default` existe localmente, e ele foi identificado anteriormente como outra conta. Não execute Terraform usando esse perfil.
- Em 9 de novembro, a automação do Chrome e do navegador integrado ainda retornou `browser access host is unavailable`. A sessão autenticada no Sonar não ficou acessível pela automação.

## Próximos passos

1. **Vincular Sonar e GitHub.** Na [organização Sonar](https://sonarcloud.io/organizations/wandrad3profile-projects/projects), conectar `wandrad3` ao GitHub e dar acesso ao repositório `wandrad3/feedback-do-aluno`. Depois, no projeto, abrir **Administration → General Settings → Repository binding** e selecionar esse repositório. A automação do Chrome continuou indisponível nesta conversa.
2. **Validar o PR e a pipeline.** Conferir o PR de correções Sonar, os testes, a análise e o Quality Gate no GitHub Actions após a rotação do token. Fazer merge em `main` somente quando o bootstrap AWS e `EMAIL_ADMIN` estiverem preparados, pois um push em `main` aciona o deploy.
3. **Preparar AWS.** Em uma nova sessão de terminal, conferir AWS CLI e Terraform no `PATH`, autenticar no perfil dedicado `feedback-aluno` e confirmar com `aws sts get-caller-identity --profile feedback-aluno` que o ID é **158363271463**. Verificar elegibilidade e uso do Free Tier na conta.
4. **Provisionar e implantar.** Seguir o [README](../README.md) para executar `terraform/bootstrap` na conta correta. Configurar `EMAIL_ADMIN` como secret GitHub sem expor o endereço publicamente. Acionar a pipeline, confirmar o ID da conta antes do apply, validar a API e a confirmação da inscrição SNS.
5. **Preparar a entrega.** Conferir o [manual público da API](USO-DA-API.md), finalizar um manual privado para os professores com os dados de acesso necessários e guardá-lo fora do repositório público. Após a avaliação, usar o fluxo manual `destroy` do GitHub Actions somente quando a remoção tiver sido autorizada.

## Texto para iniciar a próxima conversa

> Continue o projeto em `D:\wstech\projetos\feedback-do-aluno`. Leia `docs/CONTINUIDADE.md` e `README.md` primeiro. O projeto Sonar público existe, a análise passou e o token divulgado foi revogado e substituído no GitHub. A organização ainda precisa ser conectada ao GitHub. Valide o PR #1, prepare o perfil AWS `feedback-aluno` para a conta `158363271463` em `us-east-2` e só então faça merge em `main` para implantar. Mantenha 100% de cobertura e não publique credenciais. Não execute Terraform na conta AWS padrão nem acione `destroy` antes da avaliação.
