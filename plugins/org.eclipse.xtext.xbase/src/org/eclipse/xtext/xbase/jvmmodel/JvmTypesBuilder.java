/*******************************************************************************
 * Copyright (c) 2011 itemis AG (http://www.itemis.eu) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.xtext.xbase.jvmmodel;

import static com.google.common.collect.Iterables.*;
import static com.google.common.collect.Maps.*;
import static org.eclipse.xtext.util.Strings.*;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.common.notify.Adapter;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.xtext.common.types.JvmAnnotationAnnotationValue;
import org.eclipse.xtext.common.types.JvmAnnotationReference;
import org.eclipse.xtext.common.types.JvmAnnotationTarget;
import org.eclipse.xtext.common.types.JvmAnnotationType;
import org.eclipse.xtext.common.types.JvmAnnotationValue;
import org.eclipse.xtext.common.types.JvmBooleanAnnotationValue;
import org.eclipse.xtext.common.types.JvmConstructor;
import org.eclipse.xtext.common.types.JvmCustomAnnotationValue;
import org.eclipse.xtext.common.types.JvmDeclaredType;
import org.eclipse.xtext.common.types.JvmEnumerationLiteral;
import org.eclipse.xtext.common.types.JvmEnumerationType;
import org.eclipse.xtext.common.types.JvmExecutable;
import org.eclipse.xtext.common.types.JvmField;
import org.eclipse.xtext.common.types.JvmFormalParameter;
import org.eclipse.xtext.common.types.JvmGenericType;
import org.eclipse.xtext.common.types.JvmIdentifiableElement;
import org.eclipse.xtext.common.types.JvmMember;
import org.eclipse.xtext.common.types.JvmOperation;
import org.eclipse.xtext.common.types.JvmParameterizedTypeReference;
import org.eclipse.xtext.common.types.JvmStringAnnotationValue;
import org.eclipse.xtext.common.types.JvmType;
import org.eclipse.xtext.common.types.JvmTypeAnnotationValue;
import org.eclipse.xtext.common.types.JvmTypeReference;
import org.eclipse.xtext.common.types.JvmVisibility;
import org.eclipse.xtext.common.types.TypesFactory;
import org.eclipse.xtext.common.types.TypesPackage;
import org.eclipse.xtext.common.types.util.TypeReferences;
import org.eclipse.xtext.documentation.IEObjectDocumentationProvider;
import org.eclipse.xtext.naming.QualifiedName;
import org.eclipse.xtext.util.Pair;
import org.eclipse.xtext.util.Strings;
import org.eclipse.xtext.util.Tuples;
import org.eclipse.xtext.xbase.XBooleanLiteral;
import org.eclipse.xtext.xbase.XExpression;
import org.eclipse.xtext.xbase.XNumberLiteral;
import org.eclipse.xtext.xbase.XStringLiteral;
import org.eclipse.xtext.xbase.XTypeLiteral;
import org.eclipse.xtext.xbase.XbasePackage;
import org.eclipse.xtext.xbase.annotations.xAnnotations.XAnnotation;
import org.eclipse.xtext.xbase.annotations.xAnnotations.XAnnotationElementValuePair;
import org.eclipse.xtext.xbase.annotations.xAnnotations.XAnnotationValueArray;
import org.eclipse.xtext.xbase.annotations.xAnnotations.XAnnotationsPackage;
import org.eclipse.xtext.xbase.compiler.CompilationStrategyAdapter;
import org.eclipse.xtext.xbase.compiler.DocumentationAdapter;
import org.eclipse.xtext.xbase.compiler.output.ITreeAppendable;
import org.eclipse.xtext.xbase.lib.IterableExtensions;
import org.eclipse.xtext.xbase.lib.ListExtensions;
import org.eclipse.xtext.xbase.lib.Procedures;
import org.eclipse.xtext.xbase.lib.Procedures.Procedure1;
import org.eclipse.xtext.xbase.typing.NumberLiterals;

import com.google.inject.Inject;

/**
 * A set of factory and builder functions, used to create instances of ({@link TypesPackage}).
 * 
 * It's meant to be used from an implementation of {@link IJvmModelInferrer}.
 * 
 * @author Sven Efftinge - Initial contribution and API
 */
@NonNullByDefault
public class JvmTypesBuilder {

	@Inject
	private IJvmModelAssociator associator;

	@Inject
	private TypeReferences references;

	@Inject
	private IEObjectDocumentationProvider documentationProvider;
	
	@Inject
	private NumberLiterals numberLiterals;

	@Inject
	private TypesFactory typesFactory;
	
