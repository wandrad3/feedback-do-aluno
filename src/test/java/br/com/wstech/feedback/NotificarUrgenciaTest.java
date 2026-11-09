package br.com.wstech.feedback;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.StreamRecord;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NotificarUrgenciaTest {
    private static DynamodbEvent.DynamodbStreamRecord registro(String tipo, String urgencia) {
        var registro = new DynamodbEvent.DynamodbStreamRecord();
        registro.setEventName(tipo);
        var stream = new StreamRecord();
        stream.setNewImage(Map.of("descricao", new AttributeValue().withS("aula ruim"),
                "urgencia", new AttributeValue().withS(urgencia),
                "enviadoEm", new AttributeValue().withS("2026-11-08T12:00:00Z")));
        registro.setDynamodb(stream);
        return registro;
    }

    @Test void publicaApenasNovosCriticos() {
        EmailCorporativo email = mock(EmailCorporativo.class);
        DynamodbEvent event = new DynamodbEvent();
        event.setRecords(List.of(registro("MODIFY", "CRITICA"), registro("INSERT", "NORMAL"), registro("INSERT", "CRITICA")));
        assertEquals("1", new NotificarUrgencia(email).handleRequest(event, null));
        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(email).enviar(anyString(), anyString(), captor.capture());
        assertTrue(captor.getValue().contains("aula ruim"));
    }
}
