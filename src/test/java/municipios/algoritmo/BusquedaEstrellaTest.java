package municipios.algoritmo;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import municipios.DatosReales;
import municipios.modelo.Grafo;
import municipios.modelo.Municipio;

/**
 * Pruebas de BusquedaEstrella sobre un grafo pequeño hecho a mano.
 *
 * Grafo (km):
 *   A-B 1, B-G 10
 *   A-C 2, C-G 2
 *   B-D 1, D-A 1
 *
 * El camino óptimo de A a G es A-C-G = 4 km.
 * A* debe encontrar este camino utilizando f(n) = g(n) + h(n).
 */
class BusquedaEstrellaTest {

    private static final Map<String, Double> H = Map.of(
            "A", 10.0,
            "B", 2.0,
            "C", 5.0,
            "D", 4.0,
            "G", 0.0,
            "Z", 99.0
    );

    private final Heuristica heuristica =
            (actual, destino) -> H.get(actual.getNombre());

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

    private BusquedaEstrella busqueda() {
        return new BusquedaEstrella(grafo, heuristica);
    }

    @Test
    void encuentraCaminoOptimo() {
        ResultadoBusqueda r = busqueda().buscar(a, g);

        assertTrue(r.isEncontrado());
        assertEquals(List.of(a, c, g), r.getCamino());
        assertEquals(4.0, r.getCostoTotal(), 1e-9);
    }

    @Test
    void caminoDirecto() {
        ResultadoBusqueda r = busqueda().buscar(c, g);

        assertTrue(r.isEncontrado());
        assertEquals(List.of(c, g), r.getCamino());
        assertEquals(2.0, r.getCostoTotal(), 1e-9);
        assertEquals(1, r.getNodosExpandidos());
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
        ResultadoBusqueda r = assertTimeoutPreemptively(
                Duration.ofSeconds(2),
                () -> busqueda().buscar(d, g)
        );

        assertTrue(r.isEncontrado());
        assertEquals(List.of(d, b, g), r.getCamino());
    }

    @Test
    void municipioInexistenteConObjetosNull() {
        assertThrows(
                IllegalArgumentException.class,
                () -> busqueda().buscar((Municipio) null, g)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> busqueda().buscar(a, (Municipio) null)
        );
    }

    @Test
    void municipioInexistentePorNombre() {
        assertThrows(
                IllegalArgumentException.class,
                () -> busqueda().buscar("X", "G")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> busqueda().buscar("A", "X")
        );
    }

    @Test
    void buscarPorNombreIgnoraMayusculasYEspacios() {
        ResultadoBusqueda r = busqueda().buscar(" c ", "g");

        assertTrue(r.isEncontrado());
        assertEquals(List.of(c, g), r.getCamino());
    }
    @Test
    void costoAEstrellaCoincideConDijkstra() {
        ResultadoBusqueda resultadoAEstrella = busqueda().buscar(a, g);

        double costoDijkstra = Dijkstra.costoMinimo(grafo, a, g);

        assertTrue(resultadoAEstrella.isEncontrado());
        assertEquals(
                costoDijkstra,
                resultadoAEstrella.getCostoTotal(),
                1e-9
        );
    }

    /**
     * En el mismo grafo donde la búsqueda avara se equivoca (devuelve A-B-G de 11 km),
     * A* debe encontrar el óptimo A-C-G de 4 km. Es el caso que demuestra para qué sirve
     * sumar g(n) a la heurística.
     */
    @Test
    void enElGrafoDondeGreedyFallaEncuentraElOptimo() {
        ResultadoBusqueda avara = new BusquedaAvara(grafo, heuristica).buscar(a, g);
        ResultadoBusqueda estrella = busqueda().buscar(a, g);

        assertEquals(List.of(a, b, g), avara.getCamino());
        assertEquals(11.0, avara.getCostoTotal(), 1e-9);

        assertEquals(List.of(a, c, g), estrella.getCamino());
        assertEquals(4.0, estrella.getCostoTotal(), 1e-9);
        assertTrue(estrella.getCostoTotal() < avara.getCostoTotal(),
                "A* debe encontrar un camino más corto que la búsqueda avara en este grafo");
    }

