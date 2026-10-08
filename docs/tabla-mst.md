# Tabla de resultados MST

Comparación de Kruskal y Prim sobre los 20 municipios del grafo.

Generado por `municipios.analisis.Experimentos`. No editar a mano: se regenera con

```
mvn -q compile
java -cp target/classes municipios.analisis.Experimentos
```

| Algoritmo | Aristas elegidas | Costo total (km) | Aristas consideradas | Aristas descartadas | Tiempo medio (µs) |
|---|---:|---:|---:|---:|---:|
| Kruskal | 19 | 3457.6 | 34 | 15 | 8.9 |
| Prim | 19 | 3457.6 | 26 | 7 | 16.8 |

Ambos algoritmos devolvieron el mismo costo total: 3457.6 km.

Aristas elegidas (Kruskal):

- Pereira — Armenia: 45.5 km
- Manizales — Pereira: 51.0 km
- Armenia — Ibagué: 73.1 km
- Barranquilla — Santa Marta: 106.0 km
- Bucaramanga — Barrancabermeja: 114.0 km
- Soacha — Villavicencio: 114.0 km
- Barranquilla — Cartagena: 134.0 km
- Soacha — Tunja: 163.0 km
- Soacha — Ibagué: 177.0 km
- Armenia — Cali: 179.0 km
- Cali — Popayán: 180.0 km
- Bucaramanga — Cúcuta: 199.0 km
- Ibagué — Neiva: 211.0 km
- Medellín — Manizales: 221.0 km
- Popayán — Pasto: 249.0 km
- Cartagena — Montería: 250.0 km
- Santa Marta — Valledupar: 260.0 km
- Tunja — Bucaramanga: 282.0 km
- Valledupar — Bucaramanga: 449.0 km
