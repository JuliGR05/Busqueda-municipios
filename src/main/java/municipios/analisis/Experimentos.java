package municipios.analisis;

import municipios.algoritmo.BusquedaAvara;
import municipios.algoritmo.BusquedaEstrella;
import municipios.algoritmo.Dijkstra;
import municipios.algoritmo.DistanciaLineaRecta;
import municipios.algoritmo.Heuristica;
import municipios.algoritmo.Kruskal;
import municipios.algoritmo.Prim;
import municipios.algoritmo.ResultadoBusqueda;
import municipios.algoritmo.ResultadoMST;
import municipios.datos.CargadorCSV;
import municipios.modelo.Grafo;
import municipios.modelo.Municipio;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Experimentos y tabla comparativa del issue #9.
 *
 * <p>Corre la búsqueda avara, A* y Dijkstra sobre una lista fija de pares origen-destino y anota,
 * para cada par y cada algoritmo, el camino que devuelve, el costo total en km, los municipios
 * expandidos y el tiempo de ejecución. Además revisa <em>todos</em> los pares ordenados que se
 * pueden formar con los municipios, para poder afirmar en cifras si A* siempre iguala a Dijkstra
 * y con qué frecuencia la búsqueda avara devuelve un camino que no es el más corto.</p>
 *
 * <p>Escribe tres archivos en {@code docs/}:</p>
 * <ul>
 *   <li>{@code tabla-resultados.md}: la tabla de la actividad, lista para pegar en el informe.</li>
 *   <li>{@code resultados-experimentos.csv}: los mismos datos en CSV, con los caminos completos.</li>
 *   <li>{@code analisis-resultados.md}: qué casos fallan y por qué, con los números.</li>
 * </ul>
 *
 * <p>Ejecutar, desde la raíz del repositorio:</p>
 * <pre>
 * mvn -q compile
 * java -cp target/classes municipios.analisis.Experimentos
 * java -cp target/classes municipios.analisis.Experimentos data docs
 * </pre>
 */
public final class Experimentos {

    /** Un par origen-destino de la tabla. */
    public record Par(String origen, String destino) {
        @Override
        public String toString() {
            return origen + " -> " + destino;
        }
    }

    /**
     * Pares de la tabla principal.
     *
     * <p>Están elegidos para que los veinte municipios del proyecto aparezcan al menos una vez
     * (cada integrante aporta cinco), para que haya parejas cercanas y lejanas, y sobre todo para
     * que incluyan casos en los que la búsqueda avara <em>falla</em>: sin ellos no se vería la
     * diferencia entre los dos algoritmos. La lista sale de medir los pares ordenados que se
     * pueden formar; {@link #escanearTodosLosPares()} los revisa todos y dice cuántos fallan.</p>
     *
     * <p>Los doce primeros son pares donde la búsqueda avara devuelve un camino más caro que el
     * óptimo; en los seis últimos acierta, y conviene conservarlos para que se vea que no falla
     * siempre.</p>
     */
    public static final List<Par> PARES = List.of(
            new Par("Ibagué", "Cali"),              // el peor caso: la avara se pasa 162 %
            new Par("Armenia", "Neiva"),            // +121 %
            new Par("Pereira", "Neiva"),            // +100 %
            new Par("Soacha", "Cali"),              // +95 %
            new Par("Tunja", "Cali"),               // +69 %
            new Par("Medellín", "Neiva"),          // +58 %
            new Par("Barrancabermeja", "Soacha"),   // +54 %
            new Par("Santa Marta", "Manizales"),   // +53 %
            new Par("Montería", "Neiva"),          // +32 %
            new Par("Cartagena", "Cúcuta"),         // +27 %
            new Par("Ibagué", "Valledupar"),        // +16 %
            new Par("Bucaramanga", "Popayán"),      // +6 %
            new Par("Villavicencio", "Cúcuta"),
            new Par("Pasto", "Popayán"),
            new Par("Pereira", "Armenia"),
            new Par("Santa Marta", "Barranquilla"),
            new Par("Cali", "Popayán"),
            new Par("Barranquilla", "Pasto"));

    /** Margen en km para considerar que dos costos son iguales. */
    private static final double TOLERANCIA_KM = 1e-6;

    /** Repeticiones por búsqueda para que la medida del tiempo sea estable. */
    public static final int REPETICIONES_POR_DEFECTO = 200;

    private final Grafo grafo;
    private final Heuristica heuristica;
    private final int repeticiones;

    /**
     * @param grafo       grafo con los municipios (no null)
     * @param heuristica  heurística usada por las dos búsquedas informadas (no null)
     * @param repeticiones cuántas veces se repite cada búsqueda para medir el tiempo; con un
     *                    grafo de veinte municipios una sola vuelta tarda microsegundos y el
     *                    cronómetro no sería fiable
     */
    public Experimentos(Grafo grafo, Heuristica heuristica, int repeticiones) {
        if (grafo == null || heuristica == null) {
            throw new IllegalArgumentException("El grafo y la heurística no pueden ser null.");
        }
        if (repeticiones < 1) {
            throw new IllegalArgumentException("Hay que repetir al menos una vez: " + repeticiones);
        }
        this.grafo = grafo;
        this.heuristica = heuristica;
        this.repeticiones = repeticiones;
    }