	/**
	 * Sets the given {@link JvmExecutable} as the logical container for the given {@link XExpression}.
	 * This defines the context and the scope for the given expression. Also it defines how the given JvmExecutable can be executed.
	 * For instance {@link org.eclipse.xtext.xbase.compiler.JvmModelGenerator} automatically translates any given {@link XExpression} 
	 * into corresponding Java source code as the body of the given {@link JvmExecutable}. 
	 * 
	 * @param logicalContainer
	 *            the {@link JvmExecutable} the expression is associated with. Must not be <code>null</code>.
	 * @param expr
	 *            the expression. Can be <code>null</code> in which case this function does nothing.
	 */
	public void setBody(@Nullable JvmExecutable logicalContainer, @Nullable XExpression expr) {
		if (expr == null)
			return;
		removeExistingBody(logicalContainer);
		associator.associateLogicalContainer(expr, logicalContainer);
	}
	
	protected void removeExistingBody(@Nullable JvmMember member) {
		if(member != null) {
			// remove old adapters
			Iterator<Adapter> iterator = member.eAdapters().iterator();
			while (iterator.hasNext()) {
				if (iterator.next() instanceof CompilationStrategyAdapter) {
					iterator.remove();
				}
			}
			associator.removeLogicalChildAssociation(member);
		}
	}

	/**
	 * Attaches the given compile strategy to the given {@link JvmExecutable} such that the compiler knows how to
	 * implement the {@link JvmExecutable} when it is translated to Java source code.
	 * @param executable the operation or constructor to add the method body to.
	 * @param strategy the compilation strategy.
	 */
	public void setBody(@Nullable JvmExecutable executable, @Nullable Procedures.Procedure1<ITreeAppendable> strategy) {
		removeExistingBody(executable);
		setCompilationStrategy(executable, strategy);
	}
	
	/**
	 * Retrieves the attached documentation for the given source element.
	 * By default this implementation provides the text of a multi line comment preceding the definition of the given source element.
	 */
	@Nullable
	public String getDocumentation(@Nullable EObject source) {
		if (source == null)
			return null;
		if (source instanceof JvmIdentifiableElement) {
			DocumentationAdapter adapter = (DocumentationAdapter) EcoreUtil.getAdapter(source.eAdapters(), DocumentationAdapter.class);
			if (adapter != null)
				return adapter.getDocumentation();
		}
		String documentation = documentationProvider.getDocumentation(source);
		return documentation;
	}

	/**
	 * Attaches the given documentation to the given jvmElement.
	 * 
	 */
	public void setDocumentation(@Nullable JvmIdentifiableElement jvmElement, @Nullable String documentation) {
		if(jvmElement != null) { 
			if (documentation == null)
				return;
			DocumentationAdapter documentationAdapter = new DocumentationAdapter();
			documentationAdapter.setDocumentation(documentation);
			jvmElement.eAdapters().add(documentationAdapter);
		}
	}


	/**
	 * Creates a public class declaration, associated to the given sourceElement. It sets the given name, which might be
	 * fully qualified using the standard Java notation.
	 * 
	 * @param sourceElement
	 *            the sourceElement the resulting element is associated with.
	 * @param name
	 *            the qualifiedName of the resulting class.
	 * 
	 * @return a {@link JvmGenericType} representing a Java class of the given name.
	 */
	@Nullable 
	public JvmGenericType toClass(@Nullable EObject sourceElement, @Nullable QualifiedName name) {
		return toClass(sourceElement, name!=null?name.toString():null, null);
	}
	/**
	 * Creates a public class declaration, associated to the given sourceElement. It sets the given name, which might be
	 * fully qualified using the standard Java notation.
	 * 
	 * @param sourceElement
	 *            the sourceElement the resulting element is associated with.
	 * @param name
	 *            the qualifiedName of the resulting class.
	 * 
	 * @return a {@link JvmGenericType} representing a Java class of the given name.
	 */
	@Nullable 
	public JvmGenericType toClass(@Nullable EObject sourceElement, @Nullable String name) {
		return toClass(sourceElement, name, null);
	}
	
	/**
	 * Creates a public class declaration, associated to the given sourceElement. It sets the given name, which might be
	 * fully qualified using the standard Java notation.
	 * 
	 * @param sourceElement
	 *            the sourceElement the resulting element is associated with.
	 * @param name
	 *            the qualifiedName of the resulting class.
	 * @param initializer
	 *            the initializer to apply on the created class element
	 * 
	 * @return a {@link JvmGenericType} representing a Java class of the given name.
	 */
	@Nullable 
	public JvmGenericType toClass(@Nullable EObject sourceElement, @Nullable QualifiedName name, @Nullable Procedure1<JvmGenericType> initializer) {
		return toClass(sourceElement, name!=null ? name.toString() : null, initializer);
	}
	
