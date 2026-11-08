package br.com.wstech.feedback;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

public final class ReceberAvaliacao implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {
    private final DynamoDbClient dynamo;
    private final String tabela;
    private final Clock clock;
    private final Supplier<String> ids;
    private final ObjectMapper json = new ObjectMapper();

    public ReceberAvaliacao() {
        this(DynamoDbClient.create(), System.getenv("TABELA_AVALIACOES"), Clock.systemUTC(), () -> UUID.randomUUID().toString());
    }

    ReceberAvaliacao(DynamoDbClient dynamo, String tabela, Clock clock, Supplier<String> ids) {
        this.dynamo = dynamo;
        this.tabela = tabela;
        this.clock = clock;
        this.ids = ids;
    }

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent evento, Context context) {
        if (evento.getBody() == null) return resposta(400, "{\"erro\":\"JSON invalido\"}");
        try {
            JsonNode body = json.readTree(evento.getBody());
            JsonNode descricao = body.path("descricao");
            JsonNode nota = body.path("nota");
            if (!descricao.isTextual() || descricao.asText().isBlank() || descricao.asText().length() > 1000
                    || !nota.isIntegralNumber() || !nota.canConvertToInt() || nota.asInt() < 0 || nota.asInt() > 10) {
                return resposta(400, "{\"erro\":\"descricao deve ter 1 a 1000 caracteres e nota deve ser inteira de 0 a 10\"}");
            }
            Feedback feedback = Feedback.criar(ids.get(), descricao.asText().strip(), nota.asInt(), clock.instant());
            dynamo.putItem(PutItemRequest.builder().tableName(tabela).item(feedback.atributos()).build());
            return resposta(201, json.writeValueAsString(Map.of("id", feedback.id(), "urgencia", feedback.urgencia(), "enviadoEm", feedback.enviadoEm().toString())));
        } catch (com.fasterxml.jackson.core.JsonProcessingException | NullPointerException erro) {
            return resposta(400, "{\"erro\":\"JSON invalido\"}");
        }
    }

    private static APIGatewayV2HTTPResponse resposta(int status, String body) {
        return APIGatewayV2HTTPResponse.builder().withStatusCode(status).withHeaders(Map.of("content-type", "application/json; charset=utf-8")).withBody(body).build();
    }
}
