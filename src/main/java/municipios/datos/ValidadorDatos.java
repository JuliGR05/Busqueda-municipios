package municipios.datos;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 
 * <p>Compilar y ejecutar, desde la raíz del repositorio:</p>
 * <pre>
 * mkdir -p out
 * javac -encoding UTF-8 -d out $(find src/main/java -name "*.java")
 * java -cp out municipios.datos.ValidadorDatos
 * java -cp out municipios.datos.ValidadorDatos ruta/municipios.csv ruta/conexiones.csv
 * </pre>
 * <p>Código de salida: 0 si no hay errores, 1 si hay alguno (las advertencias no cuentan).</p>
 
 
 */
public class ValidadorDatos {

    /** Gravedad de un problema. */
    public enum Severidad { ERROR, ADVERTENCIA, INFO }

    /** Resultado de una verificación. */
    public enum Estado { OK, AVISO, FALLA, OMITIDA }

    /** Parte de los datos a la que pertenece una verificación. */
    public enum Grupo { MUNICIPIOS, CONEXIONES, GRAFO }

    /** Cada una de las verificaciones que hace el validador. */
    public enum Verificacion {
        M1("El archivo existe, se puede leer y no está vacío"),
        M2("Codificación UTF-8 válida, sin caracteres dañados"),
        M3("Cabecera correcta (columnas del README, separador coma)"),
        M4("Filas bien formadas: columnas completas, comillas cerradas, sin líneas vacías"),
        M5("Sin valores vacíos"),
        M6("Nombres, departamentos y zonas limpios (sin espacios sobrantes ni tildes descompuestas)"),
        M7("Sin municipios duplicados (ni escritos de dos formas)"),
        M8("Latitud y longitud son números"),
        M9("Decimales consistentes en todo el archivo (punto decimal, misma cantidad de decimales)"),
        M10("Latitud y longitud dentro del rango de Colombia"),
        M11("Ningún par de municipios con las mismas coordenadas"),
        M12("Están los 20 municipios exactos definidos en el README"),
        M13("Departamento y zona de cada municipio coinciden con el README"),
        C1("El archivo existe, se puede leer y no está vacío"),
        C2("Codificación UTF-8 válida, sin caracteres dañados"),
        C3("Cabecera correcta (columnas del README, separador coma)"),
        C4("Filas bien formadas: columnas completas, comillas cerradas, sin líneas vacías"),
        C5("Sin valores vacíos"),
        C6("Nombres limpios (sin espacios sobrantes ni tildes descompuestas)"),
        C7("Nombres escritos idénticos a los de municipios.csv (mayúsculas, tildes, espacios)"),
        C8("km es un número"),
        C9("km mayor que 0 (ni cero ni negativo)"),
        C10("Decimales consistentes en todo el archivo (punto decimal, misma cantidad de decimales)"),
        C11("Ninguna conexión de un municipio consigo mismo"),
        C12("Cada conexión aparece una sola vez (sin repetidas ni invertidas)"),
        C13("Distancias simétricas: A->B = B->A (sin valores contradictorios)"),
        G1("Sin municipios aislados (todos tienen al menos una conexión)"),
        G2("Grafo conexo: existe camino entre cualquier par de municipios"),
        G3("km por carretera >= distancia en línea recta (Haversine)");

        private final String descripcion;

        Verificacion(String descripcion) {
            this.descripcion = descripcion;
        }

        /** @return texto que describe lo que se verifica */
        public String descripcion() {
            return descripcion;
        }

        /** @return grupo al que pertenece (según la letra del identificador) */
        public Grupo grupo() {
            return switch (name().charAt(0)) {
                case 'M' -> Grupo.MUNICIPIOS;
                case 'C' -> Grupo.CONEXIONES;
                default -> Grupo.GRAFO;
            };
        }
    }

    /**
     * Un hallazgo de la validación.
     *
     * @param severidad   gravedad
     * @param verificacion verificación a la que pertenece ({@code null} si es solo informativo)
     * @param archivo     archivo afectado ({@code grafo} si involucra a ambos)
     * @param linea       línea del archivo (la cabecera es la 1); 0 si no aplica
     * @param mensaje     descripción del problema
     */
    public record Problema(Severidad severidad, Verificacion verificacion, String archivo, int linea, String mensaje) {
        @Override
        public String toString() {
            return "[" + (linea > 0 ? archivo + ":" + linea : archivo) + "] " + mensaje;
        }
    }

    // ------------------------------------------------------------------ constantes

    /** Ruta por defecto de municipios.csv (relativa a la raíz del repositorio). */
    public static final String RUTA_MUNICIPIOS = "data/municipios.csv";
    /** Ruta por defecto de conexiones.csv (relativa a la raíz del repositorio). */
    public static final String RUTA_CONEXIONES = "data/conexiones.csv";

    private static final List<String> CABECERA_MUNICIPIOS =
            List.of("nombre", "departamento", "zona", "latitud", "longitud", "fuente");
    private static final List<String> CABECERA_CONEXIONES =
            List.of("municipio1", "municipio2", "km", "fuente");

    private static final int TOTAL_MUNICIPIOS = 20;
    private static final double RADIO_TIERRA_KM = 6371.0;
    private static final double EPSILON = 1e-9;

    // Rectángulo aproximado de Colombia (grados)
    private static final double LAT_MIN = -4.3;
    private static final double LAT_MAX = 13.5;
    private static final double LON_MIN = -82.0;
    private static final double LON_MAX = -66.8;

    private static final String ARCHIVO_GRAFO = "grafo";

    private static final Pattern COORDENADA = Pattern.compile("-?\\d+([.,]\\d+)?");
    private static final Pattern KILOMETROS = Pattern.compile("\\d+([.,]\\d+)?");
    private static final Pattern NEGATIVO = Pattern.compile("-\\d+([.,]\\d+)?");
    private static final Pattern NOMBRE_VALIDO = Pattern.compile("\\p{L}+([ \\-]\\p{L}+)*");
    private static final Pattern MOJIBAKE = Pattern.compile("[\\u00C2\\u00C3][\\u0080-\\u00BF]");

    private record Esperado(String departamento, String zona) {}

    /** Los 20 municipios del README (tildes en escapes Unicode para no depender de la codificación al compilar). */
    private static final Map<String, Esperado> ESPERADOS = new LinkedHashMap<>();

