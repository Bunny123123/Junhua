# Procesador de Colecciones XML (Java Swing)

Aplicación de escritorio para cargar una colección descrita en XML desde un `.zip`, elegir una transformación XSLT y previsualizar el resultado. Muestra una vista en árbol de la colección (objetos, recursos y relaciones) y un panel de texto con el XML (original o transformado).


## Compilar y ejecutar (Windows/PowerShell)

1) Compilar a la carpeta `out`:
```
mkdir -Force out
javac -cp "lib\xalan-2.7.3.jar;lib\serializer-2.7.3.jar" -d out src\simpleapp\*.java
```

2) Ejecutar:
```
java -cp "out;lib\xalan-2.7.3.jar;lib\serializer-2.7.3.jar" simpleapp.Main
```
Atajo (compila y ejecuta limpiando JAVA_TOOL_OPTIONS):
```
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\compile.ps1
```
Opcional (ImageMagick sin tocar PATH, permanente):
```
setx JAVA_TOOL_OPTIONS '-Dxslt.magick="C:\Program Files\ImageMagick-7.1.1-Q16\magick.exe"'
```
Tras reiniciar la consola, el comando de ejecucion no cambia.

## Uso
- Botón "Elegir ZIP": selecciona un `.zip` con la colección. La app extrae temporalmente el ZIP y busca el primer `.xml` como descripción de la colección. Se muestra la estructura en el árbol y el XML en el panel derecho.
- Botón "Elegir XSLT": selecciona una hoja de transformación `.xsl`/`.xslt`.
- Botón "Transformar": aplica la transformación XSLT al XML cargado o al último XML transformado, de modo que se pueden encadenar varias XSLT en pasos sucesivos.
- Boton "Exportar": guarda en disco el ultimo resultado transformado (por ejemplo, el HTML generado por una XSLT).
- Botón "Limpiar": reinicia el estado y limpia la carpeta temporal utilizada.
- Vista detallada: tras cargar la colección o transformar a otro formato XML, haz clic en cualquier nodo del árbol para ver su XML exacto en el panel de detalle inferior.
- El selector recuerda la ultima carpeta usada entre ejecuciones, tanto al abrir colecciones y XSLT como al exportar el resultado transformado.

## Ficha generica y visor externo
- Nuevo paso intermedio: `samples\collection_to_record_collection.xsl`, que convierte una colección `dc/o` en una colección intermedia `recordCollection/record/metadata/resources/relations`.
- Transformacion al formato del visor: `samples\record_collection_to_viewer_record.xsl`, que aplana esa colección intermedia al XML `records/record/field/resource/relation` que consume el visor.
- Atajo directo mantenido: `samples\collection_to_record.xsl`, que sigue permitiendo ir de `dc/o` al formato final del visor en un solo paso.
- Visor externo: `simpleapp.RecordViewerMain`, pensado para abrir la ficha ya transformada en XML o ZIP y mostrar fichas visuales por record, con imagen principal, resumen y listado de recursos.
- La exportacion recomendada desde la app principal para este formato es `records.zip`, que incluye `records.xml` y la carpeta `resources` con las imagenes necesarias.
- El visor recuerda la ultima carpeta usada tanto al abrir como al exportar, y permite reexportar la ficha completa como XML o ZIP.
- Atajo de arranque:
```
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\open-record-viewer.ps1
```
- Tambien puedes pasarle directamente un XML o ZIP transformado:
```
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\open-record-viewer.ps1 ruta\records.xml
# o
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\open-record-viewer.ps1 ruta\records.zip
```

