<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:output method="xml" encoding="UTF-8" indent="yes"/>
  <xsl:strip-space elements="*"/>

  <xsl:template match="/dc">
    <recordCollection source="collection" root="dc">
      <xsl:for-each select="o">
        <xsl:call-template name="emit-record"/>
      </xsl:for-each>
    </recordCollection>
  </xsl:template>

  <xsl:template name="emit-record">
    <xsl:variable name="primaryMeta" select="*[not(self::rs or self::rels)][1]"/>
    <record id="{@id}" type="{name($primaryMeta)}">
      <metadata>
        <xsl:copy-of select="*[not(self::rs or self::rels)]"/>
      </metadata>

      <resources>
        <xsl:for-each select="rs/*">
          <resource type="{name()}" name="{@name}">
            <xsl:value-of select="normalize-space(.)"/>
          </resource>
        </xsl:for-each>
      </resources>

      <relations>
        <xsl:for-each select="rels/rel">
          <relation name="{@name}" ref="{(@ref | @id)[1]}"/>
        </xsl:for-each>
      </relations>
    </record>
  </xsl:template>
</xsl:stylesheet>
