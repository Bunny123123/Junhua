<?xml version="1.0" encoding="UTF-8"?><xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:app="http://xml.apache.org/xalan/java/simpleapp.generated.AppExtensionBridge" extension-element-prefixes="app" version="1.0">

  <xsl:output indent="yes" method="xml"/>

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
