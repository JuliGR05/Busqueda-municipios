package municipios.algoritmo;

/**
 * Conjuntos disjuntos sobre los índices 0..n-1, con compresión de caminos
 * y unión por rango. Sirve para detectar ciclos en el algoritmo de Kruskal.
 */
public class UnionFind {
    private final int[] padre;
    private final int[] rango;
    private int componentes;

    /** Crea n elementos, cada uno en su propio conjunto. */
    public UnionFind(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("El tamaño no puede ser negativo: " + n);
        }
        padre = new int[n];
        rango = new int[n];
        componentes = n;
        for (int i = 0; i < n; i++) {
            padre[i] = i;
        }
    }

    /** @return la raíz del conjunto de x, aplanando el camino recorrido */
    public int find(int x) {
        validar(x);
        int raiz = x;
        while (padre[raiz] != raiz) {
            raiz = padre[raiz];
        }
        // compresión de caminos: todo el recorrido apunta directo a la raíz
        while (padre[x] != raiz) {
            int siguiente = padre[x];
            padre[x] = raiz;
            x = siguiente;
        }
        return raiz;
    }

    /**
     * Une los conjuntos de a y b.
     * @return false si ya estaban en el mismo conjunto (unirlos formaría un ciclo)
     */
    public boolean union(int a, int b) {
        int raizA = find(a);
        int raizB = find(b);
        if (raizA == raizB) {
            return false;
        }
        // unión por rango: el árbol más bajo se cuelga del más alto
        if (rango[raizA] < rango[raizB]) {
            padre[raizA] = raizB;
        } else if (rango[raizA] > rango[raizB]) {
            padre[raizB] = raizA;
        } else {
            padre[raizB] = raizA;
            rango[raizA]++;
        }
        componentes--;
        return true;
    }

    /** @return true si a y b están en el mismo conjunto */
    public boolean conectados(int a, int b) {
        return find(a) == find(b);
    }

    /** @return cuántos conjuntos distintos quedan (1 si todo está conectado) */
    public int getComponentes() {
        return componentes;
    }

    private void validar(int x) {
        if (x < 0 || x >= padre.length) {
            throw new IllegalArgumentException(
                    "Índice fuera de rango: " + x + " (válido: 0 a " + (padre.length - 1) + ")");
        }
    }
}