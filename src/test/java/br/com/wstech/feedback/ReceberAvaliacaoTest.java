package br.com.wstech.feedback;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

class ReceberAvaliacaoTest {
    private final DynamoDbClient dynamo = mock(DynamoDbClient.class);
    private final ReceberAvaliacao handler = new ReceberAvaliacao(dynamo, "avaliacoes",
            Clock.fixed(Instant.parse("2026-11-08T12:00:00Z"), ZoneOffset.UTC), () -> "abc");

    private int enviar(String body) {
        APIGatewayV2HTTPEvent event = new APIGatewayV2HTTPEvent();
        event.setBody(body);
        return handler.handleRequest(event, null).getStatusCode();
    }

    @Test void gravaAvaliacaoValida() {
        APIGatewayV2HTTPEvent event = new APIGatewayV2HTTPEvent();
        event.setBody("{\"descricao\":\" aula boa \",\"nota\":10}");
        var response = handler.handleRequest(event, null);
        assertEquals(201, response.getStatusCode());
        assertTrue(response.getBody().contains("\"id\":\"abc\""));
        var captor = org.mockito.ArgumentCaptor.forClass(PutItemRequest.class);
        verify(dynamo).putItem(captor.capture());
        assertEquals("aula boa", captor.getValue().item().get("descricao").s());
    }

    @Test void rejeitaEntradasInvalidas() {
        for (String body : new String[]{null, "{", "null", "{}", "{\"descricao\":1,\"nota\":3}",
                "{\"descricao\":\"  \",\"nota\":3}", "{\"descricao\":\"a\",\"nota\":1.5}",
                "{\"descricao\":\"a\",\"nota\":2147483648}", "{\"descricao\":\"a\",\"nota\":-1}",
                "{\"descricao\":\"a\",\"nota\":11}"}) {
            assertEquals(400, enviar(body));
        }
        assertEquals(400, enviar("{\"descricao\":\"" + "a".repeat(1001) + "\",\"nota\":1}"));
        verify(dynamo, never()).putItem(any(PutItemRequest.class));
    }
}