	/**
	 * Creates a public class declaration, associated to the given sourceElement. It sets the given name, which might be
	 * fully qualified using the standard Java notation.
	 * 
	 * @param sourceElement
	 *            the sourceElement the resulting element is associated with.
	 * @param name
	 *            the qualifiedName of the resulting class.
	 * @param initializer
	 *            the initializer to apply on the created class element
	 * 
	 * @return a {@link JvmGenericType} representing a Java class of the given name.
	 */
	@Nullable 
	public JvmGenericType toClass(@Nullable EObject sourceElement, @Nullable String name, @Nullable Procedure1<JvmGenericType> initializer) {
		final JvmGenericType result = createJvmGenericType(sourceElement, name);
		if (result == null)
			return null;
		associate(sourceElement, result);
		if(initializer != null) 
			initializer.apply(result);

		return result;
	}
	
	/**
	 * Creates a public interface declaration, associated to the given sourceElement. It sets the given name, which might be
	 * fully qualified using the standard Java notation.
	 * 
	 * @param sourceElement
	 *            the sourceElement the resulting element is associated with.
	 * @param name
	 *            the qualifiedName of the resulting class.
	 * @param initializer
	 *            the initializer to apply on the created interface declaration.
	 * 
	 * @return a {@link JvmGenericType} representing a Java interface of the given name.
	 */
	@Nullable 
	public JvmGenericType toInterface(@Nullable EObject sourceElement, @Nullable String name, @Nullable Procedure1<JvmGenericType> initializer) {
		final JvmGenericType result = createJvmGenericType(sourceElement, name);
		if (result == null)
			return null;
		result.setInterface(true);
		associate(sourceElement, result);
		if(initializer != null) 
			initializer.apply(result);

		return result;
	}
	
	/**
	 * Creates a public annotation declaration, associated to the given sourceElement. It sets the given name, which might be
	 * fully qualified using the standard Java notation.
	 * 
	 * @param sourceElement
	 *            the sourceElement the resulting element is associated with.
	 * @param name
	 *            the qualifiedName of the resulting class.
	 * @param initializer
	 *            the initializer to apply on the created annotation
	 * 
	 * @return a {@link JvmAnnotationType} representing a Java annatation of the given name.
	 */
	@Nullable 
	public JvmAnnotationType toAnnotationType(@Nullable EObject sourceElement, @Nullable String name, @Nullable Procedure1<JvmAnnotationType> initializer) {
		if (sourceElement == null)
			return null;
		if (name == null)
			return null;
		Pair<String, String> fullName = splitQualifiedName(name);
		JvmAnnotationType annotationType = typesFactory.createJvmAnnotationType();
		annotationType.setSimpleName(fullName.getSecond());
		if (fullName.getFirst() != null)
			annotationType.setPackageName(fullName.getFirst());
		associate(sourceElement, annotationType);
		if(initializer != null) 
			initializer.apply(annotationType);

		return annotationType;
	}
	
	/**
	 * Creates a public enum declaration, associated to the given sourceElement. It sets the given name, which might be
	 * fully qualified using the standard Java notation.
	 * 
	 * @param sourceElement
	 *            the sourceElement the resulting element is associated with.
	 * @param name
	 *            the qualifiedName of the resulting class.
	 * @param initializer
	 *            the initializer to apply on the created enumeration type
	 * 
	 * @return a result representing a Java enum type with the given name.
	 */
	@Nullable 
	public JvmEnumerationType toEnumerationType(@Nullable EObject sourceElement, @Nullable String name, @Nullable Procedure1<JvmEnumerationType> initializer) {
		if (sourceElement == null)
			return null;
		if (name == null)
			return null;
		Pair<String, String> fullName = splitQualifiedName(name);
		JvmEnumerationType result = typesFactory.createJvmEnumerationType();
		result.setSimpleName(fullName.getSecond());
		result.setVisibility(JvmVisibility.PUBLIC);
		if (fullName.getFirst() != null)
			result.setPackageName(fullName.getFirst());
		associate(sourceElement, result);
		if(initializer != null) 
			initializer.apply(result);

		return result;
	}
	
	/**
	 * Creates a public enumeration literal, associated to the given sourceElement.
	 * 
	 * @param sourceElement
	 *            the sourceElement the resulting element is associated with.
	 * @param name
	 *            the simple name of the resulting enumeration literal.
	 * 
	 * @return a result representing a Java enumeration literal with the given name.
	 */
	@Nullable 
	public JvmEnumerationLiteral toEnumerationLiteral(@Nullable EObject sourceElement, @Nullable String name) {
		return toEnumerationLiteral(sourceElement, name, null);
	}
	
