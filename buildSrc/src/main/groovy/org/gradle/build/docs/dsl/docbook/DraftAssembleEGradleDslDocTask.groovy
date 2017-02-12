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
 *
 * Renamed to Draft
 */
class DraftAssembleEGradleDslDocTask extends AssembleDslDocTask {
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
	
	GenerationListener listener
	
    @TaskAction
    def transform() {
        XIncludeAwareXmlProvider provider = new XIncludeAwareXmlProvider()
        provider.parse(sourceFile)
        transformDocument(provider, provider.document)
    }

    private def transformDocument(XIncludeAwareXmlProvider provider, Document mainDocbookTemplate) {
        listener = new GenerationListener(){
        	public void warning(String paramString){};

			public void start(String paramString){};

			public void finish(){};
        }
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
		/* FIXME ATR, 21.01.2017 : plugin elements missing, javadoc currently not enhanced, no delegateTo generated*/
        def doc = mainDocbookTemplate
        use(BuildableDOMCategory) {
            DslDocModel model = createDslDocModelClosure(loadPluginsMetaData())
       		LinkRenderer linkRenderer = new LinkRenderer(doc, model)
            def root = doc.documentElement
            root.section.table.each { Element table ->
                mergeContent(table, model)
            }
            
            File destVersionFolder=new File(destFolder,buildGradleVersion);
            
            model.classes.each { ClassDoc classDoc ->
                 //generateDocForType(root.ownerDocument, model, linkRepository, it)
             	 
             	 createNewDocument(provider, classDoc,linkRenderer)
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
            new ClassDocRenderer(new LinkRenderer(document, model)).mergeContent(classDoc, doc.documentElement)
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
    def createNewDocument(XIncludeAwareXmlProvider provider, ClassDoc classDoc,LinkRenderer linkRenderer){
			 provider.emptyDoc()
			 Document doc = provider.document
			 //JavadocConverter jdConverter = new JavadocConverter(doc, linkBuilder);
			 
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
               		 appendDescription(classDoc, doc,propertyElement, propertyMetaData)
               		 typeElement.appendChild(propertyElement)
               }
               /* methods */
                for (MethodMetaData methodMetaData: classMetaData.declaredMethods){
               		 logger.debug "method $methodMetaData.name"
               		 Element methodElement = doc.createElement("method")
               		 
               		 methodElement.setAttribute("signature", methodMetaData.getSignature())
               		 methodElement.setAttribute("name", methodMetaData.name)
               		 TypeMetaData returnType = methodMetaData.returnType
               		 methodElement.setAttribute("returnType", returnType.name)
               		 
               		 for (ParameterMetaData paramMetaData: methodMetaData.parameters){
               		 	  Element paramElement = doc.createElement("parameter")
               		 	  paramElement.setAttribute("signature", paramMetaData.getSignature())
               		 	  paramElement.setAttribute("name",paramMetaData.name)
               		 	  paramElement.setAttribute("type", paramMetaData.type.name)
               		 	  
               		 	  methodElement.appendChild(paramElement)
               		 }
               		 appendDescription(classDoc, doc,methodElement, methodMetaData)
               		 typeElement.appendChild(methodElement)
               }
               logger.debug "append to root element: $typeElement"
               appendDescription(classDoc,doc,typeElement, classMetaData)
               if (false){
               		return;
               }
               Element blockDocsElement = doc.createElement("blockDocs");
               typeElement.appendChild(blockDocsElement);
               
               /* block docs*/
               for (ClassExtensionDoc extensionDoc : classDoc.getClassExtensions()) {
	                for (BlockDoc blockDoc : extensionDoc.getExtensionBlocks()) {
           
                  		Element blockDocElement = doc.createElement("blockDoc");
               			blockDocsElement.appendChild(blockDocElement);
                    	blockDocElement.setAttribute("multiValued",""+blockDoc.isMultiValued())
			            TypeMetaData metaData = blockDoc.getType();
			            blockDocElement.setAttribute("type", metaData.getName());
			            blockDocElement.setAttribute("id", blockDoc.getBlockProperty().getId());
			            blockDocElement.setAttribute("name", blockDoc.getBlockProperty().getName());
   					}
		        }
               /* plugin meta extensions */
               for (ClassExtensionMetaData me: classMetaData.metaPluginExtensions){
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
               
               
	}

    def appendDescription(ClassDoc classDoc, Document doc, Element parentElement, AbstractLanguageElement languageElement){
    	// FIXME ATR, 13.01.2017 : javadoc converter like in docbook done would be nice: see JavadocConverter
    	String descriptionText =null;
    	DslElementDoc elementDoc=null;
    	logger.quiet "append description: $languageElement"
    	if (languageElement instanceof ClassMetaData){
    		elementDoc=classDoc;
    		logger.quiet "identified as classDoc"
    	}else if (languageElement instanceof MethodMetaData){
    		// FIXME ATR, 21.01.2017: implement search for MethodDoc
    	}else if (languageElement instanceof PropertyMetaData){
    		// FIXME ATR, 21.01.2017: implement search for PropertyDoc
    	}
    	
    	if (elementDoc!=null){
    		try{
	    		Element element = elementDoc.getDescription();
	    		if (element!=null){
	    			logger.quiet "-- element not null"
	    			/* FIXME ATR, check if this correct...*/
	    			Element descriptionElement = doc.createElement("description")
	    			Node duplicated = doc.importNode(element,true);
	    			descriptionElement.appendChild(duplicated);
	    			parentElement.appendChild(descriptionElement)
	    			//return;
	    		
	    			//logger.quiet "--- found descripton element:$element"
	    			//descriptionText= transformDescriptionElement(element);
	    		}else{
	    			logger.quiet "--- found NO descripton element:$element"
	    		}
	    		 Element commentsElement = doc.createElement("comments")
	    		 List<Element> comments = elementDoc.getComment();
	    		 for (Element element3: comments){
	    			Node duplicated = doc.importNode(element3,true);
	    			commentsElement.appendChild(duplicated);
	    			parentElement.appendChild(commentsElement)
	    		 }
	    		 return;
	    	}catch(RuntimeException e){
	    		/* ignore */
	    		logger.quiet "was not able to get description: $e"
	    	}
    		
    	}
    	if (descriptionText==null || descriptionText.length()==0){
    		descriptionText= languageElement.getRawCommentText();
    	}
    	
    	
    	if (descriptionText!=null && descriptionText.length() >0 ){
	    	CDATASection cdata = doc.createCDATASection(descriptionText);
	    	
	    	Element descriptionElement = doc.createElement("description")
	    	descriptionElement.appendChild(cdata);
	    	parentElement.appendChild(descriptionElement)
    	}
    }
    
    String transformDescriptionElement(Node element){
    	/*<para>content</para>*/
    	if (element==null){
    		return null
    	}
    	if (!element.hasChildNodes()){
    		return null
    	}
    	NodeList nodeList = element.getChildNodes();
    	StringBuilder sb = new StringBuilder();
    	for (int i=0;i<nodeList.getLength();i++){
    		Node node = nodeList.item(i);
    		String data = node.getNodeValue();
    		if (data!=null){
    			sb.append(data);
    			sb.append("<br><br>\n\n");
    		}
    		if  (node.hasChildNodes()){
    			NodeList nodeList2 = node.getChildNodes();
    			for (int j=0;j<nodeList2.getLength();j++){
    				Node node2 = nodeList2.item(j); 
    				String subText = transformDescriptionElement(node2);
    				if (subText!=null){
    					sb.append(subText);
    				}
    			}
    		}
    	}
    	return sb.toString();
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

