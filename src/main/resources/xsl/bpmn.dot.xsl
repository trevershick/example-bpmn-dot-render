<?xml version="1.0" encoding="UTF-8" ?>

<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
	xmlns:b="http://www.omg.org/spec/BPMN/20100524/MODEL"
	xmlns:activiti="http://activiti.org/bpmn">

	<xsl:output encoding="UTF-8" indent="yes" method="text" />

	<xsl:template match="/b:definitions/b:process">
	

  
		digraph {
		node [shape=box];
		<xsl:apply-templates select="b:*"/>
		}
	</xsl:template>

	<xsl:template match="b:subProcess">
		<xsl:variable name="label">
			Sub-Process\n
			<xsl:value-of select="@name"/>
			\n{<xsl:value-of select="@id"/>}
			<xsl:if test="b:multiInstanceLoopCharacteristics">
			\nFor Each ${<xsl:value-of select="b:multiInstanceLoopCharacteristics/@activiti:elementVariable"/>} in <xsl:value-of select="b:multiInstanceLoopCharacteristics/@activiti:collection"/>
			</xsl:if>
		</xsl:variable>
		subgraph cluster_<xsl:value-of select="@id"/> {
		label="<xsl:value-of select="normalize-space($label)"/>";
			<xsl:apply-templates select="b:*"/>
		}
	</xsl:template>
<xsl:template match="b:documentation">
</xsl:template>
	

	
	
	<xsl:template match="b:sequenceFlow">
		<xsl:variable name="sourceRef" select="@sourceRef"/>
		<xsl:variable name="targetRef" select="@targetRef"/>
		<xsl:variable name="from">
				<xsl:choose>
						<xsl:when test="local-name(//b:*[@id=$sourceRef]) = 'subProcess'">
							<xsl:value-of select="//b:*[@id=$sourceRef]/b:endEvent/@id"/>
						</xsl:when>
						<xsl:otherwise>
							<xsl:value-of select="$sourceRef"/>
						</xsl:otherwise>
					</xsl:choose>		
		</xsl:variable>
		<xsl:variable name="to">
				<xsl:choose>
						<xsl:when test="local-name(//b:*[@id=$targetRef]) = 'subProcess'">
							<xsl:value-of select="//b:*[@id=$targetRef]/b:startEvent/@id"/>
						</xsl:when>
						<xsl:otherwise>
							<xsl:value-of select="$targetRef"/>
						</xsl:otherwise>
					</xsl:choose>		
		</xsl:variable>
		<xsl:value-of select="$from"/> -> <xsl:value-of select="$to"/> [label="<xsl:value-of select="@name"/>"];
	
	</xsl:template>
	
	<xsl:template match="b:exclusiveGateway">
		<xsl:value-of select="@id"/> [label="<xsl:value-of select="@name"/>",fillcolor="#6b90d4",style=filled,width=0.75,height=0.75,fixedsize=true,shape="diamond"];
	</xsl:template>
	
	<xsl:template match="b:startEvent">
		<xsl:value-of select="@id"/> [shape=circle,style=filled,fillcolor=green,label="",width=0.2];
	</xsl:template>
	<xsl:template match="b:endEvent">
		<xsl:value-of select="@id"/> [shape=circle,style=filled,fillcolor=black,label="",width=0.2];
	</xsl:template>
	<xsl:template match="b:serviceTask">
		<xsl:choose>
			<xsl:when test="@activiti:class = 'com.example.ResponseMessageTask'">
				<xsl:variable name="msg" select="concat(b:extensionElements/activiti:field[@name='message']/@stringValue, b:extensionElements/activiti:field[@name='message']/@expression)"/>
				<xsl:value-of select="@id"/> [shape=box,style=filled,fillcolor="#dd9e9e",label="Message to User\n'<xsl:value-of select="substring($msg,0,24)"/>...'",width=0.2];
			</xsl:when>
			<xsl:otherwise>
				<xsl:value-of select="@id"/> [shape=box,style=filled,fillcolor="#ffda73",label="<xsl:value-of select="@name"/>\n{<xsl:value-of select="@id"/>}",width=0.2];
			</xsl:otherwise>
		</xsl:choose>
		
	</xsl:template>
	<xsl:template match="b:userTask">
		<xsl:variable name="label">
			User Task\n
			<xsl:value-of select="@name"/>
			<xsl:if test="@activiti:candidateGroups">
				\nby <xsl:value-of select="@activiti:candidateGroups"/>
			</xsl:if>
			\n(<xsl:value-of select="@activiti:formKey"/>)
			\n{<xsl:value-of select="@id"/>}
		</xsl:variable>
		<xsl:value-of select="@id"/> [shape=box,style=filled,fillcolor="#c3c3c3",label="<xsl:value-of select="normalize-space($label)"/>",width=0.2];
	</xsl:template>
	
	<xsl:template match="b:callActivity">
		<xsl:variable name="cid" select="@id"/>
		<xsl:variable name="label">
			Call Other Process\n
			<xsl:value-of select="@name"/>
			<xsl:for-each select="b:extensionElements/activiti:in">
			\n<xsl:value-of select="@target"/>=<xsl:value-of select="@sourceExpression"/>
			</xsl:for-each>
		</xsl:variable>
		<xsl:value-of select="@id"/> [shape=box,style=filled,fillcolor="#ff9140",label="<xsl:value-of select="normalize-space($label)"/>",width=0.2];
		
		
	</xsl:template>
	
	
</xsl:stylesheet>
