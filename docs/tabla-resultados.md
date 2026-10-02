# Tabla de resultados

Comparación de la búsqueda avara, A* y Dijkstra sobre los 18 pares del issue #9.

Generado por `municipios.analisis.Experimentos`. No editar a mano: se regenera con

```
mvn -q compile
java -cp target/classes municipios.analisis.Experimentos
```

A* coincidió con Dijkstra en 380 de los 380 pares que se pueden formar con los 20 municipios; la búsqueda avara falló en 111 de ellos. El detalle está en `analisis-resultados.md`.

| Origen | Destino | Avara (km) | Avara (nodos) | A* (km) | A* (nodos) | Óptimo Dijkstra (km) | Dijkstra (nodos) | Avara se aleja | A* ahorra nodos |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Ibagué | Cali | 661.0 | 3 | 252.1 | 2 | 252.1 | 6 | +162.2 % | 4 |
| Armenia | Neiva | 629.0 | 3 | 284.1 | 3 | 284.1 | 7 | +121.4 % | 4 |
| Pereira | Neiva | 659.0 | 3 | 329.6 | 4 | 329.6 | 7 | +99.9 % | 3 |
| Soacha | Cali | 838.0 | 4 | 429.1 | 3 | 429.1 | 8 | +95.3 % | 5 |
| Tunja | Cali | 1001.0 | 5 | 592.1 | 4 | 592.1 | 11 | +69.1 % | 7 |
| Medellín | Neiva | 897.0 | 4 | 567.6 | 5 | 567.6 | 10 | +58.0 % | 5 |
| Barrancabermeja | Soacha | 860.0 | 4 | 559.0 | 4 | 559.0 | 7 | +53.8 % | 3 |
| Santa Marta | Manizales | 1781.0 | 7 | 1161.0 | 6 | 1161.0 | 9 | +53.4 % | 3 |
| Montería | Neiva | 1347.0 | 5 | 1017.6 | 6 | 1017.6 | 14 | +32.4 % | 8 |
| Cartagena | Cúcuta | 1314.0 | 5 | 1039.0 | 5 | 1039.0 | 10 | +26.5 % | 5 |
| Ibagué | Valledupar | 1582.0 | 7 | 1360.0 | 15 | 1360.0 | 19 | +16.3 % | 4 |
| Bucaramanga | Popayán | 1103.0 | 5 | 1042.0 | 13 | 1042.0 | 16 | +5.9 % | 3 |
| Villavicencio | Cúcuta | 758.0 | 4 | 758.0 | 4 | 758.0 | 13 | 0 % | 9 |
| Pasto | Popayán | 249.0 | 1 | 249.0 | 1 | 249.0 | 1 | 0 % | 0 |
| Pereira | Armenia | 45.5 | 1 | 45.5 | 1 | 45.5 | 1 | 0 % | 0 |
| Santa Marta | Barranquilla | 106.0 | 1 | 106.0 | 1 | 106.0 | 1 | 0 % | 0 |
| Cali | Popayán | 180.0 | 1 | 180.0 | 1 | 180.0 | 2 | 0 % | 1 |
| Barranquilla | Pasto | 1710.0 | 7 | 1710.0 | 13 | 1710.0 | 19 | 0 % | 6 |

`A* ahorra nodos` es la diferencia entre los municipios expandidos por Dijkstra y los expandidos por A*.
Los caminos completos, con los tiempos, están en `resultados-experimentos.csv`.
