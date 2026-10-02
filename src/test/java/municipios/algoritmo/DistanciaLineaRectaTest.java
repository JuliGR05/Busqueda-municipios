package municipios.algoritmo;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import municipios.modelo.Municipio;

class DistanciaLineaRectaTest {

    private final DistanciaLineaRecta heuristica = new DistanciaLineaRecta();

    private final Municipio bogota = new Municipio("Bogotá", 4.711, -74.072);
    private final Municipio medellin = new Municipio("Medellín", 6.244, -75.581);

    @Test
    void distanciaAUnoMismoEsCero() {
        assertEquals(0.0, heuristica.h(bogota, bogota), 1e-9);
    }

    @Test
    void esSimetrica() {
        assertEquals(heuristica.h(bogota, medellin), heuristica.h(medellin, bogota), 1e-9);
    }

    @Test
    void valorConocidoUnGradoDeLatitud() {
        // Un grado a lo largo de un meridiano = 6371 * pi / 180 ≈ 111,19 km
        Municipio p1 = new Municipio("P1", 0.0, 0.0);
        Municipio p2 = new Municipio("P2", 1.0, 0.0);
        assertEquals(111.19, heuristica.h(p1, p2), 0.01);
    }

    @Test
    void valorConocidoUnGradoDeLongitudEnElEcuador() {
        Municipio p1 = new Municipio("P1", 0.0, 0.0);
        Municipio p2 = new Municipio("P2", 0.0, 1.0);
        assertEquals(111.19, heuristica.h(p1, p2), 0.01);
    }

    @Test
    void distanciaEntreDistintosEsPositiva() {
        assertTrue(heuristica.h(bogota, medellin) > 0);
    }
}