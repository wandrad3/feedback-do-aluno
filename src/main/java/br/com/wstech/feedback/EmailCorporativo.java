package br.com.wstech.feedback;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.GetEmailIdentityRequest;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

class EmailCorporativo {
    private record Destinatario(String endereco, String nome) {}

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

    void enviar(String assunto, String titulo, String texto, String html) {
        enviar(assunto, titulo, texto, html, null);
    }

    void enviar(String assunto, String titulo, String texto, String html, String professorId) {
        if (!verificado(remetente)) throw new IllegalStateException("Remetente SES nao verificado");
        for (Destinatario destino : destinatarios(professorId)) {
            if (!verificado(destino.endereco())) {
                if (professorId != null) throw new IllegalStateException("Destinatario SES nao verificado");
                continue;
            }
            String saudacao = destino.nome().isBlank() ? "Prezada equipe," : "Prezado, professor " + destino.nome() + ",";
            ses.sendEmail(SendEmailRequest.builder().fromEmailAddress(remetente)
                    .destination(Destination.builder().toAddresses(destino.endereco()).build())
                    .content(EmailContent.builder().simple(Message.builder()
                            .subject(Content.builder().data(assunto).charset("UTF-8").build())
                            .body(Body.builder()
                                    .html(Content.builder().data(pagina(titulo, saudacao, html)).charset("UTF-8").build())
                                    .text(Content.builder().data(saudacao + "\n\n" + texto).charset("UTF-8").build()).build())
                            .build()).build()).build());
        }
    }

    private boolean verificado(String endereco) {
        return ses.getEmailIdentity(GetEmailIdentityRequest.builder().emailIdentity(endereco).build())
                .verifiedForSendingStatus();
    }

    private List<Destinatario> destinatarios(String professorId) {
        if (professorId != null) {
            Map<String, AttributeValue> item = dynamo.getItem(GetItemRequest.builder().tableName(tabela)
                    .key(Map.of("professorId", AttributeValue.fromS(professorId))).build()).item();
            if (!item.containsKey("email")) throw new IllegalStateException("Professor sem e-mail cadastrado");
            return List.of(deItem(item));
        }
        List<Destinatario> emails = new ArrayList<>();
        emails.add(new Destinatario(remetente, ""));
        Map<String, AttributeValue> cursor = null;
        do {
            ScanResponse pagina = dynamo.scan(ScanRequest.builder().tableName(tabela)
                    .exclusiveStartKey(cursor).build());
            pagina.items().stream().filter(item -> !item.get("email").s().equalsIgnoreCase(remetente))
                    .map(EmailCorporativo::deItem).forEach(emails::add);
            cursor = pagina.lastEvaluatedKey();
        } while (!cursor.isEmpty());
        return emails;
    }

    private static Destinatario deItem(Map<String, AttributeValue> item) {
        AttributeValue nome = item.get("nome");
        return new Destinatario(item.get("email").s(), nome == null ? "" : nome.s());
    }

    static String pagina(String titulo, String saudacao, String corpoHtml) {
        return "<!doctype html><html lang=\"pt-BR\"><head><meta charset=\"UTF-8\"></head>"
                + "<body style=\"margin:0;background:#f2f4f7;font-family:Arial,sans-serif;color:#172033\">"
                + "<table role=\"presentation\" style=\"width:100%;padding:24px 12px\"><tr><td align=\"center\">"
                + "<table role=\"presentation\" style=\"max-width:680px;width:100%;background:#fff\">"
                + "<tr><td style=\"padding:32px 32px 16px\">"
                + "<span style=\"display:inline-block;background:#000;color:#fff;font-weight:bold;font-size:22px;letter-spacing:3px;padding:8px 15px\">ALUN</span>"
                + "<p style=\"font-size:11px;letter-spacing:2px;margin:12px 0 0\">FACULDADE DO ALUNO</p></td></tr>"
                + "<tr><td style=\"padding:0 32px 32px\"><p style=\"font-size:15px\">" + escapar(saudacao) + "</p>"
                + "<h1 style=\"font-size:22px;margin:18px 0\">" + escapar(titulo) + "</h1>" + corpoHtml
                + "<p style=\"font-size:13px;margin-top:28px\">Atenciosamente,<br><strong>Equipe de Qualidade Acadêmica</strong></p>"
                + "</td></tr><tr><td style=\"padding:18px 32px;border-top:1px solid #ddd;font-size:11px;color:#555\">"
                + "FACULDADE DO ALUNO · Comunicação automática de qualidade acadêmica.</td></tr></table>"
                + "</td></tr></table></body></html>";
    }

    static String escapar(String valor) {
        return valor.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
