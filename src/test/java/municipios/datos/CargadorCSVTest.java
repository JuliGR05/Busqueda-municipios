package municipios.datos;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import municipios.modelo.Grafo;
import municipios.modelo.Municipio;

/**
 * Los CSV se escriben en una carpeta temporal dentro de cada prueba, así no dependen de
 * archivos externos. Además, al final se cargan los archivos fijos de
 * {@code src/test/resources/csv/} para comprobar que el cargador también funciona con
 * archivos en el classpath.
 */
class CargadorCSVTest {

    @TempDir
    Path tmp;

    private static final String ENC_MUN = "nombre;departamento;zona;latitud;longitud;fuente\n";
    private static final String ENC_CON = "municipio1;municipio2;km;fuente\n";

    private Path escribir(String nombre, String contenido) throws IOException {
        return Files.writeString(tmp.resolve(nombre), contenido, StandardCharsets.UTF_8);
    }

    private Path municipiosValidos() throws IOException {
        return escribir("municipios.csv", ENC_MUN
                + "Medellín;Antioquia;Andina;6,25;-75,56;x\n"
                + "Bogotá;Cundinamarca;Andina;4,71;-74,07;x\n"
                + "Cali;Valle;Pacífica;3,45;-76,53;x\n");
    }

    @Test
    void cargaArchivosValidosConDecimalesConComa() throws IOException {
        Path con = escribir("conexiones.csv", ENC_CON + "Medellín;Bogotá;415,5;x\n");
        Grafo g = CargadorCSV.cargarGrafo(municipiosValidos(), con);

        assertEquals(3, g.getMunicipios().size());
        Municipio med = g.buscarPorNombre("Medellín");
        Municipio bog = g.buscarPorNombre("Bogotá");
        assertEquals(415.5, g.getDistancia(med, bog), 1e-9);
        assertEquals(415.5, g.getDistancia(bog, med), 1e-9); // ambos sentidos
    }

    @Test
    void aceptaSeparadorComaYDecimalConPunto() throws IOException {
        Path mun = escribir("m.csv", "nombre,departamento,zona,latitud,longitud,fuente\n"
                + "Medellín,Antioquia,Andina,6.25,-75.56,x\n"
                + "Bogotá,Cundinamarca,Andina,4.71,-74.07,x\n");
        Path con = escribir("c.csv", "municipio1,municipio2,km,fuente\nMedellín,Bogotá,415.5,x\n");
        Grafo g = CargadorCSV.cargarGrafo(mun, con);
        assertEquals(415.5, g.getDistancia(g.buscarPorNombre("Medellín"), g.buscarPorNombre("Bogotá")), 1e-9);
    }

    @Test
    void ignoraElBomDeExcel() throws IOException {
        Path mun = escribir("m.csv", "\uFEFF" + ENC_MUN + "Cali;Valle;Pacífica;3,45;-76,53;x\n");
        Path con = escribir("c.csv", ENC_CON);
        assertEquals(1, CargadorCSV.cargarGrafo(mun, con).getMunicipios().size());
    }

