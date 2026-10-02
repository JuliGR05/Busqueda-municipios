package municipios.algoritmo;

import municipios.modelo.Grafo;
import municipios.modelo.Grafo.Conexion;
import municipios.modelo.Municipio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Implementación del algoritmo A* para encontrar el camino de menor
 * distancia por carretera entre dos municipios.
 *
 * A* combina:
 *
 * g(n) = costo real acumulado desde el origen hasta n.
 * h(n) = estimación del costo restante hasta el destino.
 * f(n) = g(n) + h(n).
 *
 * La frontera se ordena por el menor f(n).
 *
 * Si la heurística es admisible (nunca sobreestima el costo real)
 * y consistente, A* encuentra un camino óptimo.
 */

public class BusquedaEstrella {

    /** Nodo almacenado en la frontera de A*
     * Guarda: municipio, padre, g (costo real acumulado desde el origen),
     * h(estimación de costo restante) y f (costo total estimado f = g + h)
     */
    
    private static class Nodo {
        final Municipio municipio;
        final Nodo padre;
        final double g;
        final double h;
        final double f;

        Nodo(Municipio municipio, Nodo padre, double g, double h){
            this.municipio = municipio;
            this.padre = padre;
            this.g = g;
            this.h = h;
            this.f = g + h;
        }
    }

    private final Grafo grafo;
    private final Heuristica heuristica;
    /** Constructor del algoritmo A
     * @param grado grafo de municipios y conexiones
     * @param heuristica funcion utilizada para estimar la distancia restante
     */

    public BusquedaEstrella(Grafo grafo, Heuristica heuristica){
        this.grafo = grafo;
        this.heuristica = heuristica;
    }

    /**
     * Versión cómoda para la interfaz: recibe los nombres
     * de los municipios.
     *
     * @param nombreOrigen nombre del municipio de origen
     * @param nombreDestino nombre del municipio de destino
     * @return resultado de la búsqueda
     */
    public ResultadoBusqueda buscar(String nombreOrigen, String nombreDestino) {

        Municipio origen = grafo.buscarPorNombre(nombreOrigen);

        if (origen == null) {
            throw new IllegalArgumentException(
                    "El municipio de origen no existe: " + nombreOrigen
            );
        }

        Municipio destino = grafo.buscarPorNombre(nombreDestino);

        if (destino == null) {
            throw new IllegalArgumentException(
                    "El municipio de destino no existe: " + nombreDestino
            );
        }

        return buscar(origen, destino);
    }

