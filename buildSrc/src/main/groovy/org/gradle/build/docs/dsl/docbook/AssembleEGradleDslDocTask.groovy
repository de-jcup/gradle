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
package org.gradle.build.docs.dsl.docbook
import groovy.transform.CompileStatic
import org.gradle.api.*
import org.gradle.api.tasks.*
import org.gradle.build.docs.*
import org.gradle.build.docs.dsl.docbook.model.*
import org.gradle.build.docs.dsl.links.*
import org.gradle.build.docs.dsl.source.model.*
import org.gradle.build.docs.model.*
import org.w3c.dom.*

/**
 * This task is based by AssembleDslDocTask (it is a copy). It 
 * is still necessary to extend AssembleDslDocTask because of 
 * gradle build settings for dslDocbook
 * 
 * The task will generate egradle xml files for DSL code completion.
 * So javadoc and block data is available in generation
 */
class AssembleEGradleDslDocTask extends AssembleDslDocTask {
    @InputFile
    File sourceFile
    
    @InputFile
    File classMetaDataFile
    
    @InputFile
    File pluginsMetaDataFile
    
    @InputDirectory
    File classDocbookDir
    
    @OutputDirectory
    File destFolder
    
    @OutputFile
    File linksFile

	String buildGradleVersion

    @TaskAction
    def transform() {
        XIncludeAwareXmlProvider provider = new XIncludeAwareXmlProvider()
        provider.parse(sourceFile)
        transformDocument(provider, provider.document)
    }

    private def transformDocument(XIncludeAwareXmlProvider provider, Document mainDocbookTemplate) {
        buildGradleVersion = getProject().getRootProject().file("version.txt").text.trim()
        
        ClassMetaDataRepository<ClassMetaData> classRepository = new SimpleClassMetaDataRepository<ClassMetaData>()
        classRepository.load(classMetaDataFile)
        ClassMetaDataRepository<ClassLinkMetaData> linkRepository = new SimpleClassMetaDataRepository<ClassLinkMetaData>()
        //for every method found in class meta, create a javadoc/groovydoc link
        classRepository.each {name, ClassMetaData metaData ->
            linkRepository.put(name, new ClassLinkMetaData(metaData))
        }

        // workaround to IBM JDK bug
        def createDslDocModelClosure = this.&createDslDocModel.curry(classDocbookDir, mainDocbookTemplate, classRepository)

        def doc = mainDocbookTemplate
        use(BuildableDOMCategory) {
            DslDocModel model = createDslDocModelClosure(loadPluginsMetaData())
            def root = doc.documentElement
            root.section.table.each { Element table ->
                mergeContent(table, model)
            }
            
            File destVersionFolder=new File(destFolder,buildGradleVersion);
            
            model.classes.each { ClassDoc classDoc ->
                 //generateDocForType(root.ownerDocument, model, linkRepository, it)
             	 
             	 createNewDocument(provider, classDoc)
                 String fileName = classDoc.name.replaceAll("\\.","/")
                 File file = new File(destVersionFolder,fileName+".xml")
                 file.parentFile.mkdirs()
                 provider.write(file,true)
            }
        }

        linkRepository.store(linksFile)
    }

    @CompileStatic
    DslDocModel createDslDocModel(File classDocbookDir, Document document, ClassMetaDataRepository<ClassMetaData> classMetaData, Map<String, ClassExtensionMetaData> extensionMetaData) {
        // workaround to IBM JDK crash "groovy.lang.GroovyRuntimeException: Could not find matching constructor for..."
        new DslDocModel(classDocbookDir, document, classMetaData, extensionMetaData)
    }

    def loadPluginsMetaData() {
        XIncludeAwareXmlProvider provider = new XIncludeAwareXmlProvider()
        provider.parse(pluginsMetaDataFile)
        Map<String, ClassExtensionMetaData> extensions = [:]
        provider.root.plugin.each { Element plugin ->
            def pluginId = plugin.'@id'
            if (!pluginId) {
                throw new RuntimeException("No id specified for plugin: ${plugin.'@description' ?: 'unknown'}")
            }
            plugin.extends.each { Element e ->
                def targetClass = e.'@targetClass'
                if (!targetClass) {
                    throw new RuntimeException("No targetClass specified for extension provided by plugin '$pluginId'.")
                }
                def extension = extensions[targetClass]
                if (!extension) {
                    extension = new ClassExtensionMetaData(targetClass)
                    extensions[targetClass] = extension
                }
                def mixinClass = e.'@mixinClass'
                if (mixinClass) {
                    extension.addMixin(pluginId, mixinClass)
                }
                def extensionClass = e.'@extensionClass'
                if (extensionClass) {
                    def extensionId = e.'@id'
                    if (!extensionId) {
                        throw new RuntimeException("No id specified for extension '$extensionClass' for plugin '$pluginId'.")
                    }
                    extension.addExtension(pluginId, extensionId, extensionClass)
                }
            }
        }
        return extensions
    }

    def mergeContent(Element typeTable, DslDocModel model) {
        def title = typeTable.title[0].text()

        //TODO below checks makes it harder to add new sections
        //because the new section will work correctly only when the section title ends with 'types' :)
        if (title.matches('(?i).* types')) {
            mergeTypes(typeTable, model)
        } else if (title.matches('(?i).* blocks')) {
            mergeBlocks(typeTable, model)
        } else {
            return
        }

        typeTable['@role'] = 'dslTypes'
    }

