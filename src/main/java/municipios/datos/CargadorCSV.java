package municipios.datos;

import municipios.modelo.Grafo;
import municipios.modelo.Municipio;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Lee los CSV de municipios y conexiones y construye el {@link Grafo}.
 *
 * Formato esperado (la primera línea es el encabezado):
 *   municipios: nombre,departamento,zona,latitud,longitud,fuente
 *   conexiones: municipio1,municipio2,km,fuente
 *
 * - Codificación UTF-8 (se ignora el BOM que agrega Excel).
 * - El separador se detecta en el encabezado: ";" o ",".
 * - Los decimales pueden ir con punto o con coma (106.0 / 106,0).
 * - Cada conexión se registra en ambos sentidos.
 *
 * Ante datos mal formados lanza IllegalArgumentException con el archivo y la
 * línea del problema; los errores de lectura salen como IOException.
 */
public class CargadorCSV {

    private CargadorCSV() {}

    public static Grafo cargarGrafo(Path rutaMunicipios, Path rutaConexiones) throws IOException {
        Grafo grafo = new Grafo();
        cargarMunicipios(rutaMunicipios, grafo);
        cargarConexiones(rutaConexiones, grafo);
        return grafo;
    }

    // ---------------------------------------------------------------- municipios

    private static void cargarMunicipios(Path ruta, Grafo grafo) throws IOException {
        String archivo = ruta.getFileName().toString();
        List<String> lineas = leerLineas(ruta);
        String sep = detectarSeparador(archivo, lineas);
        validarEncabezado(archivo, lineas.get(0), sep,
                "nombre", "departamento", "zona", "latitud", "longitud");

        for (int i = 1; i < lineas.size(); i++) {
            if (lineas.get(i).isBlank()) {
                continue;
            }
            int n = i + 1; // número de línea real en el archivo
            String[] c = lineas.get(i).split(Pattern.quote(sep), 6);
            if (c.length < 5) {
                throw error(archivo, n, "se esperaban al menos 5 columnas (nombre, departamento, zona, "
                        + "latitud, longitud) y hay " + c.length);
            }
            String nombre = c[0].trim();
            if (nombre.isEmpty()) {
                throw error(archivo, n, "el nombre del municipio está vacío");
            }
            double lat = leerDecimal(archivo, n, "latitud", c[3]);
            double lon = leerDecimal(archivo, n, "longitud", c[4]);
            if (lat < -90 || lat > 90) {
                throw error(archivo, n, "la latitud debe estar entre -90 y 90 y es " + lat);
            }
            if (lon < -180 || lon > 180) {
                throw error(archivo, n, "la longitud debe estar entre -180 y 180 y es " + lon);
            }
            if (grafo.buscarPorNombre(nombre) != null) {
                throw error(archivo, n, "el municipio '" + nombre + "' está repetido");
            }
            grafo.agregarMunicipio(new Municipio(nombre, lat, lon));
        }

        if (grafo.getMunicipios().isEmpty()) {
            throw new IllegalArgumentException(archivo + ": no contiene ningún municipio.");
        }
    }

    // ---------------------------------------------------------------- conexiones

    private static void cargarConexiones(Path ruta, Grafo grafo) throws IOException {
        String archivo = ruta.getFileName().toString();
        List<String> lineas = leerLineas(ruta);
        String sep = detectarSeparador(archivo, lineas);
        validarEncabezado(archivo, lineas.get(0), sep, "municipio1", "municipio2", "km");

        Set<String> vistas = new HashSet<>(); // pares ya cargados, sin importar el sentido
        for (int i = 1; i < lineas.size(); i++) {
            if (lineas.get(i).isBlank()) {
                continue;
            }
            int n = i + 1;
            String[] c = lineas.get(i).split(Pattern.quote(sep), 4);
            if (c.length < 3) {
                throw error(archivo, n, "se esperaban al menos 3 columnas (municipio1, municipio2, km) "
                        + "y hay " + c.length);
            }
            Municipio a = grafo.buscarPorNombre(c[0]);
            if (a == null) {
                throw error(archivo, n, "el municipio '" + c[0].trim() + "' no está en el archivo de municipios");
            }
            Municipio b = grafo.buscarPorNombre(c[1]);
            if (b == null) {
                throw error(archivo, n, "el municipio '" + c[1].trim() + "' no está en el archivo de municipios");
            }
            if (a.equals(b)) {
                throw error(archivo, n, "'" + a + "' no puede conectarse consigo mismo");
            }
            double km = leerDecimal(archivo, n, "km", c[2]);
            if (km <= 0) {
                throw error(archivo, n, "la distancia debe ser mayor que 0 y es " + km);
            }
            String clave = a.getNombre().compareTo(b.getNombre()) < 0
                    ? a.getNombre() + "|" + b.getNombre()
                    : b.getNombre() + "|" + a.getNombre();
            if (!vistas.add(clave)) {
                throw error(archivo, n, "la conexión " + a + " - " + b + " está repetida");
            }
            grafo.agregarConexion(a, b, km); // guarda ambos sentidos
        }
    }

    // ---------------------------------------------------------------- utilidades

    private static List<String> leerLineas(Path ruta) throws IOException {
        String archivo = ruta.getFileName().toString();
        List<String> lineas;
        try {
            lineas = new ArrayList<>(Files.readAllLines(ruta, StandardCharsets.UTF_8));
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException(archivo
                    + ": el archivo no está en UTF-8 (guárdalo como \"CSV UTF-8\").", e);
        }
        if (lineas.isEmpty()) {
            throw new IllegalArgumentException(archivo + ": el archivo está vacío.");
        }
        if (lineas.get(0).startsWith("\uFEFF")) { // BOM de Excel
            lineas.set(0, lineas.get(0).substring(1));
        }
        return lineas;
    }

    private static String detectarSeparador(String archivo, List<String> lineas) {
        String encabezado = lineas.get(0);
        if (encabezado.contains(";")) return ";";
        if (encabezado.contains(",")) return ",";
        throw error(archivo, 1, "no se encontró el separador (se esperaba ',' o ';') en el encabezado");
    }

    private static void validarEncabezado(String archivo, String encabezado, String sep, String... esperadas) {
        String[] c = encabezado.split(Pattern.quote(sep), -1);
        for (int i = 0; i < esperadas.length; i++) {
            if (i >= c.length || !c[i].trim().toLowerCase(Locale.ROOT).equals(esperadas[i])) {
                throw error(archivo, 1, "el encabezado debe empezar con: " + String.join(sep, esperadas));
            }
        }
    }

    /** Acepta 4.5 y 4,5. Rechaza vacíos, texto, NaN e infinitos. */
    private static double leerDecimal(String archivo, int linea, String campo, String texto) {
        String t = texto.trim().replace(',', '.');
        if (t.isEmpty()) {
            throw error(archivo, linea, "el campo '" + campo + "' está vacío");
        }
        try {
            double v = Double.parseDouble(t);
            if (!Double.isFinite(v)) {
                throw new NumberFormatException();
            }
            return v;
        } catch (NumberFormatException e) {
            throw error(archivo, linea, "'" + campo + "' no es un número válido: '" + texto.trim() + "'");
        }
    }

    private static IllegalArgumentException error(String archivo, int linea, String mensaje) {
        return new IllegalArgumentException(archivo + ", línea " + linea + ": " + mensaje);
    }
}