package municipios.algoritmo;

import municipios.modelo.Grafo;
import municipios.modelo.Grafo.Conexion;
import municipios.modelo.Municipio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Búsqueda avara (greedy best-first) entre municipios.
 *
 * La frontera se ordena SOLO por h(n): la distancia en línea recta de n al
 * destino. No mira cuánto costó llegar hasta n (eso lo hace A*), por eso es
 * rápida pero no garantiza el mejor camino.
 *
 * Por qué NO es necesariamente óptima:
 * la heurística solo dice qué tan cerca en línea recta está el destino, no qué
 * tan bueno es el camino real. Un municipio puede quedar muy cerca en línea
 * recta y tener una vía larga o con rodeos (por ejemplo, por la cordillera).
 * Como el algoritmo se queda con el vecino de menor h(n) sin comparar los
 * costos acumulados, puede terminar en un camino más largo que otro que
 * empezaba "peor" según h(n).
 */
public class BusquedaAvara {

    /** Nodo de la frontera; guarda el padre para reconstruir el camino. */
    private static class Nodo {
        final Municipio municipio;
        final Nodo padre;
        final double costoAcumulado; // solo informativo, NO se usa para ordenar
        final double h;

        Nodo(Municipio municipio, Nodo padre, double costoAcumulado, double h) {
            this.municipio = municipio;
            this.padre = padre;
            this.costoAcumulado = costoAcumulado;
            this.h = h;
        }
    }

    private final Grafo grafo;
    private final Heuristica heuristica;

    public BusquedaAvara(Grafo grafo, Heuristica heuristica) {
        this.grafo = grafo;
        this.heuristica = heuristica;
    }


    /** Versión cómoda para la UI: recibe los nombres (sin importar tildes ni mayúsculas). */
    public ResultadoBusqueda buscar(String nombreOrigen, String nombreDestino) {
        Municipio origen = grafo.buscarPorNombre(nombreOrigen);
        if (origen == null) {
            throw new IllegalArgumentException("El municipio de origen no existe: " + nombreOrigen);
        }
        Municipio destino = grafo.buscarPorNombre(nombreDestino);
        if (destino == null) {
            throw new IllegalArgumentException("El municipio de destino no existe: " + nombreDestino);
        }
        return buscar(origen, destino);
    }

    /**
     * @throws IllegalArgumentException si origen o destino no existen (son null).
     * @return resultado con encontrado=false si no hay camino entre ambos.
     */
    public ResultadoBusqueda buscar(Municipio origen, Municipio destino) {
        // Caso: municipios inexistentes
        if (origen == null) {
            throw new IllegalArgumentException("El municipio de origen no existe.");
        }
        if (destino == null) {
            throw new IllegalArgumentException("El municipio de destino no existe.");
        }

        // Caso: origen igual a destino
        if (origen.equals(destino)) {
            List<Municipio> camino = new ArrayList<>();
            camino.add(origen);
            return new ResultadoBusqueda(camino, 0.0, 0, true);
        }

        // Frontera ordenada por h(n), menor primero
        PriorityQueue<Nodo> frontera =
                new PriorityQueue<>((a, b) -> Double.compare(a.h, b.h));
        Set<Municipio> visitados = new HashSet<>();
        int nodosExpandidos = 0;

        frontera.add(new Nodo(origen, null, 0.0, heuristica.h(origen, destino)));

        while (!frontera.isEmpty()) {
            Nodo actual = frontera.poll();

            // Un municipio puede entrar varias veces a la frontera; si ya lo
            // expandimos lo ignoramos (así se evitan los ciclos).
            if (visitados.contains(actual.municipio)) {
                continue;
            }

            // Prueba de meta al sacar de la frontera
            if (actual.municipio.equals(destino)) {
                return new ResultadoBusqueda(reconstruirCamino(actual),
                        actual.costoAcumulado, nodosExpandidos, true);
            }

            visitados.add(actual.municipio);
            nodosExpandidos++; // se cuenta cada municipio expandido (la meta no)

            for (Conexion c : grafo.getVecinos(actual.municipio)) {
                if (!visitados.contains(c.destino())) {
                    frontera.add(new Nodo(c.destino(), actual,
                            actual.costoAcumulado + c.distancia(),
                            heuristica.h(c.destino(), destino)));
                }
            }
        }

        // Frontera vacía: no hay ruta entre origen y destino
        return new ResultadoBusqueda(new ArrayList<>(), 0.0, nodosExpandidos, false);
    }

    private List<Municipio> reconstruirCamino(Nodo meta) {
        List<Municipio> camino = new ArrayList<>();
        for (Nodo n = meta; n != null; n = n.padre) {
            camino.add(n.municipio);
        }
        Collections.reverse(camino);
        return camino;
    }
}