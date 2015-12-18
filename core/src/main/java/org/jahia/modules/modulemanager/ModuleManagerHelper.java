/**
 * 
 */
package org.jahia.modules.modulemanager;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.jahia.bin.Jahia;
import org.jahia.commons.Version;
import org.jahia.data.templates.JahiaTemplatesPackage;
import org.jahia.data.templates.ModulesPackage;
import org.jahia.modules.modulemanager.payload.BundleInfo;
import org.jahia.modules.modulemanager.payload.OperationResultImpl;
import org.jahia.osgi.BundleUtils;
import org.jahia.security.license.LicenseCheckerService;
import org.jahia.services.templates.JahiaTemplateManagerService;
import org.jahia.services.templates.ModuleVersion;
import org.jahia.services.templates.TemplatePackageRegistry;
import org.jahia.settings.SettingsBean;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.springframework.binding.message.Message;
import org.springframework.binding.message.MessageBuilder;
import org.springframework.binding.message.MessageContext;
import org.springframework.binding.message.Severity;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

/**
 * @author bdjiba
 *
 */
public class ModuleManagerHelper {

  /**
   * Validate the given jahia package.
   * Below attributes are checked:
   * <ul>
   *    <li>Jahia-Package-Name: a jahia package should contains this manifest attribute key and should not be empty</li>
   *    <li>Jahia-Package-License: a valid package license feature</li>
   *    <li>Jahia-Required-Version: {@link #isValidJahiaVersion(Attributes, MessageContext)} is used </li>
   * </ul>
   * @param bundleManifest the package bundle manifest file
   * @param context the message context
   * @param originalFilename the original file name
   * @return true when the validation is fine otherwise return false.
   * @throws IOException
   */
  public static boolean isValidJahiaPackageFile(Manifest bundleManifest, MessageContext context, String originalFilename) throws IOException {
    // TODO: first check of bundleManifest
    // Assume that it is valid and try to invalidate
    // and pass over all validation and return final validation result
    boolean isValid = true;
    Attributes manifestAttributes = bundleManifest.getMainAttributes();
    if(manifestAttributes.containsKey("Jahia-Package-Name")) {
      // a package
      if(StringUtils.isBlank(manifestAttributes.getValue("Jahia-Package-Name"))){
        context.addMessage(new MessageBuilder().source("moduleFile")
            .code("serverSettings.manageModules.install.package.name.error").error()
            .build());
        isValid = false;
      }
      String licenseFeature = manifestAttributes.getValue("Jahia-Package-License");
      if(licenseFeature != null && !LicenseCheckerService.Stub.isAllowed(licenseFeature)){
        context.addMessage(new MessageBuilder().source("moduleFile")
            .code("serverSettings.manageModules.install.package.missing.license")
            .args(new String[]{originalFilename, licenseFeature})
            .error() // FIXME: Should be consider as an error
            .build());
        isValid = false;
      }
    } 
    
    return isValid & isValidJahiaVersion(bundleManifest.getMainAttributes(), context);
  }
  
  /**
   * Check if the bundle manifest contains a correct Jahia required version
   * and if it is compatible to the current running plateform.
   * The target manifest attribute is Jahia-Required-Version
   * If it return false the message context is used to specify the reason
   * @param manifestAttributes the manifest main attributes
   * @param context the message context.
   * @return true if the Jahia version
   */
  public static boolean isValidJahiaVersion(Attributes manifestAttributes, MessageContext context) {
    boolean isValidValidated = true;
    String jahiaRequiredVersion = manifestAttributes.getValue("Jahia-Required-Version");
    if (StringUtils.isEmpty(jahiaRequiredVersion)) {
        context.addMessage(new MessageBuilder().source("moduleFile")
                .code("serverSettings.manageModules.install.required.version.missing.error").error().build());
        isValidValidated = false;
    }
    if (new Version(jahiaRequiredVersion).compareTo(new Version(Jahia.VERSION)) > 0) {
        context.addMessage(new MessageBuilder().source("moduleFile")
                .code("serverSettings.manageModules.install.required.version.error")
                .args(new String[] { jahiaRequiredVersion, Jahia.VERSION }).error().build());
        isValidValidated = false;
    }
    return isValidValidated;
  }
  
