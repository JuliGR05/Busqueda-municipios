package municipios.algoritmo;

import municipios.modelo.Municipio;

// Heurística h(n): distancia en línea recta (Haversine) entre dos municipios

public class DistanciaLineaRecta implements Heuristica {
    private static final double RADIO_TIERRA_KM = 6371.0;

    @Override 
    public double h(Municipio actual, Municipio destino){
        double lat1 = Math.toRadians(actual.getLatitud());
        double lat2 = Math.toRadians(destino.getLatitud());
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(destino.getLongitud()- actual.getLongitud());

        double a = Math.sin(dLat/2) * Math.sin(dLat/2) + Math.cos(lat1) * Math.cos(lat2) *
        Math.sin(dLon/2) * Math.sin(dLon/2);
        return 2 * RADIO_TIERRA_KM * Math.asin(Math.sqrt(a));
    }
    
}
