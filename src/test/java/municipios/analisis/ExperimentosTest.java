package municipios.analisis;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import municipios.DatosReales;
import municipios.modelo.Grafo;
import municipios.modelo.Municipio;

/**
 * Pruebas de {@link Experimentos}.
 *
 * <p>Lo importante aquí no es que los números sean unos otros, sino que las afirmaciones que el
 * informe sostiene sobre ellos se puedan comprobar: que la lista de pares usa municipios que
 * existen y cubre los veinte, que A* iguala a Dijkstra en todos ellos y que la tabla incluye al
 * menos un caso donde la búsqueda avara falla.</p>
 */
class ExperimentosTest {

    private static Grafo grafo;
    private static List<Experimentos.Fila> filas;
    private static Experimentos.ResumenGlobal global;

    @BeforeAll
    static void correrUnaVez() {
        grafo = DatosReales.grafo();
        Experimentos e = new Experimentos(grafo, DatosReales.heuristica(), 1); // 1 repetición: aquí no medimos tiempo
        filas = e.ejecutar();
        global = e.escanearTodosLosPares();
    }

    @Test
    @DisplayName("Los 18 pares de la tabla usan municipios que existen en los CSV")
    void todosLosParesUsanMunicipiosQueExisten() {
        assertEquals(18, Experimentos.PARES.size());

        for (Experimentos.Par par : Experimentos.PARES) {
            assertNotNull(grafo.buscarPorNombre(par.origen()), () -> "origen no existe: " + par.origen());
            assertNotNull(grafo.buscarPorNombre(par.destino()), () -> "destino no existe: " + par.destino());
            assertNotEquals(par.origen(), par.destino(), "un par con origen igual a destino no aporta nada");
        }
    }

    @Test
    @DisplayName("Los pares de la tabla cubren los 20 municipios del proyecto")
    void losParesCubrenLosVeinteMunicipios() {
        Set<String> usados = new HashSet<>();
        Experimentos.PARES.forEach(p -> {
            usados.add(p.origen());
            usados.add(p.destino());
        });

        List<String> faltantes = grafo.getMunicipios().stream()
                .map(Municipio::getNombre)
                .filter(n -> !usados.contains(n))
                .toList();

        assertTrue(faltantes.isEmpty(), () -> "estos municipios no aparecen en ningún par: " + faltantes);
        assertEquals(DatosReales.TOTAL_MUNICIPIOS, usados.size());
    }

    @Test
    @DisplayName("No hay pares repetidos, ni siquiera invertidos")
    void noHayParesRepetidos() {
        Set<String> vistos = new HashSet<>();
        List<String> repetidos = Experimentos.PARES.stream()
                .map(p -> {
                    String[] n = {p.origen(), p.destino()};
                    java.util.Arrays.sort(n);
                    return String.join("|", n);
                })
                .filter(v -> !vistos.add(v))
                .toList();

        assertTrue(repetidos.isEmpty(), () -> "pares repetidos: " + repetidos);
    }

    @Test
    @DisplayName("A* da el óptimo en todos los pares de la tabla")
    void aEstrellaDaElOptimoEnTodaLaTabla() {
        List<String> diferencias = filas.stream()
                .filter(f -> !f.aEstrellaCoincideConDijkstra())
                .map(f -> f.par() + ": A* " + f.aEstrella().costo() + " vs óptimo " + f.costoOptimo())
                .toList();

        assertTrue(diferencias.isEmpty(), () -> "A* no coincidió con Dijkstra en: " + diferencias);
    }

    /**
     * Criterio de aceptación del issue #9: la tabla tiene que incluir al menos un caso donde la
     * búsqueda avara no encuentre el camino más corto, con su explicación.
     */
    @Test
    @DisplayName("La tabla incluye casos donde la búsqueda avara no da el óptimo")
    void laTablaIncluyeCasosDondeLaAvaraFalla() {
        List<Experimentos.Fila> fallos = filas.stream().filter(Experimentos.Fila::avaraFalla).toList();

        assertFalse(fallos.isEmpty(), "si la búsqueda avara nunca fallara, la tabla no probaría nada");
        assertTrue(fallos.size() >= 6,
                () -> "conviene tener varios casos de fallo, no solo uno: hay " + fallos.size());
        assertTrue(fallos.stream().anyMatch(f -> f.desviacionAvara() > 50.0),
                "debería haber al menos un caso con sobrecosto grande que editor sea striking");
    }

