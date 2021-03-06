/*******************************************************************************
 * Copyright (c) 2012, 2017 itemis AG (http://www.itemis.eu) and others.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.xtext.common.types.access.reflect;

import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.xtext.common.types.JvmDeclaredType;
import org.eclipse.xtext.common.types.access.impl.AbstractTypeProviderPerformanceTest;
import org.eclipse.xtext.common.types.access.impl.IndexedJvmTypeAccess;
import org.eclipse.xtext.common.types.access.impl.TypeResourceServices;
import org.eclipse.xtext.common.types.xtext.ui.tests.RefactoringTestLanguageInjectorProvider;
import org.eclipse.xtext.testing.InjectWith;
import org.eclipse.xtext.testing.XtextRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;

import com.google.inject.Inject;

/**
 * @author Sven Efftinge - Initial contribution and API
 */
@RunWith(XtextRunner.class)
@InjectWith(RefactoringTestLanguageInjectorProvider.class)
public class ReflectionTypeProviderPerformanceTest extends AbstractTypeProviderPerformanceTest {

	@Inject
	private ResourceSet resourceSet;

	@Inject
	private IndexedJvmTypeAccess indexedJvmTypeAccess;
	
	@Inject
	private TypeResourceServices services;
	
	private ReflectionTypeProvider typeProvider;

	@Before
	public void setUp() throws Exception {
		typeProvider = new ReflectionTypeProvider(getClass().getClassLoader(), resourceSet, indexedJvmTypeAccess, services);
	}
	
	@After
	public void tearDown() throws Exception {
		typeProvider = null;
	}
	
	@Override
	public ReflectionTypeProvider getTypeProvider() {
		return typeProvider;
	}
	
	@Override
	protected JvmDeclaredType loadAndResolve(String name, boolean accessMembers, boolean accessAnnotations,
			boolean accessTypeParams, boolean accessParameter, boolean accessParameterNames) {
		resourceSet.getResources().clear();
		return super.loadAndResolve(name, accessMembers, accessAnnotations, accessTypeParams, accessParameter,
				accessParameterNames);
	}
	

}