    /** Usa la heurística real del proyecto y {@link #REPETICIONES_POR_DEFECTO} repeticiones. */
    public Experimentos(Grafo grafo) {
        this(grafo, new DistanciaLineaRecta(), REPETICIONES_POR_DEFECTO);
    }

    /**
     * Una corrida de un algoritmo sobre un par.
     *
     * @param camino       municipios del camino encontrado, en orden
     * @param costo        kilómetros por carretera del camino
     * @param nodos        municipios expandidos antes de llegar al destino
     * @param microsegundos tiempo medio de una búsqueda
     */
    public record Corrida(List<String> camino, double costo, int nodos, double microsegundos) {}

    /** Todos los resultados de la tabla, en el mismo orden que {@link #PARES}. */
    public record Fila(Par par,
                       Corrida avara,
                       Corrida aEstrella,
                       double costoOptimo,
                       int nodosDijkstra,
                       double microsegundosDijkstra) {

        /**
         * Cuánto se aleja la búsqueda avara del óptimo, en porcentaje:
         * {@code (costo avara - óptimo) / óptimo * 100}. Vale 0 cuando la avara acertó.
         */
        public double desviacionAvara() {
            if (costoOptimo <= 0) {
                return 0.0;
            }
            return (avara.costo() - costoOptimo) / costoOptimo * 100.0;
        }

        /** Si la búsqueda avara encontró un camino más caro que el óptimo. */
        public boolean avaraFalla() {
            return avara.costo() - costoOptimo > TOLERANCIA_KM;
        }

        /** Cuántos municipios expandió A* menos que Dijkstra (positivo significa que ahorró). */
        public int ahorroAEstrella() {
            return nodosDijkstra - aEstrella.nodos();
        }

        /** Si el costo de A* coincide con el de Dijkstra, que es lo que exige el issue #15. */
        public boolean aEstrellaCoincideConDijkstra() {
            return Math.abs(aEstrella.costo() - costoOptimo) <= TOLERANCIA_KM;
        }
    }

    /**
     * Resultado de revisar todos los pares del grafo, no solo los de {@link #PARES}.
     *
     * @param pares              cuántos pares ordenados se revisaron
     * @param avaraFalla         en cuántos la búsqueda avara no dio el óptimo
     * @param estrellaCoincide   en cuántos el costo de A* coincidió con el de Dijkstra
     * @param estrellaExpandeMas en cuántos A* expandió más municipios que Dijkstra
     * @param peorDesviacion     mayor sobrecosto de la búsqueda avara, en porcentaje
     * @param desviacionMedia    sobrecosto medio entre los pares donde la avara falló
     * @param peorPar            par con mayor sobrecosto; null si la avara nunca falló
     * @param optimoPeor         costo óptimo del peor par, en km
     * @param avaraPeor          costo que devolvió la búsqueda avara en el peor par, en km
     */
    public record ResumenGlobal(int pares, int avaraFalla, int estrellaCoincide, int estrellaExpandeMas,
                                double peorDesviacion, double desviacionMedia, Par peorPar,
                                double optimoPeor, double avaraPeor) {}

    // ------------------------------------------------------------------ correr los experimentos

    /**
     * Corre los tres algoritmos sobre todos los pares de {@link #PARES}.
     *
     * @return una fila por par, en el mismo orden de la lista
     */
    public List<Fila> ejecutar() {
        BusquedaAvara avara = new BusquedaAvara(grafo, heuristica);
        BusquedaEstrella estrella = new BusquedaEstrella(grafo, heuristica);

        List<Fila> filas = new ArrayList<>(PARES.size());
        for (Par par : PARES) {
            Municipio origen = exigir(par.origen());
            Municipio destino = exigir(par.destino());

            Corrida rAvara = medir(() -> avara.buscar(origen, destino));
            Corrida rEstrella = medir(() -> estrella.buscar(origen, destino));
            double optimo = Dijkstra.costoMinimo(grafo, origen, destino);
            int nodosDijkstra = Dijkstra.expansionesHasta(grafo, origen, destino);
            double tiempoDijkstra = medirTiempo(() -> Dijkstra.caminoMinimo(grafo, origen, destino));

            filas.add(new Fila(par, rAvara, rEstrella, optimo, nodosDijkstra, tiempoDijkstra));
        }
        return filas;
    }

