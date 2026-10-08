# Búsqueda voraz y A* entre 20 municipios de Colombia

Informe del proyecto. Todos los números que aparecen aquí se generan con código que está en el
repositorio: si cambia un CSV o un algoritmo, se regeneran y hay que volver a ejecutar.

- Código y datos: el repositorio, rama `main`.
- Resultados que se regeneran: [`tabla-resultados.md`](tabla-resultados.md),
  [`analisis-resultados.md`](analisis-resultados.md),
  [`resultados-experimentos.csv`](resultados-experimentos.csv).
- Arquitectura: [`diagrama-clases.md`](diagrama-clases.md).

---

## 1. El problema

Dados dos municipios de Colombia, uno de origen y otro de destino, se quiere encontrar el camino
más corto entre ellos por carretera. No basta con un camino cualquiera: interesa el que menos
kilómetros recorre.

La pregunta que guía todo el trabajo es **qué algoritmo de búsqueda informada conviene**. Se
implementaron dos y se compararon:

- la **búsqueda voraz** (greedy best-first), que solo mira cuánto falta;
- **A\***, que suma lo que falta con lo ya recorrido.

Y se implementó **Dijkstra** como referencia, no como solución: es el algoritmo que sí encuentra
siempre el camino más corto, y sirve para saber si A\* acierta y cuánto se equivoca la búsqueda
voraz.

### Los 20 municipios

Cada integrante del equipo aporta cinco municipios, repartidos en cuatro zonas para que el
grafo quede repartido por el país y no solo alrededor de una región.

| Zona | Municipios |
|---|---|
| Caribe | Barranquilla (Atlántico), Cartagena (Bolívar), Santa Marta (Magdalena), Valledupar (Cesar), Montería (Córdoba) |
| Occidente y Eje Cafetero | Medellín (Antioquia), Manizales (Caldas), Pereira (Risaralda), Armenia (Quindío), Cali (Valle del Cauca) |
| Centro y Nororiente | Soacha (Cundinamarca), Tunja (Boyacá), Bucaramanga (Santander), Cúcuta (Norte de Santander), Barrancabermeja (Santander) |
| Sur y Llanos | Villavicencio (Meta), Ibagué (Tolima), Neiva (Huila), Popayán (Cauca), Pasto (Nariño) |

Los municipios están conectados entre sí, no solo con sus vecinos geográficos. Eso permite que
existan rutas alternativas, que es justamente lo que hace interesante comparar los algoritmos: si
el grafo fuera un árbol, cualquier búsqueda devolvería lo mismo.

---

## 2. El modelo

El problema se modela como un **grafo no dirigido ponderado**:

- cada municipio es un **nodo**, con nombre, latitud y longitud;
- cada carretera principal entre dos municipios es una **arista**, con su distancia en
  kilómetros;
- una arista se puede recorrer en los dos sentidos con el mismo costo, así que el grafo es no
  dirigido.

Los 20 municipios tienen entre 1 y 4 conexiones cada uno, y el grafo es **conexo**: existe camino
entre cualquier par de municipios. Eso lo comprueba `ValidadorDatos`, no se dio por supuesto.

```java
Grafo grafo = CargadorCSV.cargarGrafo(
        Path.of("data/municipios.csv"), Path.of("data/conexiones.csv"));
Municipio origen = grafo.buscarPorNombre("Ibagué");   // ignora tildes y mayúsculas
Municipio destino = grafo.buscarPorNombre("Cali");

ResultadoBusqueda r = new BusquedaEstrella(grafo, new DistanciaLineaRecta()).buscar(origen, destino);
```

---

## 3. La heurística

Los tres algoritmos informados necesitan una forma de **adivinar** cuánto falta para llegar al
destino. Esa función es `h(n)`.

En este proyecto `h(n)` es la **distancia en línea recta** entre el municipio `n` y el destino,
calculada con la fórmula de Haversine a partir de la latitud y la longitud, con un radio terrestre
de 6371 km:

```
a = sen²(Δlat/2) + cos(lat₁)·cos(lat₂)·sen²(Δlon/2)
h = 2 · R · arctan( √a / √(1−a) )        con R = 6371 km
```

Se eligió Haversine y no una tabla de distancias escritas a mano porque:

1. no hay que mantener datos a mano que puedan quedarse viejos;
2. se puede calcular para cualquier par, incluso entre municipios que no estén conectados;
3. los datos que ya había en el CSV (latitud y longitud) se usan directamente.

