package br.com.wstech.feedback;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvocationType;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;

public final class SolicitarRelatorio implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {
    private final LambdaClient lambda;
    private final String funcao;

    public SolicitarRelatorio() {
        this(LambdaClient.create(), System.getenv("FUNCAO_RELATORIO"));
    }

    SolicitarRelatorio(LambdaClient lambda, String funcao) {
        this.lambda = lambda;
        this.funcao = funcao;
    }

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent evento, Context context) {
        if (AcessoProfessor.sub(evento) == null)
            return RespostaApi.criar(403, "{\"erro\":\"Acesso exclusivo para professores\"}");
        lambda.invoke(InvokeRequest.builder().functionName(funcao).invocationType(InvocationType.EVENT)
                .payload(SdkBytes.fromUtf8String("{}")) .build());
        return RespostaApi.criar(202, "{\"mensagem\":\"Relatorio da semana anterior solicitado; envio por e-mail em processamento\"}");
    }
}