    @Test
    void filaCorruptaConPocasColumnas() throws IOException {
        Path mun = escribir("m.csv", ENC_MUN + "Medellín;Antioquia\n");
        Path con = escribir("c.csv", ENC_CON);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> CargadorCSV.cargarGrafo(mun, con));
        assertTrue(e.getMessage().contains("línea 2"), e.getMessage());
    }

    @Test
    void decimalMalFormado() throws IOException {
        Path mun = escribir("m.csv", ENC_MUN + "Medellín;Antioquia;Andina;seis;-75,56;x\n");
        Path con = escribir("c.csv", ENC_CON);
        assertThrows(IllegalArgumentException.class, () -> CargadorCSV.cargarGrafo(mun, con));
    }

    @Test
    void latitudFueraDeRango() throws IOException {
        Path mun = escribir("m.csv", ENC_MUN + "Medellín;Antioquia;Andina;95;-75,56;x\n");
        Path con = escribir("c.csv", ENC_CON);
        assertThrows(IllegalArgumentException.class, () -> CargadorCSV.cargarGrafo(mun, con));
    }

    @Test
    void conexionConMunicipioNoDefinido() throws IOException {
        Path con = escribir("c.csv", ENC_CON + "Medellín;Atlantis;100;x\n");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> CargadorCSV.cargarGrafo(municipiosValidos(), con));
        assertTrue(e.getMessage().contains("Atlantis"), e.getMessage());
    }

    @Test
    void distanciaNoPositiva() throws IOException {
        Path con = escribir("c.csv", ENC_CON + "Medellín;Bogotá;0;x\n");
        assertThrows(IllegalArgumentException.class,
                () -> CargadorCSV.cargarGrafo(municipiosValidos(), con));
    }

    @Test
    void conexionRepetidaEnCualquierSentido() throws IOException {
        Path con = escribir("c.csv", ENC_CON + "Medellín;Bogotá;415;x\nBogotá;Medellín;415;x\n");
        assertThrows(IllegalArgumentException.class,
                () -> CargadorCSV.cargarGrafo(municipiosValidos(), con));
    }

    @Test
    void municipioRepetido() throws IOException {
        Path mun = escribir("m.csv", ENC_MUN
                + "Cali;Valle;Pacífica;3,45;-76,53;x\n"
                + "cali;Valle;Pacífica;3,45;-76,53;x\n");
        Path con = escribir("c.csv", ENC_CON);
        assertThrows(IllegalArgumentException.class, () -> CargadorCSV.cargarGrafo(mun, con));
    }

    @Test
    void encabezadoIncorrecto() throws IOException {
        Path mun = escribir("m.csv", "a;b;c;d;e\nMedellín;Antioquia;Andina;6,25;-75,56\n");
        Path con = escribir("c.csv", ENC_CON);
        assertThrows(IllegalArgumentException.class, () -> CargadorCSV.cargarGrafo(mun, con));
    }

    @Test
    void archivoVacio() throws IOException {
        Path mun = escribir("m.csv", "");
        Path con = escribir("c.csv", ENC_CON);
        assertThrows(IllegalArgumentException.class, () -> CargadorCSV.cargarGrafo(mun, con));
    }

    @Test
    void archivoInexistente() {
        Path noExiste = tmp.resolve("no_existe.csv");
        assertThrows(IOException.class, () -> CargadorCSV.cargarGrafo(noExiste, noExiste));
    }

    // ------------------------------------------------------------------ archivos de src/test/resources

    /** Copia un recurso de {@code src/test/resources/csv/} a la carpeta temporal. */
    private Path recurso(String nombre) throws IOException {
        try (java.io.InputStream in = CargadorCSVTest.class.getResourceAsStream("/csv/" + nombre)) {
            assertNotNull(in, () -> "falta el recurso /csv/" + nombre + " en src/test/resources");
            Path destino = tmp.resolve(nombre);
            Files.copy(in, destino);
            return destino;
        }
    }

    @Test
    void cargaLosArchivosDePruebaDelClasspath() throws IOException {
        Grafo g = CargadorCSV.cargarGrafo(recurso("municipios-validos.csv"),
                recurso("conexiones-validas.csv"));

        assertEquals(3, g.getMunicipios().size());
        assertEquals(List.of("Medellín", "Cali", "Pasto"),
                g.getMunicipios().stream().map(Municipio::getNombre).toList());
        assertEquals(274.0, g.getDistancia(g.buscarPorNombre("Medellín"),
                g.buscarPorNombre("Cali")), 1e-9);
        assertEquals(443.0, g.getDistancia(g.buscarPorNombre("Pasto"),
                g.buscarPorNombre("Cali")), 1e-9);
    }

    @Test
    void cargaElArchivoDePruebaConComaDecimalYSeparadorPuntoYComa() throws IOException {
        Grafo g = CargadorCSV.cargarGrafo(recurso("municipios-decimal-con-coma.csv"),
                recurso("conexiones-validas.csv"));

        assertEquals(3, g.getMunicipios().size());
        assertEquals(6.2448, g.buscarPorNombre("Medellín").getLatitud(), 1e-9);
        assertEquals(-77.2781, g.buscarPorNombre("Pasto").getLongitud(), 1e-9);
    }

    @Test
    void elArchivoDePruebaConFilaTruncadaIndicaLaLinea() throws IOException {
        Path con = recurso("conexiones-validas.csv");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> CargadorCSV.cargarGrafo(recurso("municipios-fila-truncada.csv"), con));
        assertTrue(e.getMessage().contains("línea 3"), e.getMessage());
        assertTrue(e.getMessage().contains("5 columnas"), e.getMessage());
    }

    @Test
    void elArchivoDePruebaConKmInvalidoIndicaElCampo() throws IOException {
        Path mun = recurso("municipios-validos.csv");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> CargadorCSV.cargarGrafo(mun, recurso("conexiones-km-invalido.csv")));
        assertTrue(e.getMessage().contains("'km'"), e.getMessage());
        assertTrue(e.getMessage().contains("línea 3"), e.getMessage());
    }
}