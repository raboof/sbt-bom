package io.github.siculo.sbtbom

import com.github.packageurl.PackageURL
import org.cyclonedx.CycloneDxSchema
import org.cyclonedx.model.{Component, License, LicenseChoice}
import sbt.librarymanagement.ModuleReport

import java.util

class ComponentExtractor(context: ExtractorContext, source: ModuleReport) {

  import context._

  def extract: Component = {
    val group: String = source.module.organization
    val name: String = source.module.name
    val version: String = source.module.revision
    /*
      moduleReport.extraAttributes found keys are:
        - "info.apiURL"
        - "info.versionScheme"
     */
    val component = new Component()
    component.setGroup(group)
    component.setName(name)
    component.setVersion(version)
    component.setModified(false)
    component.setType(Component.Type.LIBRARY)
    component.setPurl(
      new PackageURL(PackageURL.StandardTypes.MAVEN, group, name, version, new util.TreeMap(), null).canonicalize()
    )
    component.setScope(Component.Scope.REQUIRED)
    licenseChoice.foreach(component.setLicenseChoice)

    /*
      not returned component properties are (BOM version 1.0):
        - publisher: The person(s) or organization(s) that published the component
        - hashes
        - copyright: An optional copyright notice informing users of the underlying claims to copyright ownership in a published work.
        - cpe: Specifies a well-formed CPE name. See https://nvd.nist.gov/products/cpe
        - components: Specifies optional sub-components. This is not a dependency tree. It simply provides an optional way to group large sets of components together.
        - user defined attributes: User-defined attributes may be used on this element as long as they do not have the same name as an existing attribute used by the schema.
     */

    logComponent(component)

    component
  }

  private def licenseChoice: Option[LicenseChoice] = {
    val licenses: Seq[model.License] = source.licenses.map {
      case (name, mayBeUrl) =>
        model.License(name, mayBeUrl)
    }
    if (licenses.isEmpty)
      None
    else {
      val choice = new LicenseChoice()
      licenses.foreach {
        modelLicense =>
          val license = new License()
          license.setName(modelLicense.name)
          if (schemaVersion != CycloneDxSchema.Version.VERSION_10) {
            modelLicense.url.foreach(license.setUrl)
          }
          choice.addLicense(license)
      }
      Some(choice)
    }
  }

  private def logComponent(component: Component): Unit = {
    log.info(
      s""""
         |${component.getGroup}" % "${component.getName}" % "${component.getVersion}",
         | Modified = ${component.getModified}, Component type = ${component.getType.getTypeName},
         | Scope = ${component.getScope.getScopeName}
         | """.stripMargin)
  }
}
