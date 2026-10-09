# Diapositivas de MST

Contenido para la presentación del proyecto sobre el **árbol de expansión mínima**. Cada `---`
separa una diapositiva, y cada línea `##` es el título. Los datos salen de
[`docs/tabla-mst.md`](tabla-mst.md), que genera el propio código.

Para ver el árbol sobre el mapa: abrir [`docs/mapa.html`](mapa.html) y activar la casilla del MST.

---

## Árbol de expansión mínima

- Los mismos 20 municipios y las mismas 42 conexiones del grafo.
- Además de la ruta más corta entre dos puntos, se calcula la red de carreteras más barata
  que conecta **todos** los municipios.
- El MST es el conjunto de conexiones de **menor costo total** que une los 20 municipios **sin
  formar ciclos**.
- Para 20 municipios son exactamente **19 aristas** (n − 1).
- Resultado con los datos reales: **3457,6 km**.

---

## Por qué lo calculamos

- Las búsquedas del proyecto (voraz, A\* y Dijkstra) responden a una pregunta: *¿cómo llego de A
  a B?* El MST responde otra: *¿qué mínimo de carreteras hace falta para que todo quede
  conectado?*
- Son objetivos distintos y no se reemplazan. El MST no sirve para navegar de un municipio a
  otro.
- Sirve para planear redes: caminos, fibra óptica, redes eléctricas, con el menor tramo total.
- Es un problema clásico de grafos, así que da un segundo bloque de algoritmos para comparar con
  las búsquedas: aquí los dos son **voraces**, y dan exactamente el mismo resultado.

---

## Kruskal: voraz sobre las aristas

1. Toma **todas** las aristas del grafo (una por conexión, gracias a `Grafo.getAristas()`).
2. Las ordena de menor a mayor km.
3. Recorre la lista en ese orden:
   - si los dos extremos **no están conectados** todavía, la acepta;
   - si ya estaban conectados, la **descarta** porque cerraría un ciclo.
4. Termina cuando tiene n − 1 aristas.

```java
for (Arista a : ordenadas) {
    if (elegidas.size() == n - 1) break;
    if (grupos.union(indice.get(a.origen()), indice.get(a.destino()))) {
        elegidas.add(a);
    } else {
        descartadas++;
    }
}
```

- El paso 3 es el que evita los ciclos, y lo hace `UnionFind`.

---

## UnionFind: conjuntos disjuntos

- Estructura de datos para saber si dos elementos ya están en el mismo grupo, y unirlos rápido.
- Trabaja sobre índices 0..n-1, así que Kruskal numera primero los municipios del grafo.

| Método | Qué hace |
|---|---|
| `find(x)` | Devuelve la raíz del conjunto de `x`, **comprimiendo el camino** recorrido |
| `union(a, b)` | Une los conjuntos; devuelve `false` si **ya estaban juntos** (formaría ciclo) |
| `conectados(a, b)` | `true` si `a` y `b` están en el mismo conjunto |
| `getComponentes()` | Cuántos conjuntos quedan; 1 significa que todo quedó conectado |

- Dos optimizaciones clásicas: **compresión de caminos** en `find` y **unión por rango** en
  `union`. Con las dos, el costo es prácticamente constante.
- El `false` de `union` es la señal que Kruskal usa para descartar la arista. No hay que
  comprobar ciclos a mano.

---

## Prim: voraz sobre el frente de expansión

1. Parte de **un solo municipio** (por defecto el primero del grafo, configurable).
2. Toma la arista más barata que conecta un municipio **ya dentro** del árbol con uno **fuera**.
3. La agrega, marca ese municipio como incluido y vuelve a meter en la cola las aristas que
   salen de él.
4. Se detiene cuando la cola se vacía o cuando ya están todos.

- Usa una `PriorityQueue<Arista>` ordenada por km, con el **mismo desempate por nombre** que
  Kruskal.
- Es *greedy* como la búsqueda voraz, pero sobre el frente en vez de sobre una heurística.
- El municipio inicial **no cambia el costo total** (probado con los 20 municipios), aunque en un
  grafo desconectado sí cambia qué componente cubre.

---

## Resultados

Los dos algoritmos coinciden. La tabla completa la genera `municipios.analisis.Experimentos`:

| Algoritmo | Aristas elegidas | Costo total (km) | Aristas consideradas | Aristas descartadas | Tiempo medio (µs) |
|---|---:|---:|---:|---:|---:|
| Kruskal | 19 | 3457.6 | 34 | 15 | 8.9 |
| Prim | 19 | 3457.6 | 26 | 7 | 16.8 |

- El mismo **costo total** y el mismo **número de aristas**: los dos son correctos.
- Kruskal mira más aristas porque las recorre en orden global y descarta las que cierran ciclo.
  Prim solo saca de la cola las aristas del frente, así que mira menos.
- Los tiempos son de microsegundos: con 42 aristas **no son concluyentes** en ningún sentido.
  Prim parece más lento, pero la diferencia es ruido a esta escala.

```bash
mvn -q compile
java -cp target/classes municipios.analisis.Experimentos   # regenera docs/tabla-mst.md
```

---

## Las 19 aristas del MST

- Pereira — Armenia: 45,5 km
- Manizales — Pereira: 51,0 km
- Armenia — Ibagué: 73,1 km
- Barranquilla — Santa Marta: 106,0 km
- Bucaramanga — Barrancabermeja: 114,0 km
- Soacha — Villavicencio: 114,0 km
- Barranquilla — Cartagena: 134,0 km
- Soacha — Tunja: 163,0 km
- Soacha — Ibagué: 177,0 km
- Armenia — Cali: 179,0 km
- Cali — Popayán: 180,0 km
- Bucaramanga — Cúcuta: 199,0 km
- Ibagué — Neiva: 211,0 km
- Medellín — Manizales: 221,0 km
- Popayán — Pasto: 249,0 km
- Cartagena — Montería: 250,0 km
- Santa Marta — Valledupar: 260,0 km
- Tunja — Bucaramanga: 282,0 km
- Valledupar — Bucaramanga: 449,0 km

