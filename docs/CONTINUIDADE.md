# Continuidade em uma nova conversa

Atualizado em **9 de novembro de 2026**. Este arquivo registra o estado verificado do projeto para retomar o trabalho sem expor credenciais.

## Objetivo

Concluir a plataforma pública de feedback do aluno em Java 21, AWS `us-east-2`, Terraform e GitHub Actions, com análise SonarQube Cloud, cobertura JaCoCo de 100% e recursos adequados a uma conta AWS de estudante. O repositório é [wandrad3/feedback-do-aluno](https://github.com/wandrad3/feedback-do-aluno).

## Estado verificado

- O código Java, testes, módulo Terraform, workflow, [README](../README.md) e [manual público da API](USO-DA-API.md) estão no repositório. O último commit verificado é `f302ae1`.
- Java 21 e Maven 3.9.3 estão instalados. Os testes e a validação Terraform passaram em uma sessão anterior; o JaCoCo atingiu 100% de linhas e ramos de produção naquela execução. Reexecute as verificações antes da entrega final.
- O [último workflow consultado](https://github.com/wandrad3/feedback-do-aluno/actions/runs/42528385669) falhou em **Exigir configuração Sonar**. Os jobs de deploy e destroy não foram executados.
- GitHub Actions já tem as variables `AWS_ACCOUNT_ID=158363271463`, `AWS_REGION=us-east-2`, `SONAR_ORGANIZATION=wandrad3profile-projects` e `SONAR_PROJECT_KEY=wandrad3profile-projects_feedback-do-aluno`.
- A consulta de secrets do repositório não retornou nenhum: ainda faltam `SONAR_TOKEN` e `EMAIL_ADMIN`. Nunca coloque valores desses secrets no Git, neste arquivo ou no chat.
- A chave Sonar pré-configurada retornou HTTP 404 na API pública: o projeto correspondente ainda não foi encontrado. A organização existe. Se a importação gerar outra chave, ajuste `SONAR_PROJECT_KEY` no GitHub.
- O AWS CLI local é 2.12.7, anterior à versão 2.32.0 exigida pelo fluxo `aws login` descrito no README. `terraform` não está no `PATH`; Terraform 1.10.5 foi usado temporariamente para validação anterior. Ainda não há deploy AWS confirmado na conta de destino. Não execute Terraform usando o perfil AWS padrão, que foi identificado anteriormente como outra conta.
- A automação do Chrome/Edge retornou `browser access host is unavailable`, mesmo com a extensão Chrome instalada e `sonarcloud.io` em **Sempre permitir**. Ao tentar controlar a janela como aplicativo, a automação parou porque não conseguiu confirmar a URL para aplicar a política de segurança. Capturas de tela não dão acesso à sessão autenticada.

## Próximos passos

1. **Recuperar a conexão do navegador.** Confirmar que o painel da extensão ChatGPT abre no Chrome no mesmo perfil autenticado no SonarCloud. Se necessário, reiniciar Chrome e aplicativo ChatGPT/Codex ou reinstalar a extensão em **Configurações → Uso do computador → Google Chrome**. Testar `@Chrome` em nova conversa. A [documentação oficial](https://learn.chatgpt.com/docs/chrome-extension#troubleshooting) traz a sequência de diagnóstico.
2. **Criar o projeto Sonar.** Na organização [wandrad3profile-projects](https://sonarcloud.io/organizations/wandrad3profile-projects/projects), importar o repositório público `wandrad3/feedback-do-aluno`. Preferir projeto público para o plano gratuito. Conferir a chave real e atualizar a variable `SONAR_PROJECT_KEY` caso seja diferente da pré-configurada.
3. **Configurar o token.** Gerar um token de análise na conta SonarQube Cloud e salvá-lo apenas como secret `SONAR_TOKEN` no repositório GitHub. A CLI aceita entrada interativa: `gh secret set SONAR_TOKEN -R wandrad3/feedback-do-aluno`. Não reutilizar nem tentar extrair o secret de um repositório de terceiros.
4. **Validar Sonar e cobertura.** Executar novamente o workflow, conferir que a etapa de análise realmente rodou, que o Quality Gate passou e que o relatório JaCoCo mostra 100% de linhas e ramos. Corrigir problemas observados antes do deploy.
5. **Preparar AWS.** Atualizar AWS CLI para versão compatível, autenticar no perfil dedicado `feedback-aluno` e confirmar com `aws sts get-caller-identity --profile feedback-aluno` que o ID é **158363271463**. Instalar Terraform 1.10 ou superior no `PATH`. Verificar elegibilidade e uso do Free Tier na conta.
6. **Provisionar e implantar.** Seguir o [README](../README.md) para executar `terraform/bootstrap` na conta correta. Configurar `EMAIL_ADMIN` como secret GitHub sem expor o endereço publicamente. Acionar a pipeline, confirmar o ID da conta antes do apply, validar a API e a confirmação da inscrição SNS.
7. **Preparar a entrega.** Conferir o [manual público da API](USO-DA-API.md), finalizar um manual privado para os professores com os dados de acesso necessários e guardá-lo fora do repositório público. Após a avaliação, usar o fluxo manual `destroy` do GitHub Actions somente quando a remoção tiver sido autorizada.

## Texto para iniciar a próxima conversa

> Continue o projeto em `D:\wstech\projetos\feedback-do-aluno`. Leia `docs/CONTINUIDADE.md` e `README.md` primeiro. Meu objetivo é concluir a criação do projeto SonarQube Cloud em `wandrad3profile-projects`, configurar o GitHub Actions e, depois, implantar na conta AWS `158363271463` em `us-east-2`, mantendo 100% de cobertura e sem publicar credenciais. Já autorizei a criação e configuração do projeto e dos secrets necessários. Verifique o estado atual antes de agir; a conexão `@Chrome` falhou na conversa anterior. Não execute Terraform na conta AWS padrão nem acione `destroy` antes da avaliação.
