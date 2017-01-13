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
import org.gradle.build.docs.dsl.source.model.ClassMetaData
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

    @PathSensitive(PathSensitivity.NONE)
    @InputFile
    File classMetaDataFile

    @PathSensitive(PathSensitivity.NONE)
    @InputFile
    File pluginsMetaDataFile

    @OutputFile
    File destFile

    @OutputFile
    File linksFile

    @PathSensitive(PathSensitivity.RELATIVE)
    @InputFiles
    FileCollection sources

    @TaskAction
    def transform() {
    	XIncludeAwareXmlProvider provider = new XIncludeAwareXmlProvider()
      //  provider.parse(sourceFile) /* 'dsl.xml' */
      	provider.emptyDoc()
        transformDocument(provider.document)
        provider.write(destFile,true)
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
            logger.quiet "use 1"
            EGradleDslDocModel model = createEGradleDslDocModel(doc, classRepository)//, loadPluginsMetaData())
           
             logger.quiet "use 2"
             Element root = doc.createElement("root")
             doc.appendChild(root)
             logger.quiet "root=$root"
             model.classes.each { EGradleClassDoc classDoc ->
               Element clazzElement = doc.createElement("class")
               clazzElement.setAttribute("name", classDoc.getName())
               logger.quiet "root=$root"
               root.appendChild(clazzElement)
            }
        }

        // linkRepository.store(linksFile)
    }

    @CompileStatic
    EGradleDslDocModel createEGradleDslDocModel(Document document, ClassMetaDataRepository<ClassMetaData> classMetaData) {
        // workaround to IBM JDK crash "groovy.lang.GroovyRuntimeException: Could not find matching constructor for..."
        new EGradleDslDocModel(document, classMetaData)
    }

    def loadPluginsMetaData() {
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
                
                /* def extension = extensions[targetClass]
                if (!extension) {
                    extension = new ClassExtensionMetaData(targetClass)
                    extensions[targetClass] = extension
                } */
                
                def mixinClass = e.'@mixinClass'
                
                /* if (mixinClass) {
                    extension.addMixin(pluginId, mixinClass)
                } */
                logger.quiet "mixinClass=$mixinClass"
                def extensionClass = e.'@extensionClass'
                if (extensionClass) {
                    def extensionId = e.'@id'
                    if (!extensionId) {
                        throw new RuntimeException("No id specified for extension '$extensionClass' for plugin '$pluginId'.")
                    }
                    //extension.addExtension(pluginId, extensionId, extensionClass)
                    println "extclass=$extensionClass ,id=$extensionId"
                }
            }
        }
        return extensions
    }
   
}

