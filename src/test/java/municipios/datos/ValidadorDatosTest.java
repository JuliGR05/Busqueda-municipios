package municipios.datos;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import municipios.DatosReales;
import municipios.datos.ValidadorDatos.Estado;
import municipios.datos.ValidadorDatos.Severidad;
import municipios.datos.ValidadorDatos.Verificacion;

/**
 * Pruebas de {@link ValidadorDatos}.
 *
 * <p>El validador solo sirve si detecta los errores que dicen detectar, así que además de
 * comprobar que los CSV reales del repositorio pasan las 29 verificaciones, aquí se toma una
 * copia de esos mismos CSV, se daña a propósito con un solo tipo de error por caso y se
 * comprueba que la verificación correspondiente lo detecta.</p>
 */
class ValidadorDatosTest {

    private static final Path MUNICIPIOS = DatosReales.municipios();
    private static final Path CONEXIONES = DatosReales.conexiones();

    private static List<String> municipiosOriginales;
    private static List<String> conexionesOriginales;

    /**
     * Copia de los CSV que cada caso daña; las listas de líneas son las del archivo y la
     * posición 0 es la cabecera.
     */
    private static final class Datos {
        List<String> m;
        List<String> c;
        byte[] crudoM;
        byte[] crudoC;
        boolean sinM;
        boolean sinC;

        byte[] bytesM() {
            return crudoM != null ? crudoM : (String.join("\n", m) + "\n").getBytes(StandardCharsets.UTF_8);
        }

        byte[] bytesC() {
            return crudoC != null ? crudoC : (String.join("\n", c) + "\n").getBytes(StandardCharsets.UTF_8);
        }
    }

    private record Caso(String nombre, Consumer<Datos> dano, Map<Verificacion, Estado> esperado) {}

    @BeforeAll
    static void leerLosCsvReales() throws IOException {
        municipiosOriginales = Files.readAllLines(MUNICIPIOS, StandardCharsets.UTF_8);
        conexionesOriginales = Files.readAllLines(CONEXIONES, StandardCharsets.UTF_8);
        assertFalse(municipiosOriginales.stream().anyMatch(l -> l.contains("\"")),
                "estas pruebas parten de los CSV de data/ y suponen que no llevan comillas");
        assertFalse(conexionesOriginales.stream().anyMatch(l -> l.contains("\"")),
                "estas pruebas parten de los CSV de data/ y suponen que no llevan comillas");
    }

    // ------------------------------------------------------------------ criterio de aceptación

    @Test
    @DisplayName("Los CSV de data/ pasan las 29 verificaciones sin errores")
    void losCsvRealesPasanTodasLasVerificaciones() {
        ValidadorDatos validador = new ValidadorDatos();
        validador.validar();

        assertEquals(Verificacion.values().length, 29, "el validador debe seguir teniendo 29 verificaciones");
        assertFalse(validador.hayErrores(), () -> "no debería haber errores: "
                + validador.getProblemas().stream().filter(p -> p.severidad() == Severidad.ERROR).toList());

        List<Verificacion> noOk = validador.getResultados().entrySet().stream()
                .filter(e -> e.getValue() != Estado.OK)
                .map(Map.Entry::getKey)
                .toList();
        assertTrue(noOk.isEmpty(), () -> "estas verificaciones no quedaron OK: " + noOk);
        assertEquals(0, validador.contar(Severidad.ERROR));
        assertTrue(validador.generarReporte().contains("RESULTADO: OK - pasaron las 29 verificaciones"),
                validador.generarReporte());
    }

    @Test
    @DisplayName("El constructor sin argumentos valida los CSV de data/")
    void elConstructorPorDefectoUsaLosCsvDeData() {
        ValidadorDatos validador = new ValidadorDatos();
        validador.validar();

        String reporte = validador.generarReporte();
        // Path imprime separadores nativos, así que se compara con el nombre del archivo.
        assertTrue(reporte.contains("municipios.csv"), reporte);
        assertTrue(reporte.contains("conexiones.csv"), reporte);
        assertFalse(validador.hayErrores());
    }

