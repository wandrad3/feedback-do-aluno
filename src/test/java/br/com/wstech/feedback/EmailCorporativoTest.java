package br.com.wstech.feedback;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
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
                .thenReturn(ScanResponse.builder().items(Map.of("email", AttributeValue.fromS("prof@example.com"), "nome", AttributeValue.fromS("Ana <Silva>")))
                        .lastEvaluatedKey(Map.of("professorId", AttributeValue.fromS("1"))).build())
                .thenReturn(ScanResponse.builder().items(
                        Map.of("email", AttributeValue.fromS("admin@example.com")),
                        Map.of("email", AttributeValue.fromS("pendente@example.com"))).build());
        when(ses.getEmailIdentity(any(GetEmailIdentityRequest.class)))
                .thenAnswer(invocation -> GetEmailIdentityResponse.builder().verifiedForSendingStatus(
                        !((GetEmailIdentityRequest) invocation.getArgument(0)).emailIdentity().equals("pendente@example.com")).build());
        new EmailCorporativo(dynamo, ses, "professores", "admin@example.com")
                .enviar("Aviso", "Alerta <urgente>", "Descrição: <script>\nTeste & revisão", "<table><tr><td>&lt;script&gt;</td></tr></table>");
        var enviados = org.mockito.ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(ses, times(2)).sendEmail(enviados.capture());
        assertTrue(enviados.getAllValues().get(0).content().simple().body().html().data().contains("FACULDADE DO ALUNO"));
        assertTrue(enviados.getAllValues().get(0).content().simple().body().html().data().contains("background:#000;color:#fff"));
        assertTrue(enviados.getAllValues().get(0).content().simple().body().html().data().contains("&lt;script&gt;"));
        assertFalse(enviados.getAllValues().get(0).content().simple().body().html().data().contains("<script>"));
        assertEquals("Prezada equipe,\n\nDescrição: <script>\nTeste & revisão", enviados.getAllValues().get(0).content().simple().body().text().data());
        assertTrue(enviados.getAllValues().get(1).content().simple().body().html().data().contains("Prezado, professor Ana &lt;Silva&gt;"));
        assertTrue(EmailCorporativo.pagina("<t>", "'\"&", "<p>ok</p>").contains("&#39;&quot;&amp;"));
    }

    @Test void recusaRemetenteNaoVerificado() {
        DynamoDbClient dynamo = mock(DynamoDbClient.class);
        SesV2Client ses = mock(SesV2Client.class);
        when(ses.getEmailIdentity(any(GetEmailIdentityRequest.class)))
                .thenReturn(GetEmailIdentityResponse.builder().verifiedForSendingStatus(false).build());
        assertThrows(IllegalStateException.class, () ->
                new EmailCorporativo(dynamo, ses, "professores", "admin@example.com")
                        .enviar("Aviso", "Alerta", "Texto", "<p>Texto</p>"));
        verifyNoInteractions(dynamo);
    }

    @Test void incluiAdministradorAdicionalSemDuplicarProfessor() {
        DynamoDbClient dynamo = mock(DynamoDbClient.class);
        SesV2Client ses = mock(SesV2Client.class);
        when(dynamo.scan(any(ScanRequest.class))).thenReturn(ScanResponse.builder().items(
                Map.of("email", AttributeValue.fromS("segundo@example.com")),
                Map.of("email", AttributeValue.fromS("prof@example.com"))).build());
        when(ses.getEmailIdentity(any(GetEmailIdentityRequest.class)))
                .thenReturn(GetEmailIdentityResponse.builder().verifiedForSendingStatus(true).build());
        new EmailCorporativo(dynamo, ses, "professores", "admin@example.com", "segundo@example.com", "Nome Admin")
                .enviar("Aviso", "Alerta", "Corpo", "<p>Corpo</p>");
        var captor = org.mockito.ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(ses, times(3)).sendEmail(captor.capture());
        assertEquals(List.of("admin@example.com"), captor.getAllValues().get(0).destination().toAddresses());
        assertEquals(List.of("segundo@example.com"), captor.getAllValues().get(1).destination().toAddresses());
        assertTrue(captor.getAllValues().get(1).content().simple().body().text().data()
                .startsWith("Prezado, Nome Admin,"));
        assertEquals(List.of("prof@example.com"), captor.getAllValues().get(2).destination().toAddresses());
        new EmailCorporativo(dynamo, ses, "professores", "admin@example.com", "ADMIN@example.com", "Mesmo")
                .enviar("Aviso", "Alerta", "Corpo", "<p>Corpo</p>");
    }

    @Test void enviaApenasAoProfessorSolicitante() {
        DynamoDbClient dynamo = mock(DynamoDbClient.class);
        SesV2Client ses = mock(SesV2Client.class);
        when(dynamo.getItem(any(GetItemRequest.class))).thenReturn(GetItemResponse.builder()
                .item(Map.of("email", AttributeValue.fromS("prof@example.com"), "nome", AttributeValue.fromS("Maria"))).build());
        when(ses.getEmailIdentity(any(GetEmailIdentityRequest.class)))
                .thenReturn(GetEmailIdentityResponse.builder().verifiedForSendingStatus(true).build());
        new EmailCorporativo(dynamo, ses, "professores", "admin@example.com")
                .enviar("Relatório", "Título", "Corpo", "<p>Corpo</p>", "abc");
        var captor = org.mockito.ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(ses).sendEmail(captor.capture());
        assertEquals(List.of("prof@example.com"), captor.getValue().destination().toAddresses());
        assertTrue(captor.getValue().content().simple().body().text().data().startsWith("Prezado, professor Maria,"));
        verify(dynamo, never()).scan(any(ScanRequest.class));
    }

    @Test void recusaCadastroAusenteOuPendente() {
        DynamoDbClient dynamo = mock(DynamoDbClient.class);
        SesV2Client ses = mock(SesV2Client.class);
        when(ses.getEmailIdentity(any(GetEmailIdentityRequest.class)))
                .thenReturn(GetEmailIdentityResponse.builder().verifiedForSendingStatus(true).build());
        EmailCorporativo email = new EmailCorporativo(dynamo, ses, "professores", "admin@example.com");
        when(dynamo.getItem(any(GetItemRequest.class))).thenReturn(GetItemResponse.builder().build())
                .thenReturn(GetItemResponse.builder().item(Map.of("nome", AttributeValue.fromS("Ana"))).build())
                .thenReturn(GetItemResponse.builder().item(Map.of("email", AttributeValue.fromS("prof@example.com"))).build());
        assertThrows(IllegalStateException.class, () -> email.enviar("a", "b", "c", "d", "abc"));
        assertThrows(IllegalStateException.class, () -> email.enviar("a", "b", "c", "d", "abc"));
        when(ses.getEmailIdentity(any(GetEmailIdentityRequest.class))).thenAnswer(invocation ->
                GetEmailIdentityResponse.builder().verifiedForSendingStatus(
                        ((GetEmailIdentityRequest) invocation.getArgument(0)).emailIdentity().equals("admin@example.com")).build());
        assertThrows(IllegalStateException.class, () -> email.enviar("a", "b", "c", "d", "abc"));
    }
}
