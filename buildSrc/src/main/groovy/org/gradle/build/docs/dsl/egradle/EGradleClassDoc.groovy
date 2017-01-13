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

import org.gradle.build.docs.dsl.source.model.ClassMetaData
import org.w3c.dom.*

class EGradleClassDoc{

    private final String className
    private final String id
    private final String simpleName
	final ClassMetaData classMetaData
	
	EGradleClassDoc(String className, ClassMetaData classMetaData){
		this.className = className
		this.classMetaData=classMetaData
        id = className
        simpleName = className.tokenize('.').last()
	}
	
	String getId() { return id }

    String getName() { return className }

    String getSimpleName() { return simpleName }
    
    boolean isDeprecated() {
        return classMetaData.deprecated
    }
    
    boolean isIncubating() {
        return classMetaData.incubating
    }
    
    ClassMetaData getMetaData(){
    	return classMetaData	
    }
    
    
    
}