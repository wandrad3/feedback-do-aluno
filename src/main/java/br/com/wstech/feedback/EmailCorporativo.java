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
                + "<body style=\"margin:0;background:#0b0b0b;font-family:Arial,sans-serif;color:#111\">"
                + "<table role=\"presentation\" style=\"width:100%;padding:24px 12px\"><tr><td align=\"center\">"
                + "<table role=\"presentation\" style=\"max-width:600px;width:100%;background:#fff\">"
                + "<tr><td style=\"padding:40px 44px 16px\">"
                + "<span style=\"display:inline-block;background:#000;color:#fff;font-weight:bold;font-size:22px;letter-spacing:3px;padding:8px 15px\">ALUN</span>"
                + "<p style=\"font-size:11px;letter-spacing:2px;margin:12px 0 0\">FACULDADE DO ALUNO</p></td></tr>"
                + "<tr><td style=\"padding:0 44px 32px\"><h1 style=\"font-size:22px;margin:0 0 20px\">" + escapar(titulo) + "</h1>"
                + "<p style=\"line-height:1.7;font-size:14px;white-space:normal\">" + corpo + "</p>"
                + "<p style=\"font-size:13px;margin-top:28px\">Atenciosamente,<br><strong>Equipe de Qualidade Acadêmica</strong></p>"
                + "</td></tr><tr><td style=\"padding:18px 44px;border-top:1px solid #ddd;font-size:11px;color:#555\">"
                + "FACULDADE DO ALUNO · Comunicação automática de qualidade acadêmica.</td></tr></table>"
                + "</td></tr></table></body></html>";
    }

    private static String escapar(String valor) {
        return valor.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
