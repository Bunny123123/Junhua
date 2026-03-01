<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:app="http://junhua.com/extensions"
    extension-element-prefixes="app">

  <xsl:output method="xml" indent="yes"/>

  <xsl:template match="@*|node()">
    <xsl:copy>
      <xsl:apply-templates select="@*|node()"/>
    </xsl:copy>
  </xsl:template>

  <!-- Inserta la demo dentro del primer objeto para mantener valido el esquema -->
  <xsl:template match="dc/o[1]">
    <xsl:copy>
      <xsl:apply-templates select="@*|node()"/>
      <pluginDemo>
        <mensaje>
          <app:miExtension text="Plugin externo cargado desde JAR"/>
        </mensaje>
      </pluginDemo>
    </xsl:copy>
  </xsl:template>
</xsl:stylesheet>