    def mergeBlocks(Element blocksTable, DslDocModel model) {
        blocksTable.addFirst {
            thead {
                tr {
                    td('Block')
                    td('Description')
                }
            }
        }

        def project = model.getClassDoc(Project.class.name)

        blocksTable.tr.each { Element tr ->
            mergeBlock(tr, project)
        }
    }

    def mergeBlock(Element tr, ClassDoc project) {
        String blockName = tr.td[0].text().trim()
        BlockDoc blockDoc = project.getBlock(blockName)
        tr.children = {
            td { link(linkend: blockDoc.id) { literal("$blockName { }")} }
            td(blockDoc.description)
        }
    }

    def mergeTypes(Element typeTable, DslDocModel model) {
        typeTable.addFirst {
            thead {
                tr {
                    td('Type')
                    td('Description')
                }
            }
        }

        typeTable.tr.each { Element tr ->
            mergeType(tr, model)
        }
    }

    def mergeType(Element typeTr, DslDocModel model) {
        String className = typeTr.td[0].text().trim()
        ClassDoc classDoc = model.getClassDoc(className)
        typeTr.children = {
            td {
                link(linkend: classDoc.id) { literal(classDoc.simpleName) }
            }
            td(classDoc.description)
        }
    }

    def generateDocForType(Document document, DslDocModel model, ClassMetaDataRepository<ClassLinkMetaData> linkRepository, ClassDoc classDoc) {
        try {
            //classDoc renderer renders the content of the class and also links to properties/methods
            new ClassDocRenderer(new LinkRenderer(document, model)).mergeContent(classDoc, document.documentElement)
            def linkMetaData = linkRepository.get(classDoc.name)
            linkMetaData.style = LinkMetaData.Style.Dsldoc
            classDoc.classMethods.each { methodDoc ->
                linkMetaData.addMethod(methodDoc.metaData, LinkMetaData.Style.Dsldoc)
            }
            classDoc.classBlocks.each { blockDoc ->
                linkMetaData.addBlockMethod(blockDoc.blockMethod.metaData)
            }
            classDoc.classProperties.each { propertyDoc ->
                linkMetaData.addGetterMethod(propertyDoc.name, propertyDoc.metaData.getter)
            }
        } catch (Exception e) {
            throw new DocGenerationException("Failed to generate documentation for class '$classDoc.name'.", e)
        }
    }
    
    /* ------------------------------------------------------------------------------------- */
    /* -------------------------- Own parts ------------------------------------------------ */
    /* ------------------------------------------------------------------------------------- */
    def createNewDocument(XIncludeAwareXmlProvider provider, ClassDoc classDoc){
			 provider.emptyDoc()
			 Document doc = provider.document
			 
			 Element typeElement = doc.createElement("type")
             doc.appendChild(typeElement)
	
             typeElement.setAttribute("language","gradle")
             typeElement.setAttribute("version", buildGradleVersion)
             
               typeElement.setAttribute("name", classDoc.name)
               logger.info "creating element: $classDoc.name"
               
               ClassMetaData classMetaData = classDoc.classMetaData
               if (classDoc.deprecated){
                   typeElement.setAttribute("deprecated","true")
               }
               if (classDoc.incubating){
               	   typeElement.setAttribute("incubating","true")
               }
               ClassMetaData superClassMetaData = classMetaData.superClass
               if (superClassMetaData){
                	Element superClassElement = doc.createElement("superClass")
               		superClassElement.setAttribute("name",superClassMetaData.className)
               		typeElement.appendChild(superClassElement)
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
               //for (EGradleClassMetaPluginExtension me: classDoc.metaPluginExtensions){
               //      Element pluginElement = doc.createElement("plugin")
               //		 pluginElement.setAttribute("id", me.pluginId)
               //      if (me.mixinClass){
               //      	  Element mixinElement = doc.createElement("mixin")
               //		      mixinElement.setAttribute("class", me.mixinClass)
               //		      pluginElement.appendChild(mixinElement)
               //      }
               //      if (me.extensionId){
               //       	  Element extensionElement = doc.createElement("extension")
               //		      extensionElement.setAttribute("id", me.extensionId)
               //		      extensionElement.setAttribute("class", me.extensionClass)
               //		      pluginElement.appendChild(extensionElement)
               //      }
               //		 typeElement.appendChild(pluginElement)
               //}
               logger.debug "append to root element: $typeElement"
               appendDescription(doc,typeElement, classMetaData)
               
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
    
    

    def appendPluginsMetaData(DslDocModel model) {
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
                //EGradleClassMetaPluginExtension me = new EGradleClassMetaPluginExtension();
                //me.pluginId=pluginId
                //if (mixinClass) {
                //    me.mixinClass=mixinClass
                //}
                //logger.quiet "mixinClass=$mixinClass"
                //def extensionClass = e.'@extensionClass'
                //if (extensionClass) {
//                    def extensionId = e.'@id'
              //      if (!extensionId) {
  //                      throw new RuntimeException("No id specified for extension '$extensionClass' for plugin '$pluginId'.")
    //                }
      ////              //extension.addExtension(pluginId, extensionId, extensionClass)
          //          me.extensionId=extensionId
            //        me.extensionClass=extensionClass
                    
               // }
             	//classDoc.metaPluginExtensions.add(me)   
            }
        }
        return extensions
    }
}

