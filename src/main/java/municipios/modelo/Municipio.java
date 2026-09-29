package municipios.modelo;

public class Municipio {
    private String nombre;
    private double latitud;
    private double longitud;

    public Municipio(String nombre, double latitud, double longitud) {
        this.nombre = nombre;
        this.latitud = latitud;
        this.longitud = longitud;
    }

    public String getNombre() { return nombre; }
    public double getLatitud() { return latitud; }
    public double getLongitud() { return longitud; }

    @Override
    public String toString() { return nombre; }

     /** Dos municipios son iguales si tienen el mismo nombre. */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if(!(o instanceof Municipio)) return false;
        return nombre.equals(((Municipio)o).nombre);
    }

    @Override
    public int hashCode() {
        return nombre.hashCode();
    }
}