package municipios.datos;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import municipios.modelo.Grafo;
import municipios.modelo.Municipio;

/**
 * Los CSV se escriben en una carpeta temporal dentro de cada prueba, así no
 * dependen de archivos externos. Si prefieres archivos fijos, cópialos a
 * test/resources/ y léelos con getResource.
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
}