    @Test
    @DisplayName("Validar dos veces no acumula problemas del intento anterior")
    void validarVariasVecesEsIndependiente() {
        ValidadorDatos validador = new ValidadorDatos();
        validador.validar();
        int despues = validador.getProblemas().size();

        validador.validar();
        assertEquals(despues, validador.getProblemas().size());
    }

    @Test
    @DisplayName("El main termina con código 1 cuando los datos tienen errores")
    void elMainTerminaConCodigo1SiHayErrores() throws Exception {
        Path carpeta = Files.createTempDirectory("validador-datos-");
        try {
            Files.copy(CONEXIONES, carpeta.resolve("conexiones.csv"));
            Files.writeString(carpeta.resolve("municipios.csv"),
                    "nombre,departamento,zona,latitud,longitud,fuente\nCali,Valle del Cauca,X,3.4,-76.5,prueba\n",
                    StandardCharsets.UTF_8);

            assertEquals(1, ejecutarMain(carpeta), "con datos dañados debe terminar con código 1");

            Files.copy(MUNICIPIOS, carpeta.resolve("municipios.csv"),
                    StandardCopyOption.REPLACE_EXISTING);
            assertEquals(0, ejecutarMain(carpeta), "con los datos correctos debe terminar con código 0");
        } finally {
            borrar(carpeta);
        }
    }

    @Test
    @DisplayName("El main avisa si se usan mal los argumentos")
    void elMainRechazaArgumentosIncorrectos() throws Exception {
        assertEquals(2, ejecutarMainCon("solo-un-argumento"));
    }

    // ------------------------------------------------------------------ un caso por tipo de error

