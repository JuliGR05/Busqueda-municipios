package municipios.algoritmo;

import municipios.modelo.Municipio;

/**
 * Función heurística h(n): costo estimado en km desde un municipio hasta el destino.
 *
 * <p>Debe devolver valores en kilómetros, ser no negativa y cumplir
 * h(destino, destino) = 0. Para que A* sea óptimo debe ser admisible
 * (nunca sobrestimar el costo real) y, en lo posible, consistente.</p>
 */
public interface Heuristica {
    /**
     * @param actual municipio desde donde se estima (no null)
     * @param destino municipio destino (no null)
     * @return estimación en km, siempre mayor o igual que 0
     */
    double h(Municipio actual, Municipio destino);
}