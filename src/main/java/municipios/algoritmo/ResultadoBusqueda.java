package municipios.algoritmo;

import municipios.modelo.Municipio;
import java.util.List;

public class ResultadoBusqueda {
    private List<Municipio> camino;
    private double costoTotal;
    private int nodosExpandidos;
    private boolean encontrado;

    public ResultadoBusqueda(List<Municipio> camino, double costoTotal,
                              int nodosExpandidos, boolean encontrado) {
        this.camino = camino;
        this.costoTotal = costoTotal;
        this.nodosExpandidos = nodosExpandidos;
        this.encontrado = encontrado;
    }

    public List<Municipio> getCamino() { return camino; }
    public double getCostoTotal() { return costoTotal; }
    public int getNodosExpandidos() { return nodosExpandidos; }
    public boolean isEncontrado() { return encontrado; }
}