    static Stream<Arguments> casos() throws IOException {
        String nombre1 = celda(municipiosOriginales, 1, 0);
        String conA = celda(conexionesOriginales, 1, 0);
        String conB = celda(conexionesOriginales, 1, 1);
        String km1 = celda(conexionesOriginales, 1, 2);
        int filaTilde = filaConTilde(municipiosOriginales);
        String conTilde = celda(municipiosOriginales, filaTilde, 0);

        List<Caso> casos = new ArrayList<>();
        List<String> m = municipiosOriginales;
        List<String> c = conexionesOriginales;

        // ----- municipios.csv
        caso(casos, "M1 archivo inexistente", d -> d.sinM = true, Estado.FALLA, Verificacion.M1);
        caso(casos, "M1 archivo vacío", d -> d.crudoM = new byte[0], Estado.FALLA, Verificacion.M1);
        caso(casos, "M2 no está en UTF-8 (Latin-1)",
                d -> d.crudoM = String.join("\n", d.m).getBytes(StandardCharsets.ISO_8859_1),
                Estado.FALLA, Verificacion.M2);
        caso(casos, "M2 texto dañado (mojibake)",
                d -> poner(d.m, 1, 5, "Alcald\u00c3\u00ada"), Estado.FALLA, Verificacion.M2);
        caso(casos, "M2 carácter de reemplazo",
                d -> poner(d.m, 1, 5, "a\ufffdb"), Estado.FALLA, Verificacion.M2);
        caso(casos, "M2 BOM al inicio",
                d -> d.crudoM = ("\uFEFF" + String.join("\n", d.m) + "\n").getBytes(StandardCharsets.UTF_8),
                Estado.AVISO, Verificacion.M2);
        caso(casos, "M3 cabecera distinta",
                d -> d.m.set(0, d.m.get(0).replace("latitud", "lat")), Estado.FALLA, Verificacion.M3);
        caso(casos, "M3 separador punto y coma",
                d -> d.m.set(0, d.m.get(0).replace(",", ";")), Estado.FALLA, Verificacion.M3);
        caso(casos, "M4 fila con columnas de menos",
                d -> d.m.set(2, d.m.get(2).substring(0, d.m.get(2).lastIndexOf(','))),
                Estado.FALLA, Verificacion.M4);
        caso(casos, "M4 fila con columnas de más",
                d -> d.m.set(2, d.m.get(2) + ",extra"), Estado.FALLA, Verificacion.M4);
        caso(casos, "M4 comillas sin cerrar", d -> poner(d.m, 2, 5, "\"abc"),
                Estado.FALLA, Verificacion.M4);
        caso(casos, "M4 línea vacía", d -> d.m.add(3, ""), Estado.AVISO, Verificacion.M4);
        caso(casos, "M5 departamento vacío", d -> poner(d.m, 1, 1, ""), Estado.FALLA, Verificacion.M5);
        caso(casos, "M5 latitud vacía", d -> poner(d.m, 1, 3, ""), Estado.FALLA, Verificacion.M5);
        caso(casos, "M6 espacio al final del nombre",
                d -> poner(d.m, 1, 0, nombre1 + " "), Estado.FALLA, Verificacion.M6);
        caso(casos, "M6 espacio al inicio del nombre",
                d -> poner(d.m, 1, 0, " " + nombre1), Estado.FALLA, Verificacion.M6);
        caso(casos, "M6 espacios dobles",
                d -> poner(d.m, 1, 0, nombre1 + "  x"), Estado.FALLA, Verificacion.M6);
        caso(casos, "M6 espacio de no separación",
                d -> poner(d.m, 1, 0, nombre1 + "\u00a0x"), Estado.FALLA, Verificacion.M6);
        caso(casos, "M6 tilde descompuesta",
                d -> poner(d.m, filaTilde, 0, Normalizer.normalize(conTilde, Normalizer.Form.NFD)),
                Estado.FALLA, Verificacion.M6);
        caso(casos, "M7 municipio duplicado", d -> d.m.add(d.m.get(1)), Estado.FALLA, Verificacion.M7);
        caso(casos, "M7 mismo municipio en mayúsculas", d -> {
            d.m.add(d.m.get(1));
            poner(d.m, d.m.size() - 1, 0, nombre1.toUpperCase());
        }, Estado.FALLA, Verificacion.M7);
        caso(casos, "M8 latitud no numérica", d -> poner(d.m, 1, 3, "abc"),
                Estado.FALLA, Verificacion.M8);
        caso(casos, "M8 longitud con texto",
                d -> poner(d.m, 1, 4, celda(d.m, 1, 4) + "km"), Estado.FALLA, Verificacion.M8);
        caso(casos, "M9 coma decimal",
                d -> poner(d.m, 1, 3, "\"" + celda(d.m, 1, 3).replace('.', ',') + "\""),
                Estado.FALLA, Verificacion.M9);
        caso(casos, "M9 cantidad de decimales distinta",
                d -> poner(d.m, 1, 3,
                        celda(d.m, 1, 3).substring(0, celda(d.m, 1, 3).indexOf('.') + 3)),
                Estado.AVISO, Verificacion.M9);
        caso(casos, "M10 latitud fuera de Colombia", d -> poner(d.m, 1, 3, "45.0000"),
                Estado.FALLA, Verificacion.M10);
        caso(casos, "M10 longitud positiva", d -> poner(d.m, 1, 4, "75.0000"),
                Estado.FALLA, Verificacion.M10);
        caso(casos, "M10 latitud y longitud invertidas",
                d -> intercambiar(d.m, 1, 3, 4), Estado.FALLA, Verificacion.M10);
        caso(casos, "M11 coordenadas repetidas", d -> {
            poner(d.m, 2, 3, celda(d.m, 1, 3));
            poner(d.m, 2, 4, celda(d.m, 1, 4));
        }, Estado.AVISO, Verificacion.M11);
        caso(casos, "M12 falta un municipio", d -> d.m.remove(1), Estado.FALLA, Verificacion.M12);
        caso(casos, "M12 municipio de más",
                d -> d.m.add("Bogota,Cundinamarca,Centro y Nororiente,4.7110,-74.0721,x"),
                Estado.FALLA, Verificacion.M12);
        caso(casos, "M12 nombre distinto del README",
                d -> poner(d.m, filaTilde, 0, sinTildes(conTilde)), Estado.FALLA, Verificacion.M12);
        caso(casos, "M13 departamento distinto del README",
                d -> poner(d.m, 1, 1, "Amazonas"), Estado.AVISO, Verificacion.M13);
        caso(casos, "M13 zona distinta del README",
                d -> poner(d.m, 1, 2, "Zona inventada"), Estado.AVISO, Verificacion.M13);

        // ----- conexiones.csv
        caso(casos, "C1 archivo inexistente", d -> d.sinC = true, Estado.FALLA, Verificacion.C1);
        caso(casos, "C1 archivo vacío", d -> d.crudoC = new byte[0], Estado.FALLA, Verificacion.C1);
        caso(casos, "C2 no está en UTF-8 (Latin-1)",
                d -> d.crudoC = String.join("\n", d.c).getBytes(StandardCharsets.ISO_8859_1),
                Estado.FALLA, Verificacion.C2);
        caso(casos, "C3 cabecera distinta",
                d -> d.c.set(0, d.c.get(0).replace("km", "distancia")), Estado.FALLA, Verificacion.C3);
        caso(casos, "C4 fila con columnas de menos",
                d -> d.c.set(1, d.c.get(1).substring(0, d.c.get(1).lastIndexOf(','))),
                Estado.FALLA, Verificacion.C4);
        caso(casos, "C4 fila con columnas de más",
                d -> d.c.set(1, d.c.get(1) + ",extra"), Estado.FALLA, Verificacion.C4);
        caso(casos, "C5 km vacío", d -> poner(d.c, 1, 2, ""), Estado.FALLA, Verificacion.C5);
        caso(casos, "C5 municipio vacío", d -> poner(d.c, 1, 0, ""), Estado.FALLA, Verificacion.C5);
        caso(casos, "C6 espacio al final del nombre",
                d -> poner(d.c, 1, 0, conA + " "), Estado.FALLA, Verificacion.C6);
        caso(casos, "C7 nombre en mayúsculas",
                d -> poner(d.c, 1, 0, conA.toUpperCase()), Estado.FALLA, Verificacion.C7);
        caso(casos, "C7 nombre sin tilde", datos -> sinTildeEnConexiones(datos.c),
                Estado.FALLA, Verificacion.C7);
        caso(casos, "C7 municipio inexistente", d -> poner(d.c, 1, 1, "Bogota"),
                Estado.FALLA, Verificacion.C7);
        caso(casos, "C8 km no numérico", d -> poner(d.c, 1, 2, "abc"),
                Estado.FALLA, Verificacion.C8);
        caso(casos, "C8 km con unidad", d -> poner(d.c, 1, 2, km1 + "km"),
                Estado.FALLA, Verificacion.C8);
        caso(casos, "C9 km cero", d -> poner(d.c, 1, 2, "0.0"), Estado.FALLA, Verificacion.C9);
        caso(casos, "C9 km negativo", d -> poner(d.c, 1, 2, "-5.0"),
                Estado.FALLA, Verificacion.C9);
        caso(casos, "C10 coma decimal",
                d -> poner(d.c, 1, 2, "\"" + km1.replace('.', ',') + "\""),
                Estado.FALLA, Verificacion.C10);
        caso(casos, "C10 cantidad de decimales distinta", d -> poner(d.c, 1, 2, "106"),
                Estado.AVISO, Verificacion.C10);
        caso(casos, "C11 conexión consigo mismo",
                d -> d.c.add(nombre1 + "," + nombre1 + ",10.0,x"), Estado.FALLA, Verificacion.C11);
        caso(casos, "C12 conexión repetida", d -> d.c.add(d.c.get(1)),
                Estado.FALLA, Verificacion.C12);
        caso(casos, "C12 conexión invertida", d -> d.c.add(conB + "," + conA + "," + km1 + ",x"),
                Estado.FALLA, Verificacion.C12);
        caso(casos, "C13 distancia no simétrica", d -> d.c.add(conB + "," + conA + ",999.9,x"),
                Estado.FALLA, Verificacion.C13, Verificacion.C12);

        // ----- grafo
        caso(casos, "G1 y G2 municipio aislado", d -> d.c.removeIf(l ->
                !l.equals(d.c.get(0))
                        && (l.split(",")[0].equals(nombre1) || l.split(",")[1].equals(nombre1))),
                Estado.FALLA, Verificacion.G1, Verificacion.G2);
        caso(casos, "G2 grafo con dos componentes", ValidadorDatosTest::dosComponentes,
                Estado.FALLA, Verificacion.G2);
        caso(casos, "G3 km menor que la línea recta", d -> poner(d.c, 1, 2, "0.5"),
                Estado.FALLA, Verificacion.G3);

        // ----- formatos válidos que no deben marcar problemas
        Map<Verificacion, Estado> todoOk = new EnumMap<>(Verificacion.class);
        for (Verificacion v : Verificacion.values()) {
            todoOk.put(v, Estado.OK);
        }
        casos.add(new Caso("Archivos con saltos de línea de Windows (CRLF)", d -> {
            d.crudoM = (String.join("\r\n", d.m) + "\r\n").getBytes(StandardCharsets.UTF_8);
            d.crudoC = (String.join("\r\n", d.c) + "\r\n").getBytes(StandardCharsets.UTF_8);
        }, todoOk));
        casos.add(new Caso("Archivos sin salto de línea final", d -> {
            d.crudoM = String.join("\n", d.m).getBytes(StandardCharsets.UTF_8);
            d.crudoC = String.join("\n", d.c).getBytes(StandardCharsets.UTF_8);
        }, todoOk));

        // ----- lo que no se puede verificar se marca como omitido
        Map<Verificacion, Estado> sinMunicipios = new EnumMap<>(Verificacion.class);
        sinMunicipios.put(Verificacion.M1, Estado.FALLA);
        for (Verificacion v : List.of(Verificacion.M2, Verificacion.M5, Verificacion.M12,
                Verificacion.C7, Verificacion.G1, Verificacion.G2, Verificacion.G3)) {
            sinMunicipios.put(v, Estado.OMITIDA);
        }
        casos.add(new Caso("Sin municipios.csv se omiten las verificaciones que dependen de él",
                d -> d.sinM = true, sinMunicipios));

        return casos.stream().map(caso -> Arguments.of(caso));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("casos")
    void detectaCadaTipoDeError(Caso caso, @TempDir Path carpeta) throws IOException {
        Datos d = new Datos();
        d.m = new ArrayList<>(municipiosOriginales);
        d.c = new ArrayList<>(conexionesOriginales);
        caso.dano().accept(d);

        Path dir = carpeta.resolve(String.format("%02d", Math.abs(caso.nombre().hashCode() % 100)));
        Files.createDirectories(dir);
        if (!d.sinM) {
            Files.write(dir.resolve("municipios.csv"), d.bytesM());
        }
        if (!d.sinC) {
            Files.write(dir.resolve("conexiones.csv"), d.bytesC());
        }

        ValidadorDatos v = new ValidadorDatos(dir.resolve("municipios.csv").toString(),
                dir.resolve("conexiones.csv").toString());
        v.validar();

        StringBuilder diferencias = new StringBuilder();
        for (Map.Entry<Verificacion, Estado> e : caso.esperado().entrySet()) {
            if (v.estado(e.getKey()) != e.getValue()) {
                diferencias.append(' ').append(e.getKey()).append(" esperado ").append(e.getValue())
                        .append(" pero fue ").append(v.estado(e.getKey())).append(';');
            }
        }
        assertEquals(0, diferencias.length(),
                () -> caso.nombre() + " -> se esperaba " + resumen(caso.esperado()) + diferencias);
    }

    // ------------------------------------------------------------------ utilidades

    private static void caso(List<Caso> destino, String nombre, Consumer<Datos> dano, Estado estado,
                             Verificacion... verificaciones) {
        Map<Verificacion, Estado> esperado = new EnumMap<>(Verificacion.class);
        for (Verificacion v : verificaciones) {
            esperado.put(v, estado);
        }
        destino.add(new Caso(nombre, dano, esperado));
    }

    private static String resumen(Map<Verificacion, Estado> esperado) {
        StringBuilder sb = new StringBuilder();
        esperado.forEach((v, e) -> sb.append(sb.length() > 0 ? ", " : "").append(v).append(' ').append(e));
        return sb.toString();
    }

    private static String celda(List<String> lineas, int fila, int columna) {
        return lineas.get(fila).split(",", -1)[columna];
    }

    private static void poner(List<String> lineas, int fila, int columna, String valor) {
        String[] partes = lineas.get(fila).split(",", -1);
        partes[columna] = valor;
        lineas.set(fila, String.join(",", partes));
    }

    private static void intercambiar(List<String> lineas, int fila, int col1, int col2) {
        String a = celda(lineas, fila, col1);
        poner(lineas, fila, col1, celda(lineas, fila, col2));
        poner(lineas, fila, col2, a);
    }

    private static String sinTildes(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
    }

    /** Primera fila de municipios cuyo nombre lleva tilde. */
    private static int filaConTilde(List<String> municipios) {
        for (int i = 1; i < municipios.size(); i++) {
            String nombre = celda(municipios, i, 0);
            if (!nombre.equals(sinTildes(nombre))) {
                return i;
            }
        }
        throw new IllegalStateException("Ningún municipio lleva tilde; no se puede probar ese caso.");
    }

    private static void sinTildeEnConexiones(List<String> conexiones) {
        for (int i = 1; i < conexiones.size(); i++) {
            for (int col = 0; col < 2; col++) {
                String nombre = celda(conexiones, i, col);
                if (!nombre.equals(sinTildes(nombre))) {
                    poner(conexiones, i, col, sinTildes(nombre));
                    return;
                }
            }
        }
        throw new IllegalStateException("Ninguna conexión usa un municipio con tilde.");
    }

    /** Reemplaza las conexiones por dos cadenas de municipios sin ninguna unión entre ellas. */
    private static void dosComponentes(Datos d) {
        List<String> nombres = new ArrayList<>();
        for (int i = 1; i < d.m.size(); i++) {
            nombres.add(celda(d.m, i, 0));
        }
        d.c = new ArrayList<>(List.of(d.c.get(0)));
        int mitad = nombres.size() / 2;
        for (int i = 0; i + 1 < nombres.size(); i++) {
            if (i != mitad - 1) {
                d.c.add(nombres.get(i) + "," + nombres.get(i + 1) + ",100000.0,x");
            }
        }
    }

    private static int ejecutarMain(Path carpeta) throws IOException, InterruptedException {
        return ejecutarMainCon(carpeta.resolve("municipios.csv").toString(),
                carpeta.resolve("conexiones.csv").toString());
    }

    private static int ejecutarMainCon(String... argumentos) throws IOException, InterruptedException {
        List<String> comando = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Dstdout.encoding=UTF-8",
                "-cp", System.getProperty("java.class.path")));
        comando.add(ValidadorDatos.class.getName());
        comando.addAll(List.of(argumentos));

        Process p = new ProcessBuilder(comando).redirectErrorStream(true).start();
        p.getInputStream().readAllBytes();
        return p.waitFor();
    }

    private static void borrar(Path carpeta) throws IOException {
        try (Stream<Path> rutas = Files.walk(carpeta)) {
            rutas.sorted(Comparator.reverseOrder()).forEach(r -> r.toFile().delete());
        }
    }
}