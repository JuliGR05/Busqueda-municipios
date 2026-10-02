# Análisis de resultados

Generado por `municipios.analisis.Experimentos`. **No editar a mano**: se regenera con

```
mvn -q compile
java -cp target/classes municipios.analisis.Experimentos
```

## 1. Con qué frecuencia falla la búsqueda avara

Antes de la tabla conviene el dato completo. De los 380 pares ordenados que se pueden formar con los 20 municipios del proyecto, la búsqueda avara devuelve un camino que **no** es el más corto en **111 de ellos (29 %).

Entre los que fallan, el sobrecosto medio es de 19.0 % y el peor caso es de 162.2 %.

## 2. Los pares de la tabla

La búsqueda avara se equivoca en 12 de los 18 pares de la tabla.

| Origen | Destino | Avara (km) | Óptimo (km) | Se aleja |
|---|---|---:|---:|---:|
| Ibagué | Cali | 661.0 | 252.1 | +162.2 % |
| Armenia | Neiva | 629.0 | 284.1 | +121.4 % |
| Pereira | Neiva | 659.0 | 329.6 | +99.9 % |
| Soacha | Cali | 838.0 | 429.1 | +95.3 % |
| Tunja | Cali | 1001.0 | 592.1 | +69.1 % |
| Medellín | Neiva | 897.0 | 567.6 | +58.0 % |
| Barrancabermeja | Soacha | 860.0 | 559.0 | +53.8 % |
| Santa Marta | Manizales | 1781.0 | 1161.0 | +53.4 % |
| Montería | Neiva | 1347.0 | 1017.6 | +32.4 % |
| Cartagena | Cúcuta | 1314.0 | 1039.0 | +26.5 % |
| Ibagué | Valledupar | 1582.0 | 1360.0 | +16.3 % |
| Bucaramanga | Popayán | 1103.0 | 1042.0 | +5.9 % |

Sumando solo esos 12 pares, la búsqueda avara recorrió 12672 km donde bastaban 8633 km: un 46.8 % de kilómetros de más.

### El caso más claro: Ibagué -> Cali

- La búsqueda avara recorre **661 km** por esta ruta:

  ```
  Ibagué -> Neiva -> Popayán -> Cali
  ```

- El camino más corto es de **252 km**:

  ```
  Ibagué -> Armenia -> Cali
  ```

- Un 162 % de kilómetros de más.

**Por qué se equivoca.** En cada paso la búsqueda avara saca de la frontera el municipio con menor `h(n)`, la distancia en línea recta al destino, y no mira lo que ya se recorrió. En esta ruta `h` baja casi en cada paso: el algoritmo pasa de Ibagué a Neiva y de ahí a Popayán, y desde cada uno sigue apareciendo un municipio más "cerca" del destino en el mapa. El problema es que `h` mide distancia en línea recta y no kilómetros de carretera: cuando la conexión pasa por montaña o por un tramo lento, la distancia real es mucho mayor que la geodésica, y la búsqueda avara no tiene forma de corregirlo. A* descarta esa rama en cuanto ve que `g(n) + h(n)` ya supera el mejor costo conocido, y por eso sí encuentra la ruta corta.

En los 6 pares restantes (Villavicencio -> Cúcuta, Pasto -> Popayán, Pereira -> Armenia, Santa Marta -> Barranquilla, Cali -> Popayán …) la búsqueda avara sí encontró el camino más corto. Es decir: **no falla siempre**, pero tampoco se puede confiar en ella.

## 3. A* siempre iguala a Dijkstra

En los 18 pares de la tabla, A* coincidió con Dijkstra en **18** de 18.

Y en la revisión completa de los 380 pares del grafo, A* coincidió con Dijkstra en **380 de 380**, y expandió más municipios que Dijkstra en 0.

Ese resultado no es casualidad y por eso se comprobó: la distancia en línea recta nunca es mayor que la distancia por carretera entre dos municipios, así que `h` es admisible; y como además cumple la desigualdad triangular, es consistente. Con esas dos condiciones A* no necesita reabrir nodos y devuelve siempre el óptimo. `municipios.algoritmo.ValidadorHeuristica` lo verifica sobre los 400 pares y la prueba `BusquedaEstrellaTest.coincideConDijkstraEnTodosLosParesDeLos20Municipios` deja comprobado que no se degrade.

## 4. Cuántos municipios expande cada algoritmo

| Algoritmo | Municipios expandidos en los 18 pares |
|---|---:|
| Búsqueda avara | 70 |
| A* | 91 |
| Dijkstra | 161 |

A* exploró un 57 % de lo que exploró Dijkstra: 70 municipios menos.

En los 3 pares donde empatan (Pasto -> Popayán, Pereira -> Armenia, Santa Marta -> Barranquilla) el camino óptimo es tan directo que no hay nada que descartar: los tres algoritmos llegan por la misma ruta.

La búsqueda avara es la que menos expande en términos absolutos (3.9 municipios de media frente a los 5.1 de A*), pero ese ahorro tiene un precio: son justamente los pares donde expande poco donde se equivoca. Medellín → Neiva la resuelve expandiendo 4 municipios y aun así devuelve una ruta de 897 km en vez de las 568 km óptimas.

## 5. Tiempo de ejecución

Tiempo medio de una búsqueda, con 200 repeticiones por par para que la medida sea estable:

| Algoritmo | Microsegundos por búsqueda |
|---|---:|
| Búsqueda avara | 27.3 |
| A* | 23.2 |
| Dijkstra | 71.8 |

Con 20 municipios y 25 conexiones, los tres algoritmos corren en microsegundos. La diferencia de tiempo no es un motivo para escoger uno: lo que decide es la calidad de la ruta.

## 6. Conclusiones

1. **La búsqueda avara es rápida pero no confiable.** Expande poco, pero en 29 % de los pares devuelve una ruta que no es la más corta, con sobrecostos de hasta 162 %. Si la calidad de la ruta no importa, sirve; si importa, no.
2. **A* es la opción correcta para este problema.** Siempre coincidió con el óptimo (380 de 380 pares) y exploró menos municipios que Dijkstra.
3. **Lo decisivo es la heurística, no el algoritmo.** La distancia en línea recta resulta admisible y consistente en estos datos, y eso es justo lo que hace óptimo a A*. Con una heurística más informativa, por ejemplo las distancias reales por carretera entre todos los pares, A* exploraría todavía menos.
4. **Dijkstra no hace falta para resolver el problema, pero sí como referencia.** Sin él no habría forma de saber si A* estaba encontrando el óptimo ni de medir cuánto se aleja la búsqueda avara.
5. **El tamaño del grafo es el factor decisivo.** Con 20 municipios, la ventaja de A* sobre Dijkstra es modesta. La diferencia real aparecería con miles de municipios.
