package br.com.wstech.feedback;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import java.util.Map;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.sesv2.SesV2Client;

public final class NotificarUrgencia implements RequestHandler<DynamodbEvent, String> {
    private final EmailCorporativo email;

    public NotificarUrgencia() {
        this(new EmailCorporativo(DynamoDbClient.create(), SesV2Client.create(),
                System.getenv("TABELA_PROFESSORES"), System.getenv("EMAIL_ADMIN")));
    }

    NotificarUrgencia(EmailCorporativo email) {
        this.email = email;
    }

    @Override
    public String handleRequest(DynamodbEvent evento, Context context) {
        int enviadas = 0;
        for (DynamodbEvent.DynamodbStreamRecord registro : evento.getRecords()) {
            if (!"INSERT".equals(registro.getEventName())
                    || !"CRITICA".equals(registro.getDynamodb().getNewImage().get("urgencia").getS())) continue;
            Map<String, com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue> imagem = registro.getDynamodb().getNewImage();
            email.enviar("Instituto Aurora | Avaliação crítica recebida", "Atenção: avaliação crítica",
                    "Prezada equipe,\n\nUma avaliação de aula requer atenção.\n\nDescrição: %s\nUrgência: %s\nData de envio: %s"
                            .formatted(imagem.get("descricao").getS(), imagem.get("urgencia").getS(),
                                    imagem.get("enviadoEm").getS()));
            enviadas++;
        }
        return Integer.toString(enviadas);
    }
}
