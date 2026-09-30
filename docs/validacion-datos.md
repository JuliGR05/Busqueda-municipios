# Validación de los datos (issue #4)

Este documento explica qué hace el validador de los CSV, cómo se usa, qué verifica exactamente y cómo se comprobó que funciona.

## 1. Para qué sirve

Toda la búsqueda (voraz, A*, interfaz) trabaja sobre `data/municipios.csv` y `data/conexiones.csv`. Si esos archivos tienen un nombre mal escrito, una distancia negativa o un municipio sin conexiones, los resultados serían incorrectos y el error sería difícil de rastrear. El validador revisa los CSV **antes** de usarlos y dice con precisión qué está mal y en qué línea.

Solo **reporta**: nunca modifica los archivos. Si hay errores, se corrigen en la hoja compartida y se vuelve a exportar el CSV.

## 2. Las dos clases

| Clase | Ubicación | Responsabilidad |
|---|---|---|
| `ValidadorDatos` | `src/main/java/municipios/datos/` | Lee los dos CSV, ejecuta las 29 verificaciones y genera el reporte. Es independiente de `CargadorCSV` y de `Grafo`: tiene su propia lectura para poder revisar archivos que el cargador rechazaría. |
| `PruebasValidadorDatos` | `test/municipios/datos/` | Comprueba que el validador funciona: daña los CSV reales de muchas formas (un tipo de error por caso) y verifica que la verificación correspondiente lo detecte. |

## 3. Cómo se usa

Desde la raíz del repositorio.

**Git Bash / Linux / Mac**
```bash
mkdir -p out
javac -encoding UTF-8 -d out $(find src/main/java test -name "*.java")
java -cp out municipios.datos.ValidadorDatos
java -cp out municipios.datos.PruebasValidadorDatos
```

**PowerShell (Windows)**
```powershell
mkdir out
javac -encoding UTF-8 -d out (Get-ChildItem -Recurse src\main\java,test -Filter *.java).FullName
java -cp out municipios.datos.ValidadorDatos
java -cp out municipios.datos.PruebasValidadorDatos
```

- Para validar otros archivos: `java -cp out municipios.datos.ValidadorDatos ruta/municipios.csv ruta/conexiones.csv`
- Para conservar los CSV dañados de las pruebas y poder mirarlos: `java -cp out municipios.datos.PruebasValidadorDatos --conservar` (quedan en `target/datos-danados/`).
- Si las tildes salen mal en PowerShell, ejecutar `chcp 65001` antes o usar Git Bash.

## 4. Cómo leer el reporte

El programa **siempre imprime el reporte completo**: la lista de las 29 verificaciones con su estado y, si hay problemas, el detalle agrupado por categoría.

| Estado | Significado |
|---|---|
| `[OK]` | La verificación se hizo y no encontró nada. Se muestra un dato de lo revisado (por ejemplo, "50 referencias a municipios revisadas"). |
| `[AVISO]` | Encontró algo sospechoso pero no incorrecto. No hace fallar el resultado. |
| `[FALLA]` | Encontró al menos un error que hay que corregir. |
| `[OMITIDA]` | No se pudo ejecutar porque depende de algo que falló antes (por ejemplo, no se pudo leer un archivo). Se indica el motivo. |

Cada problema del detalle indica el archivo y la línea (`[conexiones.csv:27]`); la línea 1 es la cabecera.

**Código de salida.** Además del reporte, al terminar el programa devuelve 0 si no hubo errores y 1 si hubo alguno (los avisos no cuentan). No sustituye al reporte: sirve para que un script o una automatización sepa si los datos son válidos sin tener que leer el texto.

### Ejemplo con los datos actuales

```
VERIFICACIONES REALIZADAS

 municipios.csv
  [OK]       M1   El archivo existe, se puede leer y no está vacío - 21 línea(s), incluida la cabecera
  [OK]       M12  Están los 20 municipios exactos definidos en el README - 20 de 20 municipios del README, ni más ni menos
  ...
 grafo (municipios + conexiones)
  [OK]       G2   Grafo conexo: existe camino entre cualquier par de municipios - una sola componente con los 20 municipios

RESUMEN: 29 verificaciones -> 29 OK, 0 con avisos, 0 con fallas, 0 omitidas
RESULTADO: OK - pasaron las 29 verificaciones
```

## 5. Verificaciones

Los identificadores empiezan por **M** (municipios.csv), **C** (conexiones.csv) o **G** (grafo, usando ambos archivos).

