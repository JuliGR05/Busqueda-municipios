package municipios.ui;

import municipios.algoritmo.ResultadoBusqueda;
import municipios.modelo.Grafo;
import municipios.modelo.Municipio;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Menú por consola (issue #7): lista los municipios, pide origen y destino (por número
 * o por nombre), ejecuta el algoritmo elegido (avara, A* o ambos) y muestra camino,
 * costo total y nodos expandidos. Toda entrada inválida se corrige con un mensaje y
 * se vuelve a pedir; el programa nunca se cae por lo que escriba el usuario.
 *
 * <p>Recibe la entrada y la salida por parámetro para poder probarlo sin teclado.</p>
 */
public class MenuConsola {

    /** Forma de ejecutar una búsqueda entre dos municipios. */
    @FunctionalInterface
    public interface Buscador {
        ResultadoBusqueda buscar(Municipio origen, Municipio destino);
    }

    /** Algoritmo que se puede elegir en el menú. */
    public record Algoritmo(String nombre, Buscador buscador) {}

    /** Se lanza cuando la entrada se termina (Ctrl+D / Ctrl+Z) para cerrar el menú sin error. */
    private static class FinDeEntrada extends RuntimeException {
        private static final long serialVersionUID = 1L;

        FinDeEntrada() {
            super(null, null, false, false);
        }
    }

    private static final String NOMBRE_A_ESTRELLA = "A*";
    private static final double TOLERANCIA_KM = 0.05;

    private final List<Municipio> municipios;
    private final Grafo grafo;
    private final Algoritmo avara;
    private final Algoritmo aEstrella;
    private final BufferedReader in;
    private final PrintStream out;

    /**
     * @param grafo     datos ya cargados (no null)
     * @param avara     búsqueda avara (no null)
     * @param aEstrella A*; {@code null} mientras no esté integrado (issue #15)
     * @param in        de dónde se lee lo que escribe el usuario
     * @param out       dónde se muestran los mensajes
     */
    public MenuConsola(Grafo grafo, Algoritmo avara, Algoritmo aEstrella, BufferedReader in, PrintStream out) {
        if (grafo == null || avara == null || in == null || out == null) {
            throw new IllegalArgumentException("Grafo, búsqueda avara, entrada y salida son obligatorios.");
        }
        this.grafo = grafo;
        this.municipios = grafo.getMunicipios();
        this.avara = avara;
        this.aEstrella = aEstrella;
        this.in = in;
        this.out = out;
    }

    /** Abre el menú y atiende consultas hasta que el usuario elija salir. */
    public void ejecutar() {
        out.println("==================================================");
        out.println(" Búsqueda de rutas entre municipios de Colombia");
        out.println("==================================================");
        try {
            listarMunicipios();
            boolean seguir = true;
            while (seguir) {
                out.println("\nMenú principal");
                out.println("  1) Buscar una ruta");
                out.println("  2) Ver la lista de municipios");
                out.println("  0) Salir");
                String opcion = leer("Opción: ");
                switch (normalizar(opcion)) {
                    case "1" -> nuevaConsulta();
                    case "2" -> listarMunicipios();
                    case "0", "salir" -> seguir = false;
                    default -> out.println("Opción no válida: '" + opcion + "'. Escribe 1, 2 o 0.");
                }
            }
        } catch (FinDeEntrada e) {
            out.println();
        }
        out.println("¡Hasta luego!");
    }

    // ------------------------------------------------------------------ consulta

    private void nuevaConsulta() {
        List<Algoritmo> algoritmos = elegirAlgoritmos();
        if (algoritmos == null) {
            return;
        }
        Municipio origen = pedirMunicipio("Origen", null);
        if (origen == null) {
            return;
        }
        Municipio destino = pedirMunicipio("Destino", origen);
        if (destino == null) {
            return;
        }
        ejecutarYMostrar(origen, destino, algoritmos);
    }

    /** @return los algoritmos a ejecutar, o null si el usuario decide volver al menú */
    private List<Algoritmo> elegirAlgoritmos() {
        String pendiente = aEstrella == null ? "  (aún no disponible)" : "";
        out.println("\nAlgoritmo de búsqueda:");
        out.println("  1) " + avara.nombre());
        out.println("  2) " + NOMBRE_A_ESTRELLA + pendiente);
        out.println("  3) Ambos, para comparar lado a lado" + pendiente);
        while (true) {
            String texto = leer("Elige el algoritmo (1-3, 0 para volver): ");
            switch (normalizar(texto)) {
                case "0", "volver" -> {
                    return null;
                }
                case "1", "avara" -> {
                    return List.of(avara);
                }
                case "2", "a*", "a", "astar" -> {
                    if (aEstrella != null) {
                        return List.of(aEstrella);
                    }
                    avisarAEstrellaPendiente();
                }
                case "3", "ambos" -> {
                    if (aEstrella != null) {
                        return List.of(avara, aEstrella);
                    }
                    avisarAEstrellaPendiente();
                }
                default -> out.println("Opción no válida: '" + texto + "'. Escribe 1, 2 o 3.");
            }
        }
    }