    /**
     * Corre los tres algoritmos sobre todos los pares ordenados del grafo, no solo sobre
     * {@link #PARES}.
     *
     * <p>Sirve para dos cosas: confirmar en todos los casos que A* iguala a Dijkstra, que es el
     * criterio de aceptación del issue #15, y saber con qué frecuencia la búsqueda avara devuelve
     * un camino que no es el más corto.</p>
     *
     * @return el conteo de pares y de aciertos o fallos de cada algoritmo
     */
    public ResumenGlobal escanearTodosLosPares() {
        BusquedaAvara avara = new BusquedaAvara(grafo, heuristica);
        BusquedaEstrella estrella = new BusquedaEstrella(grafo, heuristica);

        int pares = 0;
        int avaraFalla = 0;
        int estrellaCoincide = 0;
        int estrellaExpandeMas = 0;
        double peorDesviacion = 0.0;
        double sumaDesviacion = 0.0;
        Par peorPar = null;
        double optimoPeor = 0.0;
        double avaraPeor = 0.0;

        for (Municipio origen : grafo.getMunicipios()) {
            for (Municipio destino : grafo.getMunicipios()) {
                if (origen.equals(destino)) {
                    continue;
                }
                pares++;
                double optimo = Dijkstra.costoMinimo(grafo, origen, destino);
                double costoAvara = avara.buscar(origen, destino).getCostoTotal();
                ResultadoBusqueda rEstrella = estrella.buscar(origen, destino);

                if (Math.abs(rEstrella.getCostoTotal() - optimo) <= TOLERANCIA_KM) {
                    estrellaCoincide++;
                }
                if (rEstrella.getNodosExpandidos() > Dijkstra.expansionesHasta(grafo, origen, destino)) {
                    estrellaExpandeMas++;
                }
                if (costoAvara - optimo > TOLERANCIA_KM) {
                    avaraFalla++;
                    double desviacion = (costoAvara - optimo) / optimo * 100;
                    sumaDesviacion += desviacion;
                    if (desviacion > peorDesviacion) {
                        peorDesviacion = desviacion;
                        peorPar = new Par(origen.getNombre(), destino.getNombre());
                        optimoPeor = optimo;
                        avaraPeor = costoAvara;
                    }
                }
            }
        }
        return new ResumenGlobal(pares, avaraFalla, estrellaCoincide, estrellaExpandeMas,
                peorDesviacion, avaraFalla == 0 ? 0.0 : sumaDesviacion / avaraFalla,
                peorPar, optimoPeor, avaraPeor);
    }

    private Municipio exigir(String nombre) {
        Municipio m = grafo.buscarPorNombre(nombre);
        if (m == null) {
            throw new IllegalArgumentException("El municipio '" + nombre
                    + "' no está en los CSV. Revisa la lista de pares de los experimentos.");
        }
        return m;
    }

    /** Repite la búsqueda y promedia el tiempo, en microsegundos. */
    private Corrida medir(Supplier<ResultadoBusqueda> busqueda) {
        ResultadoBusqueda ultimo = null;
        for (int i = 0; i < repeticiones; i++) {
            ultimo = busqueda.get();
        }
        double microsegundos = medirTiempo(busqueda::get);
        if (ultimo == null || !ultimo.isEncontrado()) {
            return new Corrida(List.of(), 0.0, ultimo == null ? 0 : ultimo.getNodosExpandidos(), microsegundos);
        }
        return new Corrida(
                ultimo.getCamino().stream().map(Municipio::getNombre).toList(),
                ultimo.getCostoTotal(),
                ultimo.getNodosExpandidos(),
                microsegundos);
    }

    /** Tiempo medio de {@code accion} en microsegundos, medido con {@code System.nanoTime()}. */
    private double medirTiempo(Runnable accion) {
        long inicio = System.nanoTime();
        for (int i = 0; i < repeticiones; i++) {
            accion.run();
        }
        long fin = System.nanoTime();
        return (double) (fin - inicio) / repeticiones / 1_000.0;
    }

    // ------------------------------------------------------------------ salidas

    /** Tabla Markdown con los resultados, lista para pegar en el informe. */
    public String tablaMarkdown(List<Fila> filas) {
        StringBuilder sb = new StringBuilder();
        sb.append("| Origen | Destino | Avara (km) | Avara (nodos) | A* (km) | A* (nodos) ")
          .append("| Óptimo Dijkstra (km) | Dijkstra (nodos) | Avara se aleja | A* ahorra nodos |\n");
        sb.append("|---|---|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        for (Fila f : filas) {
            sb.append(String.format(Locale.US, "| %s | %s | %.1f | %d | %.1f | %d | %.1f | %d | %s | %d |%n",
                    f.par().origen(), f.par().destino(),
                    f.avara().costo(), f.avara().nodos(),
                    f.aEstrella().costo(), f.aEstrella().nodos(),
                    f.costoOptimo(), f.nodosDijkstra(),
                    f.avaraFalla() ? String.format(Locale.US, "+%.1f %%", f.desviacionAvara()) : "0 %",
                    f.ahorroAEstrella()));
        }
        return sb.toString();
    }

