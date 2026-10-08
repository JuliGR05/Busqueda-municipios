package municipios.algoritmo;

import static org.junit.jupiter.api.Assertions.*;
 
import municipios.DatosReales;
import municipios.modelo.Grafo;
import municipios.modelo.Grafo.Arista;
import municipios.modelo.Municipio;
import org.junit.jupiter.api.Test;
 
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
 
/**
 * Validación cruzada: Kruskal y Prim deben dar el mismo árbol de expansión mínima.
 *
 * <p>Conclusión sobre los empates de km: aunque en teoría dos aristas del mismo km
 * podrían dar árboles distintos, en este proyecto NO pasa, porque Kruskal y Prim
 * desempatan con el mismo criterio (km, nombre menor, nombre mayor). Ese criterio
 * ordena todas las aristas de forma estricta, y con un orden estricto el árbol
 * mínimo es único. Por eso aquí se exige el mismo costo Y las mismas aristas.</p>
 */


public class ValidacionCruzadaMSTTest {
    private final Kruskal kruskal = new Kruskal();
    private final Prim prim = new Prim();

/**
* Comprueba que el resultado es un árbol de expansión de verdad: n-1 aristas,
 * todas del grafo, ninguna cierra un ciclo y juntas conectan a todos los municipios.
 */

private static void verificarArbol(Grafo grafo, ResultadoMST r){
    List<Municipio> municipios = grafo.getMunicipios();
    int n = municipios.size();
    assertEquals(n -1, r.getNumeroAristas());
    assertTrue(r.isConexo());

    Map<Municipio, Integer> indice = new HashMap<>();
    for (int i = 0; i <n; i++){
        indice.put(municipios.get(i), i);
    }

    Set<Set<Municipio>> delGrafo = new HashSet<>();
    for (Arista a : grafo.getAristas()){
        delGrafo.add(Set.of(a.origen(), a.destino()));
    }

    UnionFind grupos = new UnionFind(n);
    for (Arista a : r.getAristas()){
        assertTrue(delGrafo.contains(Set.of(a.origen(), a.destino()))); // existe en el grafo
        //union devuelve false si cerraróa un ciclo: en un árbol nunca debe pasar
        assertTrue(grupos.union(indice.get(a.origen()), indice.get(a.destino())));
    }
    assertEquals(1, grupos.getComponentes()); // un solo grupo : conecta a todos
}

    /** Pares de nombres sin orden: {Armenia, Pereira} es lo mismo que {Pereira, Armenia}. */
    private static Set<Set<String>> parejas(ResultadoMST r) {
        Set<Set<String>> pares = new HashSet<>();
        for (Arista a : r.getAristas()) {
            pares.add(Set.of(a.origen().getNombre(), a.destino().getNombre()));
        }
        return pares;
    }
 
    @Test
    void conLosDatosRealesPrimDaElMismoCostoDesdeLos20Inicios() {
        Grafo grafo = DatosReales.grafo();
        List<Municipio> municipios = grafo.getMunicipios();
        assertEquals(DatosReales.TOTAL_MUNICIPIOS, municipios.size());
 
        ResultadoMST deKruskal = kruskal.calcular(grafo);
        verificarArbol(grafo, deKruskal);
 
        for (Municipio inicio : municipios) {
            ResultadoMST dePrim = prim.calcular(grafo, inicio);
            verificarArbol(grafo, dePrim);
            assertEquals(deKruskal.getCostoTotal(), dePrim.getCostoTotal(), 1e-6);
        }
    }
 
    @Test
    void conLosDatosRealesElArbolEsElMismoDesdeLos20Inicios() {
        Grafo grafo = DatosReales.grafo();
        Set<Set<String>> deKruskal = parejas(kruskal.calcular(grafo));
 
        for (Municipio inicio : grafo.getMunicipios()) {
            assertEquals(deKruskal, parejas(prim.calcular(grafo, inicio)));
        }
    }
 
    @Test
    void conMuchosEmpatesDeKmElArbolSigueSiendoElMismo() {
        Random azar = new Random(42); // semilla fija: la prueba siempre hace lo mismo
        for (int vuelta = 0; vuelta < 50; vuelta++) {
            Grafo grafo = grafoAleatorio(azar, 8 + azar.nextInt(8));
            ResultadoMST deKruskal = kruskal.calcular(grafo);
            verificarArbol(grafo, deKruskal);
 
            for (Municipio inicio : grafo.getMunicipios()) {
                ResultadoMST dePrim = prim.calcular(grafo, inicio);
                verificarArbol(grafo, dePrim);
                assertEquals(deKruskal.getCostoTotal(), dePrim.getCostoTotal(), 1e-9);
                assertEquals(parejas(deKruskal), parejas(dePrim));
            }
        }
    }
 
    /**
     * Grafo conexo con n municipios, cargado en orden aleatorio y con km de solo 1, 2 o 3,
     * para que haya muchísimos empates.
     */
    private static Grafo grafoAleatorio(Random azar, int n) {
        List<Municipio> ms = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ms.add(new Municipio("M" + i, 0, 0));
        }
        Collections.shuffle(ms, azar);
 
        Grafo g = new Grafo();
        for (Municipio m : ms) {
            g.agregarMunicipio(m);
        }
 
        Set<Set<Municipio>> usadas = new HashSet<>();
        // cada municipio se une con uno anterior: garantiza que el grafo sea conexo
        for (int i = 1; i < n; i++) {
            conectar(g, usadas, ms.get(i), ms.get(azar.nextInt(i)), azar);
        }
        // conexiones extra al azar, que forman ciclos
        for (int j = 0; j < 2 * n; j++) {
            Municipio a = ms.get(azar.nextInt(n));
            Municipio b = ms.get(azar.nextInt(n));
            if (!a.equals(b)) {
                conectar(g, usadas, a, b, azar);
            }
        }
        return g;
    }
 
    private static void conectar(Grafo g, Set<Set<Municipio>> usadas,
                                 Municipio a, Municipio b, Random azar) {
        if (usadas.add(Set.of(a, b))) {
            g.agregarConexion(a, b, 1 + azar.nextInt(3));
        }
    }

 
    
}