### Por qué es admisible y por qué A\* es óptimo

Estas dos propiedades son las que hacen que A\* devuelva el camino más corto, así que vale la pena
definirlas y comprobarlas.

**Admisibilidad.** Una heurística es admisible si nunca sobrestima el costo real:

```
h(n, destino)  ≤  costo del camino más corto de n a destino
```

En este caso se cumple por una razón geográfica: **ningún camino por carretera puede ser más
corto que la línea recta** entre sus dos extremos. El mínimo posible para ir de un municipio a
otro es la distancia en línea recta, y cualquier carretera solo puede ser igual o más larga.

**Consistencia.** Una heurística es consistente si para toda arista `(a, b)` y todo destino `d`:

```
h(a, d)  ≤  costo(a, b) + h(b, d)
```

Es decir, que el estimé que falta no "salte" más de lo que cuesta el paso que se está dando.
Haversine cumple la desigualdad triangular, que es exactamente esta condición.

Si `h` es admisible y consistente, A\* **no necesita reabrir nodos**: cuando saca un nodo de la
frontera su costo real ya es definitivo, y por tanto el primer camino que encuentra hasta el
destino es el más corto.

**A\* con una heurística inconsistente sigue dando el óptimo**, pero tiene que reabrir nodos
cuando encuentra un camino más barato. Eso no es un detalle teórico: hay una prueba que lo
comprueba sobre un grafo diseñado exactamente para eso
(`BusquedaEstrellaTest.reabreUnNodoCuandoApareceUnCaminoMasBarato`).

### La comprobación, no la suposición

Que la heurística "parezca" admisible no es un argumento. `ValidadorHeuristica` la revisa
municipio por municipio y devuelve un reporte. Ejecutado sobre los datos del proyecto:

```
Heurística: DistanciaLineaRecta | municipios=20
Ejemplo Santa Marta-Pasto: h=1166.1 km | Dijkstra=1816.0 km
Básicas (h(x,x)=0, h>=0, simetría): OK
Admisibilidad (h(n,d)<=Dijkstra(n,d) en 400 pares): OK
Consistencia (h(a,d)<=c(a,b)+h(b,d)): OK
RESULTADO: heurística válida (admisible y consistente). A* será óptimo.
```

Tres cosas que conviene sacar de ese reporte:

- **`h(destino, destino) = 0`**, como debe ser. Si no lo fuera, A\* nunca consideraría alcanzado
  el destino.
- **`h` nunca es negativa** y es **simétrica**: `h(a, b) = h(b, a)`.
- La admisibilidad se comprobó en los **400 pares** (20 × 20) contra Dijkstra, no solo en
  algunos. La consistencia se comprobó en toda arista por todo destino.
- El ejemplo de la actividad: **Santa Marta → Pasto** son **1166 km en línea recta** y
  **1816 km por carretera**. La línea recta subestima, como corresponde: el 64 % de la ruta real.

Ese último porcentaje es la medida de cuánta información aporta la heurística. La tabla de la
sección 7 muestra que, con estos datos, `h` resulta bastante informativa: A\* exploró un 57 % de
lo que exploró Dijkstra.

---

## 4. Los algoritmos

### 4.1 Búsqueda voraz (greedy best-first)

Mantiene una **frontera** de municipios candidatos y en cada paso expande el que tenga **menor
`h(n)`**. No mira cuánto costó llegar hasta él. Lleva un conjunto de **visitados** para no
volver a expandir un municipio y evitar ciclos, y guarda el padre de cada nodo para poder
reconstruir el camino al final.

```
frontera = {origen con h(origen)}
visitados = {}

mientras la frontera no esté vacía:
    n = el municipio de la frontera con menor h(n)

    si n ya está en visitados:   seguir        # hay entradas viejas
    si n es el destino:          devolver camino(n)

    visitados.agregar(n)
    para cada vecino v de n con km = costo(n, v):
        si v no está en visitados:
            frontera.agregar(v con padre = n)
```

**Por qué no garantiza el camino más corto.** La heurística dice "qué tan cerca está el destino",
no "qué tan bueno es el camino". Dos municipios pueden quedar igual de cerca en línea recta y
tener caminos muy distintos por carretera. Como el algoritmo elige el de menor `h` sin comparar
lo ya recorrido, puede quedarse con un camino más largo que otro que empezaba "peor" según `h`.