    static {
        String caribe = "Caribe";
        String occidente = "Occidente y Eje Cafetero";
        String centro = "Centro y Nororiente";
        String sur = "Sur y Llanos";
        ESPERADOS.put("Barranquilla", new Esperado("Atl\u00e1ntico", caribe));
        ESPERADOS.put("Cartagena", new Esperado("Bol\u00edvar", caribe));
        ESPERADOS.put("Santa Marta", new Esperado("Magdalena", caribe));
        ESPERADOS.put("Valledupar", new Esperado("Cesar", caribe));
        ESPERADOS.put("Monter\u00eda", new Esperado("C\u00f3rdoba", caribe));
        ESPERADOS.put("Medell\u00edn", new Esperado("Antioquia", occidente));
        ESPERADOS.put("Manizales", new Esperado("Caldas", occidente));
        ESPERADOS.put("Pereira", new Esperado("Risaralda", occidente));
        ESPERADOS.put("Armenia", new Esperado("Quind\u00edo", occidente));
        ESPERADOS.put("Cali", new Esperado("Valle del Cauca", occidente));
        ESPERADOS.put("Soacha", new Esperado("Cundinamarca", centro));
        ESPERADOS.put("Tunja", new Esperado("Boyac\u00e1", centro));
        ESPERADOS.put("Bucaramanga", new Esperado("Santander", centro));
        ESPERADOS.put("C\u00facuta", new Esperado("Norte de Santander", centro));
        ESPERADOS.put("Barrancabermeja", new Esperado("Santander", centro));
        ESPERADOS.put("Villavicencio", new Esperado("Meta", sur));
        ESPERADOS.put("Ibagu\u00e9", new Esperado("Tolima", sur));
        ESPERADOS.put("Neiva", new Esperado("Huila", sur));
        ESPERADOS.put("Popay\u00e1n", new Esperado("Cauca", sur));
        ESPERADOS.put("Pasto", new Esperado("Nari\u00f1o", sur));
    }

    // ------------------------------------------------------------------ estructuras internas

    private record Fila(int linea, List<String> celdas) {}

    private record FilaMunicipio(int linea, String nombre, String departamento, String zona,
                                 Double latitud, Double longitud) {}

    private record FilaConexion(int linea, String municipio1, String municipio2, Double km) {}

    private record Numero(int linea, String columna, String texto) {}

    /** Par no ordenado: A-B es lo mismo que B-A. */
    private record Par(String a, String b) {
        static Par de(String x, String y) {
            return x.compareTo(y) <= 0 ? new Par(x, y) : new Par(y, x);
        }
    }

    // ------------------------------------------------------------------ estado

    private final Path rutaMunicipios;
    private final Path rutaConexiones;
    private final String archivoMunicipios;
    private final String archivoConexiones;

    private final List<Problema> problemas = new ArrayList<>();
    private final Set<Verificacion> ejecutadas = EnumSet.noneOf(Verificacion.class);
    private final Map<Verificacion, String> detalles = new EnumMap<>(Verificacion.class);
    private final Map<String, FilaMunicipio> municipios = new LinkedHashMap<>();
    private final Map<String, String> indiceNormalizado = new HashMap<>();
    private final Map<String, Integer> filasRechazadas = new HashMap<>();
    private final List<Numero> numeros = new ArrayList<>();
    private boolean enMunicipios;
    private boolean lecturaMunicipios;
    private boolean lecturaConexiones;
    private boolean validado;

    // ------------------------------------------------------------------ API pública

    /** Crea un validador para {@code data/municipios.csv} y {@code data/conexiones.csv}. */
    public ValidadorDatos() {
        this(RUTA_MUNICIPIOS, RUTA_CONEXIONES);
    }

    /**
     * Crea un validador para los archivos indicados.
     *
     * @param rutaMunicipios ruta de municipios.csv
     * @param rutaConexiones ruta de conexiones.csv
     */
    public ValidadorDatos(String rutaMunicipios, String rutaConexiones) {
        this.rutaMunicipios = Path.of(rutaMunicipios);
        this.rutaConexiones = Path.of(rutaConexiones);
        this.archivoMunicipios = nombreDeArchivo(this.rutaMunicipios);
        this.archivoConexiones = nombreDeArchivo(this.rutaConexiones);
    }

    /**
     * Ejecuta todas las verificaciones (vuelve a leer los archivos en cada llamada).
     *
     * @return los problemas encontrados, ordenados por archivo y línea
     */
    public List<Problema> validar() {
        problemas.clear();
        ejecutadas.clear();
        detalles.clear();
        municipios.clear();
        indiceNormalizado.clear();
        filasRechazadas.clear();
        numeros.clear();

        enMunicipios = true;
        List<String> lineasMunicipios = leerLineas(rutaMunicipios, archivoMunicipios);
        lecturaMunicipios = lineasMunicipios != null;
        if (lecturaMunicipios) {
            validarMunicipios(leerTabla(archivoMunicipios, lineasMunicipios, CABECERA_MUNICIPIOS, filasRechazadas));
        }

        numeros.clear();
        enMunicipios = false;
        List<String> lineasConexiones = leerLineas(rutaConexiones, archivoConexiones);
        lecturaConexiones = lineasConexiones != null;
        if (lecturaConexiones) {
            validarConexiones(leerTabla(archivoConexiones, lineasConexiones, CABECERA_CONEXIONES, null));
        }

        problemas.sort(Comparator.comparingInt((Problema p) -> ordenArchivo(p.archivo()))
                .thenComparingInt(Problema::linea));
        validado = true;
        return getProblemas();
    }

    /** @return los problemas de la última validación (lista no modificable) */
    public List<Problema> getProblemas() {
        return Collections.unmodifiableList(new ArrayList<>(problemas));
    }

    /**
     * @param severidad gravedad a contar
     * @return cuántos problemas de esa gravedad hay
     */
    public long contar(Severidad severidad) {
        return problemas.stream().filter(p -> p.severidad() == severidad).count();
    }

    /** @return {@code true} si hay al menos un error (las advertencias no cuentan) */
    public boolean hayErrores() {
        return contar(Severidad.ERROR) > 0;
    }

