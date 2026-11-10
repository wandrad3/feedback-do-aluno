package br.com.wstech.feedback;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.GetEmailIdentityRequest;
import software.amazon.awssdk.services.sesv2.model.GetEmailIdentityResponse;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

class EmailCorporativoTest {
    @Test void enviaHtmlSeguroParaEmailsVerificados() {
        DynamoDbClient dynamo = mock(DynamoDbClient.class);
        SesV2Client ses = mock(SesV2Client.class);
        when(dynamo.scan(any(ScanRequest.class)))
                .thenReturn(ScanResponse.builder().items(Map.of("email", AttributeValue.fromS("prof@example.com")))
                        .lastEvaluatedKey(Map.of("professorId", AttributeValue.fromS("1"))).build())
                .thenReturn(ScanResponse.builder().items(
                        Map.of("email", AttributeValue.fromS("admin@example.com")),
                        Map.of("email", AttributeValue.fromS("pendente@example.com"))).build());
        when(ses.getEmailIdentity(any(GetEmailIdentityRequest.class)))
                .thenAnswer(invocation -> GetEmailIdentityResponse.builder().verifiedForSendingStatus(
                        !((GetEmailIdentityRequest) invocation.getArgument(0)).emailIdentity().equals("pendente@example.com")).build());
        new EmailCorporativo(dynamo, ses, "professores", "admin@example.com")
                .enviar("Aviso", "Alerta <urgente>", "Descrição: <script>\nTeste & revisão");
        var enviados = org.mockito.ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(ses, times(2)).sendEmail(enviados.capture());
        assertTrue(enviados.getAllValues().get(0).content().simple().body().html().data().contains("FACULDADE DO ALUNO"));
        assertTrue(enviados.getAllValues().get(0).content().simple().body().html().data().contains("background:#000;color:#fff"));
        assertTrue(enviados.getAllValues().get(0).content().simple().body().html().data().contains("&lt;script&gt;"));
        assertFalse(enviados.getAllValues().get(0).content().simple().body().html().data().contains("<script>"));
        assertEquals("Descrição: <script>\nTeste & revisão", enviados.getAllValues().get(0).content().simple().body().text().data());
        assertTrue(EmailCorporativo.pagina("<t>", "'\"&").contains("&#39;&quot;&amp;"));
    }

    @Test void recusaRemetenteNaoVerificado() {
        DynamoDbClient dynamo = mock(DynamoDbClient.class);
        SesV2Client ses = mock(SesV2Client.class);
        when(ses.getEmailIdentity(any(GetEmailIdentityRequest.class)))
                .thenReturn(GetEmailIdentityResponse.builder().verifiedForSendingStatus(false).build());
        assertThrows(IllegalStateException.class, () ->
                new EmailCorporativo(dynamo, ses, "professores", "admin@example.com")
                        .enviar("Aviso", "Alerta", "Texto"));
        verifyNoInteractions(dynamo);
    }
}
