package municipios.algoritmo;

import municipios.modelo.Municipio;
import java.util.List;

public class ResultadoBusqueda {
    private List<Municipio> camino;
    private double costoTotal;
    private int nodosExpandidos;
    private boolean encontrado;
    private long tiempoEjecucionNs;

    public ResultadoBusqueda(List<Municipio> camino, double costoTotal,
                              int nodosExpandidos, boolean encontrado) {
        this(camino, costoTotal, nodosExpandidos, encontrado, 0L);
    }

    /**
     * @param camino municipios del camino, en orden (vacío si no hay ruta)
     * @param costoTotal kilómetros por carretera del camino
     * @param nodosExpandidos municipios expandidos antes de llegar al destino
     * @param encontrado true si se encontró una ruta
     * @param tiempoEjecucionNs nanosegundos que tardó el algoritmo (0 si no se midió)
     */
    public ResultadoBusqueda(List<Municipio> camino, double costoTotal,
                              int nodosExpandidos, boolean encontrado,
                              long tiempoEjecucionNs) {
        this.camino = camino;
        this.costoTotal = costoTotal;
        this.nodosExpandidos = nodosExpandidos;
        this.encontrado = encontrado;
        this.tiempoEjecucionNs = Math.max(0L, tiempoEjecucionNs);
    }

    public List<Municipio> getCamino() { return camino; }
    public double getCostoTotal() { return costoTotal; }
    public int getNodosExpandidos() { return nodosExpandidos; }
    public boolean isEncontrado() { return encontrado; }
    /** @return nanosegundos que tardó el algoritmo; 0 si se construyó sin medir. */
    public long getTiempoEjecucionNs() { return tiempoEjecucionNs; }
    /** @return microsegundos que tardó el algoritmo. */
    public double getTiempoEjecucionMicros() { return tiempoEjecucionNs / 1_000.0; }
    /** @return milisegundos que tardó el algoritmo. */
    public double getTiempoEjecucionMs() { return tiempoEjecucionNs / 1_000_000.0; }
}