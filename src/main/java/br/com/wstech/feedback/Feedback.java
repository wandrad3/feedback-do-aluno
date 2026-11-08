package br.com.wstech.feedback;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.Map;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public record Feedback(String id, String descricao, int nota, String urgencia, Instant enviadoEm, LocalDate dia, String semana) {
    public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    public static String semana(LocalDate dia) {
        WeekFields fields = WeekFields.ISO;
        return "%04d-W%02d".formatted(dia.get(fields.weekBasedYear()), dia.get(fields.weekOfWeekBasedYear()));
    }

    public static Feedback criar(String id, String descricao, int nota, Instant enviadoEm) {
        LocalDate dia = enviadoEm.atZone(ZONE).toLocalDate();
        return new Feedback(id, descricao, nota, nota <= 3 ? "CRITICA" : "NORMAL", enviadoEm, dia, semana(dia));
    }

    public Map<String, AttributeValue> atributos() {
        return Map.of("semana", AttributeValue.fromS(semana), "id", AttributeValue.fromS(id),
                "descricao", AttributeValue.fromS(descricao), "nota", AttributeValue.fromN(Integer.toString(nota)),
                "urgencia", AttributeValue.fromS(urgencia), "enviadoEm", AttributeValue.fromS(enviadoEm.toString()),
                "dia", AttributeValue.fromS(dia.toString()));
    }

    public static Feedback deAtributos(Map<String, AttributeValue> item) {
        return new Feedback(item.get("id").s(), item.get("descricao").s(), Integer.parseInt(item.get("nota").n()),
                item.get("urgencia").s(), Instant.parse(item.get("enviadoEm").s()),
                LocalDate.parse(item.get("dia").s()), item.get("semana").s());
    }
}
