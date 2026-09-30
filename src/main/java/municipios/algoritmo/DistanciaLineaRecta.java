package municipios.algoritmo;

import municipios.modelo.Municipio;

/**
 * Heurística h(n): distancia en línea recta (Haversine) entre dos municipios.
 *
 * <p>Se calcula a partir de la latitud y longitud, no de una tabla manual.
 * Es admisible para A*: la línea recta nunca supera la distancia real por
 * carretera, y es consistente porque cumple la desigualdad triangular.</p>
 */
public class DistanciaLineaRecta implements Heuristica {
    /** Radio medio de la Tierra (WGS84) en kilómetros. */
    private static final double RADIO_TIERRA_KM = 6371.0;

    /**
     * @param actual municipio de partida (no null)
     * @param destino municipio destino (no null)
     * @return distancia en línea recta en km, 0 si son el mismo municipio
     */
    @Override
    public double h(Municipio actual, Municipio destino) {
        if (actual == null || destino == null) {
            throw new IllegalArgumentException("Los municipios no pueden ser null.");
        }
        if (actual.equals(destino)) {
            return 0.0;
        }
        double lat1 = Math.toRadians(actual.getLatitud());
        double lat2 = Math.toRadians(destino.getLatitud());
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(destino.getLongitud() - actual.getLongitud());

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(lat1) * Math.cos(lat2)
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        // min(1.0, ...) protege de errores de redondeo cuando a ~ 1.
        return 2 * RADIO_TIERRA_KM * Math.asin(Math.sqrt(Math.min(1.0, a)));
    }
    
}