	/**
	 * Same as {@link #toEnumerationLiteral(EObject, String)} but with an initializer passed as the last argument.
	 */
	@Nullable 
	public JvmEnumerationLiteral toEnumerationLiteral(@Nullable EObject sourceElement, @Nullable String name, @Nullable Procedure1<JvmEnumerationLiteral> initializer) {
		JvmEnumerationLiteral result = typesFactory.createJvmEnumerationLiteral();
		result.setSimpleName(name);
		result.setVisibility(JvmVisibility.PUBLIC);
		associate(sourceElement, result);
		if(initializer != null) 
			initializer.apply(result);
		return result;
	}
	
	@Nullable 
	protected JvmGenericType createJvmGenericType(@Nullable EObject sourceElement, @Nullable String name) {
		if (sourceElement == null)
			return null;
		if (name == null)
			return null;
		Pair<String, String> fullName = splitQualifiedName(name);
		final JvmGenericType result = typesFactory.createJvmGenericType();
		result.setSimpleName(fullName.getSecond());
		if (fullName.getFirst() != null)
			result.setPackageName(fullName.getFirst());
		result.setVisibility(JvmVisibility.PUBLIC);
		return result;
	}

	protected Pair<String, String> splitQualifiedName(String name) {
		String simpleName = name;
		String packageName = null;
		final int dotIdx = name.lastIndexOf('.');
		if (dotIdx != -1) {
			simpleName = name.substring(dotIdx + 1);
			packageName = name.substring(0, dotIdx);
		}
		Pair<String,String> fullName = Tuples.create(packageName, simpleName);
		return fullName;
	}

	/**
	 * Creates a private field with the given name and the given type associated to the given sourceElement.
	 * 
	 * @param sourceElement the sourceElement the resulting element is associated with.
	 * @param name the simple name of the resulting field.
	 * @param typeRef the type of the field
	 * 
	 * @return a {@link JvmField} representing a Java field with the given simple name and type.
	 */
	public @Nullable JvmField toField(@Nullable EObject sourceElement, @Nullable String name, @Nullable JvmTypeReference typeRef) {
		return toField(sourceElement, name, typeRef, null);
	}
	
	/**
	 * Same as {@link #toField(EObject, String, JvmTypeReference)} but with an initializer passed as the last argument.
	 */
	@Nullable	
	public JvmField toField(@Nullable EObject sourceElement, @Nullable String name, @Nullable JvmTypeReference typeRef, @Nullable Procedure1<JvmField> initializer) {
		JvmField result = typesFactory.createJvmField();
		result.setSimpleName(name);
		result.setVisibility(JvmVisibility.PRIVATE);
		result.setType(cloneWithProxies(typeRef));
		if (initializer != null && name != null)
			initializer.apply(result);
		return associate(sourceElement, result);
	}

	/**
	 * Associates a source element with a target element. This association is used for tracing. Navigation, for
	 * instance, uses this information to find the real declaration of a Jvm element.
	 * 
	 * @see IJvmModelAssociator
	 * @see IJvmModelAssociations
	 */
	@Nullable
	public <T extends JvmIdentifiableElement> T associate(@Nullable EObject sourceElement, @Nullable T target) {
		if(sourceElement != null && target != null)
			associator.associate(sourceElement, target);
		return target;
	}

	/**
	 * Creates a public method with the given name and the given return type and associates it with the given
	 * sourceElement.
	 */
	@Nullable
	public JvmOperation toMethod(@Nullable EObject sourceElement, @Nullable String name, @Nullable JvmTypeReference returnType,
			@Nullable Procedure1<JvmOperation> init) {
		JvmOperation result = typesFactory.createJvmOperation();
		result.setSimpleName(name);
		result.setVisibility(JvmVisibility.PUBLIC);
		result.setReturnType(cloneWithProxies(returnType));
		associate(sourceElement, result);
		if (init != null)
			init.apply(result);
		return result;
	}

	/**
	 * Creates a getter method for the given properties name with a simple implementation returning the value of a
	 * similarly named field.
	 * 
	 * Example: <code>
	 * public String getFoo() {
	 *   return this.foo;
	 * }
	 * </code>
	 */
	@Nullable
	public JvmOperation toGetter(@Nullable final EObject sourceElement, @Nullable final String name, @Nullable JvmTypeReference typeRef) {
		if(sourceElement == null || name == null) 
			return null;
		JvmOperation result = typesFactory.createJvmOperation();
		result.setVisibility(JvmVisibility.PUBLIC);
		result.setSimpleName("get" + Strings.toFirstUpper(name));
		result.setReturnType(cloneWithProxies(typeRef));
		setBody(result, new Procedures.Procedure1<ITreeAppendable>() {
			public void apply(@Nullable ITreeAppendable p) {
				if(p != null) {
					p = p.trace(sourceElement);
					p.append("return this.");
					p.append(name);
					p.append(";");
				}
			}
		});
		return associate(sourceElement, result);
	}

