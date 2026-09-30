package municipios.algoritmo;

import municipios.datos.CargadorCSV;
import municipios.modelo.Grafo;
import municipios.modelo.Municipio;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Verifica la heurística del issue #6 y produce un reporte citable en el informe.
 *
 * <p>Comprueba: h(destino, destino) = 0, no negatividad, simetría,
 * admisibilidad (h(n, d) &lt;= costo mínimo real con Dijkstra) y
 * consistencia (h(a, d) &lt;= costo(a, b) + h(b, d) en cada arista).</p>
 *
 * <p>Si algún par falla, suele ser un dato erróneo (coordenada o km) y no la
 * fórmula: Haversine en línea recta siempre es &lt;= distancia por carretera
 * cuando los datos son correctos. Un fallo implica que A* podría dejar de ser
 * óptimo hasta corregirlo (ver issue #4).</p>
 */
public class ValidadorHeuristica {

    private static final double TOLERANCIA_KM = 1e-6;

    private final Grafo grafo;
    private final Heuristica heuristica;

    public ValidadorHeuristica(Grafo grafo, Heuristica heuristica) {
        if (grafo == null || heuristica == null) {
            throw new IllegalArgumentException("Grafo y heurística no pueden ser null.");
        }
        this.grafo = grafo;
        this.heuristica = heuristica;
    }

    /** Reporte completo listo para citar en el informe. */
    public String generarReporte() {
        StringBuilder sb = new StringBuilder();
        List<Municipio> municipios = grafo.getMunicipios();
        sb.append(String.format(Locale.US, "Heurística: %s | municipios=%d%n",
                heuristica.getClass().getSimpleName(), municipios.size()));

        // Ejemplo de coherencia pedido en el issue: Santa Marta - Pasto.
        Municipio santaMarta = grafo.buscarPorNombre("Santa Marta");
        Municipio pasto = grafo.buscarPorNombre("Pasto");
        if (santaMarta != null && pasto != null) {
            sb.append(String.format(Locale.US, "Ejemplo Santa Marta-Pasto: h=%.1f km | Dijkstra=%.1f km%n",
                    heuristica.h(santaMarta, pasto), Dijkstra.costoMinimo(grafo, santaMarta, pasto)));
        }

        List<String> fallosBasicas = validarBasicas();
        sb.append(String.format("Básicas (h(x,x)=0, h>=0, simetría): %s%n",
                fallosBasicas.isEmpty() ? "OK" : "FALLAN " + fallosBasicas.size()));
        fallosBasicas.forEach(f -> sb.append("  - ").append(f).append('\n'));

        List<String> fallosAdmis = validarAdmisibilidad();
        sb.append(String.format("Admisibilidad (h(n,d)<=Dijkstra(n,d) en %d pares): %s%n",
                municipios.size() * municipios.size(),
                fallosAdmis.isEmpty() ? "OK" : "FALLAN " + fallosAdmis.size()));
        fallosAdmis.forEach(f -> sb.append("  - ").append(f).append('\n'));

        List<String> fallosCons = validarConsistencia();
        sb.append(String.format("Consistencia (h(a,d)<=c(a,b)+h(b,d)): %s%n",
                fallosCons.isEmpty() ? "OK" : "FALLAN " + fallosCons.size()));
        fallosCons.forEach(f -> sb.append("  - ").append(f).append('\n'));

        boolean ok = fallosBasicas.isEmpty() && fallosAdmis.isEmpty() && fallosCons.isEmpty();
        sb.append(ok ? "RESULTADO: heurística válida (admisible y consistente). A* será óptimo.\n"
                : "RESULTADO: hay fallos. Revisar causa (dato erróneo vs. fórmula) antes de usar A* como óptimo.\n");
        return sb.toString();
    }

    /** h(x,x)=0, h>=0 y h(a,b)=h(b,a). */
    public List<String> validarBasicas() {
        List<String> fallos = new ArrayList<>();
        for (Municipio a : grafo.getMunicipios()) {
            double haa = heuristica.h(a, a);
            if (Math.abs(haa) > TOLERANCIA_KM) {
                fallos.add(String.format(Locale.US, "h(%s,%s)=%.6f, se esperaba 0", a, a, haa));
            }
            for (Municipio b : grafo.getMunicipios()) {
                double hab = heuristica.h(a, b);
                double hba = heuristica.h(b, a);
                if (hab < -TOLERANCIA_KM) {
                    fallos.add(String.format(Locale.US, "h(%s,%s)=%.3f negativa", a, b, hab));
                }
                if (Math.abs(hab - hba) > 1e-6) {
                    fallos.add(String.format(Locale.US, "asimetría h(%s,%s)=%.3f vs h(%s,%s)=%.3f",
                            a, b, hab, b, a, hba));
                }
            }
        }
        return fallos;
    }

    /** Para todo par (n, destino): h(n,destino) &lt;= Dijkstra(n,destino). */
    public List<String> validarAdmisibilidad() {
        List<String> fallos = new ArrayList<>();
        for (Municipio destino : grafo.getMunicipios()) {
            for (Municipio n : grafo.getMunicipios()) {
                double h = heuristica.h(n, destino);
                double real = Dijkstra.costoMinimo(grafo, n, destino);
                if (Double.isInfinite(real)) {
                    continue; // sin camino: admisibilidad no aplica
                }
                if (h - real > 1e-6) {
                    fallos.add(String.format(Locale.US, "no admisible: h(%s,%s)=%.1f > Dijkstra=%.1f",
                            n, destino, h, real));
                }
            }
        }
        return fallos;
    }

    /** Para toda arista (a,b) y todo destino d: h(a,d) &lt;= c(a,b)+h(b,d). */
    public List<String> validarConsistencia() {
        List<String> fallos = new ArrayList<>();
        for (Municipio destino : grafo.getMunicipios()) {
            for (Municipio a : grafo.getMunicipios()) {
                for (Grafo.Conexion c : grafo.getVecinos(a)) {
                    Municipio b = c.destino();
                    double ladoIzq = heuristica.h(a, destino);
                    double ladoDer = c.distancia() + heuristica.h(b, destino);
                    if (ladoIzq - ladoDer > 1e-6) {
                        fallos.add(String.format(Locale.US,
                                "no consistente: h(%s,%s)=%.1f > c(%s,%s)=%.1f + h(%s,%s)=%.1f",
                                a, destino, ladoIzq, a, b, c.distancia(), b, destino,
                                heuristica.h(b, destino)));
                    }
                }
            }
        }
        // Las aristas se guardan en ambos sentidos: se reporta cada sentido una vez.
        // Para no duplicar el reporte se devuelve la lista tal cual (ya es por sentido).
        return fallos;
    }

    /**
     * Ejecución manual sin UI (la UI llega en el issue #7):
     * {@code java -cp out municipios.algoritmo.ValidadorHeuristica}.
     */
    public static void main(String[] args) throws Exception {
        Path base = Paths.get(args.length > 0 ? args[0] : "data");
        Grafo grafo = CargadorCSV.cargarGrafo(
                base.resolve("municipios.csv"), base.resolve("conexiones.csv"));
        ValidadorHeuristica v = new ValidadorHeuristica(grafo, new DistanciaLineaRecta());
        System.out.print(v.generarReporte());

        // Resumen de Dijkstra desde un origen, útil para el informe.
        Municipio origen = grafo.buscarPorNombre("Medellín");
        if (origen != null) {
            Map<Municipio, Double> dist = Dijkstra.distanciasDesde(grafo, origen);
            System.out.println("Dijkstra desde Medellín (km):");
            dist.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(
                            (x, y) -> x.getNombre().compareTo(y.getNombre())))
                    .forEach(e -> System.out.printf(Locale.US, "  %-16s %8.1f%n",
                            e.getKey().getNombre(), e.getValue()));
        }
    }
}
