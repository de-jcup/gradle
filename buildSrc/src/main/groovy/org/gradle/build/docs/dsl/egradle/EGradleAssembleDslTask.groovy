/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.build.docs.dsl.egradle

import groovy.transform.CompileStatic
import org.gradle.api.*
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.*
import org.gradle.build.docs.*
import org.gradle.build.docs.dsl.links.*
import org.gradle.build.docs.dsl.source.model.*
import org.gradle.build.docs.model.*
import org.w3c.dom.*
import javax.xml.parsers.*

/**
 * Generates the egradle source for the DSL proposal files.
 *
 * Uses the following as input:
 * <ul>
 * <li>Meta-data extracted from the source by {@link org.gradle.build.docs.dsl.source.ExtractDslMetaDataTask}.</li>
 * <li>Meta-data about the plugins, in the form of an XML file.</li>
 * </ul>
 *
 * Produces the following:
 * <ul>
 * <li>A docbook book XML file containing the reference.</li>
 * <li>A meta-data file containing information about where the canonical documentation for each class can be found:
 * as dsl doc, javadoc or groovydoc.</li>
 * </ul>
 */
@CacheableTask
class EGradleAssembleDslTask extends DefaultTask {

    //>3.x@PathSensitive(PathSensitivity.NONE)
    @InputFile
    File classMetaDataFile

    //>3.x@PathSensitive(PathSensitivity.NONE)
    @InputFile
    File pluginsMetaDataFile

    @OutputDirectory
    File destFolder

    @OutputFile
    File linksFile

    //>3.x@PathSensitive(PathSensitivity.RELATIVE)
    @InputFiles
    FileCollection sources

	String buildGradleVersion

    @TaskAction
    def generate() {
    	EGradleXIncludeAwareXmlProvider provider = new EGradleXIncludeAwareXmlProvider()
        transformDocument(provider)
    }

    private def transformDocument(EGradleXIncludeAwareXmlProvider provider) {
    	Project p = null
    	DocGenerationException dge = null
    	 
    	/* load metadata */
        ClassMetaDataRepository<ClassMetaData> classRepository = new EGradleSimpleClassMetaDataRepository<ClassMetaData>()
        classRepository.load(classMetaDataFile)
        
        buildGradleVersion = getProject().getRootProject().file("version.txt").text.trim()
       
        logger.quiet "start"
        use(BuildableDOMCategory) {
            EGradleDslDocModel model = createEGradleDslDocModel(classRepository)//, loadPluginsMetaData())
            appendPluginsMetaData(model)
           
            File destVersionFolder=new File(destFolder,buildGradleVersion);
           
             model.classes.each { EGradleClassDoc classDoc ->
             	 createNewDocument(provider, classDoc)
                 String fileName = classDoc.name.replaceAll("\\.","/")
                 File file = new File(destVersionFolder,fileName+".xml")
                 file.parentFile.mkdirs()
                 provider.write(file,true)
            }
        }

        // linkRepository.store(linksFile)
    }

