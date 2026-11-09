package br.com.wstech.feedback;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class FeedbackTest {
    @Test void criaERecupera() {
        Feedback urgente = Feedback.criar("id", "texto", 3, Instant.parse("2026-11-08T03:00:00Z"));
        assertEquals("CRITICA", urgente.urgencia());
        assertEquals("2026-W45", urgente.semana());
        assertEquals(urgente, Feedback.deAtributos(urgente.atributos()));
        assertEquals("NORMAL", Feedback.criar("id2", "outro", 4, Instant.parse("2026-11-08T03:00:00Z")).urgencia());
        assertEquals("2026-W53", Feedback.semana(java.time.LocalDate.of(2027, 1, 3)));
    }

    @Test void construtoresPadraoEstaoDisponiveisParaLambda() throws Exception {
        String anterior = System.getProperty("aws.region");
        System.setProperty("aws.region", "us-east-2");
        try {
            ReceberAvaliacao receber = new ReceberAvaliacao();
            assertNotNull(receber);
            var campo = ReceberAvaliacao.class.getDeclaredField("ids");
            campo.setAccessible(true);
            assertNotNull(((java.util.function.Supplier<?>) campo.get(receber)).get());
            assertNotNull(new NotificarUrgencia());
            assertNotNull(new RelatorioSemanal());
            assertNotNull(new CadastrarEmail());
            assertNotNull(new SolicitarRelatorio());
        } finally {
            if (anterior == null) System.clearProperty("aws.region");
            else System.setProperty("aws.region", anterior);
        }
    }
}
