package municipios.algoritmo;

import municipios.modelo.Grafo;
import municipios.modelo.Municipio;

public class BusquedaAvara {
    private Grafo grafo;
    private Heuristica heuristica;

    public BusquedaAvara(Grafo grafo, Heuristica heuristica) {
        this.grafo = grafo;
        this.heuristica = heuristica;
    }

    public ResultadoBusqueda buscar(Municipio origen, Municipio destino) {
        return null; // TODO — lo implementa quien tenga el issue #5
    }
}