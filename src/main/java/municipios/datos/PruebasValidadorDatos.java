package municipios.datos;

import static municipios.datos.ValidadorDatos.Verificacion.C1;
import static municipios.datos.ValidadorDatos.Verificacion.C10;
import static municipios.datos.ValidadorDatos.Verificacion.C11;
import static municipios.datos.ValidadorDatos.Verificacion.C12;
import static municipios.datos.ValidadorDatos.Verificacion.C13;
import static municipios.datos.ValidadorDatos.Verificacion.C2;
import static municipios.datos.ValidadorDatos.Verificacion.C3;
import static municipios.datos.ValidadorDatos.Verificacion.C4;
import static municipios.datos.ValidadorDatos.Verificacion.C5;
import static municipios.datos.ValidadorDatos.Verificacion.C6;
import static municipios.datos.ValidadorDatos.Verificacion.C7;
import static municipios.datos.ValidadorDatos.Verificacion.C8;
import static municipios.datos.ValidadorDatos.Verificacion.C9;
import static municipios.datos.ValidadorDatos.Verificacion.G1;
import static municipios.datos.ValidadorDatos.Verificacion.G2;
import static municipios.datos.ValidadorDatos.Verificacion.G3;
import static municipios.datos.ValidadorDatos.Verificacion.M1;
import static municipios.datos.ValidadorDatos.Verificacion.M10;
import static municipios.datos.ValidadorDatos.Verificacion.M11;
import static municipios.datos.ValidadorDatos.Verificacion.M12;
import static municipios.datos.ValidadorDatos.Verificacion.M13;
import static municipios.datos.ValidadorDatos.Verificacion.M2;
import static municipios.datos.ValidadorDatos.Verificacion.M3;
import static municipios.datos.ValidadorDatos.Verificacion.M4;
import static municipios.datos.ValidadorDatos.Verificacion.M5;
import static municipios.datos.ValidadorDatos.Verificacion.M6;
import static municipios.datos.ValidadorDatos.Verificacion.M7;
import static municipios.datos.ValidadorDatos.Verificacion.M8;
import static municipios.datos.ValidadorDatos.Verificacion.M9;

import municipios.datos.ValidadorDatos.Estado;
import municipios.datos.ValidadorDatos.Verificacion;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Pruebas de {@link ValidadorDatos}: parte de los CSV reales de {@code data/}, los daña a
 * propósito (un tipo de error por caso) y comprueba que la verificación correspondiente lo detecte.
 *
 * <pre>
 * javac -encoding UTF-8 -d out $(find src/main/java test -name "*.java")
 * java -cp out municipios.datos.PruebasValidadorDatos
 * java -cp out municipios.datos.PruebasValidadorDatos --conservar   (deja los CSV dañados en target/datos-danados/)
 * </pre>
 * Se ejecuta desde la raíz del repositorio; termina con código 1 si alguna prueba falla.
 */
public class PruebasValidadorDatos {

    private static final Path MUNICIPIOS = Path.of(ValidadorDatos.RUTA_MUNICIPIOS);
    private static final Path CONEXIONES = Path.of(ValidadorDatos.RUTA_CONEXIONES);

    /** Copia de los CSV que cada caso daña; las filas son líneas de texto (la 0 es la cabecera). */
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

    private static final List<Caso> CASOS = new ArrayList<>();
    private static int fallos;

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        boolean conservar = args.length > 0 && args[0].equals("--conservar");
        Path base = conservar ? Path.of("target", "datos-danados") : Files.createTempDirectory("validador-");
        if (conservar && Files.exists(base)) {
            borrar(base);
        }
        Files.createDirectories(base);

        List<String> m = Files.readAllLines(MUNICIPIOS, StandardCharsets.UTF_8);
        List<String> c = Files.readAllLines(CONEXIONES, StandardCharsets.UTF_8);
        if (String.join("", m).contains("\"") || String.join("", c).contains("\"")) {
            throw new IllegalStateException("Estas pruebas suponen CSV sin comillas.");
        }
        definirCasos(m, c);

        out.println("== Datos reales (criterio: 0 errores y todas las verificaciones OK)");
        ValidadorDatos reales = new ValidadorDatos();
        reales.validar();
        verificar(out, "los CSV de data/ pasan las 29 verificaciones",
                !reales.hayErrores() && reales.getResultados().values().stream().allMatch(e -> e == Estado.OK)
                        && reales.generarReporte().contains("RESULTADO: OK - pasaron las 29 verificaciones"));