	/**
	 * Creates a setter method for the given properties name with the standard implementation assigning the passed
	 * parameter to a similarly named field.
	 * 
	 * Example: <code>
	 * public void setFoo(String foo) {
	 *   this.foo = foo;
	 * }
	 * </code>
	 */
	@Nullable 
	public JvmOperation toSetter(@Nullable final EObject sourceElement, @Nullable final String name, @Nullable JvmTypeReference typeRef) {
		if(sourceElement == null || name == null) 
			return null;
		JvmOperation result = typesFactory.createJvmOperation();
		result.setVisibility(JvmVisibility.PUBLIC);
		result.setSimpleName("set" + Strings.toFirstUpper(name));
		result.getParameters().add(toParameter(sourceElement, name, cloneWithProxies(typeRef)));
		setBody(result, new Procedures.Procedure1<ITreeAppendable>() {
			public void apply(@Nullable ITreeAppendable p) {
				if(p != null) {
					p = p.trace(sourceElement);
					p.append("this.");
					p.append(name);
					p.append(" = ");
					p.append(name);
					p.append(";");
				}
			}
		});
		return associate(sourceElement, result);
	}

	/**
	 * Creates and returns a formal parameter for the given name and type, which is associated to the given source
	 * element.
	 */
	@Nullable 
	public JvmFormalParameter toParameter(@Nullable EObject sourceElement, @Nullable String name, @Nullable JvmTypeReference typeRef) {
		if(sourceElement == null || name == null)
			return null;
		JvmFormalParameter result = typesFactory.createJvmFormalParameter();
		result.setName(name);
		result.setParameterType(cloneWithProxies(typeRef));
		return associate(sourceElement, result);
	}

	/**
	 * Creates and returns a constructor with the given simple name associated to the given source element. By default
	 * the constructor will have an empty body and no arguments, hence the Java default constructor.
	 */
	@Nullable 
	public JvmConstructor toConstructor(@Nullable EObject sourceElement, @Nullable Procedure1<JvmConstructor> init) {
		JvmConstructor constructor = typesFactory.createJvmConstructor();
		constructor.setVisibility(JvmVisibility.PUBLIC);
		associate(sourceElement, constructor);
		if (init != null)
			init.apply(constructor);
		return constructor;
	}
	
	/**
	 * Creates a <code>toString()</code> method accumulating the values of all fields.
	 */
	@Nullable 
	public JvmOperation addToStringMethod(@Nullable final EObject sourceElement, @Nullable final JvmDeclaredType declaredType) {
		if(sourceElement == null || declaredType == null)
			return null;
		JvmOperation result = toMethod(sourceElement, "toString", newTypeRef(sourceElement, String.class), null);
		setBody(result, new Procedure1<ITreeAppendable>() {
			public void apply(@Nullable ITreeAppendable p) {
				if(p != null) {
					String name = declaredType.getSimpleName();
					p = p.trace(sourceElement);
					p.append("StringBuilder result = new StringBuilder(\"\\n"+name+" {\");\n");
					for (JvmField jvmField : filter(declaredType.getAllFeatures(), JvmField.class)) {
						ITreeAppendable innerP = p.trace(jvmField);
						innerP.append("result.append(\"\\n  "+jvmField.getSimpleName()+" = \").append(String.valueOf("+jvmField.getSimpleName()+").replace(\"\\n\",\"\\n  \"));\n");
					}
					p.append("result.append(\"\\n}\");\nreturn result.toString();\n");
				}
			}
		});
		return result;
	}

	/**
	 * Creates and returns an annotation of the given annotation type.
	 */
	@Nullable
	public JvmAnnotationReference toAnnotation(@Nullable EObject sourceElement, @Nullable Class<?> annotationType) {
		return toAnnotation(sourceElement, annotationType, null);
	}

	/**
	 * Creates and returns an annotation of the given annotation type's name.
	 */
	@Nullable 
	public JvmAnnotationReference toAnnotation(@Nullable EObject sourceElement, @Nullable String annotationTypeName) {
		return toAnnotation(sourceElement, annotationTypeName, null);
	}

	/**
	 * Creates and returns an annotation of the given annotation type's name and the given value.
	 * 
	 * @param sourceElement
	 *            the source element to associate the created element with.
	 * @param annotationType
	 *            the type of the created annotation.
	 * @param value
	 *            the value of the single
	 */
	@Nullable
	public JvmAnnotationReference toAnnotation(@Nullable EObject sourceElement, @Nullable Class<?> annotationType, @Nullable Object value) {
		if(sourceElement == null || annotationType == null)
			return null;
		return toAnnotation(sourceElement, annotationType.getCanonicalName(), value);
	}

