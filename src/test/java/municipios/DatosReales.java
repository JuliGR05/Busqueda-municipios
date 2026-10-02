package municipios;

import municipios.algoritmo.DistanciaLineaRecta;
import municipios.algoritmo.Heuristica;
import municipios.datos.CargadorCSV;
import municipios.modelo.Grafo;
import municipios.modelo.Municipio;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Utilidad compartida por las pruebas que necesitan trabajar con los 20 municipios
 * reales de {@code data/} en vez de con un grafo de juguete.
 *
 * <p>Maven ejecuta las pruebas con la raíz del proyecto como directorio de trabajo,
 * así que las rutas relativas a {@code data/} siempre encuentran los archivos. Con
 * un IDE puede pasar que el directorio de trabajo sea otro; por eso la clase busca
 * la carpeta {@code data/} subiendo desde el directorio actual y, si tampoco la
 * encuentra, usa la carpeta del código fuente que el compilador deja disponible
 * ({@code target/test-classes/..}).</p>
 */
public final class DatosReales {

    /** Número de municipios que define la actividad. */
    public static final int TOTAL_MUNICIPIOS = 20;

    private static final Path CARPETA = localizar();

    private DatosReales() {}

    /** @return la carpeta que contiene los dos CSV de la actividad */
    public static Path carpeta() {
        return CARPETA;
    }

    /** @return la ruta de municipios.csv */
    public static Path municipios() {
        return CARPETA.resolve("municipios.csv");
    }

    /** @return la ruta de conexiones.csv */
    public static Path conexiones() {
        return CARPETA.resolve("conexiones.csv");
    }

    /** @return el grafo real con los 20 municipios y todas sus conexiones */
    public static Grafo grafo() {
        try {
            return CargadorCSV.cargarGrafo(municipios(), conexiones());
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudieron leer los CSV de " + CARPETA.toAbsolutePath(), e);
        }
    }

    /** @return la heurística que usa el proyecto: la distancia en línea recta */
    public static Heuristica heuristica() {
        return new DistanciaLineaRecta();
    }

    /**
     * Busca un municipio por nombre sin fallar si no existe, para que las pruebas
     * puedan comparar con null y así decir "este municipio no está en los datos".
     *
     * @param nombre nombre del municipio
     * @return el municipio, o null si los CSV no lo tienen
     */
    public static Municipio municipio(Grafo grafo, String nombre) {
        return grafo.buscarPorNombre(nombre);
    }

    /** @return los 20 municipios en el orden en que aparecen en municipios.csv */
    public static List<Municipio> municipios(Grafo grafo) {
        return grafo.getMunicipios();
    }

    private static Path localizar() {
        List<Path> candidatos = List.of(
                Paths.get("data"),
                Paths.get("..", "data"),
                Paths.get("..", "..", "data"));
        for (Path candidato : candidatos) {
            if (Files.isRegularFile(candidato.resolve("municipios.csv"))) {
                return candidato;
            }
        }
        throw new IllegalStateException("No se encontró la carpeta data/ con los CSV del proyecto. "
                + "Las pruebas que usan los datos reales deben ejecutarse desde la raíz del repositorio.");
    }
}