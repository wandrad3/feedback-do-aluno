"""Diagnóstico sem publicar endereços ou mensagens de avaliações nos logs."""

import collections
import datetime as dt
import json
import os
import pathlib
import re
import subprocess
import tempfile
import urllib.parse


REGIAO = "us-east-2"
CONTA = "315205584370"
FIM = dt.datetime.now(dt.timezone.utc)
INICIO = FIM - dt.timedelta(hours=48)


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
    conta_ses = aws("sesv2", "get-account")
    print(f"ses_envios_24h={conta_ses['SendQuota']['SentLast24Hours']}")
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
    identidade_curinga = f"arn:aws:ses:{REGIAO}:{CONTA}:identity/*"
    print(f"politica_envio_autoriza_remetente_atual={identidade_atual in recursos_envio or identidade_curinga in recursos_envio or '*' in recursos_envio}")

    resposta = aws("dynamodb", "scan", "--table-name", "feedback-aluno-professores",
                   "--projection-expression", "email")
    enderecos = [item["email"]["S"] for item in resposta.get("Items", [])]
    verificados = sum(identidade_verificada(endereco) for endereco in enderecos)
    print(f"professores_cadastrados={len(enderecos)}")
    print(f"professores_ses_verificados={verificados}")
    suprimidos = 0
    for endereco in enderecos:
        try:
            aws("sesv2", "get-suppressed-destination", "--email-address", endereco)
            suprimidos += 1
        except RuntimeError:
            pass
    print(f"professores_na_lista_supressao={suprimidos}")

    resposta = aws("logs", "filter-log-events", "--log-group-name",
                   "/aws/lambda/feedback-aluno-notificar",
                   "--start-time", str(int(INICIO.timestamp() * 1000)),
                   "--end-time", str(int(FIM.timestamp() * 1000)))
    tipos = collections.Counter(filter(None, (tipo_erro(item.get("message", ""))
                                         for item in resposta.get("events", []))))
    print(f"eventos_cloudwatch={len(resposta.get('events', []))}")
    print("janela_cloudwatch_horas=48")
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
    recursos_negados = collections.Counter()
    motivos = collections.Counter()
    for item in resposta.get("events", []):
        mensagem = item.get("message", "")
        if "ses:SendEmail" not in mensagem or "not authorized" not in mensagem:
            continue
        recurso = re.search(r"on resource\s+['\"]([^'\"]+)['\"]", mensagem)
        if recurso:
            arn = recurso.group(1).rstrip(".,;)")
            classe = "remetente" if arn == identidade_atual else (
                "professor" if arn in {f"arn:aws:ses:{REGIAO}:{CONTA}:identity/{e}"
                                      for e in enderecos} else "outro")
            recursos_negados[classe] += 1
        if "no identity-based policy allows" in mensagem:
            motivos["sem_permissao_na_politica"] += 1
        elif "explicit deny" in mensagem:
            motivos["negacao_explicita"] += 1
        else:
            motivos["outro"] += 1
    for classe, quantidade in sorted(recursos_negados.items()):
        print(f"recurso_ses_negado_{classe}={quantidade}")
    for motivo, quantidade in sorted(motivos.items()):
        print(f"motivo_iam_{motivo}={quantidade}")
    for item in resposta.get("events", []):
        mensagem = item.get("message", "")
        if "ses:SendEmail" in mensagem and "not authorized" in mensagem:
            linha = mensagem.splitlines()[0]
            linha = re.sub(r"arn:[^\s,;]+", "[ARN]", linha)
            linha = re.sub(r"[\w.+%-]+@[\w.-]+", "[EMAIL]", linha)
            linha = re.sub(r"https?://\S+", "[URL]", linha)
            print(f"amostra_acesso_negado={linha[:300]}")
            break
    for funcao in ("solicitar-relatorio", "relatorio"):
        eventos = aws("logs", "filter-log-events", "--log-group-name",
                      f"/aws/lambda/feedback-aluno-{funcao}",
                      "--start-time", str(int(INICIO.timestamp() * 1000)),
                      "--end-time", str(int(FIM.timestamp() * 1000)))["events"]
        execucoes = sum(item.get("message", "").startswith("REPORT RequestId") for item in eventos)
        erros = collections.Counter(filter(None, (tipo_erro(item.get("message", ""))
                                                   for item in eventos)))
        print(f"lambda_{funcao}_execucoes={execucoes}")
        for tipo, quantidade in sorted(erros.items()):
            print(f"lambda_{funcao}_erro_{tipo}={quantidade}")
    if os.getenv("TESTAR_ENVIO", "false").lower() == "true":
        aviso = {"Records": [{"eventName": "INSERT", "dynamodb": {"NewImage": {
            "descricao": {"S": "Teste tecnico de entrega de e-mail apos correcao IAM"},
            "urgencia": {"S": "CRITICA"},
            "enviadoEm": {"S": dt.datetime.now(dt.timezone.utc).isoformat()}
        }}}]}
        for funcao, evento in (("notificar", aviso), ("relatorio", {})):
            destino = pathlib.Path(tempfile.gettempdir()) / f"feedback-{funcao}-resposta.json"
            resultado = aws("lambda", "invoke", "--function-name", f"feedback-aluno-{funcao}",
                            "--cli-binary-format", "raw-in-base64-out", "--payload",
                            json.dumps(evento), str(destino))
            if resultado.get("FunctionError") or resultado.get("StatusCode") != 200:
                conteudo = json.loads(destino.read_text(encoding="utf-8"))
                print(f"invocacao_{funcao}_erro_tipo={conteudo.get('errorType', 'desconhecido')}")
                raise RuntimeError(f"Invocação de {funcao} falhou")
            print(f"invocacao_{funcao}_ok=True")


if __name__ == "__main__":
    main()
