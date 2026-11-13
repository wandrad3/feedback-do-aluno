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
    private static final int MAX_BYTES = 150_000;
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
        String professorId = evento == null ? null : (String) evento.get("professorId");
        publicarPartes(semana, resumo.toString(), linhas, avaliacoes, inicio, porDia, porNota, criticas, soma, professorId);
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

    private void publicarPartes(String semana, String resumo, List<String> linhas, List<Feedback> avaliacoes,
                                LocalDate inicio, Map<DayOfWeek, Integer> porDia, int[] porNota,
                                int criticas, int soma, String professorId) {
        List<List<Feedback>> partes = new ArrayList<>();
        List<Feedback> atual = new ArrayList<>();
        int tamanho = resumo.length();
        for (int i = 0; i < linhas.size(); i++) {
            if (!atual.isEmpty() && (tamanho + linhas.get(i).length() + 1) * 4 > MAX_BYTES) {
                partes.add(atual);
                atual = new ArrayList<>();
                tamanho = resumo.length();
            }
            atual.add(avaliacoes.get(i));
            tamanho += linhas.get(i).length() + 1;
        }
        partes.add(atual);
        for (int i = 0; i < partes.size(); i++) {
            int inicioParte = partes.subList(0, i).stream().mapToInt(List::size).sum();
            String detalhes = String.join("\n", linhas.subList(inicioParte, inicioParte + partes.get(i).size()));
            String mensagem = "Relatório %s - parte %d/%d%n%n%s%s".formatted(semana, i + 1, partes.size(), resumo, detalhes);
            String html = html(semana, inicio, avaliacoes.size(), soma, criticas, porDia, porNota,
                    partes.get(i), i + 1, partes.size());
            email.enviar("FACULDADE DO ALUNO | Relatório semanal de avaliações",
                    "Relatório semanal de avaliações", "Segue o consolidado das avaliações da semana anterior.\n\n" + mensagem,
                    html, professorId);
        }
    }

    private static String html(String semana, LocalDate inicio, int total, int soma, int criticas,
                               Map<DayOfWeek, Integer> porDia, int[] porNota, List<Feedback> detalhes,
                               int parte, int totalPartes) {
        StringBuilder html = new StringBuilder("<p>Segue o consolidado das avaliações da semana anterior.</p>");
        html.append("<p><strong>Período:</strong> ").append(DATA.format(inicio)).append(" a ")
                .append(DATA.format(inicio.plusDays(6))).append(" (São Paulo) · <strong>Semana ISO:</strong> ")
                .append(semana).append(" · Parte ").append(parte).append('/').append(totalPartes).append("</p>");
        html.append("<table role=\"presentation\" style=\"border-collapse:collapse;width:100%;font-size:14px\">"
                + "<tr><th align=\"left\" style=\"padding:9px;background:#172033;color:#fff\">Indicador</th>"
                + "<th align=\"right\" style=\"padding:9px;background:#172033;color:#fff\">Valor</th></tr>");
        linha(html, "Total de avaliações", Integer.toString(total));
        linha(html, "Média das notas", String.format(Locale.forLanguageTag("pt-BR"), "%.2f/10", total == 0 ? 0.0 : (double) soma / total));
        linha(html, "Críticas (0 a 3)", Integer.toString(criticas));
        linha(html, "Normais (4 a 10)", Integer.toString(total - criticas));
        html.append("</table><h2 style=\"font-size:18px;margin-top:28px\">Avaliações por dia</h2>");
        html.append("<table role=\"presentation\" style=\"width:100%;border-collapse:collapse;font-size:13px\">"
                + "<tr><th align=\"left\">Dia</th><th align=\"left\">Volume</th><th align=\"right\">Total</th></tr>");
        int maximo = Math.max(1, porDia.values().stream().mapToInt(Integer::intValue).max().orElse(0));
        for (int dia = 0; dia < 7; dia++) {
            LocalDate data = inicio.plusDays(dia);
            int valor = porDia.getOrDefault(data.getDayOfWeek(), 0);
            int largura = valor == 0 ? 0 : Math.max(4, valor * 100 / maximo);
            html.append("<tr><td style=\"padding:8px 6px;border-bottom:1px solid #ddd\">")
                    .append(DATA.format(data)).append("</td><td style=\"width:65%;border-bottom:1px solid #ddd\">"
                            + "<table role=\"presentation\" style=\"width:").append(largura)
                    .append("%;background:#2463a6\"><tr><td style=\"height:12px;font-size:1px\">&nbsp;</td></tr></table></td>"
                            + "<td align=\"right\" style=\"padding:8px 6px;border-bottom:1px solid #ddd\">")
                    .append(valor).append("</td></tr>");
        }
        html.append("</table><h2 style=\"font-size:18px;margin-top:28px\">Distribuição das notas</h2>"
                + "<table role=\"presentation\" style=\"width:100%;border-collapse:collapse;font-size:13px\">"
                + "<tr><th align=\"left\">Nota</th><th align=\"right\">Quantidade</th></tr>");
        for (int nota = 0; nota <= 10; nota++) linha(html, nota + "/10", Integer.toString(porNota[nota]));
        html.append("</table><h2 style=\"font-size:18px;margin-top:28px\">Avaliações recebidas</h2>");
        if (detalhes.isEmpty()) html.append("<p>Nenhuma avaliação recebida neste período.</p>");
        else {
            html.append("<table role=\"presentation\" style=\"width:100%;border-collapse:collapse;font-size:13px\">"
                    + "<tr><th align=\"left\">Data e hora</th><th align=\"left\">Nota</th>"
                    + "<th align=\"left\">Classificação</th><th align=\"left\">Avaliação</th></tr>");
            for (Feedback feedback : detalhes) {
                html.append("<tr><td style=\"padding:8px;border-bottom:1px solid #ddd\">")
                        .append(DATA_HORA.format(feedback.enviadoEm().atZone(Feedback.ZONE)))
                        .append("</td><td style=\"padding:8px;border-bottom:1px solid #ddd\">")
                        .append(feedback.nota()).append("/10</td><td style=\"padding:8px;border-bottom:1px solid #ddd\">")
                        .append(EmailCorporativo.escapar(feedback.urgencia()))
                        .append("</td><td style=\"padding:8px;border-bottom:1px solid #ddd;overflow-wrap:anywhere\">")
                        .append(EmailCorporativo.escapar(feedback.descricao())).append("</td></tr>");
            }
            html.append("</table>");
        }
        return html.toString();
    }

    private static void linha(StringBuilder html, String titulo, String valor) {
        html.append("<tr><td style=\"padding:9px;border-bottom:1px solid #ddd\">")
                .append(EmailCorporativo.escapar(titulo))
                .append("</td><td align=\"right\" style=\"padding:9px;border-bottom:1px solid #ddd\">")
                .append(EmailCorporativo.escapar(valor)).append("</td></tr>");
    }
}
