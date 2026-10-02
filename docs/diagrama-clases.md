# Arquitectura del proyecto

Diagrama de clases y de paquetes. Sirve para responder rápido "dónde vive cada cosa" y para
enseñar cómo se relacionan el modelo, los datos, los algoritmos, el análisis y la interfaz.

## Paquetes

```
municipios/
├── modelo/     el grafo: qué es un municipio y cómo se conecta con otros
├── datos/      lectura de los CSV y control de calidad de esos archivos
├── algoritmo/  las búsquedas (avara y A*), la heurística y Dijkstra como referencia
├── analisis/    los experimentos y la tabla de resultados
└── ui/         el menú por consola y el punto de entrada
```

Las dependencias van siempre hacia adentro: `ui` y `analisis` usan `algoritmo`, `algoritmo`
usa `modelo`, y `modelo` no depende de nada. Solo `datos` lee archivos.

## Diagrama de clases

```mermaid
classDiagram
    direction LR

    class Municipio {
        -String nombre
        -double latitud
        -double longitud
        +getNombre() String
        +getLatitud() double
        +getLongitud() double
        +equals(Object) boolean
    }

    class Grafo {
        -Map~Municipio,List~ adyacencias
        -Map~String,Municipio~ porNombre
        +agregarMunicipio(Municipio) void
        +agregarConexion(Municipio, Municipio, double) void
        +getVecinos(Municipio) List~Conexion~
        +getDistancia(Municipio, Municipio) double
        +buscarPorNombre(String) Municipio
        +getMunicipios() List~Municipio~
    }

    class Conexion {
        <<record>>
        +Municipio destino
        +double distancia
    }

    class Heuristica {
        <<interface>>
        +h(Municipio actual, Municipio destino) double
    }

    class DistanciaLineaRecta {
        -double RADIO_TIERRA_KM
        +h(Municipio, Municipio) double
    }

    class ResultadoBusqueda {
        -List~Municipio~ camino
        -double costoTotal
        -int nodosExpandidos
        -boolean encontrado
        +getCamino() List~Municipio~
        +getCostoTotal() double
        +getNodosExpandidos() int
        +isEncontrado() boolean
    }

    class BusquedaAvara {
        -Grafo grafo
        -Heuristica heuristica
        +buscar(Municipio, Municipio) ResultadoBusqueda
        +buscar(String, String) ResultadoBusqueda
    }

    class BusquedaEstrella {
        -Grafo grafo
        -Heuristica heuristica
        +buscar(Municipio, Municipio) ResultadoBusqueda
        +buscar(String, String) ResultadoBusqueda
    }

    class Dijkstra {
        <<utility>>
        +distanciasDesde(Grafo, Municipio) Map
        +caminoMinimo(Grafo, Municipio, Municipio) List
        +costoMinimo(Grafo, Municipio, Municipio) double
        +expansionesHasta(Grafo, Municipio, Municipio) int
    }

    class ValidadorHeuristica {
        -Grafo grafo
        -Heuristica heuristica
        +generarReporte() String
        +validarBasicas() List
        +validarAdmisibilidad() List
        +validarConsistencia() List
    }

    class CargadorCSV {
        <<utility>>
        +cargarGrafo(Path, Path) Grafo
    }

    class ValidadorDatos {
        +validar() List
        +generarReporte() String
        +hayErrores() boolean
    }

    class Experimentos {
        -Grafo grafo
        -Heuristica heuristica
        -int repeticiones
        +ejecutar() List
        +escanearTodosLosPares() ResumenGlobal
        +tablaMarkdown(List) String
        +csv(List) String
        +analisis(List, ResumenGlobal) String
    }

    class MenuConsola {
        -Grafo grafo
        -Algoritmo avara
        -Algoritmo aEstrella
        +MenuConsola(Grafo, Algoritmo, Algoritmo, BufferedReader, PrintStream)
        +ejecutar() void
    }

    class Main {
        +main(String[]) void
    }

    Grafo "1" o-- "*" Municipio : municipios
    Grafo "1" *-- "*" Conexion : vecinos
    Heuristica <|.. DistanciaLineaRecta
    BusquedaAvara --> Grafo : consulta
    BusquedaAvara --> Heuristica : usa
    BusquedaAvara ..> ResultadoBusqueda : devuelve
    BusquedaEstrella --> Grafo : consulta
    BusquedaEstrella --> Heuristica : usa
    BusquedaEstrella ..> ResultadoBusqueda : devuelve
    Dijkstra --> Grafo : consulta
    ValidadorHeuristica --> Grafo : revisa
    ValidadorHeuristica --> Heuristica : evalúa
    ValidadorHeuristica --> Dijkstra : compara contra
    CargadorCSV ..> Grafo : construye
    ValidadorDatos ..> Grafo : no depende: lee los CSV
    Experimentos --> Grafo
    Experimentos --> Heuristica
    Experimentos --> BusquedaAvara
    Experimentos --> BusquedaEstrella
    Experimentos --> Dijkstra
    MenuConsola --> Grafo : lista municipios
    MenuConsola --> BusquedaAvara : ejecuta
    MenuConsola --> BusquedaEstrella : ejecuta
    Main ..> CargadorCSV : carga los datos
    Main ..> MenuConsola : abre el menú
```

