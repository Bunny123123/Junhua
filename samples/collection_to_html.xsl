<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:output method="html" encoding="UTF-8" indent="yes"/>
  <xsl:strip-space elements="*"/>

  <!-- Genera un resumen HTML de la coleccion:
       - Cada objeto (<o>) se muestra como una seccion con metadatos, recursos y relaciones.
       - Los metadatos respetan la jerarquia del XML original mediante listas anidadas. -->

  <xsl:template match="/">
    <html>
      <head>
        <meta charset="UTF-8"/>
        <title>Resumen de coleccion</title>
        <style type="text/css">
          body { font-family: Arial, sans-serif; margin: 2rem; background-color: #f8f8f8; }
          h1 { margin-top: 0; }
          section.objeto { background: #fff; border: 1px solid #ddd; padding: 1.5rem; margin-bottom: 1.5rem; border-radius: 8px; }
          section.objeto h2 { margin-top: 0; }
          .metadatos ul { list-style: none; margin: 0.25rem 0 0.75rem 0; padding-left: 1rem; }
          .metadatos li { margin-bottom: 0.35rem; }
          .metadatos strong { display: inline-block; min-width: 6rem; }
          ul.recursos, ul.relaciones { margin: 0.25rem 0 0.75rem 0; padding-left: 1.25rem; }
          ul.recursos li, ul.relaciones li { margin-bottom: 0.25rem; }
          .metadatos ul ul { border-left: 2px solid #e0e0e0; margin-left: 0.5rem; padding-left: 0.75rem; }
        </style>
      </head>
      <body>
        <h1>Resumen de coleccion</h1>
        <xsl:choose>
          <xsl:when test="dc/o">
            <xsl:for-each select="dc/o">
              <xsl:call-template name="render-object"/>
            </xsl:for-each>
          </xsl:when>
          <xsl:otherwise>
            <p>No se encontraron objetos en el XML.</p>
          </xsl:otherwise>
        </xsl:choose>
      </body>
    </html>
  </xsl:template>

  <xsl:template name="render-object">
    <section class="objeto">
      <h2>
        <xsl:text>Objeto </xsl:text>
        <xsl:value-of select="normalize-space(@id)"/>
      </h2>

      <xsl:if test="*[not(self::rs or self::rels)]">
        <h3>Metadatos</h3>
        <div class="metadatos">
          <xsl:for-each select="*[not(self::rs or self::rels)]">
            <div class="meta-entry">
              <strong><xsl:value-of select="name()"/></strong>
              <xsl:call-template name="render-node">
                <xsl:with-param name="current" select="."/>
              </xsl:call-template>
            </div>
          </xsl:for-each>
        </div>
      </xsl:if>

      <xsl:if test="rs/*">
        <h3>Recursos</h3>
        <ul class="recursos">
          <xsl:for-each select="rs/*">
            <li>
              <strong><xsl:value-of select="name()"/></strong>
              <xsl:if test="@name">
                <xsl:text> (</xsl:text>
                <xsl:value-of select="@name"/>
                <xsl:text>)</xsl:text>
              </xsl:if>
              <xsl:text>: </xsl:text>
              <xsl:value-of select="normalize-space(.)"/>
            </li>
          </xsl:for-each>
        </ul>
      </xsl:if>

      <xsl:if test="rels/rel">
        <h3>Relaciones</h3>
        <ul class="relaciones">
          <xsl:for-each select="rels/rel">
            <li>
              <xsl:text>Tipo </xsl:text>
              <strong><xsl:value-of select="normalize-space(@name)"/></strong>
              <xsl:text> con </xsl:text>
              <xsl:value-of select="normalize-space(@ref | @id)"/>
            </li>
          </xsl:for-each>
        </ul>
      </xsl:if>
    </section>
  </xsl:template>

  <xsl:template name="render-node">
    <xsl:param name="current"/>
    <xsl:choose>
      <xsl:when test="$current/*">
        <ul>
          <xsl:for-each select="$current/*">
            <li>
              <strong><xsl:value-of select="name()"/></strong>
              <xsl:text>: </xsl:text>
              <xsl:call-template name="render-node">
                <xsl:with-param name="current" select="."/>
              </xsl:call-template>
            </li>
          </xsl:for-each>
        </ul>
      </xsl:when>
      <xsl:otherwise>
        <xsl:value-of select="normalize-space($current)"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
</xsl:stylesheet>

