package municipios.algoritmo;

import municipios.modelo.Grafo;
import municipios.modelo.Grafo.Conexion;
import municipios.modelo.Municipio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Dijkstra sobre el grafo de municipios.
 *
 * <p>Se usa como "verdad" para validar la heurística del issue #6: el costo mínimo real entre
 * dos municipios es el que debe compararse con h(n, destino) al comprobar admisibilidad. Como
 * A* es óptimo con una heurística admisible y consistente, su resultado tiene que coincidir con
 * el de Dijkstra, y esa es la comparación que hacen el issue #15 y los experimentos del #9.</p>
 *
 * <p>Desempate determinista: a igual distancia, sale primero el municipio con
 * nombre menor (orden alfabético), para que los reportes sean reproducibles.</p>
 */
public final class Dijkstra {

    private Dijkstra() {}

    /**
     * @param grafo grafo no dirigido con distancias en km (no null)
     * @param origen municipio de partida (no null, debe estar en el grafo)
     * @return mapa con el costo mínimo desde origen a cada municipio alcanzable
     */
    public static Map<Municipio, Double> distanciasDesde(Grafo grafo, Municipio origen) {
        if (grafo == null || origen == null) {
            throw new IllegalArgumentException("El grafo y el origen no pueden ser null.");
        }
        Map<Municipio, Double> dist = new HashMap<>();
        for (Municipio m : grafo.getMunicipios()) {
            dist.put(m, Double.POSITIVE_INFINITY);
        }
        if (!dist.containsKey(origen)) {
            throw new IllegalArgumentException("El origen no está en el grafo: " + origen);
        }
        dist.put(origen, 0.0);

        PriorityQueue<Municipio> frontera = new PriorityQueue<>(
                Comparator.comparingDouble((Municipio m) -> dist.get(m))
                        .thenComparing(Municipio::getNombre));
        frontera.add(origen);

        while (!frontera.isEmpty()) {
            Municipio actual = frontera.poll();
            double dActual = dist.get(actual);
            for (Conexion c : grafo.getVecinos(actual)) {
                double nueva = dActual + c.distancia();
                if (nueva < dist.get(c.destino()) - 1e-9) {
                    dist.put(c.destino(), nueva);
                    // PriorityQueue no actualiza prioridades: se re-inserta.
                    frontera.remove(c.destino());
                    frontera.add(c.destino());
                }
            }
        }
        return dist;
    }

    /**
     * Cuenta cuántos municipios expande Dijkstra desde el origen hasta llegar al destino.
     *
     * <p>Se usa para comparar con los nodos expandidos de A* (criterio de aceptación del
     * issue #15: "expande igual o menos nodos que Dijkstra"). Se cuenta cada municipio una
     * sola vez, al procesarlo, y el destino no cuenta porque se sale de la frontera sin
     * expandirlo: es la misma convención que usa {@link BusquedaEstrella}.</p>
     *
     * <p>Con una heurística admisible y consistente, todo municipio que expande A* también
     * lo expande Dijkstra, así que este número nunca es menor que el de A*.</p>
     *
     * @param grafo    grafo no dirigido con distancias en km (no null)
     * @param origen   municipio de partida (no null, debe estar en el grafo)
     * @param destino  municipio objetivo (no null, debe estar en el grafo)
     * @return número de municipios expandidos; 0 si origen y destino son el mismo
     */
    public static int expansionesHasta(Grafo grafo, Municipio origen, Municipio destino) {
        if (grafo == null || origen == null || destino == null) {
            throw new IllegalArgumentException("El grafo, el origen y el destino no pueden ser null.");
        }
        if (origen.equals(destino)) {
            return 0;
        }
        Map<Municipio, Double> dist = new HashMap<>();
        for (Municipio m : grafo.getMunicipios()) {
            dist.put(m, Double.POSITIVE_INFINITY);
        }
        if (!dist.containsKey(origen) || !dist.containsKey(destino)) {
            throw new IllegalArgumentException("Origen y destino deben estar en el grafo.");
        }
        dist.put(origen, 0.0);
        PriorityQueue<Municipio> frontera = new PriorityQueue<>(
                Comparator.comparingDouble((Municipio m) -> dist.get(m))
                        .thenComparing(Municipio::getNombre));
        Set<Municipio> cerrados = new HashSet<>();
        frontera.add(origen);

        while (!frontera.isEmpty()) {
            Municipio actual = frontera.poll();
            if (cerrados.contains(actual)) {
                continue; // entrada vieja que quedó en la cola
            }
            if (actual.equals(destino)) {
                return cerrados.size(); // el destino se consulta, no se expande
            }
            cerrados.add(actual);
            for (Conexion c : grafo.getVecinos(actual)) {
                double nueva = dist.get(actual) + c.distancia();
                if (nueva < dist.get(c.destino()) - 1e-9) {
                    dist.put(c.destino(), nueva);
                    frontera.remove(c.destino());
                    frontera.add(c.destino());
                }
            }
        }
        return cerrados.size(); // sin camino: se expandió todo lo alcanzable desde el origen
    }

    /**
     * @return costo mínimo en km, o {@link Double#POSITIVE_INFINITY} si no hay camino
     */
    public static double costoMinimo(Grafo grafo, Municipio origen, Municipio destino) {
        if (origen == null || destino == null) {
            throw new IllegalArgumentException("Origen y destino no pueden ser null.");
        }
        if (origen.equals(destino)) {
            return 0.0;
        }
        return distanciasDesde(grafo, origen).getOrDefault(destino, Double.POSITIVE_INFINITY);
    }

    /**
     * Camino de costo mínimo reconstruido con mapa de padres.
     *
     * @return lista vacía si no hay camino
     */
    public static List<Municipio> caminoMinimo(Grafo grafo, Municipio origen, Municipio destino) {
        if (origen == null || destino == null) {
            throw new IllegalArgumentException("Origen y destino no pueden ser null.");
        }
        if (origen.equals(destino)) {
            return List.of(origen);
        }
        Map<Municipio, Double> dist = new HashMap<>();
        Map<Municipio, Municipio> padre = new HashMap<>();
        for (Municipio m : grafo.getMunicipios()) {
            dist.put(m, Double.POSITIVE_INFINITY);
        }
        if (!dist.containsKey(origen) || !dist.containsKey(destino)) {
            throw new IllegalArgumentException("Origen y destino deben estar en el grafo.");
        }
        dist.put(origen, 0.0);
        PriorityQueue<Municipio> frontera = new PriorityQueue<>(
                Comparator.comparingDouble((Municipio m) -> dist.get(m))
                        .thenComparing(Municipio::getNombre));
        frontera.add(origen);

        while (!frontera.isEmpty()) {
            Municipio actual = frontera.poll();
            if (actual.equals(destino)) {
                break;
            }
            for (Conexion c : grafo.getVecinos(actual)) {
                double nueva = dist.get(actual) + c.distancia();
                if (nueva < dist.get(c.destino()) - 1e-9) {
                    dist.put(c.destino(), nueva);
                    padre.put(c.destino(), actual);
                    frontera.remove(c.destino());
                    frontera.add(c.destino());
                }
            }
        }
        if (Double.isInfinite(dist.get(destino))) {
            return new ArrayList<>();
        }
        List<Municipio> camino = new ArrayList<>();
        for (Municipio m = destino; m != null; m = padre.get(m)) {
            camino.add(m);
            if (m.equals(origen)) {
                break;
            }
        }
        Collections.reverse(camino);
        return camino;
    }
}
