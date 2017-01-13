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

    @OutputFile
    File destFile

    @OutputFile
    File linksFile

    //>3.x@PathSensitive(PathSensitivity.RELATIVE)
    @InputFiles
    FileCollection sources

    @TaskAction
    def transform() {
    	EGradleXIncludeAwareXmlProvider provider = new EGradleXIncludeAwareXmlProvider()
      //  provider.parse(sourceFile) /* 'dsl.xml' */
      	provider.emptyDoc()
        transformDocument(provider.document)
        provider.write(destFile)
        provider.write(new File(destFile.parent,"dsl-egradle_pretty-printed.xml"),true)
    }

    private def transformDocument(Document doc) {
    	Project p = null
    	DocGenerationException dge = null
    	 
    	/* load metadata */
        ClassMetaDataRepository<ClassMetaData> classRepository = new EGradleSimpleClassMetaDataRepository<ClassMetaData>()
        classRepository.load(classMetaDataFile)
        
        
        //ClassMetaDataRepository<ClassLinkMetaData> linkRepository = new SimpleClassMetaDataRepository<ClassLinkMetaData>()
        //for every method found in class meta, create a javadoc/groovydoc link
       // classRepository.each {name, ClassMetaData metaData ->
       //     linkRepository.put(name, new ClassLinkMetaData(metaData))
       // }
        //def doc = new Document()
        
        //DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        //factory.setNamespaceAware(true);
        //Document doc = factory.newDocumentBuilder().newDocument();
        logger.quiet "before use"
        use(BuildableDOMCategory) {
            EGradleDslDocModel model = createEGradleDslDocModel(doc, classRepository)//, loadPluginsMetaData())
            appendPluginsMetaData(model)
           
             Element rootElement = doc.createElement("root")
             doc.appendChild(rootElement)
             model.classes.each { EGradleClassDoc classDoc ->
               Element classElement = doc.createElement("class")
               classElement.setAttribute("name", classDoc.name)
               logger.info "creating element: $classDoc.name"
               def classMetaData = classDoc.metaData
               if (classDoc.deprecated){
                   classElement.setAttribute("deprecated","true")
               }
               if (classDoc.incubating){
               	   classElement.setAttribute("incubating","true")
               }
               def superClassMetaData = classMetaData.superClass
               if (superClassMetaData){
                	Element superClassElement = doc.createElement("superClass")
               		superClassElement.setAttribute("name",superClassMetaData.className)
               		classElement.appendChild(superClassElement)
               }
               if (classMetaData.interface){
               	 	classElement.setAttribute("interface", "true")
               }
               
               if (classMetaData.groovy){
               	 	classElement.setAttribute("groovy", "true")
               }
               
               if (classMetaData.enum){
               	 	classElement.setAttribute("enum", "true")
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
               		 appendRawCommentText(doc,propertyElement, propertyMetaData)
               		 classElement.appendChild(propertyElement)
               }
               /* methods */
                for (MethodMetaData methodMetaData: classMetaData.declaredMethods){
               		 logger.debug "method $methodMetaData.name"
               		 Element methodElement = doc.createElement("method")
               		 methodElement.setAttribute("name", methodMetaData.name)
               		 TypeMetaData returnType = methodMetaData.returnType
               		 methodElement.setAttribute("returnType", returnType.name)
               		 
               		 for (ParameterMetaData paramMetaData: methodMetaData.parameters){
               		 	  Element paramElement = doc.createElement("param")
               		 	  paramElement.setAttribute("name",paramMetaData.name)
               		 	  paramElement.setAttribute("type", paramMetaData.type.name)
               		 	  
               		 	  methodElement.appendChild(paramElement)
               		 }
               		 appendRawCommentText(doc,methodElement, methodMetaData)
               		 classElement.appendChild(methodElement)
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
               		 classElement.appendChild(pluginElement)
               }
               //logger.quiet "element=$classElement"
                logger.debug "append to root element: $classElement"
               appendRawCommentText(doc,classElement, classMetaData)
               rootElement.appendChild(classElement)
            }
        }

        // linkRepository.store(linksFile)
    }

    @CompileStatic
    EGradleDslDocModel createEGradleDslDocModel(Document document, ClassMetaDataRepository<ClassMetaData> classMetaDataRepository) {
        new EGradleDslDocModel(document, classMetaDataRepository)
    }
    
    def appendRawCommentText(Document doc, Element parentElement, AbstractLanguageElement languageElement){
    	// FIXME ATR, 13.01.2017 : javadoc converter like in docbook done would be nice: see JavadocConverter
    	String rawCommentText = languageElement.getRawCommentText();
    	if (rawCommentText!=null && rawCommentText.length() >0 ){
	    	CDATASection cdata = doc.createCDATASection(rawCommentText);
	    	
	    	Element rawCommentElement = doc.createElement("rawComment")
	    	rawCommentElement.appendChild(cdata);
	    	parentElement.appendChild(rawCommentElement)
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