En estos datos eso pasa en **111 de los 380 pares** posibles, con sobrecostos de hasta **162 %**.

### 4.2 A\*

Es la misma estructura, pero la frontera se ordena por **`f(n) = g(n) + h(n)`**:

- `g(n)`: kilómetros por carretera ya recorridos desde el origen hasta `n`. Es el costo **real**.
- `h(n)`: kilómetros estimados que faltan. Es el costo **estimado**.
- `f(n) = g(n) + h(n)`: la mejor estimación del costo total de la ruta que pasa por `n`.

La diferencia con la búsqueda voraz es exactamente una cosa: **A\* no ignora el pasado**. Si hay
dos caminos hacia un municipio que está igual de cerca del destino, A\* prefiere el que se llegó
por menos kilómetros, y el resultado natural es que no se desvía por rutas ya caras.

Además, A\* guarda el **mejor `g` conocido por municipio** (`mejorG`). Si al revisar los vecinos de
un municipio aparece un camino **más barato** hacia un vecino que ya estaba visto, actualiza el
valor y vuelve a meterlo en la frontera. Eso es lo que permite corregir una mala decisión
anterior.

El orden de la frontera es determinista: a igual `f` sale primero el de menor `h`, y si también
empatan, el de nombre alfabético. Sin ese desempate, dos ejecuciones sobre los mismos datos
podrían dar rutas distintas y los resultados no serían reproducibles.

**Diferencia con la búsqueda voraz, en una línea:** la voraz ordena por `h(n)`; A\* ordena por
`g(n) + h(n)`.

### 4.3 Dijkstra (referencia)

Dijkstra es A\* con `h(n) = 0` para todo `n`: siempre expande el municipio más barato de llegar.
Por eso siempre da el óptimo, y por eso no lo usa el menú: existe para decir cuál **es** el
camino más corto, que es contra lo que se miden los otros dos. También sirve para contar cuántos
municipios expande (`Dijkstra.expansionesHasta`), con la misma convención que A\*.

---

## 5. Los datos

Dos archivos CSV en `data/`, con la fuente de cada dato en la propia fila.

**`municipios.csv`** — `nombre, departamento, zona, latitud, longitud, fuente`

```
Santa Marta,Magdalena,Caribe,11.2472,-74.2017,Alcaldía Distrital de Santa Marta (...)
```

**`conexiones.csv`** — `municipio1, municipio2, km, fuente`

```
Santa Marta,Barranquilla,106.0,Google Maps
```

Convenciones: separador coma, punto decimal, UTF-8, y **cada conexión aparece una sola vez**
(`Grafo` la registra en los dos sentidos al leerla).

### El control de calidad de los datos

Los resultados dependen por completo de que los datos estén bien, así que hay un validador con
**29 verificaciones** agrupadas en tres bloques: el archivo de municipios, el de conexiones y el
grafo que se construye con los dos.

Sobre los CSV del proyecto:

```
RESUMEN: 29 verificaciones -> 29 OK, 0 con avisos, 0 con fallas, 0 omitidas
RESULTADO: OK - pasaron las 29 verificaciones
```

Lo que comprueba, entre otras cosas:

- que estén los 20 municipios exactos, sin duplicados y escritos igual en todas partes
  (mayúsculas, tildes, espacios);
- que las distancias sean simétricas y mayores que cero;
- que no haya valores vacíos ni decimales mal formados;
- que las coordenadas estén dentro de Colombia y no esténlatitude y longitud invertidas;
- que el grafo sea conexo y no haya municipios aislados;
- que **ninguna conexión por carretera sea más corta que la línea recta** entre sus dos
  municipios, que es justo lo que rompería la admisibilidad de `h`.

Ese último punto dio un dato útil: la razón entre kilómetros de carretera y línea recta está
entre **1,14** (Soacha–Tunja) y **2,07** (Manizales–Ibagué). O sea, ninguna carretera del
proyecto es sospechosamente corta, y la heurística nunca va a sobreestimar.

Además, el validador señala los municipios con una sola conexión, que son callejones sin salida
relevantes para el análisis: **Villavicencio** (solo por Soacha) y **Pasto** (solo por Popayán).