	def createNewDocument(EGradleXIncludeAwareXmlProvider provider, EGradleClassDoc classDoc){
			 provider.emptyDoc()
			 Document doc = provider.document
			 
			 Element typeElement = doc.createElement("type")
             doc.appendChild(typeElement)
	
             typeElement.setAttribute("language","gradle")
             typeElement.setAttribute("version", buildGradleVersion)
             
               typeElement.setAttribute("name", classDoc.name)
               logger.info "creating element: $classDoc.name"
               def classMetaData = classDoc.metaData
               if (classDoc.deprecated){
                   typeElement.setAttribute("deprecated","true")
               }
               if (classDoc.incubating){
               	   typeElement.setAttribute("incubating","true")
               }
               def superClassMetaData = classMetaData.superClass
               if (superClassMetaData){
               		typeElement.setAttribute("superType",superClassMetaData.className)
               }
               if (classMetaData.interface){
               	 	typeElement.setAttribute("interface", "true")
               }
               
               if (classMetaData.groovy){
               	 	typeElement.setAttribute("groovy", "true")
               }
               
               if (classMetaData.enum){
               	 	typeElement.setAttribute("enum", "true")
               }
               /* properties*/
               for (PropertyMetaData propertyMetaData: classMetaData.declaredProperties){
               		 logger.debug "property $propertyMetaData.name"
               		 Element propertyElement = doc.createElement("property")
               		 propertyElement.setAttribute("name", propertyMetaData.name)
               		 TypeMetaData propertyType = propertyMetaData.type
               		 logger.debug "property type $propertyType"
               		 propertyElement.setAttribute("type", propertyType.name)
               		 logger.debug "property 3"
               		 appendDescription(doc,propertyElement, propertyMetaData)
               		 typeElement.appendChild(propertyElement)
               }
               /* methods */
                for (MethodMetaData methodMetaData: classMetaData.declaredMethods){
               		 logger.debug "method $methodMetaData.name"
               		 Element methodElement = doc.createElement("method")
               		 methodElement.setAttribute("name", methodMetaData.name)
               		 TypeMetaData returnType = methodMetaData.returnType
               		 methodElement.setAttribute("returnType", returnType.name)
               		 
               		 for (ParameterMetaData paramMetaData: methodMetaData.parameters){
               		 	  Element paramElement = doc.createElement("parameter")
               		 	  paramElement.setAttribute("name",paramMetaData.name)
               		 	  paramElement.setAttribute("type", paramMetaData.type.name)
               		 	  
               		 	  methodElement.appendChild(paramElement)
               		 }
               		 appendDescription(doc,methodElement, methodMetaData)
               		 typeElement.appendChild(methodElement)
               }
               /* plugin meta extensions */
               for (EGradleClassMetaPluginExtension me: classDoc.metaPluginExtensions){
                     Element pluginElement = doc.createElement("plugin")
               		 pluginElement.setAttribute("id", me.pluginId)
                     if (me.mixinClass){
                     	  Element mixinElement = doc.createElement("mixin")
               		      mixinElement.setAttribute("class", me.mixinClass)
               		      pluginElement.appendChild(mixinElement)
                     }
                     if (me.extensionId){
                      	  Element extensionElement = doc.createElement("extension")
               		      extensionElement.setAttribute("id", me.extensionId)
               		      extensionElement.setAttribute("class", me.extensionClass)
               		      pluginElement.appendChild(extensionElement)
                     }
               		 typeElement.appendChild(pluginElement)
               }
               logger.debug "append to root element: $typeElement"
               appendDescription(doc,typeElement, classMetaData)
               
	}

    @CompileStatic
    EGradleDslDocModel createEGradleDslDocModel(ClassMetaDataRepository<ClassMetaData> classMetaDataRepository) {
        new EGradleDslDocModel(classMetaDataRepository)
    }
    
    def appendDescription(Document doc, Element parentElement, AbstractLanguageElement languageElement){
    	// FIXME ATR, 13.01.2017 : javadoc converter like in docbook done would be nice: see JavadocConverter
    	String descriptionText = languageElement.getRawCommentText();
    	if (descriptionText!=null && descriptionText.length() >0 ){
	    	CDATASection cdata = doc.createCDATASection(descriptionText);
	    	
	    	Element descriptionElement = doc.createElement("description")
	    	descriptionElement.appendChild(cdata);
	    	parentElement.appendChild(descriptionElement)
    	}
    }
    
    

    def appendPluginsMetaData(EGradleDslDocModel model) {
        XIncludeAwareXmlProvider provider = new XIncludeAwareXmlProvider()
        provider.parse(pluginsMetaDataFile) /* plugins.xml */
        
        //Map<String, EGradleClassExtensionMetaData> extensions = [:]
        
        provider.root.plugin.each { Element plugin ->
            def pluginId = plugin.'@id'
            if (!pluginId) {
                throw new RuntimeException("No id specified for plugin: ${plugin.'@description' ?: 'unknown'}")
            }
            logger.quiet "pluginId = $pluginId"
            plugin.extends.each { Element e ->
                def targetClass = e.'@targetClass'
                if (!targetClass) {
                    throw new RuntimeException("No targetClass specified for extension provided by plugin '$pluginId'.")
                }
                logger.quiet "targetClass=$targetClass"
                def classDoc = model.getClassDoc(targetClass)  
                def mixinClass = e.'@mixinClass'
                EGradleClassMetaPluginExtension me = new EGradleClassMetaPluginExtension();
                me.pluginId=pluginId
                if (mixinClass) {
                    me.mixinClass=mixinClass
                }
                //logger.quiet "mixinClass=$mixinClass"
                def extensionClass = e.'@extensionClass'
                if (extensionClass) {
                    def extensionId = e.'@id'
                    if (!extensionId) {
                        throw new RuntimeException("No id specified for extension '$extensionClass' for plugin '$pluginId'.")
                    }
                    //extension.addExtension(pluginId, extensionId, extensionClass)
                    me.extensionId=extensionId
                    me.extensionClass=extensionClass
                    
                }
             	classDoc.metaPluginExtensions.add(me)   
            }
        }
        return extensions
    }
   
}

