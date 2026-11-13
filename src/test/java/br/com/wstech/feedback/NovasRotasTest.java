package br.com.wstech.feedback;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.AlreadyExistsException;
import software.amazon.awssdk.services.sesv2.model.CreateEmailIdentityRequest;
import software.amazon.awssdk.services.sesv2.model.GetEmailIdentityRequest;
import software.amazon.awssdk.services.sesv2.model.GetEmailIdentityResponse;
import java.util.Map;

class NovasRotasTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    private static APIGatewayV2HTTPEvent evento(String grupo, String metodo, String body) throws Exception {
        String payload = "{\"requestContext\":{\"http\":{\"method\":\"" + metodo
                + "\"},\"authorizer\":{\"jwt\":{\"claims\":{\"sub\":\"abc\",\"cognito:groups\":\"" + grupo
                + "\"}}}},\"body\":" + JSON.writeValueAsString(body) + "}";
        return JSON.readValue(payload, APIGatewayV2HTTPEvent.class);
    }

    @Test void restringeOperacoesAProfessores() throws Exception {
        DynamoDbClient dynamo = mock(DynamoDbClient.class);
        SesV2Client ses = mock(SesV2Client.class);
        LambdaClient lambda = mock(LambdaClient.class);
        assertEquals(403, new CadastrarEmail(dynamo, ses, "professores")
                .handleRequest(evento("Alunos", "POST", "{}"), null).getStatusCode());
        assertEquals(403, new SolicitarRelatorio(lambda, dynamo, ses, "professores", "relatorio")
                .handleRequest(evento("Alunos", "POST", "{}"), null).getStatusCode());
        assertNull(AcessoProfessor.sub(null));
        assertNull(AcessoProfessor.sub(new APIGatewayV2HTTPEvent()));
        for (String raw : new String[]{"{\"requestContext\":{}}",
                "{\"requestContext\":{\"authorizer\":{}}}",
                "{\"requestContext\":{\"authorizer\":{\"jwt\":{}}}}"}) {
            assertNull(AcessoProfessor.sub(JSON.readValue(raw, APIGatewayV2HTTPEvent.class)));
        }
        verifyNoInteractions(dynamo, ses, lambda);
    }

    @Test void cadastraEExcluiEmail() throws Exception {
        DynamoDbClient dynamo = mock(DynamoDbClient.class);
        SesV2Client ses = mock(SesV2Client.class);
        CadastrarEmail handler = new CadastrarEmail(dynamo, ses, "professores");
        assertEquals(400, handler.handleRequest(evento("[Professores]", "POST", "{\"email\":\"invalido\"}"), null).getStatusCode());
        assertEquals(400, handler.handleRequest(evento("Professores", "POST", "{\"email\":\""
                + "a".repeat(250) + "@x.co\"}"), null).getStatusCode());
        assertEquals(400, handler.handleRequest(evento("Professores", "POST", "{"), null).getStatusCode());
        assertEquals(400, handler.handleRequest(evento("Professores", "POST", "{\"email\":\"prof@example.com\",\"nome\":\"<script>\"}"), null).getStatusCode());
        assertEquals(202, handler.handleRequest(evento("Professores", "POST", "{\"email\":\" Prof@Example.com \"}"), null).getStatusCode());
        verify(ses).createEmailIdentity(any(CreateEmailIdentityRequest.class));
        var put = org.mockito.ArgumentCaptor.forClass(PutItemRequest.class);
        verify(dynamo).putItem(put.capture());
        assertEquals("prof@example.com", put.getValue().item().get("email").s());
        assertFalse(put.getValue().item().containsKey("nome"));
        when(ses.createEmailIdentity(any(CreateEmailIdentityRequest.class)))
                .thenThrow(AlreadyExistsException.builder().message("exists").build());
        assertEquals(202, handler.handleRequest(evento("Professores", "POST", "{\"email\":\"prof@example.com\",\"nome\":\" Ana  Silva \"}"), null).getStatusCode());
        verify(dynamo, times(2)).putItem(put.capture());
        assertEquals("Ana Silva", put.getAllValues().getLast().item().get("nome").s());
        assertEquals(200, handler.handleRequest(evento("Professores", "DELETE", ""), null).getStatusCode());
        verify(dynamo).deleteItem(any(DeleteItemRequest.class));
    }

    @Test void solicitaRelatorioAssincrono() throws Exception {
        LambdaClient lambda = mock(LambdaClient.class);
        DynamoDbClient dynamo = mock(DynamoDbClient.class);
        SesV2Client ses = mock(SesV2Client.class);
        SolicitarRelatorio handler = new SolicitarRelatorio(lambda, dynamo, ses, "professores", "relatorio");
        when(dynamo.getItem(any(GetItemRequest.class))).thenReturn(GetItemResponse.builder().build())
                .thenReturn(GetItemResponse.builder().item(Map.of("email", AttributeValue.fromS("prof@example.com"))).build());
        assertEquals(409, handler.handleRequest(evento("[Alunos, Professores]", "POST", "{}"), null).getStatusCode());
        when(ses.getEmailIdentity(any(GetEmailIdentityRequest.class)))
                .thenReturn(GetEmailIdentityResponse.builder().verifiedForSendingStatus(false).build())
                .thenReturn(GetEmailIdentityResponse.builder().verifiedForSendingStatus(true).build());
        assertEquals(409, handler.handleRequest(evento("[Alunos, Professores]", "POST", "{}"), null).getStatusCode());
        assertEquals(202, handler.handleRequest(evento("[Alunos, Professores]", "POST", "{}"), null).getStatusCode());
        var get = org.mockito.ArgumentCaptor.forClass(GetItemRequest.class);
        verify(dynamo, times(3)).getItem(get.capture());
        assertEquals("abc", get.getValue().key().get("professorId").s());
        var request = org.mockito.ArgumentCaptor.forClass(InvokeRequest.class);
        verify(lambda).invoke(request.capture());
        assertEquals("relatorio", request.getValue().functionName());
        assertEquals("{\"professorId\":\"abc\"}", request.getValue().payload().asUtf8String());
    }
}
