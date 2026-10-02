package municipios.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import municipios.DatosReales;
import municipios.algoritmo.BusquedaAvara;
import municipios.algoritmo.BusquedaEstrella;
import municipios.algoritmo.DistanciaLineaRecta;
import municipios.algoritmo.Heuristica;
import municipios.modelo.Grafo;
import municipios.modelo.Municipio;
import municipios.ui.MenuConsola.Algoritmo;

/**
 * Pruebas de {@link MenuConsola} y de {@link Main} con la entrada simulada, sin teclado.
 *
 * <p>Se cubren los criterios de aceptación del issue #7: una sesión de varias consultas sin
 * reiniciar, la comparación lado a lado al elegir "ambos" y que ninguna entrada inválida
 * provoque una excepción sin manejar.</p>
 */
class MenuConsolaTest {

    private Grafo grafo;
    private Heuristica heuristica;
    private Algoritmo avara;
    private Algoritmo aEstrella;
    private List<String> nombres;

    @TempDir
    Path temporal;

    @BeforeEach
    void preparar() {
        grafo = DatosReales.grafo();
        heuristica = new DistanciaLineaRecta();
        avara = new Algoritmo("Búsqueda avara", new BusquedaAvara(grafo, heuristica)::buscar);
        aEstrella = new Algoritmo("A*", new BusquedaEstrella(grafo, heuristica)::buscar);
        nombres = new ArrayList<>();
        grafo.getMunicipios().forEach(m -> nombres.add(m.getNombre()));
    }

    // ------------------------------------------------------------------ criterios de aceptación

    @Test
    @DisplayName("Se puede hacer una sesión de 5 consultas seguidas sin reiniciar")
    void sesionDeCincoConsultas() {
        String salida = correr(avara, aEstrella,
                "1", "1", "1", "6",       // consulta 1: menú, algoritmo, origen, destino
                "1", "1", "2", "7",       // consulta 2
                "1", "1", "3", "8",       // consulta 3
                "1", "1", "4", "9",       // consulta 4
                "1", "1", "5", "10",      // consulta 5
                "0");

        assertEquals(5, contar(salida, "Costo total:"), salida);
        assertTrue(salida.contains("¡Hasta luego!"), salida);
        assertFalse(salida.contains("Exception"), salida);
    }

    @Test
    @DisplayName("Al elegir \"ambos\" se ve la diferencia de costo y de nodos expandidos")
    void comparacionLadoALado() {
        String salida = correr(avara, aEstrella, "1", "3", "Barranquilla", "Pasto", "0");

        assertTrue(salida.contains("Comparación: Barranquilla -> Pasto"), salida);
        assertTrue(salida.contains("Diferencia de costo:"), salida);
        assertTrue(salida.contains("Diferencia de nodos expandidos:"), salida);
        assertTrue(salida.contains("Búsqueda avara"), salida);
        assertTrue(salida.contains("Nodos expandidos"), salida);
        assertTrue(salida.contains("Camino (A*):"), salida);
    }

    @Test
    @DisplayName("Ninguna entrada inválida lanza una excepción sin manejar")
    void entradasInvalidasNoRevientan() {
        String salida = correr(avara, aEstrella,
                "x", "", "9", "-1",                                // opciones del menú inválidas
                "1", "zzz", "4", "",                               // algoritmo inválido
                "0",                                              // volver al menú
                "1", "1",                                          // segunda consulta, búsqueda avara
                "", "0x", "99", "-3", "99999999999999", "Bogota",  // origen inválido
                "Medelin", "medellin",                             // sugerencia y luego nombre válido
                "MEDELLIN",                                        // destino igual al origen
                "cali", "0");                                      // destino válido y salir

        assertTrue(salida.contains("Costo total:"), salida);
        assertTrue(salida.contains("¡Hasta luego!"), salida);
        assertFalse(salida.contains("Exception"), salida);
    }

    @Test
    @DisplayName("Cada tipo de entrada inválida tiene su propio mensaje")
    void mensajesPorTipoDeEntradaInvalida() {
        String salida = correr(avara, aEstrella,
                "x", "", "9", "-1",                                // opciones del menú inválidas
                "1", "zzz", "4", "",                               // algoritmo inválido
                "0",                                              // volver al menú
                "1", "1",                                          // segunda consulta, búsqueda avara
                "", "0x", "99", "-3", "99999999999999", "Bogota",  // origen inválido
                "Medelin", "medellin",                             // sugerencia y luego nombre válido
                "MEDELLIN",                                        // destino igual al origen
                "cali", "0");                                      // destino válido y salir

        assertTrue(salida.contains("Opción no válida: 'x'. Escribe 1, 2 o 0."), salida);
        assertTrue(salida.contains("Opción no válida: '-1'"), salida);
        assertTrue(salida.contains("Opción no válida: 'zzz'. Escribe 1, 2 o 3."), salida);
        assertTrue(salida.contains("No escribiste nada"), salida);
        assertTrue(salida.contains("El número 99 no está en la lista"), salida);
        assertTrue(salida.contains("El número -3 no está en la lista"), salida);
        assertTrue(salida.contains("El número 99999999999999 no está en la lista"), salida);
        assertTrue(salida.contains("No existe un municipio llamado 'Bogota'"), salida);
        assertTrue(salida.contains("¿Quisiste decir: Medellín?"), salida);
        assertTrue(salida.contains("El destino no puede ser igual al origen (Medellín)"), salida);
    }

