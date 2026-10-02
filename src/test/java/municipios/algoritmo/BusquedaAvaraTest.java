package municipios.algoritmo;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import municipios.modelo.Grafo;
import municipios.modelo.Municipio;

/**
 * Pruebas de BusquedaAvara sobre un grafo pequeño hecho a mano.
 *
 * Grafo (km):            h(n) hacia G:
 *   A-B 1, B-G 10          A=10 B=2 C=5 D=4 G=0
 *   A-C 2, C-G 2           Z está aislado
 *   B-D 1, D-A 1  (ciclo A-B-D)
 *
 * Trampa de greedy: de A a G la heurística prefiere B (h=2) y llega por
 * A-B-G (11 km), pero el óptimo es A-C-G (4 km).
 *
 * NOTA: se asume que ResultadoBusqueda tiene getCamino(), getCostoTotal(),
 * getNodosExpandidos() e isEncontrado(). Si tus getters se llaman distinto,
 * cámbialos aquí.
 */
class BusquedaAvaraTest {

    private static final Map<String, Double> H = Map.of(
            "A", 10.0, "B", 2.0, "C", 5.0, "D", 4.0, "G", 0.0, "Z", 99.0);

    private final Heuristica heuristica = (actual, destino) -> H.get(actual.getNombre());

    private Grafo grafo;
    private Municipio a, b, c, d, g, z;

    @BeforeEach
    void preparar() {
        grafo = new Grafo();
        a = new Municipio("A", 1.0, 1.0);
        b = new Municipio("B", 2.0, 2.0);
        c = new Municipio("C", 3.0, 3.0);
        d = new Municipio("D", 4.0, 4.0);
        g = new Municipio("G", 5.0, 5.0);
        z = new Municipio("Z", 6.0, 6.0);
        for (Municipio m : List.of(a, b, c, d, g, z)) {
            grafo.agregarMunicipio(m);
        }
        grafo.agregarConexion(a, b, 1);
        grafo.agregarConexion(b, g, 10);
        grafo.agregarConexion(a, c, 2);
        grafo.agregarConexion(c, g, 2);
        grafo.agregarConexion(b, d, 1);
        grafo.agregarConexion(d, a, 1);
    }

    private BusquedaAvara busqueda() {
        return new BusquedaAvara(grafo, heuristica);
    }

    @Test
    void caminoDirecto() {
        ResultadoBusqueda r = busqueda().buscar(c, g);
        assertTrue(r.isEncontrado());
        assertEquals(List.of(c, g), r.getCamino());
        assertEquals(2.0, r.getCostoTotal(), 1e-9);
        assertEquals(1, r.getNodosExpandidos()); // solo se expande C; la meta no cuenta
    }

    @Test
    void sinCaminoGrafoDesconectado() {
        ResultadoBusqueda r = busqueda().buscar(a, z);
        assertFalse(r.isEncontrado());
        assertTrue(r.getCamino().isEmpty());
    }

    @Test
    void origenIgualDestino() {
        ResultadoBusqueda r = busqueda().buscar(a, a);
        assertTrue(r.isEncontrado());
        assertEquals(List.of(a), r.getCamino());
        assertEquals(0.0, r.getCostoTotal(), 1e-9);
        assertEquals(0, r.getNodosExpandidos());
    }

    @Test
    void grafoConCicloTermina() {
        // Ciclo A-B-D-A: sin el conjunto de visitados esto no terminaría.
        ResultadoBusqueda r = assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> busqueda().buscar(d, g));
        assertTrue(r.isEncontrado());
        assertEquals(List.of(d, b, g), r.getCamino());
    }

    @Test
    void greedyNoEsOptimoMinimoLocal() {
        ResultadoBusqueda r = busqueda().buscar(a, g);
        assertTrue(r.isEncontrado());
        assertEquals(List.of(a, b, g), r.getCamino()); // guiado solo por h(n)
        assertEquals(11.0, r.getCostoTotal(), 1e-9);
        assertTrue(r.getCostoTotal() > 4.0, "el óptimo A-C-G cuesta 4 km; greedy no lo encuentra aquí");
    }

    @Test
    void municipioInexistenteConObjetosNull() {
        assertThrows(IllegalArgumentException.class, () -> busqueda().buscar((Municipio) null, g));
        assertThrows(IllegalArgumentException.class, () -> busqueda().buscar(a, (Municipio) null));
    }

    @Test
    void municipioInexistentePorNombre() {
        assertThrows(IllegalArgumentException.class, () -> busqueda().buscar("X", "G"));
        assertThrows(IllegalArgumentException.class, () -> busqueda().buscar("A", "X"));
    }

    @Test
    void buscarPorNombreIgnoraMayusculasYEspacios() {
        ResultadoBusqueda r = busqueda().buscar(" c ", "g");
        assertTrue(r.isEncontrado());
        assertEquals(List.of(c, g), r.getCamino());
    }
}