Un validador que nunca falla no sirve de nada, así que hay 60 pruebas que toman los CSV reales,
los dañan a propósito con **un solo tipo de error por caso** y comprueban que la verificación
correspondiente lo detecta: archivo que no existe, texto que no está en UTF-8, coma decimal,
kilómetro negativo, municipio inexistente, tilde descompuesta, conexión repetida o invertida,
grafo desconectado, entre otros.

---

## 6. Pruebas

`mvn test` corre **171 pruebas** en verde. Están hechas sobre un grafo de juguete construido a
mano, no sobre los datos reales, para poder provocar a propósito las situaciones difíciles:

| Qué se prueba | Dónde |
|---|---|
| Camino directo, sin camino, origen = destino, grafo con ciclo | `BusquedaAvaraTest`, `BusquedaEstrellaTest` |
| Un caso donde la búsqueda voraz **no** da el óptimo | `BusquedaAvaraTest.greedyNoEsOptimoMinimoLocal` |
| En ese mismo grafo, A\* **sí** lo da | `BusquedaEstrellaTest.enElGrafoDondeGreedyFallaEncuentraElOptimo` |
| A\* reabre un nodo cuando aparece un camino más barato | `BusquedaEstrellaTest.reabreUnNodoCuandoApareceUnCaminoMasBarato` |
| Con heurística consistente, A\* no reabre nada | `BusquedaEstrellaTest.conHeuristicaConsistenteNoNecesitaReabrir` |
| A\* coincide con Dijkstra en los **380 pares** de los 20 municipios | `BusquedaEstrellaTest.coincideConDijkstraEnTodosLosParesDeLos20Municipios` |
| A\* nunca expande más municipios que Dijkstra | `BusquedaEstrellaTest.expandeIgualOMenosNodosQueDijkstra` |
| La heurística real es admisible y consistente, y el validador detecta que no lo sea | `ValidadorHeuristicaTest` |
| `h(x, x) = 0`, simetría, valores conocidos de Haversine | `DistanciaLineaRectaTest` |
| CSV válidos e inválidos, y carga desde `src/test/resources` | `CargadorCSVTest` |
| Las 29 verificaciones del validador y 60 casos de CSV dañados | `ValidadorDatosTest` |
| Vecinos en ambos sentidos, distancias simétricas, entradas inválidas | `GrafoTest` |
| El menú: 5 consultas seguidas, comparación, entradas inválidas | `MenuConsolaTest` |
| Que la tabla de experimentos sea completa y coherente | `ExperimentosTest` |

---

## 7. Resultados

Los 18 pares de la tabla cubren los 20 municipios, e incluyen parejas cercanas, lejanas y casos
donde los tres algoritmos dan resultados distintos. La tabla se genera con un solo comando:

```bash
mvn -q compile
java -cp target/classes municipios.analisis.Experimentos
```

| Origen | Destino | Avara (km) | Avara (nodos) | A* (km) | A* (nodos) | Óptimo (km) | Dijkstra (nodos) | Avara se aleja | A* ahorra nodos |
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

Además de la tabla, `Experimentos` revisa **los 380 pares ordenados** que se pueden formar con los
20 municipios, no solo los 18 de la tabla:

| Afirmación | Resultado |
|---|---|
| A\* coincide con el óptimo (Dijkstra) | **380 de 380** |
| A\* expandió más municipios que Dijkstra | **0 de 380** |
| La búsqueda voraz no dio el camino más corto | **111 de 380** (29 %) |
| Sobrecosto medio de la voraz, entre los que fallan | 19,0 % |
| Peor sobrecosto de la voraz | **162,2 %** |

---

## 8. Análisis

### Por qué falla la búsqueda voraz: el caso Ibagué → Cali

Es el caso más claro de toda la tabla, y lo que muestra merece más que una cifra.

| | Camino | Kilómetros |
|---|---|---|
| **Búsqueda voraz** | Ibagué → Neiva → Popayán → Cali | **661** |
| **Camino más corto** | Ibagué → Armenia → Cali | **252** |

Un **162 %** de diferencia. Lo interesante es que **la ruta que elige la búsqueda voraz es la que
"se ve mejor" en un mapa**: baja casi en línea recta hacia el suroeste, y el valor de `h` va
bajando en casi cada paso, que es exactamente lo que el algoritmo premia. Neiva y Popayán quedan
"más cerca" de Cali en línea recta que Armenia.

