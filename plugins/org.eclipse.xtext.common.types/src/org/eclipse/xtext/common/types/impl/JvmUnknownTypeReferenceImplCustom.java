/*******************************************************************************
 * Copyright (c) 2011 itemis AG (http://www.itemis.eu) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.xtext.common.types.impl;

import org.eclipse.xtext.common.types.JvmType;

/**
 * @author Sebastian Zarnekow - Initial contribution and API
 */
public class JvmUnknownTypeReferenceImplCustom extends JvmUnknownTypeReferenceImpl {

	@Override
	public JvmType getType() {
		return null;
	}
	
}