    /**
 * Grafo usado para probar la reaperción de nodos:
 *
     * <pre>
     *   A --20-- X --30-- D
     *    \                /
     *     1 ---- Z ------
     *           (2)
     * </pre>
     *
     * <p>El óptimo es A-Z-X-D = 1 + 2 + 30 = 33 km; ir directo por X cuesta 20 + 30 = 50 km.
     * La arista A-X es cara y la de A-Z es barata, así que si la heurística deja Z con un
     * {@code f} mayor que el de X, A* cierra X <em>antes</em> de conocer el camino barato y
     * después tiene que reabrirlo. Para que eso ocurra la arista directa debe ser más cara
     * que el rodeo por Z (20 &gt; 1 + 2), y para que Z siga expandiéndose después el tramo
     * que queda hacia la meta tiene que ser largo (30 km), porque es lo que le permite a h(Z, D)
     * ser grande sin sobreestimar el camino real.</p>
     */
    private static final class GrafoReapertura {

        final Grafo grafo = new Grafo();
        final Municipio a = new Municipio("A", 1.0, 1.0);
        final Municipio x = new Municipio("X", 2.0, 2.0);
        final Municipio z = new Municipio("Z", 3.0, 3.0);
        final Municipio d = new Municipio("D", 4.0, 4.0);

        GrafoReapertura() {
            grafo.agregarMunicipio(a);
            grafo.agregarMunicipio(x);
            grafo.agregarMunicipio(z);
            grafo.agregarMunicipio(d);
            grafo.agregarConexion(a, x, 20);
            grafo.agregarConexion(a, z, 1);
            grafo.agregarConexion(z, x, 2);
            grafo.agregarConexion(x, d, 30);
        }
    }

    /**
     * Heurística con valores solo cuando el destino es {@code nombreDestino}, y 0 en cualquier
     * otro destino. Así las dos heurísticas del caso se pueden comparar con la misma estructura
     * sin falsear las verificaciones de admisibilidad y consistencia.
     *
     * @param nombreDestino municipio al que se dirige la búsqueda
     * @param valores       h(n, nombreDestino) por nombre de municipio
     * @return la heurística construida
     */
    private static Heuristica heuristicaHacia(String nombreDestino, Map<String, Double> valores) {
        return (actual, meta) -> meta.getNombre().equals(nombreDestino)
                ? valores.getOrDefault(actual.getNombre(), 0.0)
                : 0.0;
    }

    /** Inconsistente en la arista Z-A (30 &gt; 1 + 0) y en la Z-X (30 &gt; 2 + 3), pero admisible. */
    private static final Map<String, Double> H_INCONSISTENTE = Map.of("X", 3.0, "Z", 30.0);

    /** Consistente: h(Z,D) ≤ c(Z,A) + h(A,D) obliga a que h(Z,D) no pase de 1 km. */
    private static final Map<String, Double> H_CONSISTENTE = Map.of("X", 3.0, "Z", 1.0);

    /**
     * Las dos heurísticas son admisibles: el óptimo del grafo son 33 km y ninguna de las dos
     * estimaciones lo sobreestima. Lo que cambia entre ellas no es si son correctas, sino si A*
     * puede confiar en que cerrar un nodo es definitivo.
     *
     * <p>No se comprueba aquí la simetría de h porque no es un requisito de A*: una heurística
     * de costo a destino puede ser asimétrica. La simetría sí se verifica en
     * {@code DistanciaLineaRectaTest}, donde la distancia en línea recta sí la cumple.</p>
     */
    @Test
    void sinSobreestimarElOptimo() {
        GrafoReapertura g = new GrafoReapertura();
        assertEquals(33.0, Dijkstra.costoMinimo(g.grafo, g.a, g.d), 1e-9);

        for (Map<String, Double> valores : List.of(H_INCONSISTENTE, H_CONSISTENTE)) {
            Heuristica h = heuristicaHacia("D", valores);
            ValidadorHeuristica v = new ValidadorHeuristica(g.grafo, h);
            assertTrue(v.validarAdmisibilidad().isEmpty(),
                    () -> "hacia D debe seguir siendo admisible con " + valores
                            + ", fallos: " + v.validarAdmisibilidad());
        }
    }

    /**
     * Con una heurística inconsistente, A* ya no puede confiar en que cerrar un nodo sea
     * definitivo: debe reabrirlo cuando aparezca un camino más barato. Al contar las
     * expansiones se ve que X se expande dos veces.
     */
    @Test
    void reabreUnNodoCuandoApareceUnCaminoMasBarato() {
        GrafoReapertura g = new GrafoReapertura();
        Heuristica inconsistente = heuristicaHacia("D", H_INCONSISTENTE);

        ResultadoBusqueda r = new BusquedaEstrella(g.grafo, inconsistente).buscar(g.a, g.d);

        assertTrue(r.isEncontrado());
        assertEquals(33.0, r.getCostoTotal(), 1e-9);
        assertEquals(List.of(g.a, g.z, g.x, g.d), r.getCamino());
        // Se expanden A, X, Z y otra vez X: 4 expansiones. El destino D no se cuenta.
        assertEquals(4, r.getNodosExpandidos(),
                "X debe reabrirse y expandirse una segunda vez");
        assertEquals(33.0, Dijkstra.costoMinimo(g.grafo, g.a, g.d), 1e-9);
    }

