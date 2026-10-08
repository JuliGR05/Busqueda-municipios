package municipios.algoritmo;

import municipios.modelo.Grafo;
import municipios.modelo.Grafo.Arista;
import municipios.modelo.Municipio;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Árbol de expansión mínima con el algoritmo de Kruskal.
 *
 * <p>Idea: se ordenan todas las aristas de menor a mayor km y se recorren en ese orden.
 * Una arista se acepta solo si conecta dos municipios que todavía no estaban conectados
 * por las aristas ya aceptadas; si ya lo estaban, aceptarla cerraría un ciclo y se
 * descarta. Se termina al tener n-1 aristas (un árbol), o al acabarse las aristas si el
 * grafo no es conexo, en cuyo caso el resultado es un bosque.</p>
 */

public class Kruskal {
    /**
     * Orden de las aristas: primero por km; si hay empate, por el nombre alfabéticamente
     * menor de sus extremos y luego por el mayor. No depende de qué extremo se llame
     * "origen", así que el resultado es siempre el mismo para los mismos datos.
     */
    private static final Comparator<Arista> ORDEN = Comparator
    .comparingDouble(Arista::distancia)
    .thenComparing(Kruskal::nombreMenor)
    .thenComparing(Kruskal::nombreMayor);

    /**
     * Calcula el árbol de expansión mínima (o bosque, si el grafo no es conexo).
     *
     * @param grafo grafo de municipios; no puede ser null
     * @return las aristas elegidas, su costo en km y cuántas aristas se miraron y descartaron
     */
    
    public ResultadoMST calcular (Grafo grafo){
        if (grafo == null){
            throw new IllegalArgumentException("El grafo no puede ser null.");
        }

    // UnionFind trabaja con índices 0..n-1, así que numeramos los municipios.
        List<Municipio> municipios = grafo.getMunicipios();
        int n = municipios.size();
        Map<Municipio, Integer> indice = new HashMap<>();
        for (int i = 0; i < n; i++) {
            indice.put(municipios.get(i), i);
        }
    //Copia ordenada: getAristas() devuelve una lista que no se puede modificar.
    List<Arista> ordenadas = new ArrayList<>(grafo.getAristas());
    ordenadas.sort(ORDEN);

    UnionFind grupos = new UnionFind(n);
    List<Arista> elegidas = new ArrayList<>();
    int consideradas = 0;
    int descartadas = 0;

    for(Arista a : ordenadas){
        if(elegidas.size() == n - 1){
            break; //ya hay un árbol completo: las demás aristas no son necesarias (sobran)
        }
        consideradas++;
        // union devuelve false si los dos extremos YA estaban en el mismo grupo,
        // es decir, ya hay un camino entre ellos con las aristas elegidas.
        // Agregar esta arista cerraría un ciclo, así que se descarta.
    if (grupos.union(indice.get(a.origen()), indice.get(a.destino()))){
        elegidas.add(a);
    } else {
        descartadas++;
    }
}

    //Quedó un solo grupo => todos los municipios están conectados.
    // (Con 0 o 1 municipios no hay nada que conectar, y también cuenta como conexo.)
    boolean conezo = grupos.getComponentes() <= 1;
    return new ResultadoMST(elegidas, consideradas, descartadas, conezo);
    }

    private static String nombreMenor(Arista a){
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
