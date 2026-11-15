"""Insere avaliações fictícias da semana ISO anterior e pode enviar o relatório.

Os IDs são determinísticos. Reexecutar o seed não altera avaliações existentes.
"""

import argparse
import datetime as dt
import json
import subprocess
import time
from zoneinfo import ZoneInfo


CONTA = "315205584370"
REGIAO = "us-east-2"
TABELA = "feedback-aluno-avaliacoes"
FUNCAO = "feedback-aluno-relatorio"
FUSO = ZoneInfo("America/Sao_Paulo")
AVALIACOES = (
    (0, 9, 10, "Aula fictícia de matemática: explicação clara e exemplos práticos."),
    (0, 15, 8, "Aula fictícia de história: boa organização do conteúdo."),
    (1, 10, 7, "Aula fictícia de ciências: atividade em grupo útil."),
    (1, 16, 3, "Aula fictícia de matemática: ritmo rápido para a turma."),
    (2, 9, 9, "Aula fictícia de português: exercícios bem explicados."),
    (2, 14, 6, "Aula fictícia de geografia: faltaram exemplos no quadro."),
    (3, 10, 10, "Aula fictícia de biologia: demonstração muito didática."),
    (3, 16, 8, "Aula fictícia de artes: material acessível e participação da turma."),
    (4, 9, 2, "Aula fictícia de física: dúvidas importantes ficaram sem resposta."),
    (4, 15, 7, "Aula fictícia de inglês: prática oral produtiva."),
    (5, 10, 8, "Aula fictícia de revisão: resumo ajudou na preparação."),
    (5, 14, 5, "Aula fictícia de laboratório: tempo para exercícios foi curto."),
    (6, 10, 9, "Aula fictícia de projeto: orientação objetiva."),
    (6, 15, 6, "Aula fictícia de encerramento: gostei da revisão final."),
)


def aws(*argumentos, aceitar_condicional=False):
    resultado = subprocess.run(
        ["aws", *argumentos, "--region", REGIAO, "--no-cli-pager", "--output", "json"],
        capture_output=True, text=True, check=False,
    )
    if resultado.returncode:
        if aceitar_condicional and "ConditionalCheckFailedException" in resultado.stderr:
            return None
        if "ProvisionedThroughputExceededException" in resultado.stderr:
            raise RuntimeError("throttling")
        raise RuntimeError(f"Falha em {argumentos[0]} {argumentos[1]} (código {resultado.returncode})")
    return json.loads(resultado.stdout)


def dados(agora):
    hoje = agora.astimezone(FUSO).date()
    inicio = hoje - dt.timedelta(days=hoje.weekday() + 7)
    semana = f"{inicio.isocalendar().year}-W{inicio.isocalendar().week:02d}"
    itens = []
    for indice, (dia, hora, nota, descricao) in enumerate(AVALIACOES, start=1):
        data = inicio + dt.timedelta(days=dia)
        instante = dt.datetime.combine(data, dt.time(hora, 30), FUSO).astimezone(dt.timezone.utc)
        itens.append({
            "semana": {"S": semana},
            "id": {"S": f"demo-{semana}-{indice:02d}"},
            "descricao": {"S": descricao},
            "nota": {"N": str(nota)},
            "urgencia": {"S": "CRITICA" if nota <= 3 else "NORMAL"},
            "enviadoEm": {"S": instante.isoformat().replace("+00:00", "Z")},
            "dia": {"S": data.isoformat()},
        })
    return inicio, semana, itens


def inserir(item):
    for tentativa in range(5):
        try:
            return aws("dynamodb", "put-item", "--table-name", TABELA,
                       "--item", json.dumps(item, ensure_ascii=False),
                       "--condition-expression", "attribute_not_exists(id)",
                       aceitar_condicional=True) is not None
        except RuntimeError as erro:
            if str(erro) != "throttling" or tentativa == 4:
                raise
            time.sleep(2 ** tentativa)
    raise RuntimeError("Não foi possível gravar o item")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--enviar-relatorio", action="store_true")
    args = parser.parse_args()
    inicio, semana, itens = dados(dt.datetime.now(dt.timezone.utc))
    print(f"semana={semana} periodo={inicio}..{inicio + dt.timedelta(days=6)} itens={len(itens)}")
    print(f"notas_criticas={sum(int(item['nota']['N']) <= 3 for item in itens)}")
    if args.dry_run:
        print("dry_run=true")
        return
    if aws("sts", "get-caller-identity")["Account"] != CONTA:
        raise RuntimeError("Conta AWS diferente da conta do projeto")
    tabela = aws("dynamodb", "describe-table", "--table-name", TABELA)["Table"]
    if tabela["TableStatus"] != "ACTIVE":
        raise RuntimeError("Tabela não está ativa")
    inseridos = 0
    for item in itens:
        inseridos += inserir(item)
        time.sleep(1.1)
    resposta = aws("dynamodb", "query", "--table-name", TABELA,
                   "--key-condition-expression", "semana = :semana",
                   "--expression-attribute-values", json.dumps({":semana": {"S": semana}}),
                   "--select", "COUNT")
    print(f"inseridos={inseridos} ja_existiam={len(itens) - inseridos} total_semana={resposta['Count']}")
    if args.enviar_relatorio:
        import pathlib
        import tempfile
        destino = pathlib.Path(tempfile.gettempdir()) / "feedback-relatorio-seed.json"
        resultado = aws("lambda", "invoke", "--function-name", FUNCAO,
                        "--cli-binary-format", "raw-in-base64-out", "--payload", "{}", str(destino))
        if resultado.get("StatusCode") != 200 or resultado.get("FunctionError"):
            raise RuntimeError("Falha na execução da Lambda de relatório")
        if json.loads(destino.read_text(encoding="utf-8")) != semana:
            raise RuntimeError("Lambda relatou outra semana")
        print("relatorio_enviado_para_destinatarios_ses_verificados=true")


if __name__ == "__main__":
    main()
