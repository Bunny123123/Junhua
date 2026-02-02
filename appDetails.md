* Funcionamiento General

La ventana principal (Main.java) construye la barra de herramientas con los botones "Elegir ZIP", "Elegir XSLT", "Transformar", "Exportar" y "Limpiar", ademas de dos paneles principales: a la izquierda un JTree con la estructura de la coleccion y un panel inferior de detalle (nodeDetailArea), y a la derecha la vista previa completa. El estado vivo se guarda en los campos selectedZip, selectedXslt, extractedDir, collectionDoc y lastResultText para coordinar carga, transformacion, validacion y exportacion.

Cada nodo del arbol almacena una referencia DOM (NodeInfo) para poder mostrar el XML exacto asociado. Un TreeSelectionListener invoca showSelectedNodeDetails(), que delega en XmlUtils.nodeToPrettyString() para formatear el fragmento seleccionado y renderizarlo en el panel de detalle.


* Carga de la coleccion

chooseZip() abre el selector de archivos y delega en loadZip(), que crea un directorio temporal, extrae el ZIP mediante ZipUtils.extractZip() y localiza el primer XML disponible. Ese XML se parsea con XmlUtils.parse(), se valida inmediatamente contra `samples/collection.xsd` llamando a XmlUtils.validateCollection(), se actualiza el arbol con buildTree() y se muestra el contenido formateado con XmlUtils.toPrettyString(). Cualquier error de esquema se notifica en la barra de estado y mediante setError().


* Seleccion de XSLT, transformacion y validacion

chooseXslt() solo guarda la ruta seleccionada. Cuando se pulsa "Transformar", el metodo transform() comprueba que haya XML y XSLT, ejecuta XmlUtils.transform() y decide si el resultado sigue siendo una coleccion mediante XmlUtils.isCollectionDocument(). Si la raiz es `dc`, se valida con XmlUtils.validateCollection(); de lo contrario se omite la validacion pero se deja constancia en la barra de estado. El DOM resultante se convierte a texto con toPrettyString(), se guarda en lastResultText y se habilita el boton "Exportar".

export() abre un JFileChooser y escribe lastResultText en UTF-8 usando Files.write(). Si ocurre cualquier problema, se reutiliza setError() para mostrar el mensaje.


* Gestion de errores y limpieza

setError() centraliza la notificacion (barra de estado + JOptionPane). clearState() reinicia las referencias, limpia el arbol, desactiva la exportacion y delega en clearExtractedDir() + XmlUtils.deleteDirectoryRecursively() para eliminar los temporales.

Con este flujo se cubren los casos clave: validar la coleccion al cargar, permitir transformaciones XSLT, volver a validar la salida compatible con el esquema y exportar el resultado que el usuario necesite conservar.
