package br.com.bora.locais.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import br.com.bora.locais.model.Local;
import br.com.bora.locais.model.Regiao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Teste unitario puro (sem Spring, sem banco) da formula de Haversine. */
class GrafoServiceTest {

    private static final Regiao REGIAO = new Regiao(1L, "Teste");

    private static Local local(double lat, double lon) {
        return new Local(1L, "X", "Geral", REGIAO, lat, lon);
    }

    @Test
    @DisplayName("Distancia de um ponto para ele mesmo e 0")
    void mesmoPonto() {
        assertEquals(0.0, GrafoService.haversine(local(-23.55, -46.63), local(-23.55, -46.63)));
    }

    @Test
    @DisplayName("1 grau de latitude equivale a cerca de 111,19 km")
    void umGrauDeLatitude() {
        assertEquals(111.19, GrafoService.haversine(local(0, 0), local(1, 0)), 0.01);
    }

    @Test
    @DisplayName("A distancia e simetrica (A->B == B->A)")
    void simetrica() {
        Local a = local(-23.5839303, -46.6080571);
        Local b = local(-23.5820993, -46.6441606);
        assertEquals(GrafoService.haversine(a, b), GrafoService.haversine(b, a));
    }
}