Se ven tres cosas: los tres municipios del Eje Cafetero quedan unidos por sus caminos cortos, y
las dos aristas de 114 km muestran que hubo **empate de km** resuelto por nombre.

---

## Desempate determinista

- Hay aristas con el **mismo número de kilómetros** (las dos de 114 km, por ejemplo). Cuando eso
  pasa, hay más de un árbol igual de válido.
- Los dos algoritmos ordenan por km y, si hay empate, por el **nombre alfabéticamente menor** de
  los dos extremos y después por el mayor.
- El desempate **no depende de qué extremo se llame origen**, así que el resultado es el mismo
  aunque el grafo se recorra al revés.
- Sin esto, la validación cruzada entre Kruskal y Prim no probaría nada: podrían diferir por el
  orden de lectura y no por un error.

```java
private static final Comparator<Arista> ORDEN = Comparator
        .comparingDouble(Arista::distancia)
        .thenComparing(Kruskal::nombreMenor)
        .thenComparing(Kruskal::nombreMayor);
```

---

## Validación cruzada

- `PrimTest.elCostoNoDependeDelMunicipioInicial` corre Prim **desde los 20 municipios**, uno por
  uno, y comprueba que el costo no cambia: siempre **3457,6 km**.
- `PrimTest.grafoPequenoDaElArbolCalculadoAMano` y `KruskalTest.grafoPequenoDaElArbolCalculadoAMano`
  comparan los dos algoritmos contra un árbol esperado **calculado a mano** en un grafo de
  juguete, no solo contra el otro algoritmo.
- `KruskalTest.descartaLaAristaQueCierraUnCiclo` y `PrimTest.descartaLaAristaQueCierraUnCiclo`
  comprueban que el ciclo se evita y que la arista cuenta como descartada.
- `conEmpatesElResultadoNoDependeDelOrdenDeCarga` (en los dos) verifica que el desempate funciona:
  el mismo grafo cargado en distinto orden da el mismo árbol.
- El grafo no conexo, el de un solo municipio, el vacío y el `null` están cubiertos en las dos
  clases.

```java
double esperado = prim.calcular(g).getCostoTotal();
for (Municipio m : g.getMunicipios()) {
    assertEquals(esperado, prim.calcular(g, m).getCostoTotal(), 1e-9);
}
```

- `feature/36-validacion-cruzada` (issue #36) agrega una prueba adicional: Kruskal contra Prim
  desde los 20 inicios y en 50 grafos aleatorios con muchos empates, verificando n − 1 aristas,
  ausencia de ciclos y conectividad. Está en revisión al momento de escribir estas diapositivas.

- Cuando el grafo **no** es conexo, los dos avisan: Kruskal devuelve un **bosque** y Prim solo
  cubre la componente del inicio. `ResultadoMST.isConexo()` sale `false` y el menú lo dice en vez
  de presentar un bosque como si fuera un árbol.

---

## El MST no es la ruta más corta

- El MST minimiza la **suma total** de kilómetros para conectar a *todos* los municipios, sin
  importar qué tan lejos quede cada par.
- Una ruta más corta entre dos municipios minimiza el costo **de ese par**, aunque obligue a
  pasar por muchos otros.
- Por eso la ruta más corta entre Santa Marta y Pasto **no es un subcamino del MST**: usa
  conexiones que el MST descartó por caras, pero que en conjunto salían más caras para el total.
- Conclusión: el MST sirve para **diseñar la red mínima**, no para **navegar**. Para navegar
  están la búsqueda voraz, A\* y Dijkstra.

---

## Dónde está en el código

| Clase | Qué aporta |
|---|---|
| `Grafo.Arista`, `Grafo.getAristas()` | Cada conexión una sola vez, que es lo que necesitan los dos algoritmos |
| `UnionFind` | Conjuntos disjuntos: `find`, `union`, `conectados`, `getComponentes` |
| `Kruskal` | `calcular(grafo)`: ordena y acepta sin cerrar ciclos |
| `Prim` | `calcular(grafo)` y `calcular(grafo, inicio)`: crece desde un municipio |
| `ResultadoMST` | Aristas, costo total, consideradas, descartadas y `isConexo()` |
| `MenuConsola` | Opción 3 del menú: Kruskal, Prim o ambos |
| `Experimentos` | `tablaMstMarkdown()` genera `docs/tabla-mst.md` |
| `docs/mapa.html` | Capa activable que dibuja las 19 aristas del MST |

- Las 42 conexiones del HTML coinciden con `data/conexiones.csv`, así que lo que se ve en el
  mapa es el mismo árbol que calcula Java.

---

## Conclusiones

- Kruskal y Prim resuelven el mismo problema y **coinciden**: 19 aristas, 3457,6 km.
- Los dos son voraces, pero sobre cosas distintas: Kruskal elige **aristas**, Prim elige **el
  siguiente municipio**; por eso Kruskal descarta más.
- `UnionFind` es lo que convierte a Kruskal en algo correcto sin comprobar ciclos a mano.
- El desempate por nombre es lo que hace que los resultados sean **reproducibles** y que la
  validación cruzada signifique algo.
- El MST resuelve un problema **distinto** al de las búsquedas, y por eso no reemplaza a ninguna.
