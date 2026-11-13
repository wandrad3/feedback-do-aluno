package br.com.wstech.feedback;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import software.amazon.awssdk.core.SdkBytes;
import java.util.Map;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvocationType;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.GetEmailIdentityRequest;

public final class SolicitarRelatorio implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {
    private final LambdaClient lambda;
    private final DynamoDbClient dynamo;
    private final SesV2Client ses;
    private final String tabela;
    private final String funcao;

    public SolicitarRelatorio() {
        this(LambdaClient.create(), DynamoDbClient.create(), SesV2Client.create(),
                System.getenv("TABELA_PROFESSORES"), System.getenv("FUNCAO_RELATORIO"));
    }

    SolicitarRelatorio(LambdaClient lambda, DynamoDbClient dynamo, SesV2Client ses, String tabela, String funcao) {
        this.lambda = lambda;
        this.dynamo = dynamo;
        this.ses = ses;
        this.tabela = tabela;
        this.funcao = funcao;
    }

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent evento, Context context) {
        String professorId = AcessoProfessor.sub(evento);
        if (professorId == null)
            return RespostaApi.criar(403, "{\"erro\":\"Acesso exclusivo para professores\"}");
        Map<String, AttributeValue> cadastro = dynamo.getItem(GetItemRequest.builder().tableName(tabela)
                .key(Map.of("professorId", AttributeValue.fromS(professorId))).build()).item();
        if (!cadastro.containsKey("email"))
            return RespostaApi.criar(409, "{\"erro\":\"Cadastre seu e-mail antes de solicitar o relatorio\"}");
        if (!ses.getEmailIdentity(GetEmailIdentityRequest.builder()
                .emailIdentity(cadastro.get("email").s()).build()).verifiedForSendingStatus())
            return RespostaApi.criar(409, "{\"erro\":\"Confirme o link de verificacao do e-mail antes de solicitar o relatorio\"}");
        lambda.invoke(InvokeRequest.builder().functionName(funcao).invocationType(InvocationType.EVENT)
                .payload(SdkBytes.fromUtf8String("{\"professorId\":\"" + professorId + "\"}")) .build());
        return RespostaApi.criar(202, "{\"mensagem\":\"Relatorio da semana anterior solicitado; envio por e-mail em processamento\"}");
    }
}
