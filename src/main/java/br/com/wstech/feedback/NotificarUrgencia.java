package br.com.wstech.feedback;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import java.util.Map;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

public final class NotificarUrgencia implements RequestHandler<DynamodbEvent, String> {
    private final SnsClient sns;
    private final String topico;

    public NotificarUrgencia() {
        this(SnsClient.create(), System.getenv("TOPICO_NOTIFICACOES"));
    }

    NotificarUrgencia(SnsClient sns, String topico) {
        this.sns = sns;
        this.topico = topico;
    }

    @Override
    public String handleRequest(DynamodbEvent evento, Context context) {
        int enviadas = 0;
        for (DynamodbEvent.DynamodbStreamRecord registro : evento.getRecords()) {
            if (!"INSERT".equals(registro.getEventName())
                    || !"CRITICA".equals(registro.getDynamodb().getNewImage().get("urgencia").getS())) continue;
            Map<String, com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue> imagem = registro.getDynamodb().getNewImage();
            sns.publish(PublishRequest.builder().topicArn(topico).subject("Avaliacao critica recebida")
                    .message("Descricao: %s%nUrgencia: %s%nData de envio: %s".formatted(
                            imagem.get("descricao").getS(), imagem.get("urgencia").getS(), imagem.get("enviadoEm").getS())).build());
            enviadas++;
        }
        return Integer.toString(enviadas);
    }
}
