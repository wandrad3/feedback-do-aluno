package br.com.wstech.feedback;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.time.DayOfWeek;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.sesv2.SesV2Client;

public final class RelatorioSemanal implements RequestHandler<Map<String, Object>, String> {
    private static final int MAX_BYTES = 220_000;
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("pt-BR"));
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR"));
    private final DynamoDbClient dynamo;
    private final EmailCorporativo email;
    private final String tabela;
    private final Clock clock;

    public RelatorioSemanal() {
        this(DynamoDbClient.create(), SesV2Client.create(), System.getenv("TABELA_AVALIACOES"),
                System.getenv("TABELA_PROFESSORES"), System.getenv("EMAIL_ADMIN"), Clock.systemUTC());
    }

    RelatorioSemanal(DynamoDbClient dynamo, SesV2Client ses, String tabela, String tabelaProfessores,
                     String emailAdmin, Clock clock) {
        this(dynamo, new EmailCorporativo(dynamo, ses, tabelaProfessores, emailAdmin), tabela, clock);
    }

    RelatorioSemanal(DynamoDbClient dynamo, EmailCorporativo email, String tabela, Clock clock) {
        this.dynamo = dynamo;
        this.email = email;
        this.tabela = tabela;
        this.clock = clock;
    }

    @Override
    public String handleRequest(Map<String, Object> evento, Context context) {
        LocalDate inicio = LocalDate.now(clock.withZone(Feedback.ZONE)).minusWeeks(1).with(java.time.DayOfWeek.MONDAY);
        String semana = Feedback.semana(inicio);
        List<Feedback> avaliacoes = buscar(semana);
        Map<DayOfWeek, Integer> porDia = new EnumMap<>(DayOfWeek.class);
        int[] porNota = new int[11];
        int criticas = 0;
        int soma = 0;
        List<String> linhas = new ArrayList<>();
        for (Feedback feedback : avaliacoes) {
            porDia.merge(feedback.dia().getDayOfWeek(), 1, Integer::sum);
            if ("CRITICA".equals(feedback.urgencia())) criticas++;
            porNota[feedback.nota()]++;
            soma += feedback.nota();
            linhas.add("Data e hora: %s (São Paulo) | Nota: %d/10 | Classificação: %s | Avaliação: %s".formatted(
                    DATA_HORA.format(feedback.enviadoEm().atZone(Feedback.ZONE)), feedback.nota(),
                    feedback.urgencia(), feedback.descricao()));
        }
        StringBuilder resumo = new StringBuilder(String.format(Locale.forLanguageTag("pt-BR"),
                "Período: %s a %s (horário de São Paulo)%nSemana ISO: %s%nTotal de avaliações: %d%nMédia das notas: %.2f/10%nAvaliações críticas (0 a 3): %d%nAvaliações normais (4 a 10): %d%n",
                DATA.format(inicio), DATA.format(inicio.plusDays(6)), semana, avaliacoes.size(),
                avaliacoes.isEmpty() ? 0.0 : (double) soma / avaliacoes.size(), criticas, avaliacoes.size() - criticas));
        resumo.append("\nAvaliações por dia:\n");
        for (int dia = 0; dia < 7; dia++) {
            LocalDate data = inicio.plusDays(dia);
            resumo.append(DATA.format(data)).append(": ").append(porDia.getOrDefault(data.getDayOfWeek(), 0)).append('\n');
        }
        resumo.append("\nDistribuição das notas:\n");
        for (int nota = 0; nota <= 10; nota++) resumo.append(nota).append("/10: ").append(porNota[nota]).append('\n');
        resumo.append("\nAvaliações recebidas:\n");
        if (avaliacoes.isEmpty()) resumo.append("Nenhuma avaliação recebida neste período.\n");
        publicarPartes(semana, resumo.toString(), linhas);
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
        StringBuilder atual = new StringBuilder();
        for (String linha : linhas) {
            if (!atual.isEmpty() && (resumo.length() + atual.length() + linha.length() + 1) * 4 > MAX_BYTES) {
                partes.add(atual.toString());
                atual = new StringBuilder();
            }
            atual.append(linha).append('\n');
        }
        partes.add(atual.toString());
        for (int i = 0; i < partes.size(); i++) {
            String mensagem = "Relatório %s - parte %d/%d%n%n%s%s".formatted(semana, i + 1, partes.size(), resumo, partes.get(i));
            email.enviar("FACULDADE DO ALUNO | Relatório semanal de avaliações",
                    "Relatório semanal de avaliações", "Prezada equipe,\n\nSegue o consolidado das avaliações da semana anterior.\n\n" + mensagem);
        }
    }
}