    /**
     * Con una heurística consistente en el mismo grafo, A* sabe que cerrar un nodo es
     * definitivo: encuentra el mismo óptimo sin reabrir nada (3 expansiones en vez de 4).
     */
    @Test
    void conHeuristicaConsistenteNoNecesitaReabrir() {
        GrafoReapertura g = new GrafoReapertura();
        Heuristica consistente = heuristicaHacia("D", H_CONSISTENTE);

        List<String> fallos = new ValidadorHeuristica(g.grafo, consistente).validarConsistencia();
        assertTrue(fallos.isEmpty(), () -> "la heurística del caso debe ser consistente: " + fallos);

        ResultadoBusqueda r = new BusquedaEstrella(g.grafo, consistente).buscar(g.a, g.d);

        assertEquals(33.0, r.getCostoTotal(), 1e-9);
        assertEquals(List.of(g.a, g.z, g.x, g.d), r.getCamino());
        // Se expanden A, Z y X, cada uno una sola vez: 3 expansiones, sin reaperción.
        assertEquals(3, r.getNodosExpandidos(),
                "sin reaperción cada municipio se expande una sola vez");
    }

    /**
     * Criterio de aceptación del issue #15: en los 20 municipios, el costo de A* tiene que
     * coincidir con el de Dijkstra en todos los pares probados. Aquí se prueban los 380
     * pares ordenados (origen distinto del destino) con la heurística real del proyecto.
     */
    @Test
    void coincideConDijkstraEnTodosLosParesDeLos20Municipios() {
        Grafo real = DatosReales.grafo();
        BusquedaEstrella estrella = new BusquedaEstrella(real, DatosReales.heuristica());

        assertEquals(DatosReales.TOTAL_MUNICIPIOS, real.getMunicipios().size());

        List<String> diferencias = new ArrayList<>();
        int pares = 0;
        for (Municipio origen : real.getMunicipios()) {
            for (Municipio destino : real.getMunicipios()) {
                if (origen.equals(destino)) {
                    continue;
                }
                pares++;
                ResultadoBusqueda r = estrella.buscar(origen, destino);
                double optimo = Dijkstra.costoMinimo(real, origen, destino);

                assertTrue(r.isEncontrado(), () -> "no encontró ruta de " + origen + " a " + destino);
                if (Math.abs(r.getCostoTotal() - optimo) > 1e-6) {
                    diferencias.add(origen + " -> " + destino + ": A* " + r.getCostoTotal()
                            + " km, Dijkstra " + optimo + " km");
                }
            }
        }

        assertEquals(DatosReales.TOTAL_MUNICIPIOS * (DatosReales.TOTAL_MUNICIPIOS - 1), pares);
        assertTrue(diferencias.isEmpty(), () -> "A* no coincide con Dijkstra en: " + diferencias);
    }

    /**
     * Criterio de aceptación del issue #15: A* expande igual o menos nodos que Dijkstra.
     * Con una heurística admisible y consistente, todo nodo que expande A* también lo
     * expande Dijkstra, así que nunca debería superar el conteo de Dijkstra.
     */
    @Test
    void expandeIgualOMenosNodosQueDijkstra() {
        Grafo real = DatosReales.grafo();
        BusquedaEstrella estrella = new BusquedaEstrella(real, DatosReales.heuristica());

        List<String> casos = new ArrayList<>();
        int[] totales = new int[2]; // [0] nodos de A*, [1] nodos de Dijkstra
        for (Municipio origen : real.getMunicipios()) {
            for (Municipio destino : real.getMunicipios()) {
                if (origen.equals(destino)) {
                    continue;
                }
                int deEstrella = estrella.buscar(origen, destino).getNodosExpandidos();
                int deDijkstra = Dijkstra.expansionesHasta(real, origen, destino);
                totales[0] += deEstrella;
                totales[1] += deDijkstra;
                if (deEstrella > deDijkstra) {
                    casos.add(origen + " -> " + destino + ": A* " + deEstrella
                            + " nodos, Dijkstra " + deDijkstra);
                }
            }
        }

        assertTrue(casos.isEmpty(), () -> "A* expandió más nodos que Dijkstra en: " + casos);
        assertTrue(totales[0] < totales[1], () -> "A* debería ahorrar trabajo: "
                + totales[0] + " frente a " + totales[1]);
    }
}
