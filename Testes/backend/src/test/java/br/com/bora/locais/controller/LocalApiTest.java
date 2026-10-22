package br.com.bora.locais.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Testes da API do grafo de locais. Usam um banco H2 EM MEMORIA (nao tocam em
 * backend/data/bora) e o GrafoSeeder popula 10 regioes, 70 locais e 133 arestas.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:bora-test;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.h2.console.enabled=false"
})
@AutoConfigureMockMvc
class LocalApiTest {

    @Autowired
    MockMvc mvc;

    // ---------- health e grafo ----------

    @Test
    @DisplayName("/api/health informa status ok e as contagens do seed")
    void health() throws Exception {
        mvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ok"))
            .andExpect(jsonPath("$.regioes").value(10))
            .andExpect(jsonPath("$.locais").value(70))
            .andExpect(jsonPath("$.arestas").value(133));
    }

    @Test
    @DisplayName("/api/grafo traz tipo 2, 10 regioes, 70 locais e 133 arestas")
    void grafoCompleto() throws Exception {
        mvc.perform(get("/api/grafo"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tipo").value(2))
            .andExpect(jsonPath("$.regioes", hasSize(10)))
            .andExpect(jsonPath("$.locais", hasSize(70)))
            .andExpect(jsonPath("$.arestas", hasSize(133)));
    }

    // ---------- busca de locais ----------

    @Test
    @DisplayName("/api/locais sem filtro devolve os 70 locais")
    void todosOsLocais() throws Exception {
        mvc.perform(get("/api/locais"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(70)));
    }

    @Test
    @DisplayName("Busca por nome ignora maiusculas/minusculas")
    void buscaPorNome() throws Exception {
        mvc.perform(get("/api/locais").param("q", "SESC"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(5)))
            .andExpect(jsonPath("$[*].nome", hasItem("Sesc Ipiranga")));
    }

    @Test
    @DisplayName("Filtro por categoria devolve so locais daquela categoria")
    void filtroPorCategoria() throws Exception {
        mvc.perform(get("/api/locais").param("categoria", "bar"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(9)))
            .andExpect(jsonPath("$[*].categoria", org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("Bar"))));
    }

    @Test
    @DisplayName("Nome + categoria combinam os dois filtros")
    void nomeECategoria() throws Exception {
        mvc.perform(get("/api/locais").param("q", "sesc").param("categoria", "Cultura"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(5)));
        mvc.perform(get("/api/locais").param("q", "sesc").param("categoria", "Bar"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("Busca sem resultado devolve lista vazia, nao erro")
    void buscaSemResultado() throws Exception {
        mvc.perform(get("/api/locais").param("q", "zzzzinexistentezzzz"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("Parametro q maior que 80 caracteres vira 400")
    void buscaMuitoLonga() throws Exception {
        mvc.perform(get("/api/locais").param("q", "a".repeat(81)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("/api/locais/categorias lista as 12 categorias distintas")
    void categorias() throws Exception {
        mvc.perform(get("/api/locais/categorias"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(12)))
            .andExpect(jsonPath("$", hasItem("Bar")))
            .andExpect(jsonPath("$", hasItem("Cultura")));
    }

    // ---------- local por id ----------

    @Test
    @DisplayName("/api/locais/{id} devolve o local certo")
    void localPorId() throws Exception {
        mvc.perform(get("/api/locais/12"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(12))
            .andExpect(jsonPath("$.nome").value("Sesc Ipiranga"))
            .andExpect(jsonPath("$.categoria").value("Cultura"));
    }

    @Test
    @DisplayName("Local inexistente devolve 404")
    void localInexistente() throws Exception {
        mvc.perform(get("/api/locais/999999"))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Id nao numerico devolve 400, nao 500")
    void idInvalido() throws Exception {
        mvc.perform(get("/api/locais/abc"))
            .andExpect(status().isBadRequest());
    }

    // ---------- vizinhos e proximos ----------

    @Test
    @DisplayName("Vizinhos do local 48: 4 vizinhos, ordenados por distancia")
    void vizinhos() throws Exception {
        mvc.perform(get("/api/locais/48/vizinhos"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(4)))
            .andExpect(jsonPath("$[*].local.id", hasItem(49)))
            .andExpect(jsonPath("$[0].km").value(0.09))
            .andExpect(jsonPath("$[3].km").value(0.42));
    }

    @Test
    @DisplayName("Local inexistente nao tem vizinhos (lista vazia)")
    void vizinhosDeLocalInexistente() throws Exception {
        mvc.perform(get("/api/locais/999999/vizinhos"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("Proximos: respeita o raio, exclui a origem e vem ordenado")
    void proximos() throws Exception {
        mvc.perform(get("/api/locais/48/proximos").param("raio", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
            .andExpect(jsonPath("$[*].km", org.hamcrest.Matchers.everyItem(lessThanOrEqualTo(1.0))))
            .andExpect(jsonPath("$[*].local.id", org.hamcrest.Matchers.not(hasItem(48))));
    }

    @Test
    @DisplayName("Proximos com raio maior traz pelo menos tantos locais quanto o menor")
    void proximosRaioMaior() throws Exception {
        int pequeno = contar("/api/locais/48/proximos?raio=1");
        int grande = contar("/api/locais/48/proximos?raio=5");
        org.junit.jupiter.api.Assertions.assertTrue(grande >= pequeno);
    }

    @Test
    @DisplayName("Raio fora da faixa (0 ou 51) devolve 400")
    void raioInvalido() throws Exception {
        mvc.perform(get("/api/locais/48/proximos").param("raio", "0"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/locais/48/proximos").param("raio", "51"))
            .andExpect(status().isBadRequest());
    }

    // ---------- rota (Dijkstra) ----------

    @Test
    @DisplayName("Rota entre vizinhos diretos (48 -> 49) nao passa de 0,09 km")
    void rotaEntreVizinhos() throws Exception {
        mvc.perform(get("/api/rota").param("de", "48").param("para", "49"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.encontrada").value(true))
            .andExpect(jsonPath("$.distanciaKm", lessThanOrEqualTo(0.09)))
            .andExpect(jsonPath("$.caminho[0].id").value(48))
            .andExpect(jsonPath("$.caminho[-1:].id", contains(49)));
    }

    @Test
    @DisplayName("Rota do exemplo do README (Sesc Ipiranga -> Kaskata's) mede 1,6 km")
    void rotaMultiplosSaltos() throws Exception {
        mvc.perform(get("/api/rota").param("de", "12").param("para", "26"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.encontrada").value(true))
            .andExpect(jsonPath("$.distanciaKm").value(1.6))
            .andExpect(jsonPath("$.caminho[0].id").value(12))
            .andExpect(jsonPath("$.caminho[-1:].id", contains(26)))
            // N locais no caminho => N-1 arestas
            .andExpect(jsonPath("$.arestas.length()").value(
                org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("Rota de um local para ele mesmo tem distancia 0")
    void rotaParaSiMesmo() throws Exception {
        mvc.perform(get("/api/rota").param("de", "10").param("para", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.encontrada").value(true))
            .andExpect(jsonPath("$.distanciaKm").value(0.0))
            .andExpect(jsonPath("$.caminho", hasSize(1)));
    }

    @Test
    @DisplayName("Locais em partes desconectadas do grafo (10 e 27) nao tem rota")
    void rotaEntreComponentesDistintos() throws Exception {
        mvc.perform(get("/api/rota").param("de", "10").param("para", "27"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.encontrada").value(false))
            .andExpect(jsonPath("$.caminho", hasSize(0)));
    }

    @Test
    @DisplayName("Rota com id inexistente devolve encontrada=false")
    void rotaComIdInexistente() throws Exception {
        mvc.perform(get("/api/rota").param("de", "10").param("para", "999999"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.encontrada").value(false));
    }

    @Test
    @DisplayName("Rota sem parametros obrigatorios devolve 400")
    void rotaSemParametros() throws Exception {
        mvc.perform(get("/api/rota"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/rota").param("de", "10"))
            .andExpect(status().isBadRequest());
    }

    // ---------- auxiliar ----------

    private int contar(String url) throws Exception {
        String corpo = mvc.perform(get(url)).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(corpo).size();
    }
}