        out.println("\n== CSV dañados a propósito (un tipo de error por caso)");
        int n = 0;
        Path primerDanado = null;
        for (Caso caso : CASOS) {
            Datos d = new Datos();
            d.m = new ArrayList<>(m);
            d.c = new ArrayList<>(c);
            caso.dano().accept(d);

            Path dir = base.resolve(String.format("%02d-%s", ++n, caso.nombre().replaceAll("[^A-Za-z0-9]+", "-")));
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

            StringBuilder faltas = new StringBuilder();
            for (Map.Entry<Verificacion, Estado> e : caso.esperado().entrySet()) {
                if (v.estado(e.getKey()) != e.getValue()) {
                    faltas.append(' ').append(e.getKey()).append(" esperado ").append(e.getValue())
                          .append(" pero fue ").append(v.estado(e.getKey())).append(';');
                }
            }
            String esperado = caso.esperado().values().stream().allMatch(e -> e == Estado.OK)
                    ? "las " + Verificacion.values().length + " verificaciones OK" : resumen(caso.esperado());
            verificar(out, caso.nombre() + " -> " + esperado, faltas.length() == 0, faltas.toString());
            if (primerDanado == null && caso.nombre().startsWith("M5")) {
                primerDanado = dir;
            }
        }

        out.println("\n== Programa completo (código de salida)");
        Path buenos = base.resolve("buenos");
        Files.createDirectories(buenos);
        Files.copy(MUNICIPIOS, buenos.resolve("municipios.csv"));
        Files.copy(CONEXIONES, buenos.resolve("conexiones.csv"));
        verificar(out, "datos correctos -> código de salida 0", ejecutarMain(buenos) == 0);
        verificar(out, "datos dañados -> código de salida 1", primerDanado != null && ejecutarMain(primerDanado) == 1);

