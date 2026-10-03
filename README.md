# Búsqueda voraz y A\* entre 20 municipios de Colombia

Proyecto en equipo que modela 20 municipios de Colombia como un grafo e implementa dos algoritmos
de búsqueda informada para encontrar la ruta entre un municipio de origen y uno de destino: la
**búsqueda voraz (greedy best-first)** y **A\***. Ambos usan como heurística la **distancia en
línea recta**, calculada con Haversine, y sus resultados se comparan entre sí y contra **Dijkstra**,
que se usa como referencia del camino realmente más corto.

---

## Tabla de contenido

1. [Descripción y objetivo](#1-descripción-y-objetivo)
2. [Cómo funciona](#2-cómo-funciona)
3. [Municipios seleccionados](#3-municipios-seleccionados)
4. [Requisitos](#4-requisitos)
5. [Cómo compilar, probar y ejecutar](#5-cómo-compilar-probar-y-ejecutar)
6. [Estructura del repositorio](#6-estructura-del-repositorio)
7. [Formato de los datos](#7-formato-de-los-datos)
8. [Herramientas de línea de comandos](#8-herramientas-de-línea-de-comandos)
9. [Documentos del proyecto](#9-documentos-del-proyecto)
10. [Equipo y división del trabajo](#10-equipo-y-división-del-trabajo)
11. [Flujo de ramas y trabajo en Git](#11-flujo-de-ramas-y-trabajo-en-git)
12. [Convenciones](#12-convenciones)
13. [Fuentes de los datos](#13-fuentes-de-los-datos)
14. [Estado del proyecto](#14-estado-del-proyecto)

---

## 1. Descripción y objetivo

Dados un municipio de origen **A** y uno de destino **B**, el programa debe:

- encontrar una ruta de A a B con búsqueda voraz;
- encontrar una ruta de A a B con A\*;
- mostrar la ruta, los kilómetros recorridos y los municipios expandidos;
- comparar ambos algoritmos lado a lado, para analizar cuándo la búsqueda voraz no encuentra la
  mejor ruta y A\* sí.

## 2. Cómo funciona

- **Grafo:** cada municipio es un nodo con nombre, latitud y longitud. Cada arista une dos
  municipios unidos por una carretera principal y guarda los kilómetros por carretera. Cada
  conexión se recorre en los dos sentidos.
- **Heurística h(n):** distancia en línea recta desde el nodo n hasta el destino, con la fórmula
  de Haversine y un radio terrestre de 6371 km. No se anota a mano: sale de las coordenadas que
  ya están en el CSV.
- **Búsqueda voraz:** en cada paso expande el nodo de la frontera con menor `h(n)`. Ignora lo ya
  recorrido. Lleva un conjunto de visitados para evitar ciclos y el padre de cada nodo para
  reconstruir la ruta.
- **A\*:** en cada paso expande el nodo con menor `f(n) = g(n) + h(n)`, donde `g(n)` son los
  kilómetros por carretera ya recorridos y `h(n)` la estimación de lo que falta. Guarda el mejor
  `g` conocido por municipio, así que si aparece un camino más barato hacia un municipio ya
  visitado lo actualiza y lo vuelve a meter en la frontera.
- **Por qué h(n) es admisible:** ningún camino por carretera puede ser más corto que la línea
  recta entre sus extremos, así que h nunca sobrestima. Con eso, más el hecho de que Haversine
  cumple la desigualdad triangular (y por tanto h es consistente), A\* es óptimo.
- **Limitación de la búsqueda voraz:** no garantiza la ruta óptima. En estos datos devuelve un
  camino que no es el más corto en 111 de los 380 pares posibles, con sobrecostos de hasta 162 %.
  Es justo lo que se quiere mostrar al compararla con A\*.

## 3. Municipios seleccionados

| Zona | Municipios |
|---|---|
| Caribe | Barranquilla (Atlántico), Cartagena (Bolívar), Santa Marta (Magdalena), Valledupar (Cesar), Montería (Córdoba) |
| Occidente y Eje Cafetero | Medellín (Antioquia), Manizales (Caldas), Pereira (Risaralda), Armenia (Quindío), Cali (Valle del Cauca) |
| Centro y Nororiente | Soacha (Cundinamarca), Tunja (Boyacá), Bucaramanga (Santander), Cúcuta (Norte de Santander), Barrancabermeja (Santander) |
| Sur y Llanos | Villavicencio (Meta), Ibagué (Tolima), Neiva (Huila), Popayán (Cauca), Pasto (Nariño) |

## 4. Requisitos

- **JDK 17 o superior.** Comprueba con `java -version`.
- **Apache Maven 3.8 o superior**, para compilar y correr las pruebas. Comprueba con `mvn -version`.

No hace falta instalar nada más: el proyecto no usa dependencias de ejecución, solo JUnit 5 para
las pruebas.

## 5. Cómo compilar, probar y ejecutar

Todos los comandos se ejecutan **desde la raíz del repositorio** (donde está el `pom.xml`). El
programa lee los CSV de la carpeta `data/` con rutas relativas, así que hay que lanzarlo desde ahí.

### Compilar

```bash
mvn -q compile
```

### Correr las pruebas

```bash
mvn test
```

Son **171 pruebas**. Para ver el detalle de una sola clase:

```bash
mvn test -Dtest=BusquedaEstrellaTest
```

### Ejecutar el programa

```bash
java -cp target/classes municipios.ui.Main
```

Abre un menú por consola que lista los 20 municipios, pide origen y destino (por número o por
nombre, sin importar mayúsculas ni tildes) y deja hacer consultas seguidas hasta elegir salir.
Se puede elegir búsqueda voraz, A\* o **ambos** para compararlas lado a lado.

Para usar otros datos, se indica la carpeta que contiene `municipios.csv` y `conexiones.csv`:

```bash
java -cp target/classes municipios.ui.Main otra/carpeta
```

### Compilar sin Maven

Si no se tiene Maven, `javac` también sirve para compilar y ejecutar, aunque **no** para correr
las pruebas:

```bash
# Linux, macOS o Git Bash
mkdir -p out
javac -encoding UTF-8 -d out $(find src/main/java -name "*.java")
java -cp out municipios.ui.Main
```

```powershell
# PowerShell en Windows
New-Item -ItemType Directory -Force out | Out-Null
javac -encoding UTF-8 -d out (Get-ChildItem -Recurse src\main\java -Filter *.java).FullName
java -cp out municipios.ui.Main
```

## 6. Estructura del repositorio

```
.
├── README.md
├── pom.xml
├── data/                          los dos CSV de la actividad
│   ├── municipios.csv
│   └── conexiones.csv
├── docs/
│   ├── informe.md                 el informe de la actividad
│   ├── diagrama-clases.md         diagrama de clases y arquitectura
│   ├── tabla-resultados.md        tabla comparativa (generada)
│   ├── analisis-resultados.md     análisis de resultados (generado)
│   ├── resultados-experimentos.csv datos completos (generado)
│   ├── validacion-datos.md        qué comprueba el validador de datos
│   └── hoja-municipios-busqueda-voraz.xlsx
└── src/
    ├── main/java/municipios/
    │   ├── modelo/                Municipio, Grafo
    │   ├── datos/                 CargadorCSV, ValidadorDatos
    │   ├── algoritmo/             Heuristica, DistanciaLineaRecta,
    │   │                          BusquedaAvara, BusquedaEstrella,
    │   │                          Dijkstra, ResultadoBusqueda,
    │   │                          ValidadorHeuristica
    │   ├── analisis/              Experimentos (genera la tabla de resultados)
    │   └── ui/                    Main, MenuConsola
    └── test/
        ├── java/municipios/       171 pruebas con JUnit 5
        │   └── DatosReales.java   helper para cargar los CSV reales en las pruebas
        └── resources/csv/         CSV de prueba que se cargan desde el classpath
```

El detalle de cada clase está en [`docs/diagrama-clases.md`](docs/diagrama-clases.md).

## 7. Formato de los datos

**`data/municipios.csv`**

| Columna | Descripción |
|---|---|
| `nombre` | Nombre del municipio |
| `departamento` | Departamento |
| `zona` | Zona a la que pertenece en la lista |
| `latitud` | Grados decimales (norte positivo) |
| `longitud` | Grados decimales (oeste negativo) |
| `fuente` | De dónde se sacaron las coordenadas |

```
Santa Marta,Magdalena,Caribe,11.2472,-74.2017,Alcaldía Distrital de Santa Marta
```

**`data/conexiones.csv`**

| Columna | Descripción |
|---|---|
| `municipio1` | Un extremo de la conexión |
| `municipio2` | El otro extremo |
| `km` | Kilómetros por carretera |
| `fuente` | Origen del dato |

```
Santa Marta,Barranquilla,106.0,Google Maps
```

**Convenciones:** separador coma, **punto** como decimal, codificación UTF-8, y cada conexión
aparece **una sola vez** (el grafo la registra en los dos sentidos). El cargador también acepta
`;` como separador y coma decimal, pero se recomienda mantener el formato de los archivos
actuales.

## 8. Herramientas de línea de comandos

Todas se ejecutan desde la raíz del repositorio, después de `mvn -q compile`.

| Qué hace | Comando |
|---|---|
| Valida los CSV (29 verificaciones) | `java -cp target/classes municipios.datos.ValidadorDatos` |
| Valida otros CSV | `java -cp target/classes municipios.datos.ValidadorDatos ruta/municipios.csv ruta/conexiones.csv` |
| Reporte de la heurística (admisible y consistente) | `java -cp target/classes municipios.algoritmo.ValidadorHeuristica` |
| Regenera la tabla y el análisis de resultados | `java -cp target/classes municipios.analisis.Experimentos` |

`ValidadorDatos` termina con código 0 si no hay errores y con 1 si hay alguno, así que sirve en
integración continua. `Experimentos` escribe `docs/tabla-resultados.md`,
`docs/resultados-experimentos.csv` y `docs/analisis-resultados.md`; los tres son generados y no
deben editarse a mano.

## 9. Documentos del proyecto

| Documento | Qué contiene |
|---|---|
| [`docs/informe.md`](docs/informe.md) | El informe de la actividad: problema, algoritmos, heurística, datos, resultados y conclusiones |
| [`docs/diagrama-clases.md`](docs/diagrama-clases.md) | Diagrama de clases y de paquetes, y el flujo de los datos |
| [`docs/tabla-resultados.md`](docs/tabla-resultados.md) | Tabla comparativa voraz / A\* / Dijkstra (generada) |
| [`docs/analisis-resultados.md`](docs/analisis-resultados.md) | Análisis de los resultados, con los casos donde la voraz falla (generado) |
| [`docs/validacion-datos.md`](docs/validacion-datos.md) | Detalle de las 29 verificaciones del validador |

## 10. Equipo y división del trabajo

**Parte común:** cada integrante escoge 5 municipios, consigue sus coordenadas y las conexiones
con sus vecinos directos (con los km por carretera) y los sube a la hoja compartida. Después
redacta su sección del informe.

| Rol | Responsabilidades | Rama |
|---|---|---|
| **Datos y distancias** | Consolidar las hojas en los CSV, verificar que el grafo quede conectado, implementar Haversine | `feature/4-validador-datos` |
| **Estructura y datos** | Clases `Municipio` y `Grafo`, lector de CSV, validador de datos | `feature/3-cargador-csv` |
| **Búsqueda voraz** | `BusquedaAvara` con frontera, visitados y casos límite; pseudocódigo | `feature/5-busqueda-avara` |
| **Interfaz** | Menú por consola con `Main` y `MenuConsola` | `feature/7-interfaz-consola` |
| **Heurística y Dijkstra** | `ValidadorHeuristica`, `Dijkstra` como referencia | `feature/6-heuristica` |
| **A\*** | `BusquedaEstrella` con `f(n)`, reaperción y desempate determinista | `feature/6-heuristica` |
| **Pruebas** | Suite de JUnit y CSV de prueba | `feature/8-pruebas` |
| **Experimentos** | Tabla comparativa y análisis de resultados | `feature/9-experimentos` |
| **Informe y cierre** | README, informe, diagrama, integración final | `feature/10-documentacion` |

**Quién necesita qué de quién**

| De | Para | Qué |
|---|---|---|
| Todos | Datos | Coordenadas y conexiones de sus 5 municipios |
| Datos | Estructura | Archivos CSV y su formato |
| Datos | Búsqueda voraz y A\* | Método de Haversine |
| Estructura | Búsqueda voraz y A\* | Clases `Municipio` y `Grafo` |
| Búsqueda voraz | Estructura | Clase de búsqueda para conectar al menú |
| Todos | A\* | Su sección del informe |

## 11. Flujo de ramas y trabajo en Git

- **`main`:** versión estable. Nadie hace push directo a `main`.
- **Una rama por responsabilidad**, creada desde `main`, con el nombre
  `feature/<número-del-issue>-<qué-hace>`.
- Cuando una parte está lista, se abre un **Pull Request** hacia `main` y otro integrante lo
  revisa antes de mezclarlo.

```bash
git checkout main
git pull                                       # traer lo último antes de empezar
git checkout -b feature/9-experimentos
# ...programar...
git add .
git commit -m "Agrega tabla comparativa de voraz, A* y Dijkstra"
git push -u origin feature/9-experimentos
# en GitHub: abrir Pull Request hacia main y pedir revisión
```

Después de mezclar, se actualiza la rama local con `git pull` en `main`.

**Commits y PRs.** Los commits van en español y en imperativo, pequeños y frecuentes, y cerrando
el issue que resuelven cuando lo cierran del todo (`Closes #9: ...`). Cada PR lleva al menos una
revisión de otro integrante.

## 12. Convenciones

- **Commits:** en español, en imperativo ("Agrega búsqueda voraz", no "Agregada búsqueda voraz").
- **Java:** clases en `PascalCase`, métodos y variables en `camelCase`, constantes en
  `MAYUSCULAS`, y Javadoc en las clases y métodos públicos.
- **Texto:** siempre en español, con tildes. Los archivos van en UTF-8.
- **Números:** `Locale.US` en todo `String.format` o `printf`, para que el punto decimal sea
  siempre el mismo y las tablas se puedan comparar.
- **Distancias:** siempre en kilómetros.
- **Firma de las búsquedas:** `ResultadoBusqueda buscar(Municipio origen, Municipio destino)`, más
  una sobrecarga `buscar(String, String)` que resuelve los nombres sin tildes ni mayúsculas.
- **Archivos generados** (`target/`, archivos de IDE) no se suben; están en `.gitignore`.

## 13. Fuentes de los datos

- **Coordenadas:** páginas oficiales de alcaldías y gobernaciones; si no había, Google Maps,
  MapCarta, Wikipedia o geodatos.net. Cada fila de `municipios.csv` indica su fuente.
- **Kilómetros por carretera:** Google Maps, ruta principal entre los dos municipios.

## 14. Estado del proyecto

- [x] Los 20 municipios tienen coordenadas y conexiones en la hoja compartida
- [x] CSV consolidados en `data/`
- [x] Calidad de los datos validada (29 verificaciones, 0 errores)
- [x] Modelo del grafo y lector de datos
- [x] Haversine y validación de admisibilidad y consistencia
- [x] Búsqueda voraz
- [x] A\*
- [x] Interfaz de consola con las dos búsquedas
- [x] Pruebas unitarias (171, en verde con `mvn test`)
- [x] Tabla comparativa y análisis de resultados
- [x] Informe y diagrama de clases
- [ ] Presentación

## 15.Visualización de rutas

El proyecto cuenta con un mapa interactivo desarrollado en HTML para visualizar
gráficamente las rutas encontradas por los algoritmos de búsqueda voraz y A*.

El mapa se encuentra en:

`docs/mapa-rutas.html`

### Tecnologías utilizadas

- **HTML, CSS y JavaScript:** estructura, estilos e interacción de la página.
- **Leaflet:** biblioteca utilizada para construir el mapa interactivo.
- **OpenStreetMap:** fuente de los datos cartográficos utilizados como mapa base.
- **Haversine:** cálculo de la distancia geográfica utilizada como heurística.
- **Búsqueda voraz:** utiliza `h(n)` para seleccionar el siguiente municipio.
- **A\*:** utiliza `f(n) = g(n) + h(n)`, considerando el costo acumulado y la heurística.

### Funcionalidades

El mapa permite:

- Seleccionar un municipio de origen y uno de destino.
- Comparar la ruta encontrada mediante búsqueda voraz y A*.
- Visualizar las conexiones entre los municipios del grafo.
- Mostrar las rutas encontradas directamente sobre el mapa.
- Consultar el costo total de cada ruta en kilómetros.
- Visualizar la cantidad de nodos expandidos por cada algoritmo.
- Mostrar los municipios que forman parte de cada camino.
- Consultar información del proceso de decisión de cada algoritmo, incluyendo
  `h(n)`, `g(n)` y `f(n)` según corresponda.

La búsqueda voraz selecciona los municipios utilizando únicamente la heurística:

`h(n) = distancia Haversine desde el municipio actual hasta el destino`

Mientras que A* utiliza:

`f(n) = g(n) + h(n)`

donde `g(n)` representa el costo acumulado del camino recorrido y `h(n)` la
distancia Haversine estimada hasta el destino.

### Acceso al mapa

Para visualizar el mapa, abrir el siguiente archivo:

[Mapa interactivo de rutas](./docs/mapa-rutas.html)