    /**
     * Ejecuta el algoritmo A* entre dos municipios.
     *
     * @param origen municipio inicial
     * @param destino municipio objetivo
     * @return resultado con camino, costo total, nodos expandidos
     *         y si se encontró una solución
     */
    public ResultadoBusqueda buscar(
            Municipio origen,
            Municipio destino) {

        
        // 1. Validar que los municipios existan.
        if (origen == null) {
            throw new IllegalArgumentException(
                    "El municipio de origen no existe."
            );
        }

        if (destino == null) {
            throw new IllegalArgumentException(
                    "El municipio de destino no existe."
            );
        }

       
        // 2. Caso especial: origen y destino son el mismo municipio.
        if (origen.equals(destino)) {

            List<Municipio> camino = new ArrayList<>();
            camino.add(origen);

            return new ResultadoBusqueda(
                    camino,
                    0.0,
                    0,
                    true
            );
        }

        // 3. Frontera de A*.
        //
        // Se ordena primero por f(n).
        // Si hay empate, usamos menor h(n).
        // Si todavía hay empate, usamos el nombre del municipio.
        //
        // Esto hace que el comportamiento sea determinista.

        PriorityQueue<Nodo> frontera = new PriorityQueue<>(
                (a, b) -> {

                    int comparacionF =
                            Double.compare(a.f, b.f);

                    if (comparacionF != 0) {
                        return comparacionF;
                    }

                    int comparacionH =
                            Double.compare(a.h, b.h);

                    if (comparacionH != 0) {
                        return comparacionH;
                    }

                    return a.municipio.getNombre()
                            .compareTo(b.municipio.getNombre());
                }
        );

        // ---------------------------------------------------------
        // 4. Guarda el menor costo g conocido para cada municipio.
        //
        // Ejemplo:
        //
        // g(Bogotá) = 0
        // g(Ibagué) = 200
        //
        // Si después encontramos:
        //
        // g(Ibagué) = 150
        //
        // actualizamos el valor.
        // ---------------------------------------------------------

        Map<Municipio, Double> mejorG = new HashMap<>();

        // ---------------------------------------------------------
        // 5. Conjunto de nodos ya expandidos.
        // ---------------------------------------------------------

        Set<Municipio> cerrados = new HashSet<>();

        int nodosExpandidos = 0;

        // ---------------------------------------------------------
        // 6. El origen empieza con:
        //
        // g = 0 porque todavía no hemos recorrido nada.
        //
        // h = estimación desde origen hasta destino.
        //
        // f = g + h.
        // ---------------------------------------------------------

        double hInicial = heuristica.h(origen, destino);

        Nodo nodoInicial = new Nodo(
                origen,
                null,
                0.0,
                hInicial
        );

        frontera.add(nodoInicial);
        mejorG.put(origen, 0.0);

       
        // 7. Comienza la búsqueda.
        while (!frontera.isEmpty()) {

            Nodo actual = frontera.poll();

            // -----------------------------------------------------
            // Puede existir una versión antigua del mismo municipio
            // en la PriorityQueue.
            //
            // Si encontramos posteriormente un camino mejor,
            // agregamos un nuevo Nodo a la frontera.
            //
            // Por eso descartamos nodos cuyo g ya no sea el mejor.
            // -----------------------------------------------------

            double mejorCostoActual =
                    mejorG.getOrDefault(
                            actual.municipio,
                            Double.POSITIVE_INFINITY
                    );

            if (actual.g > mejorCostoActual) {
                continue;
            }

            // -----------------------------------------------------
            // Si ya fue expandido, no necesitamos expandirlo otra vez.
            //
            // Con una heurística consistente como la de este proyecto,
            // cuando un nodo se cierra ya conocemos su mejor costo.
            // -----------------------------------------------------

            if (cerrados.contains(actual.municipio)) {
                continue;
            }

            // -----------------------------------------------------
            // Si sacamos el destino de la frontera, encontramos
            // el camino óptimo.
            // -----------------------------------------------------

            if (actual.municipio.equals(destino)) {

                return new ResultadoBusqueda(
                        reconstruirCamino(actual),
                        actual.g,
                        nodosExpandidos,
                        true
                );
            }

       
            // Expandimos el nodo actual.
            cerrados.add(actual.municipio);
            nodosExpandidos++;

         
            // Revisamos todos los vecinos.
            for (Conexion conexion :
                    grafo.getVecinos(actual.municipio)) {

                Municipio vecino = conexion.destino();

                // Costo real de llegar al vecino pasando por actual.
                double nuevoG =
                        actual.g + conexion.distancia();

                double gAnterior =
                        mejorG.getOrDefault(
                                vecino,
                                Double.POSITIVE_INFINITY
                        );

                // Solo nos interesa el vecino si encontramos una
                // ruta más barata que la conocida.
            
                if (nuevoG < gAnterior) {

                    // Guardamos el nuevo mejor costo.
                    mejorG.put(vecino, nuevoG);

                    // Calculamos la heurística.
                    double h =
                            heuristica.h(vecino, destino);

                    // Creamos un nuevo nodo con el mejor camino.
                    Nodo nuevoNodo = new Nodo(
                            vecino,
                            actual,
                            nuevoG,
                            h
                    );

                    // Lo agregamos a la frontera.
                    frontera.add(nuevoNodo);

                   
                    // Si el vecino había sido cerrado y encontramos
                    // una ruta mejor, lo reabrimos.
                   

                    cerrados.remove(vecino);
                }
            }
        }

       
        // 8. Si la frontera queda vacía, no existe una ruta.
       
        return new ResultadoBusqueda(
                new ArrayList<>(),
                0.0,
                nodosExpandidos,
                false
        );
    }

    /**
     * Reconstruye el camino desde el destino hasta el origen
     * utilizando los padres almacenados en cada Nodo.
     */
    private List<Municipio> reconstruirCamino(Nodo meta) {

        List<Municipio> camino = new ArrayList<>();

        Nodo actual = meta;

        while (actual != null) {
            camino.add(actual.municipio);
            actual = actual.padre;
        }

        // Lo construimos desde destino -> origen,
        // por eso debemos invertirlo.
        Collections.reverse(camino);

        return camino;
    }
}