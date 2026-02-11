<?xml version="1.0" encoding="UTF-8"?><xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:app="http://xml.apache.org/xalan/java/simpleapp.generated.AppExtensionBridge" exclude-result-prefixes="app" extension-element-prefixes="app" version="1.0">
  <xsl:output encoding="UTF-8" indent="yes" method="xml"/>
  <xsl:strip-space elements="*"/>
  <xsl:param name="target-image-format" select="'png'"/>

  <!-- Copia la coleccion pero anade para cada objeto <o> un nodo <stats>
       con totales de recursos y relaciones. Muestra un cambio manteniendo XML. -->

  <xsl:template match="@*|node()">
    <xsl:copy>
      <xsl:apply-templates select="@*|node()"/>
    </xsl:copy>
  </xsl:template>

  <xsl:template match="o">
    <xsl:copy>
      <xsl:apply-templates select="@*"/>
      <xsl:apply-templates select="node()"/>
      <stats recursos="{count(rs/*)}" relaciones="{count(rels/rel)}">
        <app:saludo select="@id"/>
      </stats>
    </xsl:copy>
  </xsl:template>

  <!-- Si un recurso local <lr> esta marcado como imagen (@name='imagen'), convierte el archivo
       al formato indicado y sustituye su contenido por la nueva ruta. Usa el elemento de
       extension <app:changeImageFormat/> (sin guiones, requerido por Xalan). -->
  <xsl:template match="rs/lr[@name='imagen']">
    <xsl:copy>
      <xsl:apply-templates select="@*"/>
      <app:changeImageFormat format="{$target-image-format}" select="."/>
    </xsl:copy>
  </xsl:template>
</xsl:stylesheet>