### 5.1 municipios.csv

| Id | Qué comprueba | Cuándo falla | Gravedad |
|---|---|---|---|
| M1 | El archivo existe, se puede leer y no está vacío. | No existe, es una carpeta, no se puede leer o está vacío. | Error |
| M2 | Codificación UTF-8 válida y sin texto dañado. | Bytes que no son UTF-8 (típico de Excel en Windows-1252), el carácter `�` o secuencias como `Ã¡` (UTF-8 leído como Latin-1). El BOM (marca al inicio) es solo aviso. | Error / Aviso |
| M3 | La cabecera es exactamente `nombre,departamento,zona,latitud,longitud,fuente`, con coma como separador. | Columnas distintas, en otro orden, o separador `;`. | Error |
| M4 | Cada fila tiene tantas columnas como la cabecera y las comillas están bien cerradas. | Faltan o sobran columnas (por ejemplo, una coma decimal sin comillas), comillas sin cerrar. Las líneas vacías son aviso. | Error / Aviso |
| M5 | Ninguna celda vacía (incluida `fuente`). | Cualquier celda vacía o solo con espacios. | Error |
| M6 | Nombre, departamento y zona escritos de forma limpia. | Espacios al inicio o al final, espacios dobles, espacios no estándar (como el de no separación) o tildes descompuestas (letra + acento separados, que se ven iguales pero no lo son). Caracteres inusuales en el nombre son aviso. | Error / Aviso |
| M7 | Sin municipios duplicados. | El mismo nombre dos veces, o el mismo nombre escrito distinto en mayúsculas o tildes. | Error |
| M8 | Latitud y longitud son números. | Texto o unidades (por ejemplo `-75.5km`). | Error |
| M9 | Decimales consistentes en todo el archivo. | Coma decimal (el README exige punto), incluso si se mezclan comas y puntos. Distinta cantidad de decimales dentro de una columna es aviso. | Error / Aviso |
| M10 | Coordenadas dentro del rango de Colombia (latitud de -4.3 a 13.5, longitud de -82.0 a -66.8). | Fuera de ese rango, longitud positiva (Colombia está al oeste) o latitud y longitud invertidas. El rectángulo es aproximado. | Error |
| M11 | Ningún par de municipios con exactamente las mismas coordenadas. | Dos filas con las mismas coordenadas. | Aviso |
| M12 | Están los 20 municipios exactos del README. | Falta uno, sobra uno, alguno está escrito distinto al README o el total no es 20. | Error |
| M13 | Departamento y zona de cada municipio coinciden con la tabla del README. | Un municipio con otro departamento u otra zona. | Aviso |

### 5.2 conexiones.csv

| Id | Qué comprueba | Cuándo falla | Gravedad |
|---|---|---|---|
| C1 a C6 | Lo mismo que M1 a M6, aplicado a este archivo. La cabecera esperada es `municipio1,municipio2,km,fuente` y en C6 se revisan las columnas de municipios. | Igual que M1 a M6. | Error / Aviso |
| C7 | Cada nombre de `municipio1` y `municipio2` existe, escrito **idéntico**, en `municipios.csv`. | Nombre inexistente, con otra mayúscula, sin tilde o con tilde descompuesta. El mensaje sugiere el nombre parecido. | Error |
| C8 | `km` es un número. | Texto o unidades (por ejemplo `45.5km`). | Error |
| C9 | `km` mayor que 0. | Distancia cero o negativa. | Error |
| C10 | Decimales consistentes en `km`. | Igual que M9. | Error / Aviso |
| C11 | Ninguna conexión de un municipio consigo mismo. | `A,A,...`. | Error |
| C12 | Cada conexión aparece una sola vez (regla del README). | La misma conexión repetida o escrita también al revés (`A,B` y `B,A`). | Error |
| C13 | Distancias simétricas: A→B = B→A. | Una conexión repetida o invertida con una distancia distinta. Como el README escribe cada conexión una sola vez y se lee en ambos sentidos, la simetría se cumple por construcción; esta verificación detecta cuando se rompe. | Error |

### 5.3 Grafo (ambos archivos)

| Id | Qué comprueba | Cuándo falla | Gravedad |
|---|---|---|---|
| G1 | Sin municipios aislados. | Un municipio sin ninguna conexión. | Error |
| G2 | Grafo conexo: existe camino entre cualquier par de municipios. | Hay grupos sin camino entre sí. El reporte lista cada componente con sus municipios. | Error |
| G3 | Los km por carretera son mayores o iguales que la distancia en línea recta (Haversine, radio 6371 km). | Una conexión con menos km que la línea recta: es imposible y volvería inadmisible la heurística. El detalle muestra la relación km/recta mínima y máxima. | Error |