  /**
   * Check is the given bundle is a package, a mega-jar
   * @param bundleManifest the target bundle manifest
   * @return true is it is jar package otherwise return false
   * @throws IOException
   */
  public static boolean isPackageModule(Manifest bundleManifest) throws IOException {
    return StringUtils.isNotBlank(bundleManifest.getMainAttributes().getValue("Jahia-Package-Name"));
  }
  
  
  /**
   * Check if a module with the same ID exists
   * @param bundleSymbolicName the bundle symbolic name
   * @param bundleJahiaGrpID the bundle jahia group id
   * @param context the message context
   * @param templateManagerService the Jahia template manager service
   * @return true if no module with the same id exist otherwise return false.
   * @throws IOException
   */
  public static boolean isDifferentModuleWithSameIdExists(String bundleSymbolicName, String bundleJahiaGrpID, MessageContext context, JahiaTemplateManagerService templateManagerService) throws IOException {
      boolean isSameIdExists = false;
      if (templateManagerService.differentModuleWithSameIdExists(bundleSymbolicName, bundleJahiaGrpID)) {
        context.addMessage(new MessageBuilder().source("moduleFile")
            .code("serverSettings.manageModules.install.moduleWithSameIdExists")
            .arg(bundleSymbolicName)
            .error()
            .build());
        isSameIdExists = true;
      }
    return isSameIdExists;
  }
  
  // Validation scope
  // Check if the module already exists
  // This method is called when there the forceUpdate flag is set to false
  public static boolean isModuleExists(TemplatePackageRegistry templatePackageRegistry, String symbolicName, String version, MessageContext context){
    boolean isModuleExists = false;
    Set<ModuleVersion> aPackage = templatePackageRegistry.getAvailableVersionsForModule(symbolicName);
    ModuleVersion moduleVersion = new ModuleVersion(version);
    if (!moduleVersion.isSnapshot() && aPackage.contains(moduleVersion)) {
        context.addMessage(new MessageBuilder().source("moduleExists")
                .code("serverSettings.manageModules.install.moduleExists")
                .args(new String[]{symbolicName, version})
                .build());
        isModuleExists = true;
    }
    return isModuleExists;
  }
  
  public static Manifest getJarFileManifest(File file) {
    Manifest manifest = null;
    JarInputStream jarStream = null;
    try {
      jarStream = new JarInputStream(new FileInputStream(file));
      manifest = jarStream.getManifest();
    } catch (IOException e) {
      // TODO: log error
    }finally {
      if(jarStream != null) {
        IOUtils.closeQuietly(jarStream);
      }
    }
    return manifest;
  }
  
  // retrive the symbolic name from the target manifest if not empty
  public static String getManifestSymbolicName(Manifest manifest) {
    if(manifest == null) {
      return null;
    }
    String symbolicName = manifest.getMainAttributes().getValue("Bundle-SymbolicName");
    if (symbolicName == null) {
        symbolicName = manifest.getMainAttributes().getValue("root-folder");
    }
    return symbolicName;
  }
  
  // retrieve the implementation version from the given manifest if not null
  public static String getManifestVestion(Manifest manifest) {
    if(manifest != null) {
      return manifest.getMainAttributes().getValue("Implementation-Version");
    }
    return null;
  }
  
  // retrieve the implementation version from the given manifest if not null
  public static String getManifestGroupId(Manifest manifest) {
    if(manifest != null) {
      return manifest.getMainAttributes().getValue("Jahia-GroupId");
    }
    return null;
  }
  
  // finally install the bundle
  private static String[] install(ModuleManager moduleManager, Resource bundleResource, String symbolicName, String version) throws IOException, BundleException {
    moduleManager.install(bundleResource);
    return new String[] { symbolicName, version };
  }
  
