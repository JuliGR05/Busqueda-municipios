package municipios.algoritmo;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import municipios.DatosReales;
import municipios.modelo.Grafo;
import municipios.modelo.Municipio;

/**
 * Pruebas del reporte de admisibilidad y consistencia de la heurística (#6).
 *
 * <p>El reporte que se cita en el informe se prueba con los 20 municipios reales, y además
 * se comprueba que el validador sí detecta una heurística que <em>sí</em> falla, porque un
 * verificador que siempre dice "OK" no sirve de nada.</p>
 */
class ValidadorHeuristicaTest {

    private Grafo real;
    private DistanciaLineaRecta heuristica;
    private ValidadorHeuristica validador;

    @BeforeEach
    void preparar() {
        real = DatosReales.grafo();
        heuristica = new DistanciaLineaRecta();
        validador = new ValidadorHeuristica(real, heuristica);
    }

    @Test
    void laHeuristicaRealEsAdmisibleYConsistenteEnLos20Municipios() {
        assertEquals(DatosReales.TOTAL_MUNICIPIOS, real.getMunicipios().size());

        assertTrue(validador.validarBasicas().isEmpty(),
                () -> "verificaciones básicas: " + validador.validarBasicas());
        assertTrue(validador.validarAdmisibilidad().isEmpty(),
                () -> "admisibilidad: " + validador.validarAdmisibilidad());
        assertTrue(validador.validarConsistencia().isEmpty(),
                () -> "consistencia: " + validador.validarConsistencia());
    }

    @Test
    void elReporteTerminaDiciendoQueLaHeuristicaEsValida() {
        String reporte = validador.generarReporte();

        assertTrue(reporte.contains("DistanciaLineaRecta"), reporte);
        assertTrue(reporte.contains("RESULTADO: heurística válida (admisible y consistente). A* será óptimo."),
                reporte);
        assertTrue(reporte.contains("Santa Marta-Pasto"), reporte);
    }

    /**
     * El ejemplo que pide el issue #6: Santa Marta-Pasto debe tener una línea recta
     * plausible y menor que el camino real por carretera que da Dijkstra.
     */
    @Test
    void santaMartaPastoEsPlausibleYMenorQueLaCarretera() {
        Municipio santaMarta = real.buscarPorNombre("Santa Marta");
        Municipio pasto = real.buscarPorNombre("Pasto");
        assertNotNull(santaMarta);
        assertNotNull(pasto);

        double recto = heuristica.h(santaMarta, pasto);
        double carretera = Dijkstra.costoMinimo(real, santaMarta, pasto);

        assertEquals(1166.1, recto, 1.0, "unos 1166 km en línea recta según la referencia del issue #6");
        assertTrue(recto < carretera,
                () -> "la línea recta (" + recto + ") debe ser menor que la carretera (" + carretera + ")");
    }

    /** Una heurística que sobreestima tiene que ser reportada como no admisible. */
    @Test
    void detectaUnaHeuristicaNoAdmisible() {
        Heuristica sobreestimada = (actual, destino) -> heuristica.h(actual, destino) + 5000.0;
        ValidadorHeuristica v = new ValidadorHeuristica(real, sobreestimada);

        List<String> fallos = v.validarAdmisibilidad();
        assertFalse(fallos.isEmpty(), "una heurística inflada +5000 km no puede ser admisible");
        assertTrue(fallos.stream().anyMatch(f -> f.contains("no admisible")), fallos::toString);
        assertTrue(v.generarReporte().contains("hay fallos"));
    }