    /**
     * @param verificacion verificación consultada
     * @return su resultado en la última validación
     */
    public Estado estado(Verificacion verificacion) {
        if (!ejecutadas.contains(verificacion)) {
            return Estado.OMITIDA;
        }
        boolean aviso = false;
        for (Problema p : problemas) {
            if (p.verificacion() == verificacion) {
                if (p.severidad() == Severidad.ERROR) {
                    return Estado.FALLA;
                }
                aviso |= p.severidad() == Severidad.ADVERTENCIA;
            }
        }
        return aviso ? Estado.AVISO : Estado.OK;
    }

    /** @return el resultado de cada verificación, en orden */
    public Map<Verificacion, Estado> getResultados() {
        Map<Verificacion, Estado> resultados = new LinkedHashMap<>();
        for (Verificacion v : Verificacion.values()) {
            resultados.put(v, estado(v));
        }
        return resultados;
    }

    /**
     * Arma el reporte: lista de verificaciones con su estado y, debajo, los problemas
     * agrupados por categoría. Si aún no se validó, valida primero.
     *
     * @return el reporte listo para imprimir
     */
    public String generarReporte() {
        if (!validado) {
            validar();
        }
        String barra = "=".repeat(70);
        StringBuilder sb = new StringBuilder();
        sb.append(barra).append("\n VALIDACIÓN DE DATOS - Búsqueda entre municipios\n").append(barra).append('\n');
        sb.append("Archivos: ").append(rutaMunicipios).append("  y  ").append(rutaConexiones).append("\n\n");
        sb.append("VERIFICACIONES REALIZADAS\n");

        Map<Estado, Integer> total = new EnumMap<>(Estado.class);
        Grupo grupo = null;
        for (Verificacion v : Verificacion.values()) {
            if (v.grupo() != grupo) {
                grupo = v.grupo();
                sb.append('\n').append(' ').append(tituloGrupo(grupo)).append('\n');
            }
            Estado estado = estado(v);
            total.merge(estado, 1, Integer::sum);
            sb.append(String.format("  %-10s %-4s %s", "[" + estado + "]", v.name(), v.descripcion()));
            if (estado == Estado.OK && detalles.containsKey(v)) {
                sb.append(" - ").append(detalles.get(v));
            }
            sb.append('\n');
            String nota = notaDe(v, estado);
            if (nota != null) {
                sb.append("                  -> ").append(nota).append('\n');
            }
        }

        int fallas = total.getOrDefault(Estado.FALLA, 0);
        int avisos = total.getOrDefault(Estado.AVISO, 0);
        int omitidas = total.getOrDefault(Estado.OMITIDA, 0);
        sb.append("\nRESUMEN: ").append(Verificacion.values().length).append(" verificaciones -> ")
          .append(total.getOrDefault(Estado.OK, 0)).append(" OK, ").append(avisos).append(" con avisos, ")
          .append(fallas).append(" con fallas, ").append(omitidas).append(" omitidas\n");

        agregarProblemasPorCategoria(sb);
        agregarInformacion(sb);

        sb.append('\n').append(barra).append('\n');
        if (fallas > 0) {
            sb.append("RESULTADO: FALLA - ").append(fallas).append(" verificación(es) con errores");
        } else if (omitidas > 0) {
            sb.append("RESULTADO: INCOMPLETO - ").append(omitidas).append(" verificación(es) no se pudieron ejecutar");
        } else if (avisos > 0) {
            sb.append("RESULTADO: OK con avisos - ").append(avisos).append(" verificación(es) para revisar");
        } else {
            sb.append("RESULTADO: OK - pasaron las ").append(Verificacion.values().length).append(" verificaciones");
        }
        return sb.append('\n').append(barra).append('\n').toString();
    }