El problema es que `h` mide distancia en línea recta y no kilómetros de carretera. La ruta por
Neiva y Popayán rodea y sube por terreno desfavorable, así que sus 409 kilómetros de carretera
costan mucho más que los 179 que marca la línea recta. La búsqueda voraz no tiene forma de saber
eso: no mira `g`, así que no puede notar que va acumulando costo.

**A\* no se equivoca** porque en cuanto mira esa rama ve que `g(n) + h(n)` ya supera el mejor
costo conocido y la descarta, sin llegar a recorrerla.

### El patrón de fondo

Los 12 pares donde la voraz falla tienen algo en común: el destino queda al otro lado de un
"atractivo" geodésico. La línea recta señala un camino que parece corto, pero que en carretera
resulta ser el largo. La voraz sigue la señal y se equivoca; A\* la combina con lo ya recorrido y
la descarta.

### Cuándo conviene cada algoritmo

**A\* es la respuesta para este problema.** Siempre dio el óptimo, y exploró menos municipios que
Dijkstra: un 57 % de su trabajo, es decir 70 municipios menos en los 18 pares de la tabla.

La búsqueda voraz **no es una alternativa peor**: es una alternativa distinta. Encaja cuando lo
que importa es el límite de tiempo y la ruta aproximada es aceptable, por ejemplo para dibujar una
sugerencia rápida en un mapa o para un sistema donde la respuesta tiene que llegar ya. Con estos
datos es la más rápida de las tres, y su ventaja de tiempo es marginal: los tres algoritmos
están en el mismo orden de magnitud (unas decenas de microsegundos por búsqueda), mientras que
la diferencia en calidad de la ruta es de hasta el 162 %.

**Dijkstra no hace falta para resolver el problema.** Sin heurística da el óptimo siempre, así
que es la opción si algún día los datos fueran tan distintos que `h` dejara de servir. Pero con
estos datos es la peor de las tres: es la más lenta y la que más municipios expande.

### Sobre el tamaño del grafo

Con 20 municipios la diferencia entre los tres algoritmos en tiempo es de microsegundos, y no
justifica ninguna decisión. **La diferencia real aparecería con miles de municipios**, donde A\*
seguiría recortando de forma importante el espacio de búsqueda y la búsqueda voraz acumularía
muchos más desvíos. Concluir "los tres algoritmos son iguales de rápidos" a partir de 20
municipios sería una equivocación: aquí el problema no es el tiempo, es la calidad de la ruta.

---

## 9. Árbol de expansión mínima (MST)

Además de las rutas más cortas entre dos municipios, el proyecto calcula el **árbol de expansión
mínima** (MST, por sus siglas en inglés) del grafo: el conjunto de conexiones de menor costo total
que conectan los 20 municipios sin formar ciclos. Para 20 nodos son exactamente 19 aristas.

### 9.1 Cómo funcionan Kruskal y Prim

- **Kruskal** ordena todas las aristas de menor a mayor y las va aceptando mientras no formen un
  ciclo, usando una estructura de conjuntos disjuntos (`UnionFind`) para saber si dos municipios
  ya estaban conectados. Es un algoritmo *voraz sobre las aristas*.
- **Prim** parte de un municipio (por defecto el primero; también configurable) y crece el árbol
  agregando siempre la arista más barata que conecta un municipio ya incluido con uno nuevo,
  apoyándose en una cola de prioridad. Es un algoritmo *voraz sobre el frente de expansión*.

Ambos resuelven el mismo problema y, con costos sin empates "ambiguos", producen el mismo árbol.
El proyecto desempata por nombre para que el resultado sea determinista.

### 9.2 Resultados

Con los 42 datos de `data/conexiones.csv`, los dos algoritmos coinciden: **19 aristas y un costo
total de 3457.6 km**. La comparación completa —aristas elegidas, costo total, aristas descartadas
y tiempo medio— está en la tabla generada por el propio código en
[`docs/tabla-mst.md`](tabla-mst.md):

| Algoritmo | Aristas elegidas | Costo total (km) | Aristas descartadas |
|---|---:|---:|---:|
| Kruskal | 19 | 3457.6 | 15 |
| Prim | 19 | 3457.6 | 7 |

Kruskal mira más aristas porque las recorre en orden global y descarta las que cierran ciclos;
Prim solo mira las aristas del frente de expansión, así que descarta menos. Los dos tiempos son
de microsegundos y, con este tamaño de grafo, no son concluyentes.

