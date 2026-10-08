package municipios.algoritmo;

import static org.junit.jupiter.api.Assertions.*;

import municipios.DatosReales;
import municipios.datos.CargadorCSV;
import municipios.modelo.Grafo;
import municipios.modelo.Grafo.Arista;
import municipios.modelo.Municipio;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

class PrimTest {

    private final Prim prim = new Prim();

    private static Municipio mun(String nombre) {
        return new Municipio(nombre, 0, 0); // las coordenadas no importan para Prim
    }

    /** Pares de nombres sin orden: {Armenia, Pereira} es lo mismo que {Pereira, Armenia}. */
    private static Set<Set<String>> parejas(ResultadoMST r) {
        Set<Set<String>> pares = new HashSet<>();
        for (Arista a : r.getAristas()) {
            pares.add(Set.of(a.origen().getNombre(), a.destino().getNombre()));
        }
        return pares;
    }

    /**
     * Armenia-Pereira 45.5, Pereira-Manizales 51, Armenia-Manizales 60,
     * Armenia-Ibagué 73.1, Pereira-Ibagué 112. El árbol se calcula a mano:
     * 45.5 + 51 + 73.1 = 169.6 km (la de 60 cierra el ciclo Armenia-Pereira-Manizales
     * y la de 112 ya no se llega a mirar).
     */
    private Grafo grafoPequeno() {
        Grafo g = new Grafo();
        Municipio armenia = mun("Armenia"), pereira = mun("Pereira");
        Municipio manizales = mun("Manizales"), ibague = mun("Ibagué");
        g.agregarMunicipio(armenia);
        g.agregarMunicipio(pereira);
        g.agregarMunicipio(manizales);
        g.agregarMunicipio(ibague);
        g.agregarConexion(armenia, pereira, 45.5);
        g.agregarConexion(pereira, manizales, 51);
        g.agregarConexion(armenia, manizales, 60);
        g.agregarConexion(armenia, ibague, 73.1);
        g.agregarConexion(pereira, ibague, 112);
        return g;
    }

    @Test
    void grafoPequenoDaElArbolCalculadoAMano() {
        ResultadoMST r = prim.calcular(grafoPequeno());
        assertEquals(3, r.getNumeroAristas());
        assertEquals(169.6, r.getCostoTotal(), 1e-9);
        assertTrue(r.isConexo());
        assertEquals(Set.of(Set.of("Armenia", "Pereira"),
                            Set.of("Pereira", "Manizales"),
                            Set.of("Armenia", "Ibagué")), parejas(r));
    }

    @Test
    void elGrafoDeJugueteDelCsvDaElMismoArbol() {
        Path dir = Paths.get("src", "test", "resources", "csv");
        try {
            Grafo g = CargadorCSV.cargarGrafo(
                    dir.resolve("municipios-toy.csv"), dir.resolve("conexiones-toy.csv"));
            ResultadoMST r = prim.calcular(g);
            assertEquals(3, r.getNumeroAristas());
            assertEquals(169.6, r.getCostoTotal(), 1e-9);
            assertTrue(r.isConexo());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void descartaLaAristaQueCierraUnCiclo() {
        ResultadoMST r = prim.calcular(grafoPequeno());
        assertFalse(parejas(r).contains(Set.of("Armenia", "Manizales")));
    }

    @Test
    void conLosDatosRealesDevuelve19AristasYUnCostoDe3457_6() {
        ResultadoMST r = prim.calcular(DatosReales.grafo());
        assertEquals(DatosReales.TOTAL_MUNICIPIOS - 1, r.getNumeroAristas());
        assertTrue(r.isConexo());
        assertEquals(3457.6, r.getCostoTotal(), 1e-6);
    }

    @Test
    void elCostoNoDependeDelMunicipioInicial() {
        Grafo g = DatosReales.grafo();
        double costoEsperado = prim.calcular(g).getCostoTotal();
        for (Municipio m : g.getMunicipios()) {
            assertEquals(costoEsperado, prim.calcular(g, m).getCostoTotal(), 1e-9);
        }
    }

    @Test
    void municipioInicialConfigurable() {
        Grafo g = grafoPequeno();
        Municipio manizales = g.getMunicipios().get(2);
        ResultadoMST r = prim.calcular(g, manizales);
        assertEquals(3, r.getNumeroAristas());
        assertEquals(169.6, r.getCostoTotal(), 1e-9);
    }

    @Test
    void municipioInicialAjenoLanzaExcepcion() {
        assertThrows(IllegalArgumentException.class,
                () -> prim.calcular(grafoPequeno(), mun("Pasto")));
    }

    @Test
    void conEmpatesElResultadoNoDependeDelOrdenDeCarga() {
        Set<Set<String>> esperado = Set.of(Set.of("Armenia", "Cali"), Set.of("Armenia", "Pereira"));
        assertEquals(esperado, parejas(prim.calcular(trianguloDeDiez(false))));
        assertEquals(esperado, parejas(prim.calcular(trianguloDeDiez(true))));
    }

    /** Tres municipios, tres aristas de 10 km: hay empate y solo se aceptan dos. */
    private Grafo trianguloDeDiez(boolean alReves) {
        Grafo g = new Grafo();
        Municipio a = mun("Armenia"), c = mun("Cali"), p = mun("Pereira");
        if (alReves) {
            g.agregarMunicipio(p);
            g.agregarMunicipio(c);
            g.agregarMunicipio(a);
            g.agregarConexion(p, c, 10);
            g.agregarConexion(p, a, 10);
            g.agregarConexion(c, a, 10);
        } else {
            g.agregarMunicipio(a);
            g.agregarMunicipio(c);
            g.agregarMunicipio(p);
            g.agregarConexion(a, c, 10);
            g.agregarConexion(a, p, 10);
            g.agregarConexion(c, p, 10);
        }
        return g;
    }

    @Test
    void grafoNoConexoDevuelveUnBosqueYLoIndica() {
        Grafo g = new Grafo();
        Municipio armenia = mun("Armenia"), pereira = mun("Pereira"), manizales = mun("Manizales");
        Municipio cali = mun("Cali"), pasto = mun("Pasto"), popayan = mun("Popayán");
        for (Municipio m : new Municipio[]{armenia, pereira, manizales, cali, pasto, popayan}) {
            g.agregarMunicipio(m);
        }
        g.agregarConexion(armenia, pereira, 45.5);
        g.agregarConexion(pereira, manizales, 51);
        g.agregarConexion(pasto, popayan, 249);
        // Cali queda aislada y el inicio por defecto es Armenia: solo se cubre su componente

        ResultadoMST r = prim.calcular(g);
        assertFalse(r.isConexo());
        assertEquals(2, r.getNumeroAristas());
        assertEquals(96.5, r.getCostoTotal(), 1e-9);
    }

    @Test
    void unSoloMunicipioNoTieneAristas() {
        Grafo g = new Grafo();
        g.agregarMunicipio(mun("Armenia"));
        ResultadoMST r = prim.calcular(g);
        assertEquals(0, r.getNumeroAristas());
        assertEquals(0.0, r.getCostoTotal(), 1e-9);
        assertTrue(r.isConexo());
    }

    @Test
    void grafoVacioNoRompe() {
        ResultadoMST r = prim.calcular(new Grafo());
        assertEquals(0, r.getNumeroAristas());
        assertTrue(r.isConexo());
    }

    @Test
    void grafoNullLanzaExcepcion() {
        assertThrows(IllegalArgumentException.class, () -> prim.calcular(null));
    }
}