    /**
     * Punto de entrada. Sin argumentos valida {@code data/municipios.csv} y
     * {@code data/conexiones.csv}; con dos, usa las rutas dadas (municipios y luego conexiones).
     * Termina con código 1 si hay errores.
     *
     * @param args nada, o {@code [rutaMunicipios, rutaConexiones]}
     */
    public static void main(String[] args) {
        if (args.length != 0 && args.length != 2) {
            System.err.println("Uso: java -cp out municipios.datos.ValidadorDatos [<municipios.csv> <conexiones.csv>]");
            System.exit(2);
        }
        ValidadorDatos validador = args.length == 2 ? new ValidadorDatos(args[0], args[1]) : new ValidadorDatos();
        validador.validar();
        PrintStream salida = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        salida.print(validador.generarReporte());
        salida.flush();
        if (validador.hayErrores()) {
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------ lectura

    /** Lee el archivo como UTF-8 estricto; devuelve sus líneas o null si no se pudo. */
    private List<String> leerLineas(Path ruta, String archivo) {
        Verificacion existe = pv(Verificacion.M1, Verificacion.C1);
        Verificacion codificacion = pv(Verificacion.M2, Verificacion.C2);
        ejecutar(existe);

        if (!Files.isRegularFile(ruta)) {
            error(existe, archivo, 0, "El archivo no existe: " + ruta.toAbsolutePath()
                    + " (ejecutar desde la raíz del repositorio o indicar la ruta)");
            return null;
        }
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(ruta);
        } catch (IOException e) {
            error(existe, archivo, 0, "No se pudo leer el archivo: " + e.getMessage());
            return null;
        }
        if (new String(bytes, StandardCharsets.ISO_8859_1).isBlank()) {
            error(existe, archivo, 0, "El archivo está vacío.");
            return null;
        }

        ejecutar(codificacion);
        String texto;
        try {
            texto = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            error(codificacion, archivo, 0, "No está en UTF-8 válido (probablemente Windows-1252/Latin-1). "
                    + "Volver a exportarlo como UTF-8.");
            return null;
        }
        if (texto.startsWith("\uFEFF")) {
            advertencia(codificacion, archivo, 1, "El archivo empieza con BOM; exportar como UTF-8 sin BOM.");
            texto = texto.substring(1);
        }

        List<String> lineas = new ArrayList<>(Arrays.asList(texto.split("\r\n|\n|\r", -1)));
        if (lineas.get(lineas.size() - 1).isEmpty()) {
            lineas.remove(lineas.size() - 1);
        }
        detalle(existe, lineas.size() + " línea(s), incluida la cabecera");
        return lineas;
    }

    /** Revisa la cabecera, separa las líneas en celdas y descarta (con error) las filas mal formadas. */
    private List<Fila> leerTabla(String archivo, List<String> lineas, List<String> cabeceraEsperada,
                                 Map<String, Integer> rechazadas) {
        Verificacion cabeceraV = pv(Verificacion.M3, Verificacion.C3);
        Verificacion filasV = pv(Verificacion.M4, Verificacion.C4);
        ejecutar(cabeceraV);
        ejecutar(filasV);

        String primera = lineas.get(0);
        if (primera.contains(";") && !primera.contains(",")) {
            error(cabeceraV, archivo, 1, "La cabecera usa ';' como separador; los CSV del repositorio usan coma (README).");
            return List.of();
        }
        List<String> cabecera = parsearLinea(archivo, 1, primera);
        if (cabecera != null && !cabecera.equals(cabeceraEsperada)) {
            error(cabeceraV, archivo, 1, "Cabecera incorrecta. Se esperaba " + String.join(",", cabeceraEsperada)
                    + " y se encontró " + String.join(",", cabecera));
        }

        List<Fila> filas = new ArrayList<>();
        for (int i = 1; i < lineas.size(); i++) {
            int numero = i + 1;
            String texto = lineas.get(i);
            if (texto.isBlank()) {
                advertencia(filasV, archivo, numero, "Línea vacía.");
                continue;
            }
            revisarCodificacion(archivo, numero, texto);
            List<String> celdas = parsearLinea(archivo, numero, texto);
            if (celdas == null) {
                continue;
            }
            if (celdas.size() != cabeceraEsperada.size()) {
                String pista = celdas.size() > cabeceraEsperada.size()
                        ? " Puede haber una coma decimal o una coma dentro de un texto sin comillas." : "";
                error(filasV, archivo, numero, "Tiene " + celdas.size() + " columnas y se esperaban "
                        + cabeceraEsperada.size() + "." + pista);
                if (rechazadas != null && !celdas.get(0).isBlank()) {
                    rechazadas.putIfAbsent(celdas.get(0), numero);
                }
                continue;
            }
            filas.add(new Fila(numero, celdas));
        }
        if (filas.isEmpty()) {
            error(filasV, archivo, 0, "No tiene ninguna fila de datos válida.");
        }
        detalle(filasV, filas.size() + " fila(s) de datos");
        return filas;
    }

    private void revisarCodificacion(String archivo, int linea, String texto) {
        Verificacion v = pv(Verificacion.M2, Verificacion.C2);
        if (texto.indexOf('\uFFFD') >= 0) {
            error(v, archivo, linea, "Contiene el carácter de reemplazo (\uFFFD): el texto se dañó al guardar o exportar.");
        }
        if (MOJIBAKE.matcher(texto).find()) {
            error(v, archivo, linea, "Contiene secuencias como 'Ã¡' o 'Ã©': parece UTF-8 leído como Latin-1. "
                    + "Corregir el texto y exportar de nuevo en UTF-8.");
        }
    }

    /** Separa una línea CSV por comas (con soporte de comillas dobles); null si las comillas están mal. */
    private List<String> parsearLinea(String archivo, int linea, String texto) {
        Verificacion v = pv(Verificacion.M4, Verificacion.C4);
        List<String> celdas = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        boolean enComillas = false;
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (enComillas) {
                if (c == '"') {
                    if (i + 1 < texto.length() && texto.charAt(i + 1) == '"') {
                        actual.append('"');
                        i++;
                    } else {
                        enComillas = false;
                    }
                } else {
                    actual.append(c);
                }
            } else if (c == '"') {
                if (actual.length() > 0) {
                    error(v, archivo, linea, "Comillas dobles en mitad de un campo sin comillas.");
                    return null;
                }
                enComillas = true;
            } else if (c == ',') {
                celdas.add(actual.toString());
                actual.setLength(0);
            } else {
                actual.append(c);
            }
        }
        if (enComillas) {
            error(v, archivo, linea, "Comillas dobles sin cerrar.");
            return null;
        }
        celdas.add(actual.toString());
        return celdas;
    }

    // ------------------------------------------------------------------ municipios.csv

    private void validarMunicipios(List<Fila> filas) {
        String archivo = archivoMunicipios;
        if (filas.isEmpty()) {
            return;
        }
        ejecutar(Verificacion.M5, Verificacion.M6, Verificacion.M7, Verificacion.M8, Verificacion.M9,
                Verificacion.M10, Verificacion.M11);

        Map<String, Integer> lineaPorNormalizado = new HashMap<>();
        Map<String, String> nombrePorCoordenada = new HashMap<>();
        int filasConNombre = 0;

        for (Fila fila : filas) {
            int linea = fila.linea();
            List<String> c = fila.celdas();
            String nombre = c.get(0);
            String departamento = c.get(1);
            String zona = c.get(2);

            for (int i = 0; i < CABECERA_MUNICIPIOS.size(); i++) {
                if (c.get(i).isBlank()) {
                    error(Verificacion.M5, archivo, linea, "Valor vacío en la columna '" + CABECERA_MUNICIPIOS.get(i) + "'.");
                }
            }
            revisarNombre(archivo, linea, "nombre", nombre);
            revisarTextoSimple(archivo, linea, "departamento", departamento);
            revisarTextoSimple(archivo, linea, "zona", zona);

            Double lat = leerNumero(archivo, linea, "latitud", c.get(3), false);
            Double lon = leerNumero(archivo, linea, "longitud", c.get(4), false);
            revisarRangoColombia(archivo, linea, lat, lon, c.get(3), c.get(4));

            if (nombre.isBlank()) {
                continue;
            }
            filasConNombre++;
            if (municipios.containsKey(nombre)) {
                error(Verificacion.M7, archivo, linea, "Municipio duplicado '" + nombre + "' (ya aparece en la línea "
                        + municipios.get(nombre).linea() + ").");
                continue;
            }
            String normalizado = normalizar(nombre);
            Integer lineaPrevia = lineaPorNormalizado.get(normalizado);
            if (lineaPrevia != null) {
                error(Verificacion.M7, archivo, linea, "'" + nombre + "' solo se diferencia en mayúsculas o tildes del "
                        + "municipio de la línea " + lineaPrevia + ": el mismo municipio escrito de dos formas.");
            }
            lineaPorNormalizado.putIfAbsent(normalizado, linea);
            municipios.put(nombre, new FilaMunicipio(linea, nombre, departamento, zona, lat, lon));
            indiceNormalizado.putIfAbsent(normalizado, nombre);

            if (lat != null && lon != null) {
                String otro = nombrePorCoordenada.putIfAbsent(lat + "," + lon, nombre);
                if (otro != null) {
                    advertencia(Verificacion.M11, archivo, linea, "'" + nombre
                            + "' tiene exactamente las mismas coordenadas que '" + otro + "'.");
                }
            }
        }
        detalle(Verificacion.M7, municipios.size() + " municipio(s) distinto(s) en " + filasConNombre + " fila(s)");
        detalle(Verificacion.M5, "revisadas " + filas.size() * CABECERA_MUNICIPIOS.size() + " celdas");
        revisarDecimales(archivo);
        compararConReadme(archivo);
    }

