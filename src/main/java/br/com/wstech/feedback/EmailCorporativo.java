package br.com.wstech.feedback;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.GetEmailIdentityRequest;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

final class EmailCorporativo {
    private final DynamoDbClient dynamo;
    private final SesV2Client ses;
    private final String tabela;
    private final String remetente;

    EmailCorporativo(DynamoDbClient dynamo, SesV2Client ses, String tabela, String remetente) {
        this.dynamo = dynamo;
        this.ses = ses;
        this.tabela = tabela;
        this.remetente = remetente;
    }

    void enviar(String assunto, String titulo, String texto) {
        if (!ses.getEmailIdentity(GetEmailIdentityRequest.builder().emailIdentity(remetente).build())
                .verifiedForSendingStatus()) throw new IllegalStateException("Remetente SES nao verificado");
        for (String destino : destinatarios()) {
            if (!ses.getEmailIdentity(GetEmailIdentityRequest.builder().emailIdentity(destino).build()).verifiedForSendingStatus()) continue;
            String html = pagina(titulo, texto);
            ses.sendEmail(SendEmailRequest.builder().fromEmailAddress(remetente)
                    .destination(Destination.builder().toAddresses(destino).build())
                    .content(EmailContent.builder().simple(Message.builder()
                            .subject(Content.builder().data(assunto).charset("UTF-8").build())
                            .body(Body.builder()
                                    .html(Content.builder().data(html).charset("UTF-8").build())
                                    .text(Content.builder().data(texto).charset("UTF-8").build()).build())
                            .build()).build()).build());
        }
    }

    private List<String> destinatarios() {
        List<String> emails = new ArrayList<>();
        emails.add(remetente);
        Map<String, AttributeValue> cursor = null;
        do {
            ScanResponse pagina = dynamo.scan(ScanRequest.builder().tableName(tabela)
                    .exclusiveStartKey(cursor).build());
            pagina.items().stream().map(item -> item.get("email").s()).filter(email -> !email.equalsIgnoreCase(remetente))
                    .forEach(emails::add);
            cursor = pagina.lastEvaluatedKey();
        } while (!cursor.isEmpty());
        return emails;
    }

    static String pagina(String titulo, String texto) {
        String corpo = escapar(texto).replace("\n", "<br>");
        return "<!doctype html><html lang=\"pt-BR\"><head><meta charset=\"UTF-8\"></head>"
                + "<body style=\"margin:0;background:#f1f5f9;font-family:Arial,sans-serif;color:#172554\">"
                + "<table role=\"presentation\" style=\"width:100%;padding:32px 12px\"><tr><td align=\"center\">"
                + "<table role=\"presentation\" style=\"max-width:600px;width:100%;background:#fff;border-radius:12px;border:1px solid #dbeafe\">"
                + "<tr><td style=\"background:#123b70;padding:24px;color:#fff\">"
                + "<span style=\"display:inline-block;background:#fbbf24;color:#123b70;font-weight:bold;font-size:20px;padding:12px;border-radius:10px\">IA</span>"
                + "<span style=\"font-size:21px;font-weight:bold;margin-left:12px\">Instituto Aurora</span></td></tr>"
                + "<tr><td style=\"padding:30px\"><h1 style=\"font-size:22px;margin-top:0\">" + escapar(titulo) + "</h1>"
                + "<p style=\"line-height:1.65;font-size:15px\">" + corpo + "</p>"
                + "<p style=\"font-size:14px;color:#475569\">Atenciosamente,<br><strong>Equipe de Qualidade Acadêmica</strong></p>"
                + "</td></tr><tr><td style=\"padding:18px 30px;background:#eff6ff;font-size:12px;color:#475569\">"
                + "Instituto Aurora · Comunicação automática do programa de qualidade das aulas.</td></tr></table>"
                + "</td></tr></table></body></html>";
    }

    private static String escapar(String valor) {
        return valor.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
