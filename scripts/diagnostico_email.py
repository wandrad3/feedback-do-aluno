"""Diagnóstico sem publicar endereços ou mensagens de avaliações nos logs."""

import collections
import datetime as dt
import json
import os
import re
import subprocess
import urllib.parse


REGIAO = "us-east-2"
CONTA = "315205584370"
INICIO = dt.datetime(2026, 11, 10, 23, 0, tzinfo=dt.timezone.utc)
FIM = dt.datetime(2026, 11, 11, 0, 0, tzinfo=dt.timezone.utc)


def aws(*argumentos):
    comando = ["aws", *argumentos, "--region", REGIAO, "--no-cli-pager", "--output", "json"]
    resultado = subprocess.run(comando, capture_output=True, text=True, check=False)
    if resultado.returncode:
        raise RuntimeError(f"Falha em {argumentos[0]} {argumentos[1]} (código {resultado.returncode})")
    return json.loads(resultado.stdout)


def identidade_verificada(endereco):
    try:
        return bool(aws("sesv2", "get-email-identity", "--email-identity", endereco)
                    ["VerifiedForSendingStatus"])
    except (RuntimeError, KeyError):
        return False


def tipo_erro(mensagem):
    if "Remetente SES nao verificado" in mensagem:
        return "remetente_ses_nao_verificado"
    if "AccessDenied" in mensagem or "not authorized" in mensagem:
        return "acesso_negado"
    if "MessageRejected" in mensagem or "Email address is not verified" in mensagem:
        return "envio_ses_rejeitado"
    if "ResourceNotFound" in mensagem:
        return "recurso_nao_encontrado"
    if "Task timed out" in mensagem:
        return "tempo_esgotado"
    classes = re.findall(r"[\w$.]+(?:Exception|Error)", mensagem)
    return classes[0] if classes else None


def main():
    conta = aws("sts", "get-caller-identity")["Account"]
    if conta != CONTA:
        raise RuntimeError("Conta AWS diferente da conta esperada")
    remetente = os.environ["EMAIL_ADMIN"]
    print("conta_aws=correta")
    print(f"ses_production_access={aws('sesv2', 'get-account')['ProductionAccessEnabled']}")
    print(f"remetente_ses_verificado={identidade_verificada(remetente)}")
    politica = aws("iam", "get-role-policy", "--role-name", "feedback-aluno-notificar",
                   "--policy-name", "ler-stream-e-publicar")["PolicyDocument"]
    if isinstance(politica, str):
        politica = json.loads(urllib.parse.unquote(politica))
    recursos_envio = [recurso for declaracao in politica["Statement"]
                      if "ses:SendEmail" in declaracao["Action"]
                      for recurso in ([declaracao["Resource"]] if isinstance(declaracao["Resource"], str)
                                      else declaracao["Resource"])]
    identidade_atual = f"arn:aws:ses:{REGIAO}:{CONTA}:identity/{remetente}"
    print(f"politica_envio_autoriza_remetente_atual={identidade_atual in recursos_envio or '*' in recursos_envio}")

    resposta = aws("dynamodb", "scan", "--table-name", "feedback-aluno-professores",
                   "--projection-expression", "email")
    enderecos = [item["email"]["S"] for item in resposta.get("Items", [])]
    verificados = sum(identidade_verificada(endereco) for endereco in enderecos)
    print(f"professores_cadastrados={len(enderecos)}")
    print(f"professores_ses_verificados={verificados}")

    resposta = aws("logs", "filter-log-events", "--log-group-name",
                   "/aws/lambda/feedback-aluno-notificar",
                   "--start-time", str(int(INICIO.timestamp() * 1000)),
                   "--end-time", str(int(FIM.timestamp() * 1000)))
    tipos = collections.Counter(filter(None, (tipo_erro(item.get("message", ""))
                                         for item in resposta.get("events", []))))
    print(f"eventos_cloudwatch={len(resposta.get('events', []))}")
    for tipo, quantidade in sorted(tipos.items()):
        print(f"erro_{tipo}={quantidade}")
    acoes = ("ses:SendEmail", "ses:GetEmailIdentity", "dynamodb:Scan",
             "dynamodb:GetItem", "logs:PutLogEvents")
    for acao in acoes:
        quantidade = sum(acao in item.get("message", "") and
                         ("AccessDenied" in item.get("message", "") or
                          "not authorized" in item.get("message", ""))
                         for item in resposta.get("events", []))
        if quantidade:
            print(f"acao_negada_{acao}={quantidade}")


if __name__ == "__main__":
    main()
