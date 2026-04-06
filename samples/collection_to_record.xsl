<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:output method="xml" encoding="UTF-8" indent="yes"/>
  <xsl:strip-space elements="*"/>

  <xsl:template match="/dc">
    <records source="collection" root="dc">
      <xsl:for-each select="o">
        <xsl:call-template name="emit-record"/>
      </xsl:for-each>
    </records>
  </xsl:template>

  <xsl:template name="emit-record">
    <xsl:variable name="primaryMeta" select="*[not(self::rs or self::rels)][1]"/>
    <xsl:variable name="preferredTitle"
                  select="(*[not(self::rs or self::rels)]//*[self::titulo or self::nombre or self::title or self::name][1])[1]"/>
    <record id="{@id}" type="{name($primaryMeta)}">
      <title>
        <xsl:choose>
          <xsl:when test="$preferredTitle">
            <xsl:value-of select="normalize-space($preferredTitle)"/>
          </xsl:when>
          <xsl:when test="$primaryMeta">
            <xsl:value-of select="concat(name($primaryMeta), ' ', @id)"/>
          </xsl:when>
          <xsl:otherwise>
            <xsl:value-of select="@id"/>
          </xsl:otherwise>
        </xsl:choose>
      </title>

      <xsl:for-each select="*[not(self::rs or self::rels)]">
        <xsl:call-template name="emit-fields">
          <xsl:with-param name="node" select="."/>
          <xsl:with-param name="group" select="name()"/>
          <xsl:with-param name="path" select="name()"/>
        </xsl:call-template>
      </xsl:for-each>

      <xsl:for-each select="rs/*">
        <resource type="{name()}" name="{@name}">
          <xsl:value-of select="normalize-space(.)"/>
        </resource>
      </xsl:for-each>

      <xsl:for-each select="rels/rel">
        <relation name="{@name}" ref="{(@ref | @id)[1]}"/>
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
