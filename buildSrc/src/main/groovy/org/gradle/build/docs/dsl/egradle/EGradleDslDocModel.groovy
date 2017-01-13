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

import org.gradle.build.docs.*
import org.gradle.build.docs.dsl.source.*
import org.gradle.build.docs.dsl.source.model.*
import org.gradle.build.docs.model.*
import org.w3c.dom.Document

class EGradleDslDocModel {
    private final Document document
    private final Map<String, EGradleClassDoc> classes = [:]
    private final ClassMetaDataRepository<ClassMetaData> classMetaDataRepository
    private final LinkedList<String> currentlyBuilding = new LinkedList<String>()

    EGradleDslDocModel(Document document, ClassMetaDataRepository<ClassMetaData> classMetaDataRepository) {
        this.document = document
        this.classMetaDataRepository = classMetaDataRepository
        
        initClasses()
    }

	private initClasses(){
		for (String clazzName: classMetaDataRepository.getKeys()){
			ClassMetaData metaData = classMetaDataRepository.get(clazzName);
			EGradleClassDoc classDoc = new EGradleClassDoc(clazzName, metaData);
			classes[clazzName]=classDoc
		}
	}

    boolean isKnownType(String className) {
        return classMetaDataRepository.find(className) != null
    }

 	Collection<EGradleClassDoc> getClasses() {
        return classes.values().findAll { !it.name.contains('.internal.') }
    }
    
    EGradleClassDoc getClassDoc(String className) {
        EGradleClassDoc classDoc = classes[className]
        if (classDoc == null) {
            classDoc = loadClassDoc(className)
            classes[className] = classDoc
            //new ReferencedTypeBuilder(this).build(classDoc)
        }
        return classDoc
    }
}