## Cómo fluye un dato por el programa

```mermaid
flowchart TD
    A["data/municipios.csv<br/>data/conexiones.csv"] --> B["CargadorCSV.cargarGrafo"]
    B --> C["Grafo<br/>Municipio + Conexion"]
    C --> D["Main / MenuConsola"]
    C --> E["Experimentos"]
    D --> F["BusquedaAvara.buscar"]
    D --> G["BusquedaEstrella.buscar"]
    E --> F
    E --> G
    E --> H["Dijkstra<br/>(referencia)"]
    F --> I["DistanciaLineaRecta.h"]
    G --> I
    E --> J["docs/tabla-resultados.md<br/>docs/resultados-experimentos.csv<br/>docs/analisis-resultados.md"]
    A --> K["ValidadorDatos<br/>29 verificaciones"]
    C --> L["ValidadorHeuristica<br/>admisible y consistente"]
    H --> L
```

1. `CargadorCSV` lee los dos CSV y construye el `Grafo`. Falla con un mensaje que dice el
   archivo y la línea si algo está mal.
2. `Main` crea la heurística (`DistanciaLineaRecta`), las dos búsquedas y el `MenuConsola`.
3. El menú pide origen y destino y llama a `buscar(origen, destino)` del algoritmo elegido.
4. Ambas búsquedas devuelven un `ResultadoBusqueda` con el camino, el costo, los municipios
   expandidos y si se encontró algo.
5. `Experimentos` hace lo mismo pero sobre una lista fija de pares y escribe los resultados.

## Puntos de diseño que conviene conocer

- **`Heuristica` es una interfaz, no una clase.** `BusquedaAvara` y `BusquedaEstrella` reciben
  una heurística cualquiera. Eso es lo que permite probar los algoritmos con valores de `h`
  inventados en un grafo de juguete, sin depender de coordenadas reales, y lo que permite
  comprobar qué pasa cuando la heurística *no* es admisible.
- **`Grafo` guarda las conexiones en los dos sentidos** al agregarlas. Ningún algoritmo tiene
  que comprobar si una arista es bidireccional, y `ValidadorDatos` verifica que el CSV no traiga la
  misma conexión dos veces.
- **`Dijkstra` es código de apoyo, no parte de la solución.** El menú nunca lo ofrece. Existe
  para dos cosas: comprobar que la heurística es admisible (issue #6) y decir cuál es el
  verdadero camino más corto, que es contra lo que se mide a A* y a la búsqueda avara
  (issues #9 y #15).
- **`ResultadoBusqueda` es inmutable y lo devuelven las dos búsquedas igual**, así que el menú
  y los experimentos pueden tratar los dos resultados de la misma manera.
- **`MenuConsola` recibe la entrada y la salida por parámetro.** Es lo que permite probarlo sin
  teclado: las pruebas le pasan un `StringReader` y capturan lo que escribe.