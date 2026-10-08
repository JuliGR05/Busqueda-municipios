package municipios.algoritmo;

import municipios.modelo.Grafo;
import municipios.modelo.Grafo.Arista;
import municipios.modelo.Grafo.Conexion;
import municipios.modelo.Municipio;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Árbol de expansión mínima con el algoritmo de Prim.
 *
 * <p>Se parte de un único municipio (por defecto, el primero del grafo) y se hace
 * crecer el árbol de a una arista a la vez, tomando siempre la de menor km que
 * conecta el árbol con un municipio todavía afuera. Si el grafo no es conexo, el
 * árbol solo cubre la componente del municipio inicial.</p>
 */
public class Prim {

    // Mismo criterio de desempate que Kruskal: así el resultado no depende del orden.
    private static final Comparator<Arista> ORDEN = Comparator
            .comparingDouble(Arista::distancia)
            .thenComparing(Prim::nombreMenor)
            .thenComparing(Prim::nombreMayor);

    public ResultadoMST calcular(Grafo grafo) {
        return calcular(grafo, null);
    }

    public ResultadoMST calcular(Grafo grafo, Municipio inicio) {
        if (grafo == null) {
            throw new IllegalArgumentException("El grafo no puede ser null.");
        }

        List<Municipio> municipios = grafo.getMunicipios();
        if (municipios.isEmpty()) {
            return new ResultadoMST(List.of(), 0, 0, true);
        }

        Municipio raiz = (inicio != null) ? inicio : municipios.get(0);
        if (!municipios.contains(raiz)) {
            throw new IllegalArgumentException(
                    "El municipio inicial no pertenece al grafo: " + raiz);
        }

        Set<Municipio> visitados = new HashSet<>();
        List<Arista> elegidas = new ArrayList<>();
        PriorityQueue<Arista> pendientes = new PriorityQueue<>(ORDEN);
        int consideradas = 0;
        int descartadas = 0;

        visitados.add(raiz);
        agregarAristasDeSalida(grafo, raiz, visitados, pendientes);

        while (!pendientes.isEmpty() && visitados.size() < municipios.size()) {
            Arista a = pendientes.poll();
            consideradas++;
            if (visitados.contains(a.destino())) {
                descartadas++;
                continue;
            }
            elegidas.add(a);
            visitados.add(a.destino());
            agregarAristasDeSalida(grafo, a.destino(), visitados, pendientes);
        }

        boolean conexo = visitados.size() == municipios.size();
        return new ResultadoMST(elegidas, consideradas, descartadas, conexo);
    }

    private static void agregarAristasDeSalida(Grafo grafo, Municipio desde,
            Set<Municipio> visitados, PriorityQueue<Arista> pendientes) {
        for (Conexion c : grafo.getVecinos(desde)) {
            if (!visitados.contains(c.destino())) {
                pendientes.add(new Arista(desde, c.destino(), c.distancia()));
            }
        }
    }

    private static String nombreMenor(Arista a) {
        String x = a.origen().getNombre();
        String y = a.destino().getNombre();
        return x.compareTo(y) <= 0 ? x : y;
    }

    private static String nombreMayor(Arista a) {
        String x = a.origen().getNombre();
        String y = a.destino().getNombre();
        return x.compareTo(y) <= 0 ? y : x;
    }
}
