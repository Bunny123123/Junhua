<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:output method="xml" encoding="UTF-8" indent="yes"/>
  <xsl:strip-space elements="*"/>

  <xsl:template match="/recordCollection">
    <records source="{@source}" root="{@root}" intermediate="recordCollection">
      <xsl:for-each select="record">
        <xsl:call-template name="emit-viewer-record"/>
      </xsl:for-each>
    </records>
  </xsl:template>

  <xsl:template name="emit-viewer-record">
    <xsl:variable name="preferredTitle"
                  select="(metadata//*[self::titulo or self::nombre or self::title or self::name][1])[1]"/>
    <xsl:variable name="firstField" select="metadata//*[not(*)][1]"/>
    <record id="{@id}" type="{@type}">
      <title>
        <xsl:choose>
          <xsl:when test="$preferredTitle">
            <xsl:value-of select="normalize-space($preferredTitle)"/>
          </xsl:when>
          <xsl:when test="$firstField">
            <xsl:value-of select="normalize-space($firstField)"/>
          </xsl:when>
          <xsl:otherwise>
            <xsl:value-of select="@id"/>
          </xsl:otherwise>
        </xsl:choose>
      </title>

      <xsl:for-each select="metadata/*">
        <xsl:call-template name="emit-fields">
          <xsl:with-param name="node" select="."/>
          <xsl:with-param name="group" select="name()"/>
          <xsl:with-param name="path" select="name()"/>
        </xsl:call-template>
      </xsl:for-each>

      <xsl:for-each select="resources/resource">
        <resource type="{@type}" name="{@name}">
          <xsl:value-of select="normalize-space(.)"/>
        </resource>
      </xsl:for-each>

      <xsl:for-each select="relations/relation">
        <relation name="{@name}" ref="{@ref}"/>
      </xsl:for-each>
    </record>
  </xsl:template>

  <xsl:template name="emit-fields">
    <xsl:param name="node"/>
    <xsl:param name="group"/>
    <xsl:param name="path"/>
    <xsl:choose>
      <xsl:when test="$node/*">
        <xsl:for-each select="$node/*">
          <xsl:call-template name="emit-fields">
            <xsl:with-param name="node" select="."/>
            <xsl:with-param name="group" select="$group"/>
            <xsl:with-param name="path" select="concat($path, '/', name())"/>
          </xsl:call-template>
        </xsl:for-each>
      </xsl:when>
      <xsl:otherwise>
        <field name="{name($node)}" label="{name($node)}" group="{$group}" path="{$path}">
          <xsl:value-of select="normalize-space($node)"/>
        </field>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
</xsl:stylesheet>
