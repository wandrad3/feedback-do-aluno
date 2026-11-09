package br.com.wstech.feedback;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.AlreadyExistsException;
import software.amazon.awssdk.services.sesv2.model.CreateEmailIdentityRequest;
import java.util.Map;
import java.util.regex.Pattern;

public final class CadastrarEmail implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]{1,64}@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private final DynamoDbClient dynamo;
    private final SesV2Client ses;
    private final String tabela;
    private final ObjectMapper json = new ObjectMapper();

    public CadastrarEmail() {
        this(DynamoDbClient.create(), SesV2Client.create(), System.getenv("TABELA_PROFESSORES"));
    }

    CadastrarEmail(DynamoDbClient dynamo, SesV2Client ses, String tabela) {
        this.dynamo = dynamo;
        this.ses = ses;
        this.tabela = tabela;
    }

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent evento, Context context) {
        String sub = AcessoProfessor.sub(evento);
        if (sub == null) return RespostaApi.criar(403, "{\"erro\":\"Acesso exclusivo para professores\"}");
        if ("DELETE".equals(evento.getRequestContext().getHttp().getMethod())) {
            dynamo.deleteItem(DeleteItemRequest.builder().tableName(tabela)
                    .key(Map.of("professorId", AttributeValue.fromS(sub))).build());
            return RespostaApi.criar(200, "{\"mensagem\":\"Cadastro removido\"}");
        }
        try {
            JsonNode body = json.readTree(evento.getBody());
            String email = body.path("email").asText("").strip().toLowerCase(java.util.Locale.ROOT);
            if (email.length() > 254 || !EMAIL.matcher(email).matches())
                return RespostaApi.criar(400, "{\"erro\":\"E-mail invalido\"}");
            try {
                ses.createEmailIdentity(CreateEmailIdentityRequest.builder().emailIdentity(email).build());
            } catch (AlreadyExistsException ignorado) {
                // Uma identidade já verificada pode ser reutilizada.
            }
            dynamo.putItem(PutItemRequest.builder().tableName(tabela).item(Map.of(
                    "professorId", AttributeValue.fromS(sub), "email", AttributeValue.fromS(email))).build());
            return RespostaApi.criar(202, "{\"mensagem\":\"Verifique seu e-mail e confirme o link enviado pela AWS para receber alertas\"}");
        } catch (com.fasterxml.jackson.core.JsonProcessingException | NullPointerException erro) {
            return RespostaApi.criar(400, "{\"erro\":\"JSON invalido\"}");
        }
    }
}
