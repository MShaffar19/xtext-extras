/*******************************************************************************
 * Copyright (c) 2009, 2017 itemis AG (http://www.itemis.eu) and others.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.xtext.common.types.access.impl;

import org.apache.log4j.Level;
import org.eclipse.xtext.common.types.JvmDeclaredType;
import org.eclipse.xtext.common.types.access.ClassLoaderFromJar;
import org.eclipse.xtext.common.types.testSetups.Bug470767;
import org.eclipse.xtext.common.types.xtext.ui.tests.RefactoringTestLanguageInjectorProvider;
import org.eclipse.xtext.testing.InjectWith;
import org.eclipse.xtext.testing.XtextRunner;
import org.eclipse.xtext.testing.logging.LoggingTester;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.common.collect.Iterables;

/**
 * @author Sebastian Zarnekow - Initial contribution and API
 */
@RunWith(XtextRunner.class)
@InjectWith(RefactoringTestLanguageInjectorProvider.class)
public class JarClasspathTypeProviderTest extends ClasspathTypeProviderTest {

	@Override
	protected ClasspathTypeProvider createTypeProvider() {
		return new ClasspathTypeProvider(new ClassLoaderFromJar(), getResourceSet(), getIndexedJvmTypeAccess(), null);
	}
	
	@Test
	public void testBug470767_02() {
		LoggingTester.captureLogging(Level.ERROR, DeclaredTypeFactory.class, new Runnable() {
			@Override
			public void run() {
				String typeName = Bug470767.class.getName();
				JvmDeclaredType type = (JvmDeclaredType) getTypeProvider().findTypeByName(typeName);
				assertEquals(1, Iterables.size(type.getAllNestedTypes()));
			}
			
		}).assertNoLogEntries();
	}

}
