package municipios.algoritmo;

import static org.junit.jupiter.api.Assertions.*;

import municipios.modelo.Grafo.Arista;
import municipios.modelo.Municipio;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class ResultadoMSTTest {

    private final Municipio medellin = new Municipio("Medellín", 6.25, -75.56);
    private final Municipio bogota = new Municipio("Bogotá", 4.71, -74.07);
    private final Municipio cali = new Municipio("Cali", 3.45, -76.53);

    private ResultadoMST ejemplo() {
        List<Arista> aristas = List.of(
                new Arista(medellin, bogota, 415),
                new Arista(bogota, cali, 460));
        return new ResultadoMST(aristas, 5, 3, true);
    }

    @Test
    void costoTotalEsLaSumaDeLasAristas() {
        assertEquals(875.0, ejemplo().getCostoTotal(), 1e-9);
        assertEquals(2, ejemplo().getNumeroAristas());
    }

    @Test
    void guardaConsideradasDescartadasYConexo() {
        ResultadoMST r = ejemplo();
        assertEquals(5, r.getAristasConsideradas());
        assertEquals(3, r.getAristasDescartadas());
        assertTrue(r.isConexo());
    }

    @Test
    void toStringEsLegible() {
        String texto = ejemplo().toString();
        assertTrue(texto.contains("Medellín – Bogotá: 415.0 km"));
        assertTrue(texto.contains("875.0 km"));
    }

    @Test
    void laListaDeAristasNoSePuedeModificar() {
        assertThrows(UnsupportedOperationException.class,
                () -> ejemplo().getAristas().add(new Arista(medellin, cali, 1)));
    }

    @Test
    void cambiarLaListaOriginalNoAfectaAlResultado() {
        List<Arista> original = new ArrayList<>(List.of(new Arista(medellin, bogota, 415)));
        ResultadoMST r = new ResultadoMST(original, 1, 0, true);
        original.add(new Arista(bogota, cali, 460));
        assertEquals(1, r.getNumeroAristas());
    }
}