## Coleccion de peliculas de ejemplo
- Se incluye `samples\movie_collection\collection.xml`, una coleccion de peliculas con posters, autores y personajes caricaturizados.
- Los recursos visuales locales estan en `samples\movie_collection\resources\`.
- ZIP listo para la app principal: `samples\movie_collection\collection.zip`.
- Script para regenerar imagenes y ZIP:
```
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\build-movie-collection-sample.ps1
```
- Flujo recomendado:
  1. Cargar `samples\movie_collection\collection.zip` en la app principal.
  2. Transformar con `samples\collection_to_record_collection.xsl`.
  3. Transformar de nuevo con `samples\record_collection_to_viewer_record.xsl`.
  4. Exportar el resultado como `records.zip`.
  5. Abrirlo con `scripts\open-record-viewer.ps1`.

## Coleccion medica de ejemplo
- Se incluye `samples\medical_dataset\collection.xml`, un nuevo caso de estudio medico con estudios radiologicos sinteticos, pacientes anonimizados y hallazgos enlazados.
- Los recursos locales incluyen radiografias ilustradas, heatmaps, informes de texto y una portada del dataset en `samples\medical_dataset\resources\`.
- ZIP listo para la app principal: `samples\medical_dataset\collection.zip`.
- Script para regenerar imagenes, informes y ZIP:
```
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\build-medical-dataset-sample.ps1
```
- Flujo recomendado:
  1. Cargar `samples\medical_dataset\collection.zip` en la app principal.
  2. Transformar con `samples\collection_to_record_collection.xsl`.
  3. Transformar de nuevo con `samples\record_collection_to_viewer_record.xsl`.
  4. Exportar el resultado como `records.zip`.
  5. Abrirlo con `scripts\open-record-viewer.ps1`.
- Nota: todos los pacientes, estudios y hallazgos de esta muestra son sinteticos; no contiene datos clinicos reales.

## Extensiones XSLT (namespace propio + puente Xalan)
- Usa un namespace propio en las hojas: `xmlns:app="http://junhua.com/extensions"` y `extension-element-prefixes="app"`.
- La app preprocesa la XSLT y genera un puente compatible con Xalan.
- Clase puente: `out\xslt-bridge\simpleapp\generated\AppExtensionBridge.java`.
- XSLT reescrita: `out\xslt-bridge\*.xsl`.
- Clase compilada: `out\simpleapp\generated\AppExtensionBridge.class`.

Parametros utiles:
```
-Dxslt.bridge.dir=RUTA                # carpeta donde se guarda la XSLT reescrita y el puente
-Dxslt.bridge.classes.dir=RUTA        # carpeta donde se compilan las clases generadas
-Dxslt.ext.debug=true                 # log de ejecucion de elementos de extension
-Dxslt.plugins.config=RUTA            # ruta al plugins.xml (por defecto plugins/plugins.xml)
-Dxslt.plugins.bundle.dir=RUTA        # carpeta extra con jars de plugins
```

## Plugins por JAR (plugins.xml + bundle)
- En el arranque, `Main` carga `plugins/plugins.xml` y registra dinamicamente handlers de extensiones.
- Cada entrada define la tabla: `elemento XSLT -> clase#metodo`.
- Los `.jar` se pueden cargar de 5 formas:
- `bundleDir` en la raiz `<plugins ...>` (auto-carga todos los `.jar` del directorio).
- elementos `<bundle path="..."/>`.
- atributo `jar` dentro de cada `<plugin .../>`.
- dependencias globales con `<dependencies><dependency .../></dependencies>`.
- dependencias por plugin con atributo `dependencies="a.jar,b.jar"` o nodos `<dependency .../>`.
- Rutas relativas se resuelven respecto a la carpeta donde esta `plugins.xml`.
- Si `jar` es solo nombre de archivo (sin carpeta), se resuelve automaticamente dentro de `bundleDir`.

Ejemplo (`plugins/plugins.xml`):
```xml
<plugins bundleDir="bundle">
  <dependencies>
    <dependency jar="mi-plugin-deps.jar" />
  </dependencies>

  <plugin element="saludo" class="com.ejemplo.plugins.MiPlugin" method="saludo" jar="mi-plugin.jar" />
  <plugin element="changeImageFormat" class="com.ejemplo.plugins.MiPlugin" method="changeImageFormat" jar="mi-plugin.jar" />
  <plugin element="miExtension" class="com.ejemplo.plugins.MiPlugin" method="ejecutar" jar="mi-plugin.jar" />
</plugins>
```

