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

class EGradleDslDocModel {
    /* use a tree map to have sorted class names*/
    private final TreeMap<String, EGradleClassDoc> classes = [:]
    private final ClassMetaDataRepository<ClassMetaData> classMetaDataRepository
    private final LinkedList<String> currentlyBuilding = new LinkedList<String>()

    EGradleDslDocModel(ClassMetaDataRepository<ClassMetaData> classMetaDataRepository) {
        this.classMetaDataRepository = classMetaDataRepository
        
        initClasses()
    }

	private initClasses(){
		for (String clazzName: classMetaDataRepository.getKeys()){
		       if (clazzName.indexOf("AbstractTask")!=-1){ 
                    System.out.println(">>>>>>>>EGradleDslDocModel: init clazz:"+clazzName);
               }
		
			ClassMetaData metaData = classMetaDataRepository.get(clazzName);
			EGradleClassDoc classDoc = new EGradleClassDoc(clazzName, metaData);
			classes[clazzName]=classDoc
		}
	}

    boolean isKnownType(String className) {
        return classMetaDataRepository.find(className) != null
    }

 	Collection<EGradleClassDoc> getClasses() {
        return classes.values().findAll { isAllowed(it.name) }
    }
    
    /* ATR, 07.03.2017 */
    private boolean isAllowed(String name){ 
        if (name.indexOf('.internal.')==-1){ 
            return true;
        }
        int apiInternalIndex = name.indexOf('org.gradle.api.internal.'); 
        if (apiInternalIndex ==-1){ 
//            System.out.println("skipping:"+name)
            return false;
            // we do only need internal api parts to get full inheritage for SDK
        }
        int count = name.length() - name.replace(".", "").length();
        if (count>4){
            /* currently we are only interested in "org.gradle.api.internal.$xyz" but not in"org.gradle.api.internal.deeper.$xyz" */
            return false
        }
        System.out.println("+++++++++++++++ allowing internal part:"+name)
        return true;
    }
    
    EGradleClassDoc getClassDoc(String className) {
        EGradleClassDoc classDoc = classes[className]
        if (classDoc == null) {
            //classDoc = loadClassDoc(className)
            //classes[className] = classDoc
            //new ReferencedTypeBuilder(this).build(classDoc)
            throw new RuntimeException("class doc is created for ALL classes, so should be always available!")
        }
        return classDoc
    }
}
