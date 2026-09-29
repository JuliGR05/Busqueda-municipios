package municipios.algoritmo;

import municipios.modelo.Municipio;

public interface Heuristica {
    double h(Municipio actual, Municipio destino);
}