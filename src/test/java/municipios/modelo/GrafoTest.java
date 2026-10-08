package municipios.modelo;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import municipios.DatosReales;

class GrafoTest {

    private Grafo grafo;
    private Municipio medellin, bogota, cali;

    @BeforeEach
    void preparar() {
        grafo = new Grafo();
        medellin = new Municipio("Medellín", 6.25, -75.56);
        bogota = new Municipio("Bogotá", 4.71, -74.07);
        cali = new Municipio("Cali", 3.45, -76.53);
        grafo.agregarMunicipio(medellin);
        grafo.agregarMunicipio(bogota);
        grafo.agregarMunicipio(cali);
        grafo.agregarConexion(medellin, bogota, 415);
    }

    @Test
    void vecinosEnAmbosSentidos() {
        assertEquals(1, grafo.getVecinos(medellin).size());
        assertEquals(bogota, grafo.getVecinos(medellin).get(0).destino());
        assertEquals(1, grafo.getVecinos(bogota).size());
        assertEquals(medellin, grafo.getVecinos(bogota).get(0).destino());
    }

    @Test
    void distanciaEntreVecinosEsSimetrica() {
        assertEquals(415.0, grafo.getDistancia(medellin, bogota), 1e-9);
        assertEquals(415.0, grafo.getDistancia(bogota, medellin), 1e-9);
    }

    @Test
    void distanciaEntreNoVecinosEsMenosUno() {
        assertEquals(-1.0, grafo.getDistancia(medellin, cali), 1e-9);
    }

    @Test
    void municipioSinConexionesTieneListaVacia() {
        assertTrue(grafo.getVecinos(cali).isEmpty());
    }

    @Test
    void buscarPorNombreIgnoraTildesYMayusculas() {
        assertEquals(medellin, grafo.buscarPorNombre("medellin"));
        assertEquals(bogota, grafo.buscarPorNombre("  BOGOTA "));
        assertNull(grafo.buscarPorNombre("Atlantis"));
        assertNull(grafo.buscarPorNombre(null));
    }

    @Test
    void reconectarActualizaLaDistanciaSinDuplicar() {
        grafo.agregarConexion(medellin, bogota, 400);
        assertEquals(1, grafo.getVecinos(medellin).size());
        assertEquals(400.0, grafo.getDistancia(bogota, medellin), 1e-9);
    }

    @Test
    void conexionesInvalidasLanzanExcepcion() {
        assertThrows(IllegalArgumentException.class, () -> grafo.agregarConexion(medellin, medellin, 5));
        assertThrows(IllegalArgumentException.class, () -> grafo.agregarConexion(medellin, cali, 0));
        assertThrows(IllegalArgumentException.class, () -> grafo.agregarConexion(medellin, cali, -3));
        assertThrows(IllegalArgumentException.class,
                () -> grafo.agregarConexion(medellin, new Municipio("Pasto", 1.2, -77.3), 10));
    }

    @Test
    void nombreEquivalenteSeRechaza() {
        assertThrows(IllegalArgumentException.class,
                () -> grafo.agregarMunicipio(new Municipio("medellin", 6.0, -75.0)));
    }

    @Test
    void getAristasDevuelveCadaConexionUnaSolaVez() {
        grafo.agregarConexion(bogota, cali, 460);
        List<Grafo.Arista> aristas = grafo.getAristas();
        assertEquals(2, aristas.size()); // Medellín-Bogotá y Bogotá-Cali, no 4
    }

    @Test
    void getAristasConDatosRealesDevuelve42SinDuplicados() {
        List<Grafo.Arista> aristas = DatosReales.grafo().getAristas();
        assertEquals(42, aristas.size());
        Set<Set<Municipio>> pares = new HashSet<>();
        for (Grafo.Arista a : aristas) {
            assertTrue(pares.add(Set.of(a.origen(), a.destino())));
        }
    }
}