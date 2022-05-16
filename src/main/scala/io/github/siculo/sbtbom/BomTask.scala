package io.github.siculo.sbtbom

import io.github.siculo.sbtbom.PluginConstants._
import io.github.siculo.sbtbom.extractor._
import org.apache.commons.io.FileUtils
import org.cyclonedx.model.Bom
import org.cyclonedx.parsers.XmlParser
import org.cyclonedx.{BomGeneratorFactory, CycloneDxSchema}
import sbt._

import java.nio.charset.Charset
import scala.collection.JavaConverters._

case class BomTaskProperties(report: UpdateReport, currentConfiguration: Configuration, log: Logger, schemaVersion: String)

abstract class BomTask[T](protected val properties: BomTaskProperties) {

  def execute: T

  protected def getBomText: String = {
    val context: ExtractorContext = ExtractorContext(schemaVersion, currentConfiguration, log)
    val bom: Bom = new BomExtractor(context, report).extract
    val bomText: String = getXmlText(bom)
    logBomInfo(context, bom)
    bomText
  }

  protected def writeToFile(destFile: File, text: String): Unit = {
    FileUtils.write(destFile, text, Charset.forName("UTF-8"), false)
  }

  protected def validateBomFile(bomFile: File): Unit = {
    val parser = new XmlParser()
    val exceptions = parser.validate(bomFile, schemaVersion).asScala
    if (exceptions.nonEmpty) {
      val message = s"The BOM file ${bomFile.getAbsolutePath} does not conform to the CycloneDX BOM standard as defined by the XSD"
      log.error(s"$message:")
      exceptions.foreach {
        exception =>
          log.error(s"- ${exception.getMessage}")
      }
      throw new BomError(message)
    }
  }

  @throws[BomError]
  protected def raiseException(message: String): Unit = {
    log.error(message)
    throw new BomError(message)
  }

  private def getXmlText(bom: Bom): String = {
    val bomGenerator = BomGeneratorFactory.createXml(schemaVersion, bom)
    bomGenerator.generate
    val bomText = bomGenerator.toXmlString
    bomText
  }

  protected def logBomInfo(params: ExtractorContext, bom: Bom): Unit = {
    log.info(s"Schema version: ${schemaVersion.getVersionString}")
    // log.info(s"Serial number : ${bom.getSerialNumber}")
    log.info(s"Scope         : ${params.configuration.id}")
  }

  protected def report: UpdateReport = properties.report

  protected def currentConfiguration: Configuration = properties.currentConfiguration

  protected def log: Logger = properties.log

  protected lazy val schemaVersion: CycloneDxSchema.Version =
    supportedVersions.find(_.getVersionString == properties.schemaVersion) match {
      case Some(foundVersion) => foundVersion
      case None =>
        val message = s"Unsupported schema version ${properties.schemaVersion}"
        log.error(message)
        throw new BomError(message)
    }
}