    /**
     * Una heurística que no respeta la desigualdad triangular rompe la consistencia: para la
     * arista P-Q de 1 km, h(P, M) = 400 pero h(Q, M) = 0, así que 400 > 1 + 0.
     *
     * <p>Con solo tres municipios se puede aislar la inconsistencia sin que la heurística sea
     * admisible, porque el camino real P→M son 2 km y 400 los sobreestima; lo que se comprueba
     * aquí es que las dos verificaciones son independientes y ambas reportan el fallo.</p>
     */
    @Test
    void detectaUnaHeuristicaInconsistente() {
        Grafo pequeno = new Grafo();
        Municipio p = new Municipio("P", 5.0, -75.0);
        Municipio q = new Municipio("Q", 5.1, -75.0);
        Municipio meta = new Municipio("M", 5.2, -75.0);
        pequeno.agregarMunicipio(p);
        pequeno.agregarMunicipio(q);
        pequeno.agregarMunicipio(meta);
        pequeno.agregarConexion(p, q, 1);
        pequeno.agregarConexion(q, meta, 1);

        Map<String, Double> valores = Map.of("P", 400.0, "Q", 0.0, "M", 0.0);
        Heuristica incoherente = (actual, destino) -> valores.get(actual.getNombre());
        ValidadorHeuristica v = new ValidadorHeuristica(pequeno, incoherente);

        List<String> fallos = v.validarConsistencia();
        assertFalse(fallos.isEmpty(), "h(P,M)=400 no puede ser <= 1 + h(Q,M)=1");
        assertTrue(fallos.stream().anyMatch(f -> f.contains("no consistente")), fallos::toString);

        // También sobreestima el camino real (2 km), así que las dos verificaciones lo detectan.
        assertFalse(v.validarAdmisibilidad().isEmpty(),
                "h(P,M)=400 sobreestima el camino real de 2 km");
    }

    /**
     * Una heurística admisible pero inconsistente, en un grafo con dos caminos a la meta.
     *
     * <pre>
     *   A -1- B -10- G        A -5- G
     * </pre>
     *
     * El camino real más corto de A a G son 5 km. Con h(A,G) = 4 la heurística es admisible
     * (4 ≤ 5), pero en la arista A-B se incumple la consistencia: 4 > 1 + h(B,G) = 1.
     *
     * <p>h devuelve 0 para cualquier par que no sea (A, G), de forma que la inadmisibilidad no
     * se falsea en los demás pares y lo que se comprueba es solo la inconsistencia.</p>
     */
    @Test
    void detectaInconsistenciaEnUnaHeuristicaQueSiEsAdmisible() {
        Grafo pequeno = new Grafo();
        Municipio p = new Municipio("A", 5.0, -75.0);
        Municipio q = new Municipio("B", 5.1, -75.0);
        Municipio meta = new Municipio("G", 5.2, -75.0);
        pequeno.agregarMunicipio(p);
        pequeno.agregarMunicipio(q);
        pequeno.agregarMunicipio(meta);
        pequeno.agregarConexion(p, q, 1);
        pequeno.agregarConexion(q, meta, 10);
        pequeno.agregarConexion(p, meta, 5);

        assertEquals(5.0, Dijkstra.costoMinimo(pequeno, p, meta), 1e-9);

        Heuristica admisiblePeroInconsistente = (actual, destino) ->
                actual.equals(p) && destino.equals(meta) ? 4.0 : 0.0;
        ValidadorHeuristica v = new ValidadorHeuristica(pequeno, admisiblePeroInconsistente);

        assertTrue(v.validarAdmisibilidad().isEmpty(),
                () -> "h(A,G)=4 no sobreestima el camino real de 5 km. Fallos: "
                        + v.validarAdmisibilidad());
        List<String> fallos = v.validarConsistencia();
        assertFalse(fallos.isEmpty(), "h(A,G)=4 > c(A,B)=1 + h(B,G)=0: sí es inconsistente");
        assertTrue(fallos.stream().anyMatch(f -> f.contains("no consistente")), fallos::toString);
    }

    @Test
    void elReporteListaLasVerificacionesQueSeHacen() {
        String reporte = validador.generarReporte();

        assertTrue(reporte.contains("Básicas"), reporte);
        assertTrue(reporte.contains("Admisibilidad"), reporte);
        assertTrue(reporte.contains("Consistencia"), reporte);
        assertTrue(reporte.contains("400 pares"), "20 x 20 pares: " + reporte);
    }
}