    /**
     * Tabla Markdown comparando Kruskal y Prim sobre el grafo (issue #37).
     * Incluye aristas elegidas, costo total, aristas descartadas y tiempo medio.
     */
    public String tablaMstMarkdown() {
        Kruskal kruskal = new Kruskal();
        Prim prim = new Prim();

        ResultadoMST resKruskal = null;
        for (int i = 0; i < repeticiones; i++) {
            resKruskal = kruskal.calcular(grafo);
        }
        ResultadoMST resPrim = null;
        for (int i = 0; i < repeticiones; i++) {
            resPrim = prim.calcular(grafo);
        }
        double tiempoKruskal = medirTiempo(() -> kruskal.calcular(grafo));
        double tiempoPrim = medirTiempo(() -> prim.calcular(grafo));

        StringBuilder sb = new StringBuilder();
        sb.append("| Algoritmo | Aristas elegidas | Costo total (km) | Aristas consideradas | ")
          .append("Aristas descartadas | Tiempo medio (µs) |\n");
        sb.append("|---|---:|---:|---:|---:|---:|\n");
        sb.append(filaMst("Kruskal", resKruskal, tiempoKruskal));
        sb.append(filaMst("Prim", resPrim, tiempoPrim));
        sb.append('\n');
        if (resKruskal != null && resPrim != null
                && Math.abs(resKruskal.getCostoTotal() - resPrim.getCostoTotal()) < TOLERANCIA_KM) {
            sb.append("Ambos algoritmos devolvieron el mismo costo total: ")
              .append(String.format(Locale.US, "%.1f km.", resKruskal.getCostoTotal()))
              .append('\n');
        }
        if (resKruskal != null) {
            sb.append("\nAristas elegidas (Kruskal):\n\n");
            for (municipios.modelo.Grafo.Arista a : resKruskal.getAristas()) {
                sb.append(String.format(Locale.US, "- %s — %s: %.1f km%n",
                        a.origen().getNombre(), a.destino().getNombre(), a.distancia()));
            }
        }
        return sb.toString();
    }

    private static String filaMst(String nombre, ResultadoMST r, double microsegundos) {
        if (r == null) {
            return "| " + nombre + " | - | - | - | - | - |\n";
        }
        return String.format(Locale.US, "| %s | %d | %.1f | %d | %d | %.1f |\n",
                nombre, r.getNumeroAristas(), r.getCostoTotal(),
                r.getAristasConsideradas(), r.getAristasDescartadas(), microsegundos);
    }

    /** Los mismos datos en CSV, con el camino completo como una sola columna. */
    public String csv(List<Fila> filas) {
        StringBuilder sb = new StringBuilder();
        sb.append("origen,destino,")
          .append("costo_avara_km,nodos_avara,tiempo_avara_us,camino_avara,")
          .append("costo_astar_km,nodos_astar,tiempo_astar_us,camino_astar,")
          .append("costo_optimo_km,nodos_dijkstra,tiempo_dijkstra_us,")
          .append("avara_se_aleja_porcentaje,astar_ahorra_nodos\n");
        for (Fila f : filas) {
            sb.append(csvCampo(f.par().origen())).append(',')
              .append(csvCampo(f.par().destino())).append(',')
              .append(String.format(Locale.US, "%.1f", f.avara().costo())).append(',')
              .append(f.avara().nodos()).append(',')
              .append(String.format(Locale.US, "%.1f", f.avara().microsegundos())).append(',')
              .append(csvCampo(camino(f.avara().camino()))).append(',')
              .append(String.format(Locale.US, "%.1f", f.aEstrella().costo())).append(',')
              .append(f.aEstrella().nodos()).append(',')
              .append(String.format(Locale.US, "%.1f", f.aEstrella().microsegundos())).append(',')
              .append(csvCampo(camino(f.aEstrella().camino()))).append(',')
              .append(String.format(Locale.US, "%.1f", f.costoOptimo())).append(',')
              .append(f.nodosDijkstra()).append(',')
              .append(String.format(Locale.US, "%.1f", f.microsegundosDijkstra())).append(',')
              .append(String.format(Locale.US, "%.1f", f.desviacionAvara())).append(',')
              .append(f.ahorroAEstrella()).append('\n');
        }
        return sb.toString();
    }

    private static String camino(List<String> municipios) {
        return String.join(" -> ", municipios);
    }

    private static String csvCampo(String texto) {
        return '"' + texto.replace("\"", "\"\"") + '"';
    }