    @Test
    @DisplayName("El menú aclara que A* no está disponible cuando no se le entrega")
    void avaraSinAEstrella() {
        String salida = correr(avara, null, "1", "2", "1", "2", "0");

        assertTrue(salida.contains("A* todavía no está integrado"), salida);
        assertTrue(salida.contains("(aún no disponible)"), salida);
    }

    // ------------------------------------------------------------------ entrada de municipios

    @Test
    @DisplayName("Acepta el nombre con o sin tildes y en mayúsculas o minúsculas")
    void municipioPorNombre() {
        String salida = correr(avara, aEstrella, "1", "1", "MEDELLIN", "cali", "0");

        assertTrue(salida.contains("Búsqueda avara: Medellín -> Cali"), salida);
    }

    @Test
    @DisplayName("Acepta el municipio por su número en la lista")
    void municipioPorNumero() {
        String salida = correr(avara, aEstrella, "1", "1", "6", "10", "0");

        assertTrue(salida.contains("Búsqueda avara: " + nombres.get(5) + " -> " + nombres.get(9)), salida);
    }

    @Test
    @DisplayName("La lista muestra los 20 municipios numerados")
    void listaDeMunicipios() {
        String salida = correr(avara, aEstrella, "2", "0");

        for (int i = 0; i < nombres.size(); i++) {
            final String buscado = (i + 1) + ") " + nombres.get(i);
            assertTrue(salida.contains(buscado), () -> "falta " + buscado + " en:\n" + salida);
        }
        assertEquals(DatosReales.TOTAL_MUNICIPIOS, nombres.size());
    }

    @Test
    @DisplayName("Escribir \"lista\" en el prompt vuelve a mostrar los municipios")
    void pedirLaListaDesdeElPrompt() {
        String salida = correr(avara, aEstrella, "1", "1", "lista", "0", "0");

        assertTrue(contar(salida, "Municipios disponibles:") >= 2, salida);
    }

    // ------------------------------------------------------------------ salidas y casos límite

    @Test
    @DisplayName("Se sale con 0 o con la palabra \"salir\"")
    void salir() {
        assertTrue(correr(avara, aEstrella, "0").contains("¡Hasta luego!"));
        assertTrue(correr(avara, aEstrella, "SALIR").contains("¡Hasta luego!"));
    }

    @Test
    @DisplayName("Se puede volver al menú con 0 desde donde se esté")
    void volverConCero() {
        String salida = correr(avara, aEstrella, "1", "0", "1", "1", "0", "1", "1", "6", "0", "0");

        assertTrue(salida.contains("¡Hasta luego!"), salida);
    }

    @Test
    @DisplayName("Si la entrada se corta a mitad de una consulta el menú cierra sin error")
    void entradaTerminadaAMitad() {
        String salida = correrTexto(avara, aEstrella, "1\n1\n");

        assertTrue(salida.contains("¡Hasta luego!"), salida);
        assertFalse(salida.contains("Exception"), salida);
    }

    @Test
    @DisplayName("Una entrada totalmente vacía cierra el menú sin error")
    void entradaVacia() {
        assertTrue(correrTexto(avara, aEstrella, "").contains("¡Hasta luego!"));
    }

    @Test
    @DisplayName("Si no hay ruta se avisa con un mensaje amigable")
    void mensajeSiNoHayRuta() {
        Grafo islas = new Grafo();
        Municipio uno = new Municipio("Uno", 4.0, -74.0);
        Municipio dos = new Municipio("Dos", 4.5, -74.5);
        Municipio tres = new Municipio("Tres", 5.0, -75.0);
        islas.agregarMunicipio(uno);
        islas.agregarMunicipio(dos);
        islas.agregarMunicipio(tres);
        islas.agregarConexion(uno, dos, 100.0);
        Algoritmo avaraIslas = new Algoritmo("Búsqueda avara", new BusquedaAvara(islas, heuristica)::buscar);
        Algoritmo estrellaIslas = new Algoritmo("A*", new BusquedaEstrella(islas, heuristica)::buscar);

        String salida = correrEn(islas, avaraIslas, estrellaIslas, "1", "3", "Uno", "Tres", "0");

        assertEquals(2, contar(salida, "No se encontró una ruta entre Uno y Tres"), salida);
        assertTrue(salida.contains("no estén conectados"), salida);
        assertFalse(salida.contains("Exception"), salida);
    }