    private void revisarRangoColombia(String archivo, int linea, Double lat, Double lon, String textoLat, String textoLon) {
        boolean latFuera = lat != null && (lat < LAT_MIN || lat > LAT_MAX);
        boolean lonFuera = lon != null && (lon < LON_MIN || lon > LON_MAX);
        if (!latFuera && !lonFuera) {
            return;
        }
        String rango = "Colombia: latitud " + LAT_MIN + " a " + LAT_MAX + ", longitud " + LON_MIN + " a " + LON_MAX;
        if (latFuera && lonFuera && lat >= LON_MIN && lat <= LON_MAX && lon >= LAT_MIN && lon <= LAT_MAX) {
            error(Verificacion.M10, archivo, linea, "Latitud (" + textoLat + ") y longitud (" + textoLon
                    + ") parecen estar invertidas. " + rango + ".");
            return;
        }
        if (latFuera) {
            error(Verificacion.M10, archivo, linea, "Latitud " + textoLat + " fuera del rango de Colombia (" + rango + ").");
        }
        if (lonFuera) {
            String pista = lon > 0 ? " Colombia está al oeste de Greenwich: la longitud debe ser negativa." : "";
            error(Verificacion.M10, archivo, linea, "Longitud " + textoLon + " fuera del rango de Colombia (" + rango + ")."
                    + pista);
        }
    }

    /** Compara con los 20 municipios, departamentos y zonas del README. */
    private void compararConReadme(String archivo) {
        ejecutar(Verificacion.M12, Verificacion.M13);
        if (municipios.isEmpty()) {
            error(Verificacion.M12, archivo, 0, "No se leyó ningún municipio válido; se esperaban " + TOTAL_MUNICIPIOS + ".");
            return;
        }

        Map<String, String> esperadosNormalizados = new HashMap<>();
        for (String esperado : ESPERADOS.keySet()) {
            esperadosNormalizados.put(normalizar(esperado), esperado);
        }
        Set<String> cubiertos = new HashSet<>();

        for (FilaMunicipio m : municipios.values()) {
            Esperado esperado = ESPERADOS.get(m.nombre());
            if (esperado != null) {
                cubiertos.add(m.nombre());
                if (!m.departamento().isBlank() && !m.departamento().equals(esperado.departamento())) {
                    advertencia(Verificacion.M13, archivo, m.linea(), "Departamento '" + m.departamento() + "' para "
                            + m.nombre() + ": el README indica '" + esperado.departamento() + "'.");
                }
                if (!m.zona().isBlank() && !m.zona().equals(esperado.zona())) {
                    advertencia(Verificacion.M13, archivo, m.linea(), "Zona '" + m.zona() + "' para " + m.nombre()
                            + ": el README indica '" + esperado.zona() + "'.");
                }
                continue;
            }
            String parecido = esperadosNormalizados.get(normalizar(m.nombre()));
            if (parecido != null) {
                cubiertos.add(parecido);
                if (!mismoTextoVisible(m.nombre(), parecido)) {
                    error(Verificacion.M12, archivo, m.linea(), "El nombre está escrito '" + m.nombre()
                            + "' y el README lo escribe '" + parecido + "'.");
                }
            } else {
                error(Verificacion.M12, archivo, m.linea(), "'" + m.nombre() + "' no es uno de los 20 municipios del README.");
            }
        }
        for (String esperado : ESPERADOS.keySet()) {
            if (!cubiertos.contains(esperado) && !filasRechazadas.containsKey(esperado)) {
                error(Verificacion.M12, archivo, 0, "Falta el municipio '" + esperado + "' del README.");
            }
        }
        if (municipios.size() != TOTAL_MUNICIPIOS) {
            error(Verificacion.M12, archivo, 0, "Hay " + municipios.size() + " municipios y se esperaban " + TOTAL_MUNICIPIOS + ".");
        }
        detalle(Verificacion.M12, cubiertos.size() + " de " + TOTAL_MUNICIPIOS + " municipios del README, ni más ni menos");
        detalle(Verificacion.M13, "departamento y zona de los " + cubiertos.size() + " municipios revisados");
    }

    // ------------------------------------------------------------------ conexiones.csv