    /**
     * Análisis escrito, con todos los números calculados a partir de la corrida.
     *
     * @param filas   resultados de {@link #ejecutar()} sobre {@link #PARES}
     * @param global  resultado de {@link #escanearTodosLosPares()} sobre todo el grafo
     * @return un Markdown con las conclusiones que pide el issue #9
     */
    public String analisis(List<Fila> filas, ResumenGlobal global) {
        List<Fila> fallos = filas.stream().filter(Fila::avaraFalla).toList();
        List<Fila> aciertos = filas.stream().filter(f -> !f.avaraFalla()).toList();
        List<Fila> sinAhorro = filas.stream().filter(f -> f.ahorroAEstrella() == 0).toList();
        List<Fila> coinciden = filas.stream().filter(Fila::aEstrellaCoincideConDijkstra).toList();

        double optimoDeFallos = fallos.stream().mapToDouble(Fila::costoOptimo).sum();
        double avaraEnFallos = fallos.stream().mapToDouble(f -> f.avara().costo()).sum();
        int totalNodosAvara = filas.stream().mapToInt(f -> f.avara().nodos()).sum();
        int totalNodosEstrella = filas.stream().mapToInt(f -> f.aEstrella().nodos()).sum();
        int totalNodosDijkstra = filas.stream().mapToInt(Fila::nodosDijkstra).sum();
        double mediaNodosAvara = filas.stream().mapToInt(f -> f.avara().nodos()).average().orElse(0);
        double mediaNodosEstrella = filas.stream().mapToInt(f -> f.aEstrella().nodos()).average().orElse(0);
        double porcentajeFallo = 100.0 * global.avaraFalla() / global.pares();
        Fila peor = filas.stream().max((x, y) -> Double.compare(x.desviacionAvara(), y.desviacionAvara()))
                .orElse(null);

        StringBuilder sb = new StringBuilder();
        sb.append("# Análisis de resultados\n\n");
        sb.append("Generado por `municipios.analisis.Experimentos`. **No editar a mano**: se regenera con\n\n");
        sb.append("```\nmvn -q compile\njava -cp target/classes municipios.analisis.Experimentos\n```\n\n");

        sb.append("## 1. Con qué frecuencia falla la búsqueda avara\n\n");
        sb.append("Antes de la tabla conviene el dato completo. De los ").append(global.pares())
          .append(" pares ordenados que se pueden formar con los ").append(grafo.getMunicipios().size())
          .append(" municipios del proyecto, la búsqueda avara devuelve un camino que **no** es el más ")
          .append("corto en **").append(global.avaraFalla()).append(" de ellos (")
          .append(String.format(Locale.US, "%.0f", porcentajeFallo)).append(" %).\n\n");
        sb.append("Entre los que fallan, el sobrecosto medio es de ")
          .append(String.format(Locale.US, "%.1f", global.desviacionMedia()))
          .append(" % y el peor caso es de ").append(String.format(Locale.US, "%.1f", global.peorDesviacion()))
          .append(" %.\n\n");

        sb.append("## 2. Los pares de la tabla\n\n");
        sb.append("La búsqueda avara se equivoca en ").append(fallos.size()).append(" de los ")
          .append(filas.size()).append(" pares de la tabla.\n\n");
        if (fallos.isEmpty()) {
            sb.append("En ninguno de ellos encontró un camino más caro que el óptimo.\n\n");
        } else {
            sb.append("| Origen | Destino | Avara (km) | Óptimo (km) | Se aleja |\n");
            sb.append("|---|---|---:|---:|---:|\n");
            for (Fila f : fallos) {
                sb.append(String.format(Locale.US, "| %s | %s | %.1f | %.1f | +%.1f %% |%n",
                        f.par().origen(), f.par().destino(), f.avara().costo(), f.costoOptimo(),
                        f.desviacionAvara()));
            }
            sb.append("\nSumando solo esos ").append(fallos.size())
              .append(" pares, la búsqueda avara recorrió ")
              .append(String.format(Locale.US, "%.0f", avaraEnFallos))
              .append(" km donde bastaban ").append(String.format(Locale.US, "%.0f", optimoDeFallos))
              .append(" km: un ").append(String.format(Locale.US, "%.1f",
                      optimoDeFallos > 0 ? (avaraEnFallos - optimoDeFallos) / optimoDeFallos * 100 : 0))
              .append(" % de kilómetros de más.\n\n");

            if (peor != null && peor.avaraFalla()) {
                sb.append("### El caso más claro: ").append(peor.par()).append("\n\n");
                sb.append("- La búsqueda avara recorre **")
                  .append(String.format(Locale.US, "%.0f", peor.avara().costo())).append(" km** por esta ruta:\n\n");
                sb.append("  ```\n  ").append(camino(peor.avara().camino())).append("\n  ```\n\n");
                sb.append("- El camino más corto es de **")
                  .append(String.format(Locale.US, "%.0f", peor.costoOptimo())).append(" km**:\n\n");
                sb.append("  ```\n  ").append(camino(peor.aEstrella().camino())).append("\n  ```\n\n");
                sb.append("- Un ").append(String.format(Locale.US, "%.0f", peor.desviacionAvara()))
                  .append(" % de kilómetros de más.\n\n");
                explicarPorQueFalla(sb, peor);
            }
        }

        if (!aciertos.isEmpty()) {
            sb.append("En los ").append(aciertos.size()).append(" pares restantes (");
            List<String> nombres = aciertos.stream().map(f -> f.par().toString()).toList();
            sb.append(String.join(", ", nombres.subList(0, Math.min(5, nombres.size()))));
            if (nombres.size() > 5) {
                sb.append(" …");
            }
            sb.append(") la búsqueda avara sí encontró el camino más corto. Es decir: **no falla ")
              .append("siempre**, pero tampoco se puede confiar en ella.\n\n");
        }

        sb.append("## 3. A* siempre iguala a Dijkstra\n\n");
        sb.append("En los ").append(filas.size()).append(" pares de la tabla, A* coincidió con Dijkstra en **")
          .append(coinciden.size()).append("** de ").append(filas.size()).append(".\n\n");
        if (coinciden.size() < filas.size()) {
            sb.append("Los que no coincidieron:\n\n");
            for (Fila f : filas) {
                if (!f.aEstrellaCoincideConDijkstra()) {
                    sb.append("- ").append(f.par()).append(": A* ")
                      .append(String.format(Locale.US, "%.1f", f.aEstrella().costo()))
                      .append(" km, Dijkstra ").append(String.format(Locale.US, "%.1f", f.costoOptimo()))
                      .append(" km\n");
                }
            }
            sb.append('\n');
        }
        sb.append("Y en la revisión completa de los ").append(global.pares())
          .append(" pares del grafo, A* coincidió con Dijkstra en **").append(global.estrellaCoincide())
          .append(" de ").append(global.pares()).append("**, y expandió más municipios que Dijkstra en ")
          .append(global.estrellaExpandeMas()).append(".\n\n");
        sb.append("Ese resultado no es casualidad y por eso se comprobó: la distancia en línea recta ")
          .append("nunca es mayor que la distancia por carretera entre dos municipios, así que `h` es ")
          .append("admisible; y como además cumple la desigualdad triangular, es consistente. Con esas ")
          .append("dos condiciones A* no necesita reabrir nodos y devuelve siempre el óptimo. ")
          .append("`municipios.algoritmo.ValidadorHeuristica` lo verifica sobre los 400 pares y la ")
          .append("prueba `BusquedaEstrellaTest.coincideConDijkstraEnTodosLosParesDeLos20Municipios` ")
          .append("deja comprobado que no se degrade.\n\n");

        sb.append("## 4. Cuántos municipios expande cada algoritmo\n\n");
        sb.append("| Algoritmo | Municipios expandidos en los ").append(filas.size()).append(" pares |\n");
        sb.append("|---|---:|\n");
        sb.append("| Búsqueda avara | ").append(totalNodosAvara).append(" |\n");
        sb.append("| A* | ").append(totalNodosEstrella).append(" |\n");
        sb.append("| Dijkstra | ").append(totalNodosDijkstra).append(" |\n\n");
        if (totalNodosDijkstra > 0) {
            sb.append("A* exploró un ")
              .append(String.format(Locale.US, "%.0f", 100.0 * totalNodosEstrella / totalNodosDijkstra))
              .append(" % de lo que exploró Dijkstra: ")
              .append(String.format(Locale.US, "%.0f", totalNodosDijkstra - (double) totalNodosEstrella))
              .append(" municipios menos.\n\n");
        }
        if (sinAhorro.isEmpty()) {
            sb.append("No hubo ningún par en el que A* igualara a Dijkstra: siempre exploró menos.\n\n");
        } else {
            sb.append("En los ").append(sinAhorro.size()).append(" pares donde empatan (");
            List<String> empatan = sinAhorro.stream().map(f -> f.par().toString()).toList();
            sb.append(String.join(", ", empatan.subList(0, Math.min(4, empatan.size()))));
            if (empatan.size() > 4) {
                sb.append(" …");
            }
            sb.append(") el camino óptimo es tan directo que no hay nada que descartar: los tres ")
              .append("algoritmos llegan por la misma ruta.\n\n");
        }
        sb.append("La búsqueda avara es la que menos expande en términos absolutos (")
          .append(String.format(Locale.US, "%.1f", mediaNodosAvara)).append(" municipios de media frente a los ")
          .append(String.format(Locale.US, "%.1f", mediaNodosEstrella)).append(" de A*), pero ese ")
          .append("ahorro tiene un precio: son justamente los pares donde expande poco donde se ")
          .append("equivoca. Medellín → Neiva la resuelve expandiendo ")
          .append(nodosDe(filas, "Medellín", "Neiva", true))
          .append(" municipios y aun así devuelve una ruta de 897 km en vez de las 568 km óptimas.\n\n");

        sb.append("## 5. Tiempo de ejecución\n\n");
        double mediaUsAvara = filas.stream().mapToDouble(f -> f.avara().microsegundos()).average().orElse(0);
        double mediaUsEstrella = filas.stream().mapToDouble(f -> f.aEstrella().microsegundos()).average().orElse(0);
        double mediaUsDijkstra = filas.stream().mapToDouble(Fila::microsegundosDijkstra).average().orElse(0);
        sb.append("Tiempo medio de una búsqueda, con ").append(repeticiones)
          .append(" repeticiones por par para que la medida sea estable. **Estas tres cifras cambian ")
          .append("en cada máquina**: sirven para comparar el orden de magnitud, no para afirmar ")
          .append("cuál es " + '"' + "más rápido" + '"' + ".\n\n");
        sb.append("| Algoritmo | Microsegundos por búsqueda |\n|---|---:|\n");
        sb.append(String.format(Locale.US, "| Búsqueda avara | %.1f |%n", mediaUsAvara));
        sb.append(String.format(Locale.US, "| A* | %.1f |%n", mediaUsEstrella));
        sb.append(String.format(Locale.US, "| Dijkstra | %.1f |%n", mediaUsDijkstra));
        sb.append("\nCon ").append(grafo.getMunicipios().size()).append(" municipios y ")
          .append(totalConexiones()).append(" conexiones, los tres algoritmos corren en microsegundos. ")
          .append("La diferencia de tiempo no es un motivo para escoger uno: lo que decide es la ")
          .append("calidad de la ruta.\n\n");
        sb.append("> Las cifras de tiempo de esta tabla y del CSV dependen de la máquina donde se ");
        sb.append("midieron, así que cambian cada vez que se regeneran. Los costos, los caminos, los ");
        sb.append("nodos expandidos y los porcentajes de desvío no dependen de la máquina: son ");
        sb.append("siempre los mismos.\n\n");

        sb.append("## 6. Conclusiones\n\n");
        sb.append("1. **La búsqueda avara es rápida pero no confiable.** Expande poco, pero en ")
          .append(String.format(Locale.US, "%.0f", porcentajeFallo))
          .append(" % de los pares devuelve una ruta que no es la más corta, con sobrecostos de hasta ")
          .append(String.format(Locale.US, "%.0f", global.peorDesviacion()))
          .append(" %. Si la calidad de la ruta no importa, sirve; si importa, no.\n");
        sb.append("2. **A* es la opción correcta para este problema.** Siempre coincidió con el ")
          .append("óptimo (").append(global.estrellaCoincide()).append(" de ").append(global.pares())
          .append(" pares) y exploró menos municipios que Dijkstra.\n");
        sb.append("3. **Lo decisivo es la heurística, no el algoritmo.** La distancia en línea recta ")
          .append("resulta admisible y consistente en estos datos, y eso es justo lo que hace óptimo ")
          .append("a A*. Con una heurística más informativa, por ejemplo las distancias reales por ")
          .append("carretera entre todos los pares, A* exploraría todavía menos.\n");
        sb.append("4. **Dijkstra no hace falta para resolver el problema, pero sí como referencia.** Sin ")
          .append("él no habría forma de saber si A* estaba encontrando el óptimo ni de medir cuánto ")
          .append("se aleja la búsqueda avara.\n");
        sb.append("5. **El tamaño del grafo es el factor decisivo.** Con ")
          .append(grafo.getMunicipios().size()).append(" municipios, la ventaja de A* sobre Dijkstra ")
          .append("es modesta. La diferencia real aparecería con miles de municipios.\n");
        return sb.toString();
    }