    @Test
    @DisplayName("En los 380 pares del grafo A* siempre iguala a Dijkstra y nunca expande más")
    void aEstrellaIgualaADijkstraEnTodoElGrafo() {
        assertEquals(DatosReales.TOTAL_MUNICIPIOS * (DatosReales.TOTAL_MUNICIPIOS - 1), global.pares());
        assertEquals(global.pares(), global.estrellaCoincide(),
                "A* debe dar el óptimo en todos los pares del grafo");
        assertEquals(0, global.estrellaExpandeMas(),
                "con una heurística consistente A* nunca debe expandir más que Dijkstra");
    }

    @Test
    @DisplayName("La búsqueda avara falla en una parte apreciable de los pares del grafo")
    void laAvaraFallaEnUnaParteApreciable() {
        assertTrue(global.avaraFalla() > 0, "si nunca fallara, no habría nada que comparar");
        assertTrue(global.peorDesviacion() > 50.0,
                () -> "se esperaba un caso de sobrecosto grande; el peor fue "
                        + global.peorDesviacion() + " %");
        assertNotNull(global.peorPar());
        assertTrue(global.desviacionMedia() > 0 && global.desviacionMedia() <= global.peorDesviacion());
    }

    @Test
    @DisplayName("La desviación se calcula como (avara - óptimo) / óptimo")
    void laDesviacionSeCalculaBien() {
        for (Experimentos.Fila f : filas) {
            double esperado = (f.avara().costo() - f.costoOptimo()) / f.costoOptimo() * 100;
            assertEquals(esperado, f.desviacionAvara(), 1e-9, () -> "mal calculada en " + f.par());
            assertEquals(f.avara().costo() > f.costoOptimo() + 1e-6, f.avaraFalla(),
                    () -> "avaraFalla() no cuadra en " + f.par());
        }
    }

    @Test
    @DisplayName("A* nunca expandió más municipios que Dijkstra en la tabla")
    void aEstrellaNoExpandeMasQueDijkstra() {
        List<String> problemas = filas.stream()
                .filter(f -> f.ahorroAEstrella() < 0)
                .map(f -> f.par() + ": A* " + f.aEstrella().nodos() + " vs Dijkstra " + f.nodosDijkstra())
                .toList();

        assertTrue(problemas.isEmpty(), () -> "A* expandió más que Dijkstra en: " + problemas);
    }

    @Test
    @DisplayName("El camino devuelto siempre empieza en el origen y termina en el destino")
    void losCaminosSonCoherentes() {
        for (Experimentos.Fila f : filas) {
            List<String> greedy = f.avara().camino();
            List<String> optimo = f.aEstrella().camino();

            assertFalse(greedy.isEmpty(), () -> "la avara no devolvió camino en " + f.par());
            assertEquals(f.par().origen(), greedy.get(0), () -> "origen incorrecto en " + f.par());
            assertEquals(f.par().destino(), greedy.get(greedy.size() - 1), () -> "destino incorrecto en " + f.par());
            assertEquals(f.par().origen(), optimo.get(0));
            assertEquals(f.par().destino(), optimo.get(optimo.size() - 1));
        }
    }

    @Test
    @DisplayName("La tabla Markdown tiene una fila por par y todas las columnas")
    void laTablaMarkdownEstaCompleta() {
        String tabla = new Experimentos(grafo, DatosReales.heuristica(), 1).tablaMarkdown(filas);
        List<String> lineas = tabla.lines().toList();

        assertTrue(lineas.get(0).startsWith("| Origen | Destino |"), lineas.get(0));
        assertTrue(lineas.get(1).startsWith("|---"), lineas.get(1));
        assertEquals(filas.size() + 2, lineas.size());
        long columnas = lineas.get(0).chars().filter(c -> c == '|').count();
        for (String linea : lineas.subList(2, lineas.size())) {
            assertEquals(columnas, linea.chars().filter(c -> c == '|').count(),
                    () -> "la fila no tiene las mismas columnas que la cabecera: " + linea);
        }
    }

