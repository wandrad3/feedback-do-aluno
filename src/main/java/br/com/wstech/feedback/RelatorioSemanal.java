package br.com.wstech.feedback;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

public final class RelatorioSemanal implements RequestHandler<Map<String, Object>, String> {
    private static final int MAX_BYTES = 220_000;
    private final DynamoDbClient dynamo;
    private final SnsClient sns;
    private final String tabela;
    private final String topico;
    private final Clock clock;

    public RelatorioSemanal() {
        this(DynamoDbClient.create(), SnsClient.create(), System.getenv("TABELA_AVALIACOES"),
                System.getenv("TOPICO_NOTIFICACOES"), Clock.systemUTC());
    }

    RelatorioSemanal(DynamoDbClient dynamo, SnsClient sns, String tabela, String topico, Clock clock) {
        this.dynamo = dynamo;
        this.sns = sns;
        this.tabela = tabela;
        this.topico = topico;
        this.clock = clock;
    }

    @Override
    public String handleRequest(Map<String, Object> evento, Context context) {
        LocalDate inicio = LocalDate.now(clock.withZone(Feedback.ZONE)).minusWeeks(1).with(java.time.DayOfWeek.MONDAY);
        String semana = Feedback.semana(inicio);
        List<Feedback> avaliacoes = buscar(semana);
        Map<LocalDate, Integer> porDia = new HashMap<>();
        Map<String, Integer> porUrgencia = new HashMap<>();
        int soma = 0;
        List<String> linhas = new ArrayList<>();
        for (Feedback feedback : avaliacoes) {
            porDia.merge(feedback.dia(), 1, Integer::sum);
            porUrgencia.merge(feedback.urgencia(), 1, Integer::sum);
            soma += feedback.nota();
            linhas.add("Data: %s | Nota: %d | Urgencia: %s | Descricao: %s".formatted(
                    feedback.enviadoEm(), feedback.nota(), feedback.urgencia(), feedback.descricao()));
        }
        String resumo = "Semana: %s%nQuantidade: %d%nMedia: %.2f%nPor dia: %s%nPor urgencia: %s%n".formatted(
                semana, avaliacoes.size(), avaliacoes.isEmpty() ? 0.0 : (double) soma / avaliacoes.size(),
                new java.util.TreeMap<>(porDia), new java.util.TreeMap<>(porUrgencia));
        publicarPartes(semana, resumo, linhas);
        return semana;
    }

    private List<Feedback> buscar(String semana) {
        List<Feedback> encontrados = new ArrayList<>();
        Map<String, AttributeValue> cursor = null;
        do {
            QueryResponse pagina = dynamo.query(QueryRequest.builder().tableName(tabela)
                    .keyConditionExpression("semana = :semana")
                    .expressionAttributeValues(Map.of(":semana", AttributeValue.fromS(semana)))
                    .exclusiveStartKey(cursor).build());
            pagina.items().stream().map(Feedback::deAtributos).forEach(encontrados::add);
            cursor = pagina.lastEvaluatedKey();
        } while (!cursor.isEmpty());
        return encontrados;
    }

    private void publicarPartes(String semana, String resumo, List<String> linhas) {
        List<String> partes = new ArrayList<>();
        StringBuilder atual = new StringBuilder(resumo);
        for (String linha : linhas) {
            if ((atual.length() + linha.length() + 1) * 4 > MAX_BYTES) {
                partes.add(atual.toString());
                atual = new StringBuilder();
            }
            atual.append(linha).append('\n');
        }
        partes.add(atual.toString());
        for (int i = 0; i < partes.size(); i++) {
            String mensagem = "Relatorio %s - parte %d/%d%n%s".formatted(semana, i + 1, partes.size(), partes.get(i));
            sns.publish(PublishRequest.builder().topicArn(topico).subject("Relatorio semanal de avaliacoes")
                    .message(mensagem).build());
        }
    }
}
