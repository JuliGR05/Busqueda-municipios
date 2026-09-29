package municipios.algoritmo;

import municipios.modelo.Grafo;
import municipios.modelo.Grafo.Conexion;
import municipios.modelo.Municipio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Dijkstra sobre el grafo de municipios.
 *
 * <p>Se usa como "verdad" para validar la heurística del issue #6:
 * el costo mínimo real entre dos municipios es el que debe compararse con
 * h(n, destino) al comprobar admisibilidad. Más adelante el issue #15 (A*)
 * debe coincidir en costo con Dijkstra en todos los pares.</p>
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