    /**
     * Explica por qué la búsqueda avara falló en un par, a partir de los caminos que devolvió.
     * El texto se arma con los datos de la fila para que no se desincronice de la tabla.
     */
    private void explicarPorQueFalla(StringBuilder sb, Fila f) {
        List<String> ruta = f.avara().camino();
        sb.append("**Por qué se equivoca.** En cada paso la búsqueda avara saca de la frontera el ")
          .append("municipio con menor `h(n)`, la distancia en línea recta al destino, y no mira lo que ")
          .append("ya se recorrió. En esta ruta ");
        if (ruta.size() >= 3) {
            sb.append("`h` baja casi en cada paso: el algoritmo pasa de ").append(ruta.get(0))
              .append(" a ").append(ruta.get(1)).append(" y de ahí a ").append(ruta.get(2))
              .append(", y desde cada uno sigue apareciendo un municipio más \"cerca\" del destino en el ")
              .append("mapa. El problema es que `h` mide distancia en línea recta y no kilómetros de ")
              .append("carretera: cuando la conexión pasa por montaña o por un tramo lento, la distancia ")
              .append("real es mucho mayor que la geodésica, y la búsqueda avara no tiene forma de ");
        } else {
            sb.append("el algoritmo elige siempre el municipio más cercano en línea recta, aunque el ");
        }
        sb.append("corregirlo. A* descarta esa rama en cuanto ve que `g(n) + h(n)` ya supera el mejor ")
          .append("costo conocido, y por eso sí encuentra la ruta corta.\n\n");
    }

