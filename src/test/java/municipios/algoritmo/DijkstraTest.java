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
 * Pruebas de Dijkstra, que se usa como "verdad" para validar la heurística (#6),
 * comprobar que A* da el óptimo (#15) y comparar los resultados (#9).
 *
 * Grafo de juguete (km):   A-B 1, B-G 10
 *   A-C 2, C-G 2           Z está aislado
 *   B-D 1, D-A 1 (ciclo)
 *
 * El camino mínimo de A a G es A-C-G = 4 km (y no A-B-G = 11 km).
 */
class DijkstraTest {

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

    @Test
    void encuentraElCaminoMasCorto() {
        List<Municipio> camino = Dijkstra.caminoMinimo(grafo, a, g);

        assertEquals(List.of(a, c, g), camino);
        assertEquals(4.0, Dijkstra.costoMinimo(grafo, a, g), 1e-9);
    }

    @Test
    void distanciasDesdeTodosLosMunicipios() {
        Map<Municipio, Double> dist = Dijkstra.distanciasDesde(grafo, a);

        assertEquals(0.0, dist.get(a), 1e-9);
        assertEquals(4.0, dist.get(g), 1e-9);
        assertEquals(1.0, dist.get(b), 1e-9);
        assertEquals(2.0, dist.get(c), 1e-9);
        assertEquals(1.0, dist.get(d), 1e-9); // A-D está conectada directamente
        assertTrue(Double.isInfinite(dist.get(z)), "Z está aislado: no hay camino hasta él");
    }

    @Test
    void sinCaminoDevuelveInfinitoYCaminoVacio() {
        assertTrue(Double.isInfinite(Dijkstra.costoMinimo(grafo, a, z)));
        assertTrue(Dijkstra.caminoMinimo(grafo, a, z).isEmpty());
    }

    @Test
    void origenIgualDestino() {
        assertEquals(0.0, Dijkstra.costoMinimo(grafo, a, a), 1e-9);
        assertEquals(List.of(a), Dijkstra.caminoMinimo(grafo, a, a));
    }

    @Test
    void entradasInvalidasLanzanExcepcion() {
        assertThrows(IllegalArgumentException.class, () -> Dijkstra.costoMinimo(grafo, null, g));
        assertThrows(IllegalArgumentException.class, () -> Dijkstra.caminoMinimo(grafo, a, null));
        assertThrows(IllegalArgumentException.class,
                () -> Dijkstra.distanciasDesde(grafo, new Municipio("Fuera", 0, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> Dijkstra.caminoMinimo(grafo, new Municipio("Fuera", 0, 0), g));
    }

    @Test
    void elCaminoMinimoEsSimetrico() {
        assertEquals(4.0, Dijkstra.costoMinimo(grafo, g, a), 1e-9);
        assertEquals(List.of(g, c, a), Dijkstra.caminoMinimo(grafo, g, a));
    }

    /**
     * Cuenta las expansiones con la misma convención que usa A*: el destino se consulta,
     * pero no se cuenta como expandido. Para llegar a G por el óptimo A-C-G, Dijkstra
     * expande A, B, D y C porque antes de conocer el camino corto tiene que resolver
     * también la rama que pasa por B (1 + 10 = 11 km).
     */
    @Test
    void cuentaLasExpansiones() {
        assertEquals(4, Dijkstra.expansionesHasta(grafo, a, g));
        assertEquals(0, Dijkstra.expansionesHasta(grafo, a, a));
        assertEquals(1, Dijkstra.expansionesHasta(grafo, a, b));
    }

    @Test
    void expansionesHastaDestinoInalcanzableCuentaLoAlcanzable() {
        // Sin camino a Z se expanden todos los municipios conectados con A.
        assertEquals(5, Dijkstra.expansionesHasta(grafo, a, z));
    }

    @Test
    void nuncaExpandeMasNodosQueElNumeroDeMunicipios() {
        Grafo real = DatosReales.grafo();
        List<Municipio> municipios = real.getMunicipios();

        for (Municipio origen : municipios) {
            for (Municipio destino : municipios) {
                if (origen.equals(destino)) {
                    continue;
                }
                int cuenta = Dijkstra.expansionesHasta(real, origen, destino);
                assertTrue(cuenta >= 0 && cuenta <= municipios.size() - 1,
                        () -> "cuenta de expansiones fuera de rango en " + origen + " -> " + destino);
            }
        }
    }
}