    @Test
    @DisplayName("El CSV tiene una fila por par y los caminos completos entre comillas")
    void elCsvEstaCompleto() {
        String csv = new Experimentos(grafo, DatosReales.heuristica(), 1).csv(filas);
        List<String> lineas = csv.lines().toList();

        assertEquals(filas.size() + 1, lineas.size());
        assertTrue(lineas.get(0).startsWith("origen,destino,costo_avara_km"), lineas.get(0));
        assertTrue(csv.contains("Ibagué -> Armenia -> Cali"),
                "el CSV debe traer el camino óptimo completo, no solo el número de municipios");
        assertTrue(csv.contains("Ibagué -> Neiva -> Popayán -> Cali"),
                "el CSV debe traer también el camino que eligió la búsqueda avara");
    }

    @Test
    @DisplayName("El análisis escrito menciona los números que salen de la corrida")
    void elAnalisisCitaLosNumerosDeLaCorrida() {
        String analisis = new Experimentos(grafo, DatosReales.heuristica(), 1).analisis(filas, global);

        assertTrue(analisis.contains(String.valueOf(global.pares())), "debe citar los 380 pares");
        assertTrue(analisis.contains(String.valueOf(global.avaraFalla())),
                "debe decir en cuántos pares falló la búsqueda avara");
        assertTrue(analisis.contains(String.format(Locale.US, "%.1f", global.peorDesviacion()))
                        || analisis.contains(String.format(Locale.US, "%.0f", global.peorDesviacion())),
                () -> "debe citar el peor sobrecosto, que es "
                        + String.format(Locale.US, "%.1f", global.peorDesviacion()) + " %");
        assertTrue(analisis.contains("Por qué se equivoca"), "debe explicar el fallo del caso más claro");
        assertTrue(analisis.contains("Conclusiones"), "debe cerrar con conclusiones");
    }

    @Test
    @DisplayName("El main escribe los tres archivos de resultados")
    void elMainEscribeLosTresArchivos(@TempDir Path salida) throws IOException {
        Experimentos.main(new String[] {"data", salida.toString()});

        assertTrue(Files.isRegularFile(salida.resolve("resultados-experimentos.csv")));
        assertTrue(Files.isRegularFile(salida.resolve("tabla-resultados.md")));
        assertTrue(Files.isRegularFile(salida.resolve("analisis-resultados.md")));

        String tabla = Files.readString(salida.resolve("tabla-resultados.md"));
        assertTrue(tabla.startsWith("# Tabla de resultados"), tabla.substring(0, 40));
        assertTrue(tabla.contains("No editar a mano"), "debe avisar de que el archivo es generado");

        List<String> lineasCsv = Files.readAllLines(salida.resolve("resultados-experimentos.csv"));
        assertEquals(filas.size() + 1, lineasCsv.size());
    }

    @Test
    @DisplayName("Rechaza argumentos inválidos")
    void rechazaArgumentosInvalidos() {
        Experimentos e = new Experimentos(grafo, DatosReales.heuristica(), 1);
        assertThrows(IllegalArgumentException.class, () -> new Experimentos(null, DatosReales.heuristica(), 1));
        assertThrows(IllegalArgumentException.class, () -> new Experimentos(grafo, null, 1));
        assertThrows(IllegalArgumentException.class, () -> new Experimentos(grafo, DatosReales.heuristica(), 0));
    }

    @Test
    @DisplayName("Las columnas del CSV son las que pide el issue #9")
    void lasColumnasDelCsvSonLasEsperadas() {
        String cabecera = new Experimentos(grafo, DatosReales.heuristica(), 1)
                .csv(List.of()).lines().findFirst().orElse("");

        List<String> columnas = List.of(cabecera.split(",")).stream().collect(Collectors.toList());
        for (String esperada : List.of("origen", "destino", "costo_avara_km", "nodos_avara",
                "tiempo_avara_us", "camino_avara", "costo_astar_km", "nodos_astar", "tiempo_astar_us",
                "camino_astar", "costo_optimo_km", "nodos_dijkstra", "tiempo_dijkstra_us",
                "avara_se_aleja_porcentaje", "astar_ahorra_nodos")) {
            assertTrue(columnas.contains(esperada), () -> "falta la columna " + esperada);
        }
    }

    @Test
    @DisplayName("La tabla MST compara Kruskal y Prim con el costo total compartido")
    void tablaMstComparaKruskalYPrim() {
        String tabla = new Experimentos(grafo, DatosReales.heuristica(), 1).tablaMstMarkdown();
        assertTrue(tabla.contains("Kruskal"));
        assertTrue(tabla.contains("Prim"));
        assertTrue(tabla.contains("3457.6"));
        assertTrue(tabla.contains("Aristas descartadas"));
    }
}