package municipios.ui;

import municipios.algoritmo.BusquedaAvara;
import municipios.algoritmo.BusquedaEstrella;
import municipios.algoritmo.DistanciaLineaRecta;
import municipios.algoritmo.Heuristica;
import municipios.algoritmo.Kruskal;
import municipios.algoritmo.Prim;
import municipios.datos.CargadorCSV;
import municipios.modelo.Grafo;
import municipios.ui.MenuConsola.Algoritmo;
import municipios.ui.MenuConsola.AlgoritmoMST;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Punto de entrada del programa. Carga los CSV al iniciar y abre el menú por consola con las
 * dos búsquedas informadas ya conectadas: la búsqueda avara y A*.
 *
 * <p>Compilar (desde la raíz del repositorio), con Maven:</p>
 * <pre>
 * mvn -q compile
 * </pre>
 * <p>Ejecutar:</p>
 * <pre>
 * java -cp target/classes municipios.ui.Main              (usa la carpeta data/)
 * java -cp target/classes municipios.ui.Main otra/carpeta (usa municipios.csv y conexiones.csv de esa carpeta)
 * </pre>
 * Si los CSV no se pueden cargar muestra el motivo y termina con código 1.
 */
public class Main {

    public static void main(String[] args) {
        Path carpeta = Paths.get(args.length > 0 ? args[0] : "data");
        Grafo grafo = cargarDatos(carpeta);

        Heuristica heuristica = new DistanciaLineaRecta();
        Algoritmo busquedaAvara =
                new Algoritmo("Búsqueda avara", new BusquedaAvara(grafo, heuristica)::buscar);
        Algoritmo busquedaAEstrella =
                new Algoritmo("A*", new BusquedaEstrella(grafo, heuristica)::buscar);

        Kruskal kruskal = new Kruskal();
        Prim prim = new Prim();
        List<AlgoritmoMST> algoritmosMST = List.of(
                new AlgoritmoMST("Kruskal", (g, inicio) -> kruskal.calcular(g)),
                new AlgoritmoMST("Prim", prim::calcular));

        BufferedReader entrada = new BufferedReader(new InputStreamReader(System.in));
        new MenuConsola(grafo, busquedaAvara, busquedaAEstrella, algoritmosMST, entrada, System.out).ejecutar();
    }

    private static Grafo cargarDatos(Path carpeta) {
        try {
            return CargadorCSV.cargarGrafo(carpeta.resolve("municipios.csv"), carpeta.resolve("conexiones.csv"));
        } catch (IOException e) {
            terminarConError("No se pudo leer el archivo: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            terminarConError(e.getMessage());
        } catch (RuntimeException e) {
            terminarConError("Los datos no se pudieron procesar (" + e + ").");
        }
        return null; // no se llega aquí: terminarConError cierra el programa
    }

    private static void terminarConError(String motivo) {
        System.err.println("No se pudieron cargar los datos: " + motivo);
        System.err.println("Ejecuta el programa desde la raíz del repositorio o indica la carpeta con los CSV:");
        System.err.println("  java -cp target/classes municipios.ui.Main <carpeta-con-municipios.csv-y-conexiones.csv>");
        System.err.println("Para revisar los CSV: java -cp target/classes municipios.datos.ValidadorDatos");
        System.exit(1);
    }
}