    private void validarConexiones(List<Fila> filas) {
        String archivo = archivoConexiones;
        if (filas.isEmpty()) {
            return;
        }
        boolean puedeVerificarNombres = !municipios.isEmpty();
        ejecutar(Verificacion.C5, Verificacion.C6, Verificacion.C8, Verificacion.C9, Verificacion.C10,
                Verificacion.C11, Verificacion.C12, Verificacion.C13);
        if (puedeVerificarNombres) {
            ejecutar(Verificacion.C7);
        }

        Map<Par, FilaConexion> unicas = new LinkedHashMap<>();
        int referencias = 0;

        for (Fila fila : filas) {
            int linea = fila.linea();
            List<String> c = fila.celdas();
            String a = c.get(0);
            String b = c.get(1);

            for (int i = 0; i < CABECERA_CONEXIONES.size(); i++) {
                if (c.get(i).isBlank()) {
                    error(Verificacion.C5, archivo, linea, "Valor vacío en la columna '" + CABECERA_CONEXIONES.get(i) + "'.");
                }
            }
            revisarNombre(archivo, linea, "municipio1", a);
            revisarNombre(archivo, linea, "municipio2", b);
            if (puedeVerificarNombres) {
                verificarExistencia(archivo, linea, "municipio1", a);
                verificarExistencia(archivo, linea, "municipio2", b);
                referencias += (a.isBlank() ? 0 : 1) + (b.isBlank() ? 0 : 1);
            }

            Double km = leerNumero(archivo, linea, "km", c.get(2), true);
            if (km != null && km <= 0) {
                error(Verificacion.C9, archivo, linea, "El km debe ser mayor que 0 (es " + c.get(2) + ").");
            }
            if (a.isBlank() || b.isBlank()) {
                continue;
            }
            if (a.equals(b)) {
                error(Verificacion.C11, archivo, linea, "'" + a + "' está conectado consigo mismo.");
                continue;
            }

            Par par = Par.de(a, b);
            FilaConexion previa = unicas.get(par);
            if (previa == null) {
                unicas.put(par, new FilaConexion(linea, a, b, km));
                continue;
            }
            boolean mismoSentido = previa.municipio1().equals(a);
            error(Verificacion.C12, archivo, linea, (mismoSentido
                    ? "Conexión repetida " + a + " - " + b
                    : "Conexión invertida " + a + " - " + b + " ya escrita como " + previa.municipio1() + " - "
                            + previa.municipio2())
                    + " (línea " + previa.linea() + "). El README indica que cada conexión aparece una sola vez.");
            if (km != null && previa.km() != null && Math.abs(km - previa.km()) > EPSILON) {
                error(Verificacion.C13, archivo, linea, "Distancia NO simétrica: " + previa.municipio1() + "->"
                        + previa.municipio2() + " = " + formatear(previa.km()) + " km (línea " + previa.linea()
                        + ") pero " + a + "->" + b + " = " + formatear(km) + " km.");
            }
        }

        revisarDecimales(archivo);
        detalle(Verificacion.C7, referencias + " referencia(s) a municipios revisadas");
        detalle(Verificacion.C12, unicas.size() + " conexión(es), cada una escrita una sola vez");
        detalle(Verificacion.C13, unicas.size() + " conexión(es) con una sola distancia, válida en ambos sentidos");
        if (puedeVerificarNombres) {
            validarGrafo(unicas);
        }
    }

    private void verificarExistencia(String archivo, int linea, String columna, String nombre) {
        if (nombre.isBlank() || municipios.containsKey(nombre)) {
            return;
        }
        Integer lineaRechazada = filasRechazadas.get(nombre);
        String sugerido = indiceNormalizado.get(normalizar(nombre));
        String texto;
        if (lineaRechazada != null) {
            texto = "está en " + archivoMunicipios + " (línea " + lineaRechazada
                    + "), pero esa fila tiene errores de formato y no se pudo usar";
        } else if (sugerido == null) {
            texto = "no existe en " + archivoMunicipios;
        } else if (mismoTextoVisible(nombre, sugerido)) {
            texto = "se ve igual a '" + sugerido + "' de " + archivoMunicipios
                    + ", pero la tilde está codificada distinto (una está descompuesta)";
        } else {
            texto = "no coincide exactamente con " + archivoMunicipios + "; se parece a '" + sugerido + "'";
        }
        error(Verificacion.C7, archivo, linea, "'" + nombre + "' (columna " + columna + ") " + texto + ".");
    }

    // ------------------------------------------------------------------ grafo

    private void validarGrafo(Map<Par, FilaConexion> unicas) {
        ejecutar(Verificacion.G1, Verificacion.G2, Verificacion.G3);
        Map<String, Set<String>> vecinos = new LinkedHashMap<>();
        for (String nombre : municipios.keySet()) {
            vecinos.put(nombre, new LinkedHashSet<>());
        }

        double razonMin = Double.MAX_VALUE;
        double razonMax = -1;
        String etiquetaMin = null;
        String etiquetaMax = null;
        int imposibles = 0;
        int revisadas = 0;

        for (FilaConexion con : unicas.values()) {
            FilaMunicipio ma = municipios.get(con.municipio1());
            FilaMunicipio mb = municipios.get(con.municipio2());
            if (ma == null || mb == null) {
                continue;
            }
            vecinos.get(ma.nombre()).add(mb.nombre());
            vecinos.get(mb.nombre()).add(ma.nombre());

            if (con.km() == null || ma.latitud() == null || ma.longitud() == null
                    || mb.latitud() == null || mb.longitud() == null) {
                continue;
            }
            revisadas++;
            double recta = haversineKm(ma.latitud(), ma.longitud(), mb.latitud(), mb.longitud());
            String etiqueta = ma.nombre() + " - " + mb.nombre();
            if (con.km() + EPSILON < recta) {
                imposibles++;
                error(Verificacion.G3, archivoConexiones, con.linea(), etiqueta + ": " + formatear(con.km())
                        + " km por carretera es MENOR que la distancia en línea recta (" + formatear(recta)
                        + " km). Revisar el km o las coordenadas; así la heurística no sería admisible.");
            } else if (recta > 0) {
                double razon = con.km() / recta;
                if (razon < razonMin) {
                    razonMin = razon;
                    etiquetaMin = etiqueta;
                }
                if (razon > razonMax) {
                    razonMax = razon;
                    etiquetaMax = etiqueta;
                }
            }
        }
        if (etiquetaMin != null) {
            detalle(Verificacion.G3, revisadas + " conexiones; km/recta mínima " + formatear(razonMin) + " ("
                    + etiquetaMin + "), máxima " + formatear(razonMax) + " (" + etiquetaMax + ")"
                    + (imposibles > 0 ? "; sin contar las que fallan" : ""));
        }

        int minGrado = Integer.MAX_VALUE;
        int maxGrado = 0;
        for (Map.Entry<String, Set<String>> e : vecinos.entrySet()) {
            int grado = e.getValue().size();
            minGrado = Math.min(minGrado, grado);
            maxGrado = Math.max(maxGrado, grado);
            if (grado == 0) {
                error(Verificacion.G1, ARCHIVO_GRAFO, 0, "'" + e.getKey() + "' está aislado: no tiene ninguna conexión en "
                        + archivoConexiones + ".");
            }
        }
        detalle(Verificacion.G1, "cada municipio tiene entre " + minGrado + " y " + maxGrado + " conexiones");

        List<List<String>> componentes = componentesConexas(vecinos);
        if (componentes.size() > 1) {
            error(Verificacion.G2, ARCHIVO_GRAFO, 0, "El grafo NO es conexo: hay " + componentes.size()
                    + " componentes sin camino entre sí. Algunas búsquedas origen-destino no tendrán solución.");
            for (int i = 0; i < componentes.size(); i++) {
                List<String> comp = componentes.get(i);
                error(Verificacion.G2, ARCHIVO_GRAFO, 0, "Componente " + (i + 1) + " de " + componentes.size() + " ("
                        + comp.size() + " municipio(s)): " + String.join(", ", comp));
            }
        } else {
            detalle(Verificacion.G2, "una sola componente con los " + vecinos.size() + " municipios");
        }

        List<String> unaConexion = new ArrayList<>();
        for (Map.Entry<String, Set<String>> e : vecinos.entrySet()) {
            if (e.getValue().size() == 1) {
                unaConexion.add(e.getKey() + " (vía " + e.getValue().iterator().next() + ")");
            }
        }
        if (!unaConexion.isEmpty()) {
            info(ARCHIVO_GRAFO, "Municipios con una sola conexión (callejones sin salida, relevantes para el análisis "
                    + "de la búsqueda avara): " + String.join(", ", unaConexion) + ".");
        }
    }