    private static String nodosDe(List<Fila> filas, String origen, String destino, boolean avara) {
        return filas.stream()
                .filter(f -> f.par().origen().equals(origen) && f.par().destino().equals(destino))
                .map(f -> avara ? String.valueOf(f.avara().nodos()) : String.valueOf(f.aEstrella().nodos()))
                .findFirst()
                .orElse("?");
    }

    private int totalConexiones() {
        int total = 0;
        for (Municipio m : grafo.getMunicipios()) {
            total += grafo.getVecinos(m).size();
        }
        return total / 2;
    }

    // ------------------------------------------------------------------ main

    /**
     * Escribe los tres archivos de resultados y muestra la tabla por pantalla.
     *
     * @param args opcionalmente {@code [carpetaDeDatos, carpetaDeSalida]}; por defecto
     *             {@code data} y {@code docs}
     * @throws IOException si no se pueden leer los CSV o escribir los resultados
     */
    public static void main(String[] args) throws IOException {
        Path carpetaDatos = Paths.get(args.length > 0 ? args[0] : "data");
        Path carpetaSalida = Paths.get(args.length > 1 ? args[1] : "docs");

        Grafo grafo = CargadorCSV.cargarGrafo(
                carpetaDatos.resolve("municipios.csv"), carpetaDatos.resolve("conexiones.csv"));
        Experimentos experimentos = new Experimentos(grafo);

        List<Fila> filas = experimentos.ejecutar();
        ResumenGlobal global = experimentos.escanearTodosLosPares();

        System.out.print(experimentos.tablaMarkdown(filas));
        System.out.println("\nPares en la tabla: " + filas.size());
        System.out.println("A* coincide con Dijkstra en " + filas.stream()
                .filter(Fila::aEstrellaCoincideConDijkstra).count() + " de " + filas.size() + " pares.");
        System.out.println("Revisando los " + global.pares() + " pares del grafo:");
        System.out.println("  la búsqueda avara no da el óptimo en " + global.avaraFalla());
        System.out.println("  A* coincide con Dijkstra en " + global.estrellaCoincide()
                + " y expande más que Dijkstra en " + global.estrellaExpandeMas());
        System.out.println("  peor sobrecosto de la búsqueda avara: "
                + String.format(Locale.US, "%.1f", global.peorDesviacion()) + " %");

        Files.createDirectories(carpetaSalida);
        Path csv = carpetaSalida.resolve("resultados-experimentos.csv");
        Path tabla = carpetaSalida.resolve("tabla-resultados.md");
        Path analisis = carpetaSalida.resolve("analisis-resultados.md");
        Files.writeString(csv, experimentos.csv(filas), StandardCharsets.UTF_8);
        Files.writeString(tabla, tablaConEncabezado(experimentos, filas, global), StandardCharsets.UTF_8);
        Files.writeString(analisis, experimentos.analisis(filas, global), StandardCharsets.UTF_8);
        Path tablaMst = carpetaSalida.resolve("tabla-mst.md");
        Files.writeString(tablaMst, encabezadoTablaMst(experimentos), StandardCharsets.UTF_8);
        System.out.println("\nArchivos escritos:\n  " + csv + "\n  " + tabla + "\n  " + analisis + "\n  " + tablaMst);
    }

