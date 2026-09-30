package municipios.ui;

import municipios.algoritmo.BusquedaAvara;
import municipios.algoritmo.DistanciaLineaRecta;
import municipios.algoritmo.Heuristica;
import municipios.datos.CargadorCSV;
import municipios.modelo.Grafo;
import municipios.ui.MenuConsola.Algoritmo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Punto de entrada del programa. Carga los CSV al iniciar y abre el menú por consola.
 *
 * <p>Compilar (desde la raíz del repositorio):</p>
 * <pre>
 * Git Bash / Linux / Mac:  mkdir -p out
 *                          javac -encoding UTF-8 -d out $(find src/main/java -name "*.java")
 * PowerShell (Windows):    mkdir out
 *                          javac -encoding UTF-8 -d out (Get-ChildItem -Recurse src\main\java -Filter *.java).FullName
 * </pre>
 * <p>Ejecutar:</p>
 * <pre>
 * java -cp out municipios.ui.Main              (usa la carpeta data/)
 * java -cp out municipios.ui.Main otra/carpeta (usa municipios.csv y conexiones.csv de esa carpeta)
 * </pre>
 * Si los CSV no se pueden cargar muestra el motivo y termina con código 1.
 */
public class Main {

    public static void main(String[] args) {
        Path carpeta = Paths.get(args.length > 0 ? args[0] : "data");
        Grafo grafo = cargarDatos(carpeta);

        Heuristica heuristica = new DistanciaLineaRecta();
        BusquedaAvara avara = new BusquedaAvara(grafo, heuristica);
        Algoritmo busquedaAvara = new Algoritmo("Búsqueda avara", avara::buscar);
        // TODO issue #15: cuando exista A*, reemplazar null por
        //   new Algoritmo("A*", new AEstrella(grafo, heuristica)::buscar)
        Algoritmo aEstrella = null;

        BufferedReader entrada = new BufferedReader(new InputStreamReader(System.in));
        new MenuConsola(grafo, busquedaAvara, aEstrella, entrada, System.out).ejecutar();
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
        System.err.println("  java -cp out municipios.ui.Main <carpeta-con-municipios.csv-y-conexiones.csv>");
        System.err.println("Para revisar los CSV: java -cp out municipios.datos.ValidadorDatos");
        System.exit(1);
    }
}
