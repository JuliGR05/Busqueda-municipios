package municipios.modelo;

import java.text.Normalizer;
import java.util.*;

/**
 * Grafo no dirigido de municipios: cada conexión se puede recorrer en ambos
 * sentidos con la misma distancia (km por carretera).
 */
public class Grafo {
    // LinkedHashMap: conserva el orden en que se agregaron los municipios
    private final Map<Municipio, List<Conexion>> adyacencias = new LinkedHashMap<>();
    // Índice por nombre normalizado (sin tildes ni mayúsculas) para buscarPorNombre
    private final Map<String, Municipio> porNombre = new HashMap<>();

    public void agregarMunicipio(Municipio m) {
        if (m == null) {
            throw new IllegalArgumentException("El municipio no puede ser null.");
        }
        if (adyacencias.containsKey(m)) {
            return; // ya estaba
        }
        String clave = normalizar(m.getNombre());
        if (porNombre.containsKey(clave)) {
            throw new IllegalArgumentException("Ya existe un municipio con un nombre equivalente a '"
                    + m.getNombre() + "': " + porNombre.get(clave).getNombre());
        }
        adyacencias.put(m, new ArrayList<>());
        porNombre.put(clave, m);
    }

    /** Agrega la conexión en ambos sentidos. Si ya existía, actualiza la distancia. */
    public void agregarConexion(Municipio origen, Municipio destino, double distancia) {
        if (!adyacencias.containsKey(origen) || !adyacencias.containsKey(destino)) {
            throw new IllegalArgumentException(
                    "Los dos municipios deben estar agregados al grafo antes de conectarlos.");
        }
        if (origen.equals(destino)) {
            throw new IllegalArgumentException("Un municipio no puede conectarse consigo mismo: " + origen);
        }
        if (!(distancia > 0)) {
            throw new IllegalArgumentException("La distancia debe ser mayor que 0 (" + origen + " - " + destino + ").");
        }
        poner(origen, destino, distancia);
        poner(destino, origen, distancia);
    }

    private void poner(Municipio desde, Municipio hasta, double distancia) {
        List<Conexion> lista = adyacencias.get(desde);
        lista.removeIf(c -> c.destino().equals(hasta));
        lista.add(new Conexion(hasta, distancia));
    }

    /** Vecinos directos; lista vacía (nunca null) si no tiene o no existe. */
    public List<Conexion> getVecinos(Municipio m) {
        return Collections.unmodifiableList(adyacencias.getOrDefault(m, List.of()));
    }

    /** Distancia de la conexión directa, o -1 si no están conectados directamente. */
    public double getDistancia(Municipio origen, Municipio destino) {
        for (Conexion c : getVecinos(origen)) {
            if (c.destino().equals(destino)) {
                return c.distancia();
            }
        }
        return -1;
    }

    /** Busca ignorando mayúsculas, tildes y espacios de los bordes ("medellin" encuentra "Medellín"). */
    public Municipio buscarPorNombre(String nombre) {
        if (nombre == null) {
            return null;
        }
        return porNombre.get(normalizar(nombre));
    }

    /** Todos los municipios, en el orden en que se cargaron. */
    public List<Municipio> getMunicipios() {
        return new ArrayList<>(adyacencias.keySet());
    }

    /**Conexión entre dos municipios vista como arista (origen,destino,km) */
    public record Arista(Municipio origen, Municipio destino, double distancia){}

    /** Todas las conexiones del grafo, cada una, una sola vez. Internamente cada conexión
     * se guarda en ambos sentidos; aquí solose devuelve una de las dos. 
     */

    public List<Arista> getAristas(){
        List<Arista> aristas = new ArrayList<>();
        Set<Municipio> procesados = new HashSet<>();
        for (Map.Entry<Municipio, List<Conexion>> entrada : adyacencias.entrySet()){
            Municipio origen = entrada.getKey();
            for (Conexion c : entrada.getValue()){
                if (!procesados.contains(c.destino())){
                    aristas.add(new Arista(origen, c.destino(), c.distancia()));
                }
            }
            procesados.add(origen);
        }
        return Collections.unmodifiableList(aristas);
    }
    private static String normalizar(String s) {
        return Normalizer.normalize(s.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    public record Conexion(Municipio destino, double distancia) {}
}