package municipios.ui;

import municipios.algoritmo.BusquedaAvara;
import municipios.algoritmo.Dijkstra;
import municipios.algoritmo.DistanciaLineaRecta;
import municipios.algoritmo.Heuristica;
import municipios.algoritmo.ResultadoBusqueda;
import municipios.datos.CargadorCSV;
import municipios.modelo.Grafo;
import municipios.modelo.Municipio;
import municipios.ui.MenuConsola.Algoritmo;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Pruebas de {@link MenuConsola} y {@link Main} con la entrada simulada (sin teclado).
 * A* todavía no existe (issue #15), así que para probar "ambos" se usa un A* simulado con Dijkstra.
 *
 * <pre>
 * javac -encoding UTF-8 -d out $(find src/main/java test -name "*.java")
 * java -cp out municipios.ui.PruebasMenuConsola
 * </pre>
 * Se ejecuta desde la raíz del repositorio; termina con código 1 si alguna prueba falla.
 */
public class PruebasMenuConsola {

    private static final Path DATOS = Paths.get("data");
    private static PrintStream consola;
    private static int total;
    private static int fallos;

    public static void main(String[] args) throws Exception {
        consola = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        Grafo grafo = CargadorCSV.cargarGrafo(DATOS.resolve("municipios.csv"), DATOS.resolve("conexiones.csv"));
        Heuristica heuristica = new DistanciaLineaRecta();
        Algoritmo avara = new Algoritmo("Búsqueda avara", new BusquedaAvara(grafo, heuristica)::buscar);
        Algoritmo aEstrellaSimulado = new Algoritmo("A*", (o, d) -> {
            List<Municipio> camino = Dijkstra.caminoMinimo(grafo, o, d);
            return new ResultadoBusqueda(camino, Dijkstra.costoMinimo(grafo, o, d), camino.size() + 3, !camino.isEmpty());
        });
        List<String> nombres = new ArrayList<>();
        grafo.getMunicipios().forEach(m -> nombres.add(m.getNombre()));

        consola.println("== Criterios de aceptación del issue #7");
        String cinco = correr(grafo, avara, null,
                "1", "1", "1", "6",       // consulta 1: menú, algoritmo, origen, destino
                "1", "1", "2", "7",       // consulta 2
                "1", "1", "3", "8",       // consulta 3
                "1", "1", "4", "9",       // consulta 4
                "1", "1", "5", "10",      // consulta 5
                "0");
        probar("sesión de 5 consultas seguidas sin reiniciar", contar(cinco, "Costo total:") == 5
                && cinco.contains("¡Hasta luego!") && !cinco.contains("Exception"));

        String ambos = correr(grafo, avara, aEstrellaSimulado, "1", "3", "Barranquilla", "Pasto", "0");
        probar("al elegir \"ambos\" se ve la diferencia de costo y de nodos expandidos",
                ambos.contains("Comparación: Barranquilla -> Pasto") && ambos.contains("Diferencia de costo:")
                        && ambos.contains("Diferencia de nodos expandidos:") && ambos.contains("Búsqueda avara")
                        && ambos.contains("Nodos expandidos") && ambos.contains("Camino (A*):"));

        String invalidas = correr(grafo, avara, null,
                "x", "", "9", "-1",                                   // opciones del menú inválidas
                "1", "zzz", "4", "", "2", "3", "1",                   // algoritmo inválido y A* no disponible
                "", "0x", "99", "-3", "99999999999999", "Bogota",     // origen inválido
                "Medelin", "medellin",                                // sugerencia y luego nombre válido
                "MEDELLIN",                                           // destino igual al origen
                "cali", "0");
        probar("ninguna entrada inválida lanza una excepción sin manejar", invalidas.contains("Costo total:")
                && invalidas.contains("¡Hasta luego!") && !invalidas.contains("Exception"));

        consola.println("\n== Mensajes por tipo de entrada inválida");
        probar("opción de menú inválida", invalidas.contains("Opción no válida: 'x'. Escribe 1, 2 o 0.")
                && invalidas.contains("Opción no válida: '-1'"));
        probar("algoritmo inválido", invalidas.contains("Opción no válida: 'zzz'. Escribe 1, 2 o 3."));
        probar("A* aún no disponible (elegir 2)", invalidas.contains("A* todavía no está integrado"));
        probar("origen vacío", invalidas.contains("No escribiste nada"));
        probar("número fuera de rango, negativo o gigante", invalidas.contains("El número 99 no está en la lista")
                && invalidas.contains("El número -3 no está en la lista")
                && invalidas.contains("El número 99999999999999 no está en la lista"));
        probar("municipio inexistente", invalidas.contains("No existe un municipio llamado 'Bogota'"));
        probar("sugerencia por error de tipeo", invalidas.contains("¿Quisiste decir: Medellín?"));
        probar("origen igual al destino", invalidas.contains("El destino no puede ser igual al origen (Medellín)"));

        consola.println("\n== Entrada de municipios");
        String porNombre = correr(grafo, avara, null, "1", "1", "MEDELLIN", "cali", "0");
        probar("nombre sin tilde y en mayúsculas o minúsculas", porNombre.contains("Búsqueda avara: Medellín -> Cali"));
        String porNumero = correr(grafo, avara, null, "1", "1", "6", "10", "0");
        probar("por número (6 = " + nombres.get(5) + ", 10 = " + nombres.get(9) + ")",
                porNumero.contains("Búsqueda avara: " + nombres.get(5) + " -> " + nombres.get(9)));
        String lista = correr(grafo, avara, null, "2", "0");
        boolean todos = true;
        for (int i = 0; i < nombres.size(); i++) {
            todos &= lista.contains((i + 1) + ") " + nombres.get(i));
        }
        probar("la lista muestra los " + nombres.size() + " municipios numerados", todos);
        String pidiendoLista = correr(grafo, avara, null, "1", "1", "lista", "0", "0");
        probar("'lista' en el prompt vuelve a mostrar los municipios", contar(pidiendoLista, "Municipios disponibles:") >= 2);

        consola.println("\n== Salidas y casos límite");
        probar("salir con 0", correr(grafo, avara, null, "0").contains("¡Hasta luego!"));
        probar("salir con la palabra 'salir'", correr(grafo, avara, null, "SALIR").contains("¡Hasta luego!"));
        probar("volver con 0 desde el algoritmo y desde el origen", correr(grafo, avara, null,
                "1", "0", "1", "1", "0", "1", "1", "6", "0", "0").contains("¡Hasta luego!"));
        String cortada = correrTexto(grafo, avara, null, "1\n1\n");
        probar("la entrada termina a mitad de una consulta (Ctrl+D) sin error",
                cortada.contains("¡Hasta luego!") && !cortada.contains("Exception"));
        probar("entrada totalmente vacía sin error", correrTexto(grafo, avara, null, "").contains("¡Hasta luego!"));

        Grafo islas = new Grafo();
        Municipio uno = new Municipio("Uno", 4.0, -74.0);
        Municipio dos = new Municipio("Dos", 4.5, -74.5);
        Municipio tres = new Municipio("Tres", 5.0, -75.0);
        islas.agregarMunicipio(uno);
        islas.agregarMunicipio(dos);
        islas.agregarMunicipio(tres);
        islas.agregarConexion(uno, dos, 100.0);
        Algoritmo avaraIslas = new Algoritmo("Búsqueda avara", new BusquedaAvara(islas, heuristica)::buscar);
        String sinRuta = correr(islas, avaraIslas, null, "1", "1", "Uno", "Tres", "0");
        probar("mensaje amigable si no hay ruta", sinRuta.contains("No se encontró una ruta entre Uno y Tres")
                && sinRuta.contains("no estén conectados") && !sinRuta.contains("Exception"));

        Algoritmo quebrado = new Algoritmo("Algoritmo con error", (o, d) -> {
            throw new IllegalStateException("fallo simulado");
        });
        String conFallo = correr(grafo, quebrado, null, "1", "1", "1", "2", "0");
        probar("si un algoritmo lanza una excepción el menú sigue funcionando", conFallo.contains("fallo simulado")
                && conFallo.contains("¡Hasta luego!"));

        consola.println("\n== Main (carga de datos)");
        Path temporal = Files.createTempDirectory("menu-");
        try {
            Resultado bueno = ejecutarMain(DATOS, "0\n");
            probar("Main con datos correctos abre el menú y termina con código 0",
                    bueno.codigo == 0 && bueno.salida.contains("rutas entre municipios"));

            Resultado inexistente = ejecutarMain(temporal.resolve("no-existe"), "");
            probar("Main con carpeta inexistente muestra error claro y termina con código 1",
                    inexistente.codigo == 1 && inexistente.salida.contains("No se pudieron cargar los datos"));

            Files.copy(DATOS.resolve("municipios.csv"), temporal.resolve("municipios.csv"));
            Files.writeString(temporal.resolve("conexiones.csv"),
                    "municipio1,municipio2,km,fuente\nCali,Armenia,abc,x\n", StandardCharsets.UTF_8);
            Resultado danado = ejecutarMain(temporal, "");
            probar("Main con un CSV dañado indica el archivo y la línea, y termina con código 1",
                    danado.codigo == 1 && danado.salida.contains("conexiones.csv, l") && danado.salida.contains("2"));
        } finally {
            try (Stream<Path> rutas = Files.walk(temporal)) {
                rutas.sorted(Comparator.reverseOrder()).forEach(r -> r.toFile().delete());
            }
        }

        consola.println("\n" + (total - fallos) + " de " + total + " pruebas correctas");
        if (fallos > 0) {
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------ utilidades

    private static String correr(Grafo grafo, Algoritmo avara, Algoritmo aEstrella, String... lineas) {
        return correrTexto(grafo, avara, aEstrella, String.join("\n", lineas) + "\n");
    }

    private static String correrTexto(Grafo grafo, Algoritmo avara, Algoritmo aEstrella, String entrada) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream salida = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        new MenuConsola(grafo, avara, aEstrella, new BufferedReader(new StringReader(entrada)), salida).ejecutar();
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private record Resultado(int codigo, String salida) {}

    private static Resultado ejecutarMain(Path carpeta, String entrada) throws IOException, InterruptedException {
        Process proceso = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Dstdout.encoding=UTF-8", "-Dfile.encoding=UTF-8", "-cp", System.getProperty("java.class.path"),
                "municipios.ui.Main", carpeta.toString()).redirectErrorStream(true).start();
        proceso.getOutputStream().write(entrada.getBytes(StandardCharsets.UTF_8));
        proceso.getOutputStream().close();
        String salida = new String(proceso.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new Resultado(proceso.waitFor(), salida);
    }

    private static int contar(String texto, String buscado) {
        int cuenta = 0;
        for (int i = texto.indexOf(buscado); i >= 0; i = texto.indexOf(buscado, i + buscado.length())) {
            cuenta++;
        }
        return cuenta;
    }

    private static void probar(String descripcion, boolean correcto) {
        total++;
        if (!correcto) {
            fallos++;
        }
        consola.println((correcto ? "  [OK]    " : "  [FALLA] ") + descripcion);
    }
}