    private List<List<String>> componentesConexas(Map<String, Set<String>> vecinos) {
        List<List<String>> componentes = new ArrayList<>();
        Set<String> visitados = new HashSet<>();
        for (String inicio : vecinos.keySet()) {
            if (visitados.contains(inicio)) {
                continue;
            }
            List<String> componente = new ArrayList<>();
            Deque<String> pendientes = new ArrayDeque<>();
            pendientes.add(inicio);
            visitados.add(inicio);
            while (!pendientes.isEmpty()) {
                String actual = pendientes.poll();
                componente.add(actual);
                for (String vecino : vecinos.get(actual)) {
                    if (visitados.add(vecino)) {
                        pendientes.add(vecino);
                    }
                }
            }
            componentes.add(componente);
        }
        return componentes;
    }

    private static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(dLon / 2), 2);
        return 2 * RADIO_TIERRA_KM * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    // ------------------------------------------------------------------ valores sueltos

    /** Nombre de municipio limpio: sin espacios sobrantes o raros, tildes normales, solo letras. */
    private void revisarNombre(String archivo, int linea, String columna, String valor) {
        if (valor.isBlank()) {
            return;
        }
        Verificacion v = pv(Verificacion.M6, Verificacion.C6);
        boolean hayError = revisarEspacios(archivo, linea, columna, valor);
        if (!valor.equals(Normalizer.normalize(valor, Normalizer.Form.NFC))) {
            error(v, archivo, linea, "'" + valor + "' (columna " + columna + ") tiene la tilde descompuesta (letra y acento "
                    + "separados): se ve igual pero no es el mismo texto. Reescribir el nombre.");
            hayError = true;
        }
        if (!hayError && !NOMBRE_VALIDO.matcher(valor).matches()) {
            advertencia(v, archivo, linea, "'" + valor + "' (columna " + columna + ") tiene caracteres inusuales para un "
                    + "nombre de municipio (solo se esperan letras, espacios y guiones).");
        }
    }

    private void revisarTextoSimple(String archivo, int linea, String columna, String valor) {
        if (!valor.isBlank()) {
            revisarEspacios(archivo, linea, columna, valor);
        }
    }

    private boolean revisarEspacios(String archivo, int linea, String columna, String valor) {
        Verificacion v = pv(Verificacion.M6, Verificacion.C6);
        boolean hayError = false;
        if (!valor.equals(valor.strip())) {
            error(v, archivo, linea, "'" + valor + "' (columna " + columna + ") tiene espacios al inicio o al final.");
            hayError = true;
        }
        if (valor.contains("  ")) {
            error(v, archivo, linea, "'" + valor + "' (columna " + columna + ") tiene espacios dobles.");
            hayError = true;
        }
        for (int i = 0; i < valor.length(); i++) {
            char ch = valor.charAt(i);
            if (ch != ' ' && (Character.isWhitespace(ch) || Character.isSpaceChar(ch))) {
                error(v, archivo, linea, "'" + valor + "' (columna " + columna + ") tiene un espacio no estándar (U+"
                        + String.format("%04X", (int) ch) + ", por ejemplo un espacio de no separación).");
                hayError = true;
                break;
            }
        }
        return hayError;
    }

    /**
     * Convierte un valor numérico (acepta punto o coma decimal; la coma se reporta luego como
     * error de decimales) y lo anota para revisar la consistencia de decimales.
     *
     * @return el número, o null si no es válido o está vacío (ya se reportó)
     */
    private Double leerNumero(String archivo, int linea, String columna, String valor, boolean esKm) {
        if (valor.isBlank()) {
            return null;
        }
        if (esKm && NEGATIVO.matcher(valor).matches()) {
            error(Verificacion.C9, archivo, linea, "Km negativo: " + valor + ".");
            return null;
        }
        if (!(esKm ? KILOMETROS : COORDENADA).matcher(valor).matches()) {
            error(pv(Verificacion.M8, Verificacion.C8), archivo, linea, "'" + valor + "' (columna " + columna
                    + ") no es un número válido. Se espera un número con punto decimal, por ejemplo "
                    + (esKm ? "106.0" : "4.5333") + ".");
            return null;
        }
        numeros.add(new Numero(linea, columna, valor));
        return Double.valueOf(valor.replace(',', '.'));
    }

    /** Decimales consistentes en todo el archivo: solo punto y misma cantidad de decimales por columna. */
    private void revisarDecimales(String archivo) {
        Verificacion v = pv(Verificacion.M9, Verificacion.C10);
        boolean hayPuntos = numeros.stream().anyMatch(n -> n.texto().contains("."));
        for (Numero n : numeros) {
            if (n.texto().contains(",")) {
                error(v, archivo, n.linea(), "'" + n.texto() + "' (columna " + n.columna() + ") usa coma decimal; los CSV "
                        + "deben usar punto" + (hayPuntos ? " (mezcla comas y puntos en el mismo archivo)." : " (README)."));
            }
        }

        Map<String, List<Numero>> porColumna = new LinkedHashMap<>();
        for (Numero n : numeros) {
            porColumna.computeIfAbsent(n.columna(), k -> new ArrayList<>()).add(n);
        }
        List<String> resumen = new ArrayList<>();
        for (Map.Entry<String, List<Numero>> col : porColumna.entrySet()) {
            Map<Integer, Integer> frecuencia = new TreeMap<>();
            for (Numero n : col.getValue()) {
                frecuencia.merge(decimalesDe(n.texto()), 1, Integer::sum);
            }
            int habitual = -1;
            int veces = -1;
            for (Map.Entry<Integer, Integer> e : frecuencia.entrySet()) {
                if (e.getValue() >= veces) {
                    habitual = e.getKey();
                    veces = e.getValue();
                }
            }
            resumen.add(col.getKey() + ": " + habitual + " decimal(es)");
            for (Numero n : col.getValue()) {
                if (decimalesDe(n.texto()) != habitual) {
                    advertencia(v, archivo, n.linea(), "Cantidad de decimales inconsistente en la columna '" + col.getKey()
                            + "': '" + n.texto() + "' tiene " + decimalesDe(n.texto()) + " y lo habitual es " + habitual + ".");
                }
            }
        }
        detalle(v, String.join("; ", resumen));
        numeros.clear();
    }

    private static int decimalesDe(String texto) {
        int marca = Math.max(texto.indexOf('.'), texto.indexOf(','));
        return marca < 0 ? 0 : texto.length() - marca - 1;
    }

    // ------------------------------------------------------------------ reporte

    private void agregarProblemasPorCategoria(StringBuilder sb) {
        boolean titulo = false;
        for (Verificacion v : Verificacion.values()) {
            List<Problema> lista = new ArrayList<>();
            for (Problema p : problemas) {
                if (p.verificacion() == v && p.severidad() != Severidad.INFO) {
                    lista.add(p);
                }
            }
            if (lista.isEmpty()) {
                continue;
            }
            if (!titulo) {
                sb.append("\nPROBLEMAS POR CATEGORÍA\n");
                titulo = true;
            }
            sb.append('\n').append(' ').append(v.name()).append(" - ").append(v.descripcion()).append(" (")
              .append(lista.size()).append(")\n");
            for (Problema p : lista) {
                sb.append("   ").append(p.severidad() == Severidad.ERROR ? "ERROR " : "AVISO ").append(p).append('\n');
            }
        }
    }

    private void agregarInformacion(StringBuilder sb) {
        boolean titulo = false;
        for (Problema p : problemas) {
            if (p.severidad() == Severidad.INFO) {
                if (!titulo) {
                    sb.append("\nDATOS DE INTERÉS\n");
                    titulo = true;
                }
                sb.append("   ").append(p.mensaje()).append('\n');
            }
        }
    }

    private String tituloGrupo(Grupo grupo) {
        return switch (grupo) {
            case MUNICIPIOS -> archivoMunicipios;
            case CONEXIONES -> archivoConexiones;
            case GRAFO -> "grafo (municipios + conexiones)";
        };
    }

    private String notaDe(Verificacion v, Estado estado) {
        if (estado == Estado.OMITIDA) {
            String motivo = switch (v.grupo()) {
                case MUNICIPIOS -> lecturaMunicipios ? "no hay filas de datos válidas" : "no se pudo leer " + archivoMunicipios;
                case CONEXIONES -> !lecturaConexiones ? "no se pudo leer " + archivoConexiones
                        : v == Verificacion.C7 && municipios.isEmpty() ? "no hay municipios válidos con los que comparar"
                        : "no hay filas de datos válidas";
                case GRAFO -> "necesita ambos archivos leídos y con datos válidos";
            };
            return "no se ejecutó: " + motivo;
        }
        if (estado == Estado.OK) {
            return null;
        }
        long errores = problemas.stream().filter(p -> p.verificacion() == v && p.severidad() == Severidad.ERROR).count();
        long avisos = problemas.stream().filter(p -> p.verificacion() == v && p.severidad() == Severidad.ADVERTENCIA).count();
        String nota = (errores > 0 ? errores + " error(es)" : "") + (errores > 0 && avisos > 0 ? " y " : "")
                + (avisos > 0 ? avisos + " advertencia(s)" : "");
        return nota + " (ver PROBLEMAS POR CATEGORÍA)";
    }

    // ------------------------------------------------------------------ utilidades

    /** Elige la verificación del archivo que se está procesando (municipios o conexiones). */
    private Verificacion pv(Verificacion deMunicipios, Verificacion deConexiones) {
        return enMunicipios ? deMunicipios : deConexiones;
    }

    private void ejecutar(Verificacion... verificaciones) {
        ejecutadas.addAll(Arrays.asList(verificaciones));
    }

    private void detalle(Verificacion v, String texto) {
        detalles.put(v, texto);
    }

    private int ordenArchivo(String archivo) {
        if (archivo.equals(archivoMunicipios)) {
            return 0;
        }
        return archivo.equals(archivoConexiones) ? 1 : 2;
    }

    private static String normalizar(String texto) {
        String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return sinTildes.toLowerCase(Locale.ROOT).replaceAll("[\\s\\u00A0]+", " ").strip();
    }

    private static boolean mismoTextoVisible(String a, String b) {
        return Normalizer.normalize(a, Normalizer.Form.NFC).equals(Normalizer.normalize(b, Normalizer.Form.NFC));
    }

    private static String formatear(double valor) {
        return String.format(Locale.ROOT, "%.2f", valor);
    }

    private static String nombreDeArchivo(Path ruta) {
        Path nombre = ruta.getFileName();
        return nombre == null ? ruta.toString() : nombre.toString();
    }

    private void error(Verificacion v, String archivo, int linea, String mensaje) {
        problemas.add(new Problema(Severidad.ERROR, v, archivo, linea, mensaje));
    }

    private void advertencia(Verificacion v, String archivo, int linea, String mensaje) {
        problemas.add(new Problema(Severidad.ADVERTENCIA, v, archivo, linea, mensaje));
    }

    private void info(String archivo, String mensaje) {
        problemas.add(new Problema(Severidad.INFO, null, archivo, 0, mensaje));
    }
}