package br.com.wstech.feedback;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import java.util.Map;

final class AcessoProfessor {
    private AcessoProfessor() {}

    static String sub(APIGatewayV2HTTPEvent evento) {
        if (evento == null || evento.getRequestContext() == null
                || evento.getRequestContext().getAuthorizer() == null
                || evento.getRequestContext().getAuthorizer().getJwt() == null) return null;
        Map<String, String> claims = evento.getRequestContext().getAuthorizer().getJwt().getClaims();
        if (claims == null || !"Professores".equals(claims.get("cognito:groups"))
                && !java.util.Arrays.asList(claims.getOrDefault("cognito:groups", "")
                        .replace("[", "").replace("]", "").split(",\\s*")).contains("Professores")) return null;
        return claims.get("sub");
    }
}
