package br.com.wstech.feedback;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

class RelatorioSemanalTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-09T12:00:00Z"), ZoneOffset.UTC);
    private final DynamoDbClient dynamo = mock(DynamoDbClient.class);
    private final SnsClient sns = mock(SnsClient.class);
    private final RelatorioSemanal handler = new RelatorioSemanal(dynamo, sns, "avaliacoes", "arn:topico", CLOCK);

    @Test void enviaRelatorioVazio() {
        when(dynamo.query(any(QueryRequest.class))).thenReturn(QueryResponse.builder().items(List.of()).lastEvaluatedKey(Map.of()).build());
        assertEquals("2026-W45", handler.handleRequest(Map.of(), null));
        var captor = org.mockito.ArgumentCaptor.forClass(PublishRequest.class);
        verify(sns).publish(captor.capture());
        assertTrue(captor.getValue().message().contains("Media: 0,00"));
    }

    @Test void paginaECalcula() {
        Feedback primeira = Feedback.criar("1", "bom", 9, Instant.parse("2026-11-03T12:00:00Z"));
        Feedback segunda = Feedback.criar("2", "ruim", 1, Instant.parse("2026-11-04T12:00:00Z"));
        when(dynamo.query(any(QueryRequest.class)))
                .thenReturn(QueryResponse.builder().items(primeira.atributos()).lastEvaluatedKey(Map.of("id", AttributeValue.fromS("1"))).build())
                .thenReturn(QueryResponse.builder().items(segunda.atributos()).build());
        assertEquals("2026-W45", handler.handleRequest(Map.of(), null));
        verify(dynamo, times(2)).query(any(QueryRequest.class));
        var captor = org.mockito.ArgumentCaptor.forClass(PublishRequest.class);
        verify(sns).publish(captor.capture());
        assertTrue(captor.getValue().message().contains("Media: 5,00"));
        assertTrue(captor.getValue().message().contains("bom"));
        assertTrue(captor.getValue().message().contains("ruim"));
    }

    @Test void divideMensagemLonga() {
        String longa = "x".repeat(1000);
        var itens = java.util.stream.IntStream.range(0, 60)
                .mapToObj(i -> Feedback.criar(Integer.toString(i), longa, 2, Instant.parse("2026-11-03T12:00:00Z")).atributos()).toList();
        when(dynamo.query(any(QueryRequest.class))).thenReturn(QueryResponse.builder().items(itens).build());
        handler.handleRequest(Map.of(), null);
        verify(sns, atLeast(2)).publish(any(PublishRequest.class));
    }
}