Contrato del metodo plugin:
- Firma esperada: `(XSLProcessorContext, ElemExtensionCall)`.
- Puede ser `static` o de instancia.
- Alternativamente, la clase puede implementar `ExtensionElementHandler` usando el metodo `invoke`.
- Si la clase implementa `ExtensionElementHandler`, el atributo `method` es opcional.

Demo plugin externo:
```
pwsh scripts\build-plugin-example.ps1
```
Genera:
- `plugins\bundle\mi-plugin.jar` (handlers `saludo`, `changeImageFormat`, `miExtension`).
- `plugins\bundle\mi-plugin-deps.jar` (dependencia externa del plugin principal).

Luego puedes transformar con `samples\collection_plugin_demo.xsl`, que invoca `<app:miExtension/>`.

Verificacion end-to-end automatica:
```
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke-test.ps1
```

## Conversion de imagenes
- `app:changeImageFormat` intenta usar ImageMagick (`magick`) si esta instalado.
- Si no hay ImageMagick, se usa un fallback con Java ImageIO (formatos basicos: png/jpg/jpeg/bmp/gif).
- Para forzar ImageMagick sin tocar PATH:
```
setx JAVA_TOOL_OPTIONS '-Dxslt.magick="C:\Program Files\ImageMagick-7.1.1-Q16\magick.exe"'
```

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
- `samples/collection_to_record_collection.xsl:1` - Convierte la coleccion a una coleccion intermedia `recordCollection/record`.
- `samples/record_collection_to_viewer_record.xsl:1` - Convierte la coleccion intermedia al formato final `records/record` del visor.
- `samples/collection_to_record.xsl:1` - Atajo directo desde la coleccion al formato final `records/record`.
- `samples/collection_stats.xsl:1` - Mantiene el XML, a?ade un nodo `<stats>` con totales y demuestra elementos de extension `<app:saludo/>` y `<app:changeImageFormat/>`.
- `samples/collection_plugin_demo.xsl:1` - Demo de plugin externo usando `<app:miExtension/>` cargado desde JAR.
- `samples/collection.xsd:1` - Esquema XSD utilizado para validar las colecciones.
- `samples/collection_example/collection.xml:1` - Coleccion de ejemplo (libro y autor) con un recurso local y URLs.
- `samples/movie_collection/collection.xml:1` - Coleccion de peliculas con posters, autores y personajes.

Para crear un ZIP de ejemplo listo para cargar:
```
pwsh scripts\make-sample-zip.ps1
```
Esto genera `samples\collection.zip`.

## Código relevante
- `src/simpleapp/Main.java:1` — UI: carga ZIP, selección XSLT, árbol y previsualización.
- `src/simpleapp/RecordViewerMain.java:1` - visor externo para las fichas genéricas transformadas.
- `src/simpleapp/ZipUtils.java:1` — Extracción segura de ZIP.
- `src/simpleapp/XmlUtils.java:1` — Parseo DOM, pretty print y transformación XSLT (JAXP).
- `src/simpleapp/PluginRegistryLoader.java:1` - carga del registro de plugins desde XML/JAR.
- `plugins/plugins.xml:1` - tabla de mapeo `elemento -> clase#metodo`.

## Proximos pasos sugeridos
- Soportar variantes de esquema (p. ej. Relax NG o multiples XSD segun version).
- Gestion de multiples archivos XML dentro del ZIP y eleccion explicita.
- Exportar resultados en otros formatos (PDF, Markdown, etc.) o con plantillas predefinidas.
- Extender el procesamiento para tratar contenidos/recursos ademas de la estructura.
