package br.com.wstech.feedback;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.StreamRecord;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

class NotificarUrgenciaTest {
    private static DynamodbEvent.DynamodbStreamRecord record(String tipo, String urgencia) {
        var record = new DynamodbEvent.DynamodbStreamRecord();
        record.setEventName(tipo);
        var stream = new StreamRecord();
        stream.setNewImage(Map.of("descricao", new AttributeValue().withS("aula ruim"),
                "urgencia", new AttributeValue().withS(urgencia),
                "enviadoEm", new AttributeValue().withS("2026-11-08T12:00:00Z")));
        record.setDynamodb(stream);
        return record;
    }

    @Test void publicaApenasNovosCriticos() {
        SnsClient sns = mock(SnsClient.class);
        DynamodbEvent event = new DynamodbEvent();
        event.setRecords(List.of(record("MODIFY", "CRITICA"), record("INSERT", "NORMAL"), record("INSERT", "CRITICA")));
        assertEquals("1", new NotificarUrgencia(sns, "arn:topico").handleRequest(event, null));
        var captor = org.mockito.ArgumentCaptor.forClass(PublishRequest.class);
        verify(sns).publish(captor.capture());
        assertTrue(captor.getValue().message().contains("aula ruim"));
    }
}