    @Test
    @DisplayName("Si un algoritmo lanza una excepción el menú sigue funcionando")
    void algoritmoQueFalla() {
        Algoritmo quebrado = new Algoritmo("Algoritmo con error",
                (o, d) -> {
                    throw new IllegalStateException("fallo simulado");
                });

        String salida = correr(quebrado, aEstrella, "1", "1", "1", "2", "0");

        assertTrue(salida.contains("fallo simulado"), salida);
        assertTrue(salida.contains("¡Hasta luego!"), salida);
    }

    // ------------------------------------------------------------------ Main

    @Test
    @DisplayName("Main con datos correctos abre el menú y termina con código 0")
    void mainConDatosCorrectos() throws IOException, InterruptedException {
        Resultado r = ejecutarMain(DatosReales.carpeta(), "0\n");

        assertEquals(0, r.codigo(), r.salida());
        assertTrue(r.salida().contains("rutas entre municipios"), r.salida());
    }

    @Test
    @DisplayName("Main con una carpeta inexistente explica el motivo y termina con código 1")
    void mainSinCarpetaDeDatos() throws IOException, InterruptedException {
        Resultado r = ejecutarMain(temporal.resolve("no-existe"), "");

        assertEquals(1, r.codigo(), r.salida());
        assertTrue(r.salida().contains("No se pudieron cargar los datos"), r.salida());
    }

    @Test
    @DisplayName("Main con un CSV dañado indica el archivo y la línea, y termina con código 1")
    void mainConCsvDanado() throws IOException, InterruptedException {
        Files.copy(DatosReales.municipios(), temporal.resolve("municipios.csv"));
        Files.writeString(temporal.resolve("conexiones.csv"),
                "municipio1,municipio2,km,fuente\nCali,Armenia,abc,x\n", StandardCharsets.UTF_8);

        Resultado r = ejecutarMain(temporal, "");

        assertEquals(1, r.codigo(), r.salida());
        assertTrue(r.salida().contains("conexiones.csv, l"), r.salida());
        assertTrue(r.salida().contains("2"), r.salida());
    }

    @Test
    @DisplayName("Main también funciona con una carpeta propia bien formada")
    void mainConCarpetaPropia() throws IOException, InterruptedException {
        Files.copy(DatosReales.municipios(), temporal.resolve("municipios.csv"));
        Files.copy(DatosReales.conexiones(), temporal.resolve("conexiones.csv"));

        Resultado r = ejecutarMain(temporal, "0\n");

        assertEquals(0, r.codigo(), r.salida());
    }

    // ------------------------------------------------------------------ utilidades

    private String correr(Algoritmo algA, Algoritmo algB, String... lineas) {
        return correrTexto(algA, algB, String.join("\n", lineas) + "\n");
    }

    private String correrTexto(Algoritmo algA, Algoritmo algB, String entrada) {
        return correrEn(grafo, algA, algB, entrada);
    }

    private String correrEn(Grafo g, Algoritmo algA, Algoritmo algB, String... lineas) {
        return correrEn(g, algA, algB, String.join("\n", lineas) + "\n");
    }

    private String correrEn(Grafo g, Algoritmo algA, Algoritmo algB, String entrada) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream salida = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        new MenuConsola(g, algA, algB, new BufferedReader(new StringReader(entrada)), salida).ejecutar();
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private record Resultado(int codigo, String salida) {}

    private Resultado ejecutarMain(Path carpeta, String entrada) throws IOException, InterruptedException {
        Process proceso = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Dstdout.encoding=UTF-8", "-Dfile.encoding=UTF-8",
                "-cp", rutaDeClases(),
                Main.class.getName(), carpeta.toString())
                .redirectErrorStream(true).start();
        proceso.getOutputStream().write(entrada.getBytes(StandardCharsets.UTF_8));
        proceso.getOutputStream().close();
        String salida = new String(proceso.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new Resultado(proceso.waitFor(), salida);
    }

    /**
     * Surefire deja {@code java.class.path} apuntando a su propio arranque, así que se arma la
     * ruta a mano con las clases compiladas y los jar de prueba. Si ya viene una ruta usable,
     * se respeta.
     */
    private String rutaDeClases() {
        String actual = System.getProperty("java.class.path");
        if (actual != null && !actual.contains("surefire") && Files.isRegularFile(
                Path.of(actual.split(java.io.File.pathSeparator)[0]))) {
            return actual;
        }
        List<String> partes = new ArrayList<>();
        partes.add(Path.of("target", "classes").toAbsolutePath().toString());
        partes.add(Path.of("target", "test-classes").toAbsolutePath().toString());
        return String.join(java.io.File.pathSeparator, partes);
    }

    private static int contar(String texto, String buscado) {
        int cuenta = 0;
        for (int i = texto.indexOf(buscado); i >= 0; i = texto.indexOf(buscado, i + buscado.length())) {
            cuenta++;
        }
        return cuenta;
    }
}