	/**
	 * Creates and returns an annotation of the given annotation type's name and the given value.
	 * 
	 * @param sourceElement
	 *            the source element to associate the created element with.
	 * @param annotationTypeName
	 *            the type name of the created annotation.
	 * @param value
	 *            the value of the single
	 */
	@Nullable 
	public JvmAnnotationReference toAnnotation(@Nullable EObject sourceElement, @Nullable String annotationTypeName, @Nullable Object value) {
		JvmAnnotationReference result = typesFactory.createJvmAnnotationReference();
		JvmType jvmType = references.findDeclaredType(annotationTypeName, sourceElement);
		if (!(jvmType instanceof JvmAnnotationType)) {
			throw new IllegalArgumentException("The given class " + annotationTypeName + " is not an annotation type.");
		}
		result.setAnnotation((JvmAnnotationType) jvmType);
		if (value != null) {
			if (value instanceof String) {
				JvmStringAnnotationValue annotationValue = typesFactory.createJvmStringAnnotationValue();
				annotationValue.getValues().add((String) value);
				result.getValues().add(annotationValue);
			}
		}
		return result;
	}

	/**
	 * Creates a clone of the given {@link JvmTypeReference} without resolving any proxies.
	 * The clone will be associated with the original element by means of {@link JvmModelAssociator associations}.
	 */
	@Nullable 
	public JvmTypeReference cloneWithProxies(@Nullable JvmTypeReference typeRef) {
		if(typeRef == null)
			return null;
		if (typeRef instanceof JvmParameterizedTypeReference && !typeRef.eIsProxy()
				&& !typeRef.eIsSet(TypesPackage.Literals.JVM_PARAMETERIZED_TYPE_REFERENCE__TYPE))
			throw new IllegalArgumentException("typeref#type was null");
		return cloneAndAssociate(typeRef);
	}

	/**
	 * Creates a clone of the given {@link JvmIdentifiableElement} without resolving any proxies.
	 * The clone will be associated with the original element by means of {@link JvmModelAssociator associations}.
	 */
	@Nullable 
	public <T extends JvmIdentifiableElement> T cloneWithProxies(@Nullable T original) {
		if(original == null)
			return null;
		return cloneAndAssociate(original);
	}
	
	/**
	 * Creates a deep copy of the given object and associates each copied instance with the
	 * clone. Does not resolve any proxies.
	 */
	protected <T extends EObject> T cloneAndAssociate(T original) {
		EcoreUtil.Copier copier = new EcoreUtil.Copier(false) {
			private static final long serialVersionUID = 1L;

			@Override@Nullable 
			protected EObject createCopy(@Nullable EObject eObject) {
				EObject result = super.createCopy(eObject);
				if (result != null && eObject != null && !eObject.eIsProxy()) {
					associator.associatePrimary(eObject, result);
				}
				return result;
			}
		};
		@SuppressWarnings("unchecked")
		T copy = (T) copier.copy(original);
		copier.copyReferences();
		return copy;
	}
	
	/**
	 * Attaches the given compile strategy to the given {@link JvmField} such that the compiler knows how to
	 * initialize the {@link JvmField} when it is translated to Java source code.
	 * @param field the field to add the initializer to.
	 * @param strategy the compilation strategy.
	 */
	public void setInitializer(@Nullable JvmField field, @Nullable Procedures.Procedure1<ITreeAppendable> strategy) {
		if (strategy == null || field == null)
			return;
		removeExistingBody(field);
		setCompilationStrategy(field, strategy);
	}
	
	/**
	 * Sets the given {@link JvmField} as the logical container for the given {@link XExpression}.
	 * This defines the context and the scope for the given expression.
	 * 
	 * @param field
	 *            the {@link JvmField} that is initialized by the expression. Must not be <code>null</code>.
	 * @param expr
	 *            the initialization expression. Can be <code>null</code> in which case this function does nothing.
	 */
	public void setInitializer(@Nullable JvmField field, @Nullable XExpression expr) {
		if (expr == null)
			return;
		removeExistingBody(field);
		associator.associateLogicalContainer(expr, field);
	}
	
	protected void setCompilationStrategy(@Nullable JvmMember member,
			@Nullable Procedures.Procedure1<ITreeAppendable> strategy) {
		if(member == null || strategy == null)
			return;
		CompilationStrategyAdapter adapter = new CompilationStrategyAdapter();
		adapter.setCompilationStrategy(strategy);
		member.eAdapters().add(adapter);
	}

