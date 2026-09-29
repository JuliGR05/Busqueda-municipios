package municipios.modelo;

import java.util.*;

public class Grafo {
    private Map<Municipio, List<Conexion>> adyacencias = new HashMap<>();

    public void agregarMunicipio(Municipio m) {
        // TODO
    }

    public void agregarConexion(Municipio origen, Municipio destino, double distancia) {
        // TODO
    }

    public List<Conexion> getVecinos(Municipio m) {
        return null; // TODO
    }

    public double getDistancia(Municipio origen, Municipio destino) {
        return -1; // TODO
    }

    public Municipio buscarPorNombre(String nombre) {
        return null; // TODO
    }

    public record Conexion(Municipio destino, double distancia) {}
}