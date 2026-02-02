# Procesador de Colecciones XML (Java Swing)

Aplicación de escritorio para cargar una colección descrita en XML desde un `.zip`, elegir una transformación XSLT y previsualizar el resultado. Muestra una vista en árbol de la colección (objetos, recursos y relaciones) y un panel de texto con el XML (original o transformado).



## Compilar y ejecutar (Windows/PowerShell)

1) Compilar a la carpeta `out`:
```
mkdir -Force out
javac -cp "lib\xalan-2.7.3.jar;lib\serializer-2.7.3.jar" -d out src\com\example\simpleapp\*.java
```

2) Ejecutar:
```
java -cp "out;lib\xalan-2.7.3.jar;lib\serializer-2.7.3.jar" com.example.simpleapp.Main
```


## Uso
- Botón "Elegir ZIP": selecciona un `.zip` con la colección. La app extrae temporalmente el ZIP y busca el primer `.xml` como descripción de la colección. Se muestra la estructura en el árbol y el XML en el panel derecho.
- Botón "Elegir XSLT": selecciona una hoja de transformación `.xsl`/`.xslt`.
- Botón "Transformar": aplica la transformación XSLT al XML cargado y muestra el resultado en el panel derecho.
- Boton "Exportar": guarda en disco el ultimo resultado transformado (por ejemplo, el HTML generado por una XSLT) y, si eliges ZIP, incluye las imagenes convertidas y la carpeta de recursos para poder verificarlas.
- Botón "Limpiar": reinicia el estado y limpia la carpeta temporal utilizada.
- Vista detallada: tras cargar la colección, haz clic en cualquier nodo del árbol para ver su XML exacto en el panel de detalle inferior.

## Resolucion centralizada de clases de extension
- La transformacion analiza la hoja XSLT y resuelve todas las clases Java declaradas en namespaces de extension (`java:` o `xalan://`) desde un unico servicio. Esto deja una lista ordenada de clases resueltas para depurar o delegar comportamientos en el futuro.

## Conversion de imagenes (ImageMagick)
- El elemento de extension `app:changeImageFormat` usa la CLI de ImageMagick (`magick` en Windows, `magick`/`convert` en Linux/macOS).
- Instala ImageMagick y asegura que el comando este en el `PATH` antes de aplicar XSLT que conviertan formatos de imagen.
- El ejemplo `samples/collection_stats.xsl` convierte recursos `<lr name="imagen">` al formato indicado por el parametro `target-image-format`.

## Validacion XSD
- El esquema base esta en `samples/collection.xsd` y define la jerarquia dc/o con metadatos, recursos y relaciones.
- Al cargar un ZIP, el XML encontrado se valida contra este esquema y se muestra el error si no lo cumple.
- Tras transformar, si el resultado mantiene la raiz `dc` se vuelve a validar automaticamente. Si la XSLT genera otro formato (por ejemplo HTML) la validacion se omite y la barra de estado lo indica.

## Formato XML de colección (propuesto)
Raíz `<dc>` con objetos `<o id="...">`. Cada objeto puede incluir:
- metadatos (cualquier subárbol XML que no sea `rs` ni `rels`),
- recursos `<rs>` con `<lr name="...">ruta local</lr>` o `<url name="...">https://...</url>`,
- relaciones `<rels>` con `<rel ref="ID_OBJETO" name="..."/>`.

La UI tolera ligeras variaciones (p. ej., si en `rel` viniera `id` en lugar de `ref`).

## Ejemplos incluidos
- `samples/identity.xsl:1` - Transformacion identidad (no cambia el XML).
- `samples/collection_to_html.xsl:1` - Convierte la coleccion en HTML con metadatos, recursos y relaciones.
- `samples/collection_stats.xsl:1` - Mantiene el XML, añade un nodo `<stats>` con totales y demuestra el elemento de extensión `<app:saludo/>`.
- `samples/collection.xsd:1` - Esquema XSD utilizado para validar las colecciones.
- `samples/collection_example/collection.xml:1` - Coleccion de ejemplo (libro y autor) con un recurso local y URLs.

Para crear un ZIP de ejemplo listo para cargar:
```
pwsh scripts\make-sample-zip.ps1
```
Esto genera `samples\collection.zip`.

## Código relevante
- `src/com/example/simpleapp/Main.java:1` — UI: carga ZIP, selección XSLT, árbol y previsualización.
- `src/com/example/simpleapp/ZipUtils.java:1` — Extracción segura de ZIP.
- `src/com/example/simpleapp/XmlUtils.java:1` — Parseo DOM, pretty print y transformación XSLT (JAXP).

## Proximos pasos sugeridos
- Soportar variantes de esquema (p. ej. Relax NG o multiples XSD segun version).
- Gestion de multiples archivos XML dentro del ZIP y eleccion explicita.
- Exportar resultados en otros formatos (PDF, Markdown, etc.) o con plantillas predefinidas.
- Extender el procesamiento para tratar contenidos/recursos ademas de la estructura.