Además, el reporte incluye una sección **Datos de interés** (no es una verificación): lista los municipios con una sola conexión ("callejones sin salida"), que son los casos donde la búsqueda avara puede quedar atrapada.

## 6. Pruebas

`PruebasValidadorDatos` toma los CSV reales de `data/`, aplica **un solo daño por caso** y comprueba que la verificación esperada cambie a `FALLA` (o `AVISO`). Total: **65 chequeos**, que se dividen así:

| Grupo | Chequeos | Qué prueba |
|---|---|---|
| Datos reales | 1 | Los CSV de `data/` pasan las 29 verificaciones sin errores. |
| CSV dañados a propósito | 59 | Un tipo de error por caso (detalle abajo). |
| Formatos válidos | 2 | Saltos de línea de Windows (CRLF) y archivo sin salto de línea final no deben dar ningún problema. |
| Dependencias | 1 | Sin `municipios.csv`, las verificaciones que dependen de él quedan `OMITIDA`. |
| Programa completo | 2 | El programa devuelve código 0 con datos correctos y 1 con datos dañados. |

Los 59 casos dañados por verificación:

| Verificación | Casos | Daños que se prueban |
|---|---|---|
| M1 | 2 | archivo inexistente; archivo vacío |
| M2 | 4 | Latin-1; texto dañado (`Ã¡`); carácter de reemplazo; BOM (aviso) |
| M3 | 2 | cabecera distinta; separador `;` |
| M4 | 4 | fila con columnas de menos; de más; comillas sin cerrar; línea vacía (aviso) |
| M5 | 2 | departamento vacío; latitud vacía |
| M6 | 5 | espacio al final; al inicio; espacios dobles; espacio de no separación; tilde descompuesta |
| M7 | 2 | municipio duplicado; mismo municipio en mayúsculas |
| M8 | 2 | latitud no numérica; longitud con unidad |
| M9 | 2 | coma decimal; distinta cantidad de decimales (aviso) |
| M10 | 3 | latitud fuera de Colombia; longitud positiva; latitud y longitud invertidas |
| M11 | 1 | coordenadas repetidas (aviso) |
| M12 | 3 | falta un municipio; municipio de más; nombre distinto del README |
| M13 | 2 | departamento distinto; zona distinta (avisos) |
| C1 | 2 | archivo inexistente; archivo vacío |
| C2 | 1 | Latin-1 |
| C3 | 1 | cabecera distinta |
| C4 | 2 | fila con columnas de menos; de más |
| C5 | 2 | km vacío; municipio vacío |
| C6 | 1 | espacio al final del nombre |
| C7 | 3 | nombre en mayúsculas; sin tilde; municipio inexistente |
| C8 | 2 | km no numérico; km con unidad |
| C9 | 2 | km cero; km negativo |
| C10 | 2 | coma decimal; distinta cantidad de decimales (aviso) |
| C11 | 1 | conexión consigo mismo |
| C12 | 2 | conexión repetida; conexión invertida |
| C13 | 1 | distancia no simétrica |
| G1 y G2 | 1 | municipio aislado (falla G1 y también G2) |
| G2 | 1 | grafo con dos componentes sin municipios aislados |
| G3 | 1 | km menor que la línea recta |

Las pruebas se comprobaron también en sentido contrario: al desactivar a propósito tres verificaciones del validador, las pruebas correspondientes fallaron. Si se agregan casos, el total real lo imprime `PruebasValidadorDatos` al terminar.

## 7. Qué no verifica

- **Que los km sean los correctos.** Solo comprueba que sean números positivos y coherentes con la línea recta.
- **Que las coordenadas sean las reales de cada municipio.** Comprueba que estén dentro de Colombia y sean coherentes con los km, pero no las contrasta con la fuente.
- **La hoja compartida ni el `.xlsx` de `docs/`.** Solo lee los CSV. Comparar la hoja con los CSV hay que hacerlo aparte 
- **El rectángulo de Colombia** (M10) es aproximado: sirve para detectar signos o valores invertidos, no para validar la ubicación exacta.
- **Los 20 municipios** (M12 y M13) están escritos dentro de `ValidadorDatos` tomados del README. Si el grupo cambia la lista, hay que actualizarla allí.