	/**
	 * Creates a new {@link JvmTypeReference} pointing to the given class and containing the given type arguments.
	 * 
	 * @param ctx
	 *            an EMF context, which is used to look up the {@link org.eclipse.xtext.common.types.JvmType} for the
	 *            given clazz.
	 * @param clazz
	 *            the class the type reference shall point to.
	 * @param typeArgs
	 *            type arguments
	 * 
	 * @return the newly created {@link JvmTypeReference}
	 */
	@Nullable 
	public JvmTypeReference newTypeRef(@Nullable EObject ctx, @Nullable Class<?> clazz, @Nullable JvmTypeReference... typeArgs) {
		return references.getTypeForName(clazz, ctx, typeArgs);
	}

	/**
	 * Creates a new {@link JvmTypeReference} pointing to the given class and containing the given type arguments.
	 * 
	 * @param ctx
	 *            an EMF context, which is used to look up the {@link org.eclipse.xtext.common.types.JvmType} for the
	 *            given clazz.
	 * @param typeName
	 *            the name of the type the reference shall point to.
	 * @param typeArgs
	 *            type arguments
	 * @return the newly created {@link JvmTypeReference}
	 */
	@Nullable 
	public JvmTypeReference newTypeRef(@Nullable EObject ctx, @Nullable String typeName, @Nullable JvmTypeReference... typeArgs) {
		return references.getTypeForName(typeName, ctx, typeArgs);
	}
	
	/**
	 * Creates a new {@link JvmTypeReference} pointing to the given class and containing the given type arguments.
	 * 
	 * @param type
	 *            the type the reference shall point to.
	 * @param typeArgs
	 *            type arguments
	 * @return the newly created {@link JvmTypeReference}
	 */
	@Nullable 
	public JvmTypeReference newTypeRef(@Nullable JvmDeclaredType type, @Nullable JvmTypeReference... typeArgs) {
		return references.createTypeRef(type, typeArgs);
	}

	/**
	 * @return an array type of the given type reference. Add one dimension if the given {@link JvmTypeReference} is
	 *         already an array.
	 */
	@Nullable 
	public JvmTypeReference addArrayTypeDimension(@Nullable JvmTypeReference componentType) {
		return references.createArrayType(componentType);
	}

	/**
	 * Translates {@link XAnnotation XAnnotations} to {@link JvmAnnotationReference JvmAnnotationReferences} 
	 * and adds them to the given {@link JvmAnnotationTarget}.
	 * @param target the annotation target. May not be <code>null</code>.
	 * @param annotations the annotations. May not be <code>null</code>.
	 */
	public void translateAnnotationsTo(@Nullable Iterable<? extends XAnnotation> annotations, @Nullable JvmAnnotationTarget target) {
		if(annotations != null && target != null) {
			for (XAnnotation anno : annotations) {
				JvmAnnotationReference annotationReference = getJvmAnnotationReference(anno);
				if(annotationReference != null)
					target.getAnnotations().add(annotationReference);
			}
		}
	}

	@Nullable 
	protected JvmAnnotationReference getJvmAnnotationReference(XAnnotation anno) {
		JvmAnnotationReference reference = typesFactory.createJvmAnnotationReference();
		final JvmAnnotationType annotation = (JvmAnnotationType) anno.eGet(
				XAnnotationsPackage.Literals.XANNOTATION__ANNOTATION_TYPE, false);
		reference.setAnnotation(annotation);
		for (XAnnotationElementValuePair val : anno.getElementValuePairs()) {
			XExpression valueExpression = val.getValue();
			JvmAnnotationValue annotationValue = getJvmAnnotationValue(valueExpression);
			if (annotationValue != null) {
				JvmOperation op = (JvmOperation) val.eGet(
						XAnnotationsPackage.Literals.XANNOTATION_ELEMENT_VALUE_PAIR__ELEMENT, false);
				annotationValue.setOperation(op);
				reference.getValues().add(annotationValue);
			}
		}
		if (anno.getValue() != null) {
			JvmAnnotationValue value = getJvmAnnotationValue(anno.getValue());
			if (value != null) {
				reference.getValues().add(value);
			}
		}
		return reference;
	}

	@Nullable 
	protected JvmAnnotationValue getJvmAnnotationValue(@Nullable XExpression value) {
		if (value instanceof XAnnotationValueArray) {
			EList<XExpression> values = ((XAnnotationValueArray) value).getValues();
			JvmAnnotationValue result = null;
			for (XExpression expr : values) {
				AnnotationValueTranslator translator = translator(expr);
				if (translator == null)
					throw new IllegalArgumentException("expression " + value
							+ " is not supported in annotation literals");
				if (result == null) {
					result = translator.createValue(expr);
				}
				translator.appendValue(result, expr);
			}
			return result;
		} else if (value != null) {
			AnnotationValueTranslator translator = translator(value);
			if (translator == null)
				throw new IllegalArgumentException("expression " + value + " is not supported in annotation literals");
			JvmAnnotationValue result = translator.createValue(value);
			translator.appendValue(result, value);
			return result;
		}
		return null;
	}