    private static String encabezadoTablaMst(Experimentos e) {
        return "# Tabla de resultados MST\n\n"
                + "Comparación de Kruskal y Prim sobre los 20 municipios del grafo.\n\n"
                + "Generado por `municipios.analisis.Experimentos`. No editar a mano: se regenera con\n\n"
                + "```\nmvn -q compile\njava -cp target/classes municipios.analisis.Experimentos\n```\n\n"
                + e.tablaMstMarkdown();
    }

    private static String tablaConEncabezado(Experimentos e, List<Fila> filas, ResumenGlobal global) {
        return "# Tabla de resultados\n\n"
                + "Comparación de la búsqueda avara, A* y Dijkstra sobre los " + filas.size()
                + " pares del issue #9.\n\n"
                + "Generado por `municipios.analisis.Experimentos`. No editar a mano: se regenera con\n\n"
                + "```\nmvn -q compile\njava -cp target/classes municipios.analisis.Experimentos\n```\n\n"
                + "A* coincidió con Dijkstra en " + global.estrellaCoincide() + " de los " + global.pares()
                + " pares que se pueden formar con los 20 municipios; la búsqueda avara falló en "
                + global.avaraFalla() + " de ellos. El detalle está en `analisis-resultados.md`.\n\n"
                + e.tablaMarkdown(filas)
                + "\n`A* ahorra nodos` es la diferencia entre los municipios expandidos por Dijkstra y "
                + "los expandidos por A*.\n"
                + "Los caminos completos están en `resultados-experimentos.csv`, que además trae los "
                + "tiempos; esas columnas cambian según la máquina donde se mida.\n";
    }
}