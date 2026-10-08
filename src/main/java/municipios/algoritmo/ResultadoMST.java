package municipios.algoritmo;

import municipios.modelo.Grafo.Arista;

import java.util.List;
import java.util.Locale;

/**
 * Resultado de un algoritmo de árbol de expansión mínima (Kruskal o Prim).
 * Si el grafo no es conexo, las aristas forman un bosque y {@link #isConexo()} es false.
 */
public class ResultadoMST {
    private final List<Arista> aristas;
    private final double costoTotal;
    private final int aristasConsideradas;
    private final int aristasDescartadas;
    private final boolean conexo;

    public ResultadoMST(List<Arista> aristas, int aristasConsideradas,
                        int aristasDescartadas, boolean conexo) {
        this.aristas = List.copyOf(aristas);
        double suma = 0;
        for (Arista a : this.aristas) {
            suma += a.distancia();
        }
        this.costoTotal = suma;
        this.aristasConsideradas = aristasConsideradas;
        this.aristasDescartadas = aristasDescartadas;
        this.conexo = conexo;
    }

    /** @return las aristas elegidas, en el orden en que el algoritmo las agregó */
    public List<Arista> getAristas() { return aristas; }

    /** @return la suma de los km de las aristas elegidas */
    public double getCostoTotal() { return costoTotal; }

    /** @return cuántas aristas tiene el árbol (n-1 si el grafo es conexo) */
    public int getNumeroAristas() { return aristas.size(); }

    /** @return cuántas aristas miró el algoritmo antes de terminar */
    public int getAristasConsideradas() { return aristasConsideradas; }

    /** @return cuántas aristas descartó por formar un ciclo */
    public int getAristasDescartadas() { return aristasDescartadas; }

    /** @return true si el árbol conecta todos los municipios */
    public boolean isConexo() { return conexo; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.US,
                "Árbol de expansión mínima: %d aristas, %.1f km, %s%n",
                aristas.size(), costoTotal, conexo ? "conexo" : "NO conexo (bosque)"));
        for (Arista a : aristas) {
            sb.append(String.format(Locale.US, "  %s – %s: %.1f km%n",
                    a.origen(), a.destino(), a.distancia()));
        }
        return sb.toString();
    }
}