	private Map<EClass, AnnotationValueTranslator> translators = newLinkedHashMap();

	@Nullable 
	protected AnnotationValueTranslator translator(@Nullable XExpression obj) {
		synchronized (translators) {
			if (translators.isEmpty()) {
				translators.put(XAnnotationsPackage.Literals.XANNOTATION, new AnnotationValueTranslator() {
					public JvmAnnotationValue createValue(XExpression expr) {
						return typesFactory.createJvmAnnotationAnnotationValue();
					}

					public void appendValue(JvmAnnotationValue value, XExpression expr) {
						JvmAnnotationAnnotationValue annotationValue = (JvmAnnotationAnnotationValue) value;
						JvmAnnotationReference annotationReference = getJvmAnnotationReference((XAnnotation) expr);
						annotationValue.getAnnotations().add(annotationReference);
					}
				});
				translators.put(XbasePackage.Literals.XSTRING_LITERAL, new AnnotationValueTranslator() {
					public JvmAnnotationValue createValue(XExpression expr) {
						return typesFactory.createJvmStringAnnotationValue();
					}

					public void appendValue(JvmAnnotationValue value, XExpression expr) {
						JvmStringAnnotationValue annotationValue = (JvmStringAnnotationValue) value;
						String string = ((XStringLiteral) expr).getValue();
						annotationValue.getValues().add(string);
					}
				});
				translators.put(XbasePackage.Literals.XBOOLEAN_LITERAL, new AnnotationValueTranslator() {
					public JvmAnnotationValue createValue(XExpression expr) {
						return typesFactory.createJvmBooleanAnnotationValue();
					}

					public void appendValue(JvmAnnotationValue value, XExpression expr) {
						JvmBooleanAnnotationValue annotationValue = (JvmBooleanAnnotationValue) value;
						boolean isTrue = ((XBooleanLiteral) expr).isIsTrue();
						annotationValue.getValues().add(isTrue);
					}
				});
				translators.put(XbasePackage.Literals.XTYPE_LITERAL, new AnnotationValueTranslator() {
					public JvmAnnotationValue createValue(XExpression expr) {
						return typesFactory.createJvmTypeAnnotationValue();
					}

					public void appendValue(JvmAnnotationValue value, XExpression expr) {
						JvmTypeAnnotationValue annotationValue = (JvmTypeAnnotationValue) value;
						final XTypeLiteral literal = (XTypeLiteral) expr;
						JvmType proxy = (JvmType) literal.eGet(XbasePackage.Literals.XTYPE_LITERAL__TYPE, false);
						JvmParameterizedTypeReference reference = typesFactory
								.createJvmParameterizedTypeReference();
						reference.setType(proxy);
						annotationValue.getValues().add(reference);
					}
				});
				translators.put(XbasePackage.Literals.XNUMBER_LITERAL, new AnnotationValueTranslator() {
					public JvmAnnotationValue createValue(XExpression expr) {
						String primitiveType = numberLiterals.getJavaType((XNumberLiteral) expr).getName();
						EClass eClass = (EClass) TypesPackage.eINSTANCE.getEClassifier("Jvm" + toFirstUpper(primitiveType) + "AnnotationValue");
						return (JvmAnnotationValue) typesFactory.create(eClass);
					}

					@SuppressWarnings("unchecked")
					public void appendValue(JvmAnnotationValue value, XExpression expr) {
						EStructuralFeature valueFeature = value.eClass().getEStructuralFeature("value");
						List<? super Number> values = (List<? super Number>) value.eGet(valueFeature);
						XNumberLiteral literal = (XNumberLiteral) expr;
						Number number = numberLiterals.numberValue(literal, numberLiterals.getJavaType(literal));
						values.add(number);
					}
				});
				translators.put(XbasePackage.Literals.XFEATURE_CALL, new AnnotationValueTranslator() {
					public JvmAnnotationValue createValue(XExpression expr) {
						return typesFactory.createJvmCustomAnnotationValue();
					}

					public void appendValue(JvmAnnotationValue value, XExpression expr) {
						JvmCustomAnnotationValue annotationValue = (JvmCustomAnnotationValue) value;
						annotationValue.getValues().add(expr);
					}
				});
			}
		}
		return obj == null ? null : translators.get(obj.eClass());
	}

	public interface AnnotationValueTranslator {
		JvmAnnotationValue createValue(XExpression expr);

		void appendValue(JvmAnnotationValue value, XExpression expr);
	}
}