    private void avisarAEstrellaPendiente() {
        out.println("A* todavía no está integrado en el programa (issue #15). Elige 1 o escribe 0 para volver.");
    }

    /**
     * Pide un municipio por número o por nombre hasta que sea válido.
     *
     * @param excluido municipio que no se acepta (el origen, al pedir el destino); puede ser null
     * @return el municipio elegido, o null si el usuario vuelve al menú
     */
    private Municipio pedirMunicipio(String rol, Municipio excluido) {
        while (true) {
            String texto = leer(rol + " (número o nombre; 'lista' para verlos; 0 para volver): ");
            if (texto.equals("0") || normalizar(texto).equals("volver")) {
                return null;
            }
            if (normalizar(texto).equals("lista")) {
                listarMunicipios();
                continue;
            }
            Municipio elegido = interpretar(texto);
            if (elegido == null) {
                continue;
            }
            if (elegido.equals(excluido)) {
                out.println("El destino no puede ser igual al origen (" + elegido + "). Elige otro municipio.");
                continue;
            }
            return elegido;
        }
    }

    /** Convierte lo escrito en un municipio; si no se puede, explica por qué y devuelve null. */
    private Municipio interpretar(String texto) {
        if (texto.isEmpty()) {
            out.println("No escribiste nada. Escribe el número o el nombre del municipio.");
            return null;
        }
        if (texto.matches("[+-]?\\d+")) {
            try {
                int numero = Integer.parseInt(texto);
                if (numero >= 1 && numero <= municipios.size()) {
                    return municipios.get(numero - 1);
                }
            } catch (NumberFormatException e) {
                // número demasiado grande: se trata igual que uno fuera de rango
            }
            out.println("El número " + texto + " no está en la lista. Elige uno entre 1 y " + municipios.size() + ".");
            return null;
        }
        Municipio porNombre = grafo.buscarPorNombre(texto);
        if (porNombre != null) {
            return porNombre;
        }
        out.println("No existe un municipio llamado '" + texto + "'. Escribe su número (1-" + municipios.size()
                + ") o su nombre; las mayúsculas y las tildes no importan.");
        List<String> parecidos = sugerencias(texto);
        if (!parecidos.isEmpty()) {
            out.println("¿Quisiste decir: " + String.join(", ", parecidos) + "?");
        }
        return null;
    }

    // ------------------------------------------------------------------ resultados

    private void ejecutarYMostrar(Municipio origen, Municipio destino, List<Algoritmo> algoritmos) {
        List<ResultadoBusqueda> resultados = new ArrayList<>();
        for (Algoritmo algoritmo : algoritmos) {
            try {
                resultados.add(algoritmo.buscador().buscar(origen, destino));
            } catch (RuntimeException e) {
                out.println("\nNo se pudo ejecutar " + algoritmo.nombre() + ": " + e.getMessage());
                resultados.add(null);
            }
        }
        boolean comparable = algoritmos.size() == 2 && resultados.stream().allMatch(r -> r != null && r.isEncontrado());
        if (comparable) {
            mostrarComparacion(origen, destino, algoritmos, resultados);
            return;
        }
        for (int i = 0; i < algoritmos.size(); i++) {
            if (resultados.get(i) != null) {
                mostrarIndividual(origen, destino, algoritmos.get(i), resultados.get(i));
            }
        }
    }

    private void mostrarIndividual(Municipio origen, Municipio destino, Algoritmo algoritmo, ResultadoBusqueda r) {
        out.println("\n--------------------------------------------------");
        out.println(algoritmo.nombre() + ": " + origen + " -> " + destino);
        if (!r.isEncontrado()) {
            out.println("  No se encontró una ruta entre " + origen + " y " + destino + ".");
            out.println("  Se revisaron " + r.getNodosExpandidos() + " municipio(s) sin llegar al destino; "
                    + "puede que no estén conectados en los datos.");
            return;
        }
        out.println("  Camino: " + camino(r));
        out.println("  Municipios en el camino: " + r.getCamino().size());
        out.println("  Costo total: " + km(r.getCostoTotal()));
        out.println("  Nodos expandidos: " + r.getNodosExpandidos());
    }