### 9.3 Por qué el MST no es la ruta más corta entre dos municipios

Son objetivos distintos. El MST minimiza la **suma total** de kilómetros para conectar a *todos* los
municipios, sin importar qué tan lejos quede cada par; una ruta más corta entre dos municipios
minimiza el costo **de ese par**, aunque obligue a pasar por muchos otros. Por ejemplo, el MST
prefiere "Armenia–Pereira–Manizales–Ibagué" (45.5 + 51 + 73.1 km) porque abarata el total, pero
la ruta más corta entre Santa Marta y Pasto no es un subcamino del MST: usa las conexiones que el
MST descartó como "caras" porque en conjunto convenían menos. Por eso el MST sirve para diseñar la
red mínima de carreteras —no para navegar entre dos puntos— y para eso están la búsqueda voraz,
A\* y Dijkstra.

---

## 10. Limitaciones

- **Los datos son un grafo pequeño y disperso.** 25 conexiones para 20 municipios. El
  comportamiento de los algoritmos cambiaría en una red urbana densa, que es donde los caminos
  se solapan y la heurística tiene más margen de equivocarse.
- **Los kilómetros son de Google Maps, no de una fuente única.** Las distancias por carretera
  entre municipios dependen del tramo principal que se midió, y un mismo par de municipios puede
  tener varias rutas válidas de distinta longitud.
- **La heurística es admisible pero no muy informativa.** Entre 1,14 y 2,07 veces la línea recta:
  está en un rango intermedio. Con las distancias reales por carretera entre todos los pares
  A\* exploraría aún menos.
- **Los tiempos de ejecución no son concluyentes.** Con este tamaño de grafo, 200 repeticiones
  por par dan cifras de microsegundos donde la medición es sensible al ruido de la máquina. Son
  útiles para ver el orden de magnitud, no para afirmar cuál es "más rápido".
- **No se comparó con más algoritmos.** Solo voraz, A\* y Dijkstra. breadth-first sería otra
  alternativa de referencia cuando todos los costos fueran iguales, que no es el caso aquí.
- **La búsqueda voraz y A\* no manejan multietiqueta ni restricciones de ruta** (no pasar por un
  municipio, cerrar una carretera). El modelo es un camino más corto simple.
- **Las coordenadas son de fuentes distintas**, así que hay una variación de unas décimas de
  grado entre municipios, suficiente para mover `h` pero no para cambiar los caminos óptimos.

---

## 11. Cómo reproducir todo

Desde la raíz del repositorio, con JDK 17 o superior y Maven:

```bash
mvn -q compile            # compilar
mvn test                  # las 171 pruebas
java -cp target/classes municipios.ui.Main                # el menú por consola
java -cp target/classes municipios.ui.Main data           # con otra carpeta de datos
java -cp target/classes municipios.analisis.Experimentos  # regenera los resultados
java -cp target/classes municipios.algoritmo.ValidadorHeuristica  # reporte de la heurística
java -cp target/classes municipios.datos.ValidadorDatos            # 29 verificaciones de los CSV
```

El detalle de cada paso está en el [README](../README.md).

---

## 12. Conclusiones

1. **La búsqueda voraz es rápida pero no confiable.** En el 29 % de los pares devolvió una ruta
   que no era la más corta, con sobrecostos de hasta 162 %. Su utilidad depende de que la
   calidad de la ruta no importe.
2. **A\* es la opción correcta aquí.** Dio el óptimo en 380 de 380 pares y expandió menos
   municipios que Dijkstra, todo gracias a que `h` resultó admisible y consistente.
3. **Lo decisivo no fue el algoritmo, fue la heurística.** Elegir la distancia en línea recta y
   verificar que nunca sobreestima es lo que convierte a A\* en la solución. Un A\* con una `h`
   mal elegida habría dado respuestas tan malas como la búsqueda voraz.
4. **Un resultado no se afirma, se comprueba.** La admisibilidad se verificó en los 400 pares, la
   paridad de A\* con Dijkstra en los 380, y el validador de datos se probó con 60 CSV dañados a
   propósito. Eso es lo que permite decir "siempre" sin adivinar.
5. **El tamaño del grafo es el factor decisivo.** Con 20 municipios los tres algoritmos corren en
   microsegundos y la calidad de la ruta pesa mucho más que la velocidad.