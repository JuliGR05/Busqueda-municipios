# Tabla de resultados

Comparación de la búsqueda avara, A* y Dijkstra sobre los 18 pares del issue #9.

Generado por `municipios.analisis.Experimentos`. No editar a mano: se regenera con

```
mvn -q compile
java -cp target/classes municipios.analisis.Experimentos
```

A* coincidió con Dijkstra en 380 de los 380 pares que se pueden formar con los 20 municipios; la búsqueda avara falló en 108 de ellos. El detalle está en `analisis-resultados.md`.

| Origen | Destino | Avara (km) | Avara (nodos) | A* (km) | A* (nodos) | Óptimo Dijkstra (km) | Dijkstra (nodos) | Avara se aleja | A* ahorra nodos |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Ibagué | Cali | 661.0 | 3 | 252.1 | 2 | 252.1 | 6 | +162.2 % | 4 |
| Armenia | Neiva | 629.0 | 3 | 284.1 | 3 | 284.1 | 7 | +121.4 % | 4 |
| Pereira | Neiva | 659.0 | 3 | 323.0 | 4 | 323.0 | 7 | +104.0 % | 3 |
| Soacha | Cali | 739.0 | 3 | 429.1 | 3 | 429.1 | 9 | +72.2 % | 6 |
| Tunja | Cali | 902.0 | 4 | 592.1 | 5 | 592.1 | 11 | +52.3 % | 6 |
| Medellín | Neiva | 897.0 | 4 | 561.0 | 5 | 561.0 | 11 | +59.9 % | 6 |
| Barrancabermeja | Soacha | 429.0 | 1 | 429.0 | 2 | 429.0 | 5 | 0 % | 3 |
| Santa Marta | Manizales | 1326.0 | 5 | 1137.0 | 10 | 1137.0 | 12 | +16.6 % | 2 |
| Montería | Neiva | 1347.0 | 5 | 1011.0 | 6 | 1011.0 | 15 | +33.2 % | 9 |
| Cartagena | Cúcuta | 902.0 | 2 | 873.0 | 5 | 873.0 | 7 | +3.3 % | 2 |
| Ibagué | Valledupar | 1444.0 | 4 | 1071.0 | 10 | 1071.0 | 17 | +34.8 % | 7 |
| Bucaramanga | Popayán | 1004.0 | 4 | 1004.0 | 13 | 1004.0 | 18 | 0 % | 5 |
| Villavicencio | Cúcuta | 740.0 | 3 | 740.0 | 5 | 740.0 | 13 | 0 % | 8 |
| Pasto | Popayán | 249.0 | 1 | 249.0 | 1 | 249.0 | 1 | 0 % | 0 |
| Pereira | Armenia | 45.5 | 1 | 45.5 | 1 | 45.5 | 1 | 0 % | 0 |
| Santa Marta | Barranquilla | 106.0 | 1 | 106.0 | 1 | 106.0 | 1 | 0 % | 0 |
| Cali | Popayán | 180.0 | 1 | 180.0 | 1 | 180.0 | 2 | 0 % | 1 |
| Barranquilla | Pasto | 1686.0 | 6 | 1686.0 | 18 | 1686.0 | 19 | 0 % | 1 |

`A* ahorra nodos` es la diferencia entre los municipios expandidos por Dijkstra y los expandidos por A*.
Los caminos completos están en `resultados-experimentos.csv`, que además trae los tiempos; esas columnas cambian según la máquina donde se mida.
