package br.com.wstech.feedback;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import java.util.Map;

final class RespostaApi {
    private RespostaApi() {}

    static APIGatewayV2HTTPResponse criar(int status, String corpo) {
        return APIGatewayV2HTTPResponse.builder().withStatusCode(status)
                .withHeaders(Map.of("content-type", "application/json; charset=utf-8"))
                .withBody(corpo).build();
    }
}