        if (!conservar) {
            borrar(base);
        } else {
            out.println("\nCSV dañados guardados en " + base.toAbsolutePath());
        }
        out.println("\n" + (CASOS.size() + 3 - fallos) + " de " + (CASOS.size() + 3) + " pruebas correctas");
        if (fallos > 0) {
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------ casos

    private static void definirCasos(List<String> m, List<String> c) {
        String nombre1 = celda(m, 1, 0);
        String conA = celda(c, 1, 0);
        String conB = celda(c, 1, 1);
        String km1 = celda(c, 1, 2);
        int filaTilde = filaConTilde(m);
        String conTilde = celda(m, filaTilde, 0);

        // ----- municipios.csv
        caso("M1 archivo inexistente", d -> d.sinM = true, Estado.FALLA, M1);
        caso("M1 archivo vacío", d -> d.crudoM = new byte[0], Estado.FALLA, M1);
        caso("M2 no está en UTF-8 (Latin-1)", d -> d.crudoM = String.join("\n", d.m).getBytes(StandardCharsets.ISO_8859_1), Estado.FALLA, M2);
        caso("M2 texto dañado (mojibake)", d -> poner(d.m, 1, 5, "Alcald\u00c3\u00ada"), Estado.FALLA, M2);
        caso("M2 carácter de reemplazo", d -> poner(d.m, 1, 5, "a\ufffdb"), Estado.FALLA, M2);
        caso("M2 BOM al inicio", d -> d.crudoM = ("\uFEFF" + String.join("\n", d.m) + "\n").getBytes(StandardCharsets.UTF_8), Estado.AVISO, M2);
        caso("M3 cabecera distinta", d -> d.m.set(0, d.m.get(0).replace("latitud", "lat")), Estado.FALLA, M3);
        caso("M3 separador punto y coma", d -> d.m.set(0, d.m.get(0).replace(",", ";")), Estado.FALLA, M3);
        caso("M4 fila con columnas de menos", d -> d.m.set(2, d.m.get(2).substring(0, d.m.get(2).lastIndexOf(','))), Estado.FALLA, M4);
        caso("M4 fila con columnas de más", d -> d.m.set(2, d.m.get(2) + ",extra"), Estado.FALLA, M4);
        caso("M4 comillas sin cerrar", d -> poner(d.m, 2, 5, "\"abc"), Estado.FALLA, M4);
        caso("M4 línea vacía", d -> d.m.add(3, ""), Estado.AVISO, M4);
        caso("M5 departamento vacío", d -> poner(d.m, 1, 1, ""), Estado.FALLA, M5);
        caso("M5 latitud vacía", d -> poner(d.m, 1, 3, ""), Estado.FALLA, M5);
        caso("M6 espacio al final del nombre", d -> poner(d.m, 1, 0, nombre1 + " "), Estado.FALLA, M6);
        caso("M6 espacio al inicio del nombre", d -> poner(d.m, 1, 0, " " + nombre1), Estado.FALLA, M6);
        caso("M6 espacios dobles", d -> poner(d.m, 1, 0, nombre1 + "  x"), Estado.FALLA, M6);
        caso("M6 espacio de no separación", d -> poner(d.m, 1, 0, nombre1 + "\u00a0x"), Estado.FALLA, M6);
        caso("M6 tilde descompuesta", d -> poner(d.m, filaTilde, 0, Normalizer.normalize(conTilde, Normalizer.Form.NFD)), Estado.FALLA, M6);
        caso("M7 municipio duplicado", d -> d.m.add(d.m.get(1)), Estado.FALLA, M7);
        caso("M7 mismo municipio en mayúsculas", d -> { d.m.add(d.m.get(1)); poner(d.m, d.m.size() - 1, 0, nombre1.toUpperCase()); }, Estado.FALLA, M7);
        caso("M8 latitud no numérica", d -> poner(d.m, 1, 3, "abc"), Estado.FALLA, M8);
        caso("M8 longitud con texto", d -> poner(d.m, 1, 4, celda(d.m, 1, 4) + "km"), Estado.FALLA, M8);
        caso("M9 coma decimal", d -> poner(d.m, 1, 3, "\"" + celda(d.m, 1, 3).replace('.', ',') + "\""), Estado.FALLA, M9);
        caso("M9 cantidad de decimales distinta", d -> poner(d.m, 1, 3, celda(d.m, 1, 3).substring(0, celda(d.m, 1, 3).indexOf('.') + 3)), Estado.AVISO, M9);
        caso("M10 latitud fuera de Colombia", d -> poner(d.m, 1, 3, "45.0000"), Estado.FALLA, M10);
        caso("M10 longitud positiva", d -> poner(d.m, 1, 4, "75.0000"), Estado.FALLA, M10);
        caso("M10 latitud y longitud invertidas", d -> intercambiar(d.m, 1, 3, 4), Estado.FALLA, M10);
        caso("M11 coordenadas repetidas", d -> { poner(d.m, 2, 3, celda(d.m, 1, 3)); poner(d.m, 2, 4, celda(d.m, 1, 4)); }, Estado.AVISO, M11);
        caso("M12 falta un municipio", d -> d.m.remove(1), Estado.FALLA, M12);
        caso("M12 municipio de más", d -> d.m.add("Bogota,Cundinamarca,Centro y Nororiente,4.7110,-74.0721,x"), Estado.FALLA, M12);
        caso("M12 nombre distinto del README", d -> poner(d.m, filaTilde, 0, sinTildes(conTilde)), Estado.FALLA, M12);
        caso("M13 departamento distinto del README", d -> poner(d.m, 1, 1, "Amazonas"), Estado.AVISO, M13);
        caso("M13 zona distinta del README", d -> poner(d.m, 1, 2, "Zona inventada"), Estado.AVISO, M13);

        // ----- conexiones.csv
        caso("C1 archivo inexistente", d -> d.sinC = true, Estado.FALLA, C1);
        caso("C1 archivo vacío", d -> d.crudoC = new byte[0], Estado.FALLA, C1);
        caso("C2 no está en UTF-8 (Latin-1)", d -> d.crudoC = String.join("\n", d.c).getBytes(StandardCharsets.ISO_8859_1), Estado.FALLA, C2);
        caso("C3 cabecera distinta", d -> d.c.set(0, d.c.get(0).replace("km", "distancia")), Estado.FALLA, C3);
        caso("C4 fila con columnas de menos", d -> d.c.set(1, d.c.get(1).substring(0, d.c.get(1).lastIndexOf(','))), Estado.FALLA, C4);
        caso("C4 fila con columnas de más", d -> d.c.set(1, d.c.get(1) + ",extra"), Estado.FALLA, C4);
        caso("C5 km vacío", d -> poner(d.c, 1, 2, ""), Estado.FALLA, C5);
        caso("C5 municipio vacío", d -> poner(d.c, 1, 0, ""), Estado.FALLA, C5);
        caso("C6 espacio al final del nombre", d -> poner(d.c, 1, 0, conA + " "), Estado.FALLA, C6);
        caso("C7 nombre en mayúsculas", d -> poner(d.c, 1, 0, conA.toUpperCase()), Estado.FALLA, C7);
        caso("C7 nombre sin tilde", d -> sinTildeEnConexiones(d.c), Estado.FALLA, C7);
        caso("C7 municipio inexistente", d -> poner(d.c, 1, 1, "Bogota"), Estado.FALLA, C7);
        caso("C8 km no numérico", d -> poner(d.c, 1, 2, "abc"), Estado.FALLA, C8);
        caso("C8 km con unidad", d -> poner(d.c, 1, 2, km1 + "km"), Estado.FALLA, C8);
        caso("C9 km cero", d -> poner(d.c, 1, 2, "0.0"), Estado.FALLA, C9);
        caso("C9 km negativo", d -> poner(d.c, 1, 2, "-5.0"), Estado.FALLA, C9);
        caso("C10 coma decimal", d -> poner(d.c, 1, 2, "\"" + km1.replace('.', ',') + "\""), Estado.FALLA, C10);
        caso("C10 cantidad de decimales distinta", d -> poner(d.c, 1, 2, "106"), Estado.AVISO, C10);
        caso("C11 conexión consigo mismo", d -> d.c.add(nombre1 + "," + nombre1 + ",10.0,x"), Estado.FALLA, C11);
        caso("C12 conexión repetida", d -> d.c.add(d.c.get(1)), Estado.FALLA, C12);
        caso("C12 conexión invertida", d -> d.c.add(conB + "," + conA + "," + km1 + ",x"), Estado.FALLA, C12);
        caso("C13 distancia no simétrica", d -> d.c.add(conB + "," + conA + ",999.9,x"), Estado.FALLA, C13, C12);

        // ----- grafo
        caso("G1 y G2 municipio aislado", d -> d.c.removeIf(l -> !l.equals(d.c.get(0)) && (l.split(",")[0].equals(nombre1) || l.split(",")[1].equals(nombre1))), Estado.FALLA, G1, G2);
        caso("G2 grafo con dos componentes", d -> dosComponentes(d), Estado.FALLA, G2);
        caso("G3 km menor que la línea recta", d -> poner(d.c, 1, 2, "0.5"), Estado.FALLA, G3);

        // ----- formatos válidos que no deben marcar problemas
        Map<Verificacion, Estado> todoOk = new EnumMap<>(Verificacion.class);
        for (Verificacion v : Verificacion.values()) {
            todoOk.put(v, Estado.OK);
        }
        CASOS.add(new Caso("Archivos con saltos de línea de Windows (CRLF)", d -> {
            d.crudoM = (String.join("\r\n", d.m) + "\r\n").getBytes(StandardCharsets.UTF_8);
            d.crudoC = (String.join("\r\n", d.c) + "\r\n").getBytes(StandardCharsets.UTF_8);
        }, todoOk));
        CASOS.add(new Caso("Archivos sin salto de línea final", d -> {
            d.crudoM = String.join("\n", d.m).getBytes(StandardCharsets.UTF_8);
            d.crudoC = String.join("\n", d.c).getBytes(StandardCharsets.UTF_8);
        }, todoOk));

        // ----- lo que no se puede verificar se marca como omitido
        Map<Verificacion, Estado> sinMunicipios = new EnumMap<>(Verificacion.class);
        sinMunicipios.put(M1, Estado.FALLA);
        for (Verificacion v : List.of(M2, M5, M12, C7, G1, G2, G3)) {
            sinMunicipios.put(v, Estado.OMITIDA);
        }
        CASOS.add(new Caso("Sin municipios.csv se omiten las verificaciones que dependen de él", d -> d.sinM = true, sinMunicipios));
    }

    private static void caso(String nombre, Consumer<Datos> dano, Estado estado, Verificacion... verificaciones) {
        Map<Verificacion, Estado> esperado = new EnumMap<>(Verificacion.class);
        for (Verificacion v : verificaciones) {
            esperado.put(v, estado);
        }
        CASOS.add(new Caso(nombre, dano, esperado));
    }

    // ------------------------------------------------------------------ utilidades

    private static void verificar(PrintStream out, String descripcion, boolean ok) {
        verificar(out, descripcion, ok, "");
    }

    private static void verificar(PrintStream out, String descripcion, boolean ok, String detalle) {
        out.println((ok ? "  [OK]    " : "  [FALLA] ") + descripcion + (ok ? "" : " ->" + detalle));
        if (!ok) {
            fallos++;
        }
    }

    private static String resumen(Map<Verificacion, Estado> esperado) {
        StringBuilder sb = new StringBuilder();
        esperado.forEach((v, e) -> sb.append(sb.length() > 0 ? ", " : "").append(v).append(' ').append(e));
        return sb.toString();
    }

    private static int ejecutarMain(Path dir) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"), "municipios.datos.ValidadorDatos",
                dir.resolve("municipios.csv").toString(), dir.resolve("conexiones.csv").toString())
                .redirectErrorStream(true).start();
        p.getInputStream().readAllBytes();
        return p.waitFor();
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

    private static void borrar(Path carpeta) throws IOException {
        try (Stream<Path> rutas = Files.walk(carpeta)) {
            rutas.sorted(Comparator.reverseOrder()).forEach(r -> r.toFile().delete());
        }
    }
}