    private void mostrarComparacion(Municipio origen, Municipio destino, List<Algoritmo> algoritmos,
                                    List<ResultadoBusqueda> resultados) {
        Algoritmo a = algoritmos.get(0);
        Algoritmo b = algoritmos.get(1);
        ResultadoBusqueda ra = resultados.get(0);
        ResultadoBusqueda rb = resultados.get(1);

        out.println("\n--------------------------------------------------");
        out.println("Comparación: " + origen + " -> " + destino);
        int ancho = Math.max(20, Math.max(a.nombre().length(), b.nombre().length()) + 2);
        String formato = "  %-24s %-" + ancho + "s %-" + ancho + "s%n";
        out.printf(formato, "", a.nombre(), b.nombre());
        out.printf(formato, "Costo total", km(ra.getCostoTotal()), km(rb.getCostoTotal()));
        out.printf(formato, "Nodos expandidos", ra.getNodosExpandidos(), rb.getNodosExpandidos());
        out.printf(formato, "Municipios en el camino", ra.getCamino().size(), rb.getCamino().size());
        out.println();
        out.println("  Camino (" + a.nombre() + "): " + camino(ra));
        out.println("  Camino (" + b.nombre() + "): " + camino(rb));
        out.println();
        out.println("Diferencia de costo: " + diferenciaDeCosto(a.nombre(), ra, b.nombre(), rb));
        out.println("Diferencia de nodos expandidos: " + diferenciaDeNodos(a.nombre(), ra, b.nombre(), rb));
    }

    private static String diferenciaDeCosto(String nombreA, ResultadoBusqueda ra, String nombreB, ResultadoBusqueda rb) {
        double diferencia = ra.getCostoTotal() - rb.getCostoTotal();
        if (Math.abs(diferencia) < TOLERANCIA_KM) {
            return "ambos encontraron el mismo costo (" + km(ra.getCostoTotal()) + ").";
        }
        boolean gana = diferencia > 0;
        double menor = Math.min(ra.getCostoTotal(), rb.getCostoTotal());
        double mayor = Math.max(ra.getCostoTotal(), rb.getCostoTotal());
        return (gana ? nombreB : nombreA) + " encontró un camino " + km(Math.abs(diferencia)) + " más corto que "
                + (gana ? nombreA : nombreB) + String.format(Locale.US, " (%.1f%% menos).", (mayor - menor) / mayor * 100);
    }

    private static String diferenciaDeNodos(String nombreA, ResultadoBusqueda ra, String nombreB, ResultadoBusqueda rb) {
        int na = ra.getNodosExpandidos();
        int nb = rb.getNodosExpandidos();
        if (na == nb) {
            return "ambos expandieron " + na + " nodo(s).";
        }
        String menos = na < nb ? nombreA : nombreB;
        return menos + " expandió " + Math.abs(na - nb) + " nodo(s) menos (" + na + " frente a " + nb + ").";
    }

    // ------------------------------------------------------------------ utilidades

    private void listarMunicipios() {
        out.println("\nMunicipios disponibles:");
        int filas = (municipios.size() + 1) / 2;
        for (int i = 0; i < filas; i++) {
            StringBuilder linea = new StringBuilder("  ").append(String.format("%2d) %-18s", i + 1, municipios.get(i)));
            int j = i + filas;
            if (j < municipios.size()) {
                linea.append(String.format("%2d) %s", j + 1, municipios.get(j)));
            }
            out.println(linea.toString().stripTrailing());
        }
    }

    /** Lee una línea; si la entrada se acabó, cierra el menú de forma ordenada. */
    private String leer(String mensaje) {
        out.print(mensaje);
        out.flush();
        try {
            String linea = in.readLine();
            if (linea == null) {
                throw new FinDeEntrada();
            }
            return linea.trim();
        } catch (IOException e) {
            throw new FinDeEntrada();
        }
    }

    private static String camino(ResultadoBusqueda r) {
        return r.getCamino().stream().map(Municipio::getNombre).collect(Collectors.joining(" -> "));
    }

    private static String km(double valor) {
        return String.format(Locale.US, "%.1f km", valor);
    }

    private List<String> sugerencias(String texto) {
        String buscado = normalizar(texto);
        if (buscado.length() < 2) {
            return List.of();
        }
        List<String> parecidos = new ArrayList<>();
        for (Municipio m : municipios) {
            String candidato = normalizar(m.getNombre());
            if (candidato.contains(buscado) || buscado.contains(candidato) || distancia(candidato, buscado) <= 2) {
                parecidos.add(m.getNombre());
            }
        }
        return parecidos;
    }

    /** Distancia de edición (Levenshtein) para sugerir nombres con errores de tipeo. */
    private static int distancia(String a, String b) {
        int[] anterior = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            anterior[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            int[] actual = new int[b.length() + 1];
            actual[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int costo = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                actual[j] = Math.min(Math.min(actual[j - 1] + 1, anterior[j] + 1), anterior[j - 1] + costo);
            }
            anterior = actual;
        }
        return anterior[b.length()];
    }

    /** Minúsculas y sin tildes, para comparar lo que escribe el usuario. */
    private static String normalizar(String texto) {
        return Normalizer.normalize(texto.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