  public static OperationResult installBundles(ModuleManager moduleManager, File file, MessageContext context, String originalFilename, boolean forceUpdate, JahiaTemplateManagerService templateManagerService, TemplatePackageRegistry templatePackageRegistry) throws IOException, BundleException {
      JarFile jarFile = new JarFile(file);
      try {
        OperationResult installResult = null;
        Manifest manifest = getJarFileManifest(file);
        if (isPackageModule(manifest)) {
          if(isValidJahiaPackageFile(manifest, context, originalFilename)) {
            ModulesPackage pack = ModulesPackage.create(jarFile);
            List<String> providedBundles = new ArrayList<String>(pack.getModules().keySet());
            for (Map.Entry<String, ModulesPackage.PackagedModule> entry : pack.getModules().entrySet()) {
              OperationResult res = installModule(moduleManager, entry.getValue().getModuleFile(), getJarFileManifest(entry.getValue().getModuleFile()), context, providedBundles, forceUpdate, templateManagerService, templatePackageRegistry);
              // to be reviewed
              if(installResult == null) {
                installResult = res;
              } else {
                installResult.getBundleInfoList().addAll(res.getBundleInfoList());
              }
              
              if (res.isSuccess()) {
                installResult.getBundleInfoList().addAll(res.getBundleInfoList());
              } else {
                break;
              }
            }
          }
        } else {
          installResult = installModule(moduleManager, file, manifest, context, null, forceUpdate, templateManagerService, templatePackageRegistry);
        }
        
        if(installResult != null && installResult.isSuccess()) {
          startBundles(context, installResult.getBundleInfoList(), templateManagerService, moduleManager, SettingsBean.getInstance());
        } else {
          if(installResult == null) { //FIXME need or not?
            installResult = OperationResultImpl.FAIL;
          }
          // Add failure reasons
          Message[] failRaisons = context.getAllMessages();
          for(Message message : failRaisons) {
            if(message.getSeverity() == Severity.ERROR) { // only errors
              installResult.addMessage(message.getText());
            }
          }
        }
        return installResult;
      } finally {
          IOUtils.closeQuietly(jarFile);
      }
  }
  
  private static OperationResult installModule(ModuleManager moduleManager, File file, Manifest manifest, MessageContext context, List<String> providedBundles, boolean forceUpdate, JahiaTemplateManagerService templateManagerService, TemplatePackageRegistry templatePackageRegistry) throws IOException, BundleException {
      try {
          String symbolicName = getManifestSymbolicName(manifest);
          String version = getManifestVestion(manifest);
          String groupId = getManifestGroupId(manifest);
          if(isDifferentModuleWithSameIdExists(symbolicName, groupId, context, templateManagerService)) {
              return null;
          }
          if(!forceUpdate & isModuleExists(templatePackageRegistry, symbolicName, version, context)) {
              return null;
          }
  
          // TODO check for missing dependencies before installing?
          OperationResult result = moduleManager.install(new FileSystemResource(file));
          if(result.isSuccess()) {
            result.getBundleInfoList().add(new BundleInfo(symbolicName, version));
          }
          return result;
      } finally {
        //
      }
  }
  
  private static void startBundles(MessageContext context, List<BundleInfo> bundleInfoList, JahiaTemplateManagerService templateManagerService, ModuleManager moduleManager, SettingsBean settingsBean) throws BundleException {
      for (BundleInfo bundleInfo : bundleInfoList) {
          Bundle bundle = BundleUtils.getBundle(bundleInfo.getSymbolicName(), bundleInfo.getVersion());
          if (bundle != null) {
            Set<ModuleVersion> allVersions = templateManagerService.getTemplatePackageRegistry().getAvailableVersionsForModule(bundle.getSymbolicName());
            JahiaTemplatesPackage currentVersion = templateManagerService.getTemplatePackageRegistry().lookupById(bundle.getSymbolicName());
            if (allVersions.size() == 1 ||
                ((settingsBean.isDevelopmentMode() && currentVersion != null && BundleUtils.getModule(bundle).getVersion().compareTo(currentVersion.getVersion()) > 0))) {
              moduleManager.start(bundleInfo.getSymbolicName() + "-" + bundleInfo.getVersion());
              context.addMessage(new MessageBuilder().source("moduleFile")
                  .code("serverSettings.manageModules.install.uploadedAndStarted")
                  .args(new String[]{bundle.getSymbolicName(), bundle.getVersion().toString()})
                  .build());
            } else {
              context.addMessage(new MessageBuilder().source("moduleFile")
                  .code("serverSettings.manageModules.install.uploaded")
                  .args(new String[]{bundle.getSymbolicName(), bundle.getVersion().toString()})
                  .build());
            }
          }
      }
  }

  
}
