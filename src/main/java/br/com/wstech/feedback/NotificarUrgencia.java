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
                System.getenv("TABELA_PROFESSORES"), System.getenv("EMAIL_ADMIN"),
                System.getenv("EMAIL_ADMIN_ADICIONAL"), System.getenv("NOME_ADMIN_ADICIONAL")));
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
            String descricao = imagem.get("descricao").getS();
            String urgencia = imagem.get("urgencia").getS();
            String data = imagem.get("enviadoEm").getS();
            String html = "<p>Uma avaliação de aula requer atenção.</p>"
                    + "<table role=\"presentation\" style=\"border-collapse:collapse;width:100%;font-size:14px\">"
                    + "<tr><th align=\"left\" style=\"padding:10px;background:#f2f4f7\">Campo</th><th align=\"left\" style=\"padding:10px;background:#f2f4f7\">Informação</th></tr>"
                    + "<tr><td style=\"padding:10px;border-bottom:1px solid #ddd\">Descrição</td><td style=\"padding:10px;border-bottom:1px solid #ddd\">" + EmailCorporativo.escapar(descricao) + "</td></tr>"
                    + "<tr><td style=\"padding:10px;border-bottom:1px solid #ddd\">Urgência</td><td style=\"padding:10px;border-bottom:1px solid #ddd\">" + EmailCorporativo.escapar(urgencia) + "</td></tr>"
                    + "<tr><td style=\"padding:10px;border-bottom:1px solid #ddd\">Data de envio</td><td style=\"padding:10px;border-bottom:1px solid #ddd\">" + EmailCorporativo.escapar(data) + "</td></tr></table>";
            email.enviar("FACULDADE DO ALUNO | Avaliação crítica recebida", "Atenção: avaliação crítica",
                    "Uma avaliação de aula requer atenção.\n\nDescrição: %s\nUrgência: %s\nData de envio: %s"
                            .formatted(descricao, urgencia, data), html);
            enviadas++;
        }
        return Integer.toString(enviadas);
    }
}
