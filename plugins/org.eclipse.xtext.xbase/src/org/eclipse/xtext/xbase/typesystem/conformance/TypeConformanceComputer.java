/*******************************************************************************
 * Copyright (c) 2011 itemis AG (http://www.itemis.eu) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.xtext.xbase.typesystem.conformance;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.xtext.common.types.JvmArrayType;
import org.eclipse.xtext.common.types.JvmComponentType;
import org.eclipse.xtext.common.types.JvmGenericType;
import org.eclipse.xtext.common.types.JvmType;
import org.eclipse.xtext.common.types.JvmTypeParameter;
import org.eclipse.xtext.common.types.JvmTypeParameterDeclarator;
import org.eclipse.xtext.xbase.typesystem.references.AnyTypeReference;
import org.eclipse.xtext.xbase.typesystem.references.ArrayTypeReference;
import org.eclipse.xtext.xbase.typesystem.references.CompoundTypeReference;
import org.eclipse.xtext.xbase.typesystem.references.LightweightTypeReference;
import org.eclipse.xtext.xbase.typesystem.references.ParameterizedTypeReference;
import org.eclipse.xtext.xbase.typesystem.references.TypeReferenceOwner;
import org.eclipse.xtext.xbase.typesystem.references.WildcardTypeReference;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.LinkedHashMultiset;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multiset.Entry;
import com.google.common.collect.Sets;
import com.google.common.primitives.Booleans;
import com.google.inject.Singleton;

/**
 * @author Sven Efftinge - Initial contribution and API
 * @author Sebastian Zarnekow
 */
@Singleton
public class TypeConformanceComputer {

	protected AbstractConformanceVisitor<LightweightTypeReference> leftDispatcher = createStrategySelector();
	
	@NonNull
	protected TypeConformanceStrategySelector createStrategySelector() {
		return new TypeConformanceStrategySelector(this);
	}

	public boolean isConformant(LightweightTypeReference left, LightweightTypeReference right) {
		return isConformant(left, right, false);
	}
	
	public boolean isConformant(LightweightTypeReference left, LightweightTypeReference right, boolean ignoreGenerics) {
		if (left == right && left != null)
			return true;
		TypeConformanceResult result = isConformant(left, right, new TypeConformanceComputationArgument(ignoreGenerics, false, true));
		return result.isConformant();
	}
	
	@NonNull
	public TypeConformanceResult isConformant(LightweightTypeReference left, LightweightTypeReference right, TypeConformanceComputationArgument flags) {
		if (left == right && left != null)
			return TypeConformanceResult.SUCCESS;
		return left.accept(leftDispatcher, TypeConformanceComputationArgument.Internal.create(right, flags.rawType, flags.asTypeArgument, flags.allowPrimitiveConversion));
	}
	
	/**
	 * Populates a {@link Multiset} with the maximum number of necessary steps
	 * from a given type to its super types. Sorting the set by the steps creates 
	 * a stable order on from the direct super class, the most specialized implemented 
	 * interfaces up to object.
	 * E.g. although {@link StringBuilder} implements {@link java.io.Serializable} and 
	 * {@link CharSequence}, serializable is treated as more specific by this algorithm
	 * since the super class AbstractStringBuilder implements {@link CharSequence}, too.
	 * Thus the number of steps to {@link java.io.Serializable} is <code>1</code> while 
	 * {@link CharSequence} requires <code>2</code> hops. 
	 */
	@NonNullByDefault
	protected static class MaxDistanceRawTypeAcceptor implements SuperTypeAcceptor {

		/**
		 * The set with with the distance information.
		 */
		private final Multiset<JvmType> distances;
		
		/**
		 * All seen raw types mapped to their resolved parameterized references.
		 */
		private final Multimap<JvmType, LightweightTypeReference> rawTypeToReference;
		
		protected MaxDistanceRawTypeAcceptor(
				Multiset<JvmType> result, 
				Multimap<JvmType, LightweightTypeReference> all) {
			this.distances = result;
			this.rawTypeToReference = all;
		}
		
		public boolean accept(LightweightTypeReference superType, int distance) {
			if (superType == null)
				throw new IllegalStateException("superType may not be null");
			JvmType type = superType.getType();
			rawTypeToReference.put(type, superType);
			if (distances.contains(type)) {
				int currentCount = distances.count(type);
				if (currentCount < distance + 1) {
					distances.setCount(type, distance + 1);
				}
			} else {
				distances.add(type, distance + 1);
			}
			return true;
		}
		
	}
	
	/**
	 * Compute the common super type for the given types.
	 * 
	 * May return <code>null</code> in case one of the types is primitive void but not all 
	 * of them are.
	 */
	@Nullable
	public LightweightTypeReference getCommonSuperType(final @NonNull List<LightweightTypeReference> types) {
		if (types==null || types.isEmpty())
			throw new IllegalArgumentException("Types can't be null or empty "+types);
		if (types.size()==1)
			return types.get(0);
		
		// Check the straight forward case - one of the types is a supertype of all the others.
		// Further more check if any of the types is Void.TYPE -> all have to be Void.TYPE
		boolean allVoid = true;
		for(LightweightTypeReference type: types) {
			if (!type.isPrimitiveVoid()) {
				allVoid = false;
				break;
			}
		}
		if (allVoid) {
			return types.get(0);
		}
		for(LightweightTypeReference type: types) {
			if (conformsToAll(type, types))
				return type;
			if (type.isPrimitiveVoid()) {
				// we saw void but was not all were void
				return null;
			}
		}
//		if (allTypesAreArrays(types)) {
//			List<LightweightTypeReference> componentTypes = getComponentTypes(types);
//			LightweightTypeReference resultComponent = doGetCommonSuperType(componentTypes, new TypeConformanceComputationArgument(false, false, false));
//			if (resultComponent != null) {
//				JvmGenericArrayTypeReference result = factory.createJvmGenericArrayTypeReference();
//				result.setComponentType(resultComponent);
//				return result;
//			}
//		}
		// TODO handle all primitives
		// TODO handle arrays
		if (containsPrimitiveOrAnyReferences(types)) {
			List<LightweightTypeReference> withoutPrimitives = replacePrimitivesAndRemoveAnyReferences(types);
			if (withoutPrimitives.equals(types))
				return null;
			return getCommonSuperType(withoutPrimitives);
		}
		LightweightTypeReference firstType = types.get(0);
		final List<LightweightTypeReference> tail = types.subList(1, types.size());
		// mapping from rawtype to resolved parameterized types
		// used to determine the correct type arguments
		Multimap<JvmType, LightweightTypeReference> all = LinkedHashMultimap.create();
		// cumulated rawtype to max distance (used for sorting)
		Multiset<JvmType> cumulatedDistance = LinkedHashMultiset.create();
		
		initializeDistance(firstType, all, cumulatedDistance);
		cumulateDistance(tail, all, cumulatedDistance);
		
		List<Entry<JvmType>> candidates = Lists.newArrayList(cumulatedDistance.entrySet());
		if (candidates.size() == 1) { // only one super type -> should be java.lang.Object
			JvmType firstRawType = candidates.get(0).getElement();
			return getFirstForRawType(all, firstRawType);
		}
		inplaceSortByDistanceAndName(candidates);
		// try to find a matching parameterized type for the raw types in ascending order
		List<LightweightTypeReference> referencesWithSameDistance = Lists.newArrayListWithExpectedSize(2);
		int wasDistance = -1;
		boolean classSeen = false;
		outer: for(Entry<JvmType> rawTypeCandidate: candidates) {
			JvmType rawType = rawTypeCandidate.getElement();
			LightweightTypeReference result = null;
			if (wasDistance == -1) {
				wasDistance = rawTypeCandidate.getCount();
			} else {
				if (wasDistance != rawTypeCandidate.getCount()) {
					if (classSeen)
						break;
					result = getTypeParametersForSupertype(all, rawType, firstType.getOwner(), types);
					for(LightweightTypeReference alreadyCollected: referencesWithSameDistance) {
						if (isConformant(result, alreadyCollected, true)) {
							classSeen = classSeen || isClass(rawType);
							continue outer;
						}
					}
					wasDistance = rawTypeCandidate.getCount(); 
				}
			}
			if (result == null)
				result = getTypeParametersForSupertype(all, rawType, firstType.getOwner(), types);
			if (result != null) {
				boolean isClass = isClass(rawType);
				classSeen = classSeen || isClass;
				if (isClass)
					referencesWithSameDistance.add(0, result);
				else
					referencesWithSameDistance.add(result);
			}
		}
		if (referencesWithSameDistance.size() == 1) {
			return referencesWithSameDistance.get(0);
		} else if (referencesWithSameDistance.size() > 1) {
			CompoundTypeReference result = new CompoundTypeReference(referencesWithSameDistance.get(0).getOwner(), false);
			for(LightweightTypeReference reference: referencesWithSameDistance) {
				result.addComponent(reference);
			}
			return result;
		}
		return null;
	}

	protected boolean isClass(JvmType type) {
		if (type instanceof JvmArrayType)
			return isClass(((JvmArrayType) type).getComponentType());
		return type instanceof JvmGenericType && !((JvmGenericType) type).isInterface();
	}

	protected List<LightweightTypeReference> replacePrimitivesAndRemoveAnyReferences(List<LightweightTypeReference> types) {
		List<LightweightTypeReference> result = Lists.newArrayList();
		for(LightweightTypeReference type: types) {
			if (!(type instanceof AnyTypeReference))
				result.add(type.getWrapperTypeIfPrimitive());
		}
		return result;
	}

	protected boolean containsPrimitiveOrAnyReferences(List<LightweightTypeReference> types) {
		for(LightweightTypeReference type: types) {
			if (type.isPrimitive() || type instanceof AnyTypeReference)
				return true;
		}
		return false;
	}
	
//	protected List<LightweightTypeReference> getComponentTypes(List<LightweightTypeReference> types) {
//		ITypeReferenceVisitor<LightweightTypeReference> componentTypeComputer = new AbstractTypeReferenceVisitor.InheritanceAware<LightweightTypeReference>() {
//			@Override
//			public LightweightTypeReference doVisitTypeReference(LightweightTypeReference reference) {
//				return null;
//			}
//			@Override
//			protected LightweightTypeReference handleNullReference() {
//				return null;
//			}
//			@Override
//			public LightweightTypeReference doVisitMultiTypeReference(JvmMultiTypeReference multi) {
//				JvmMultiTypeReference result = factory.createJvmMultiTypeReference();
//				for(LightweightTypeReference reference: multi.getReferences()) {
//					LightweightTypeReference component = visit(reference);
//					if (component != null) {
//						if (component.eContainer() == null) {
//							result.getReferences().add(component);
//						} else {
//							JvmDelegateTypeReference delegate = factory.createJvmDelegateTypeReference();
//							delegate.setDelegate(component);
//							result.getReferences().add(delegate);
//						}
//					}
//				}
//				return result;
//			}
//			@Override
//			public LightweightTypeReference doVisitGenericArrayTypeReference(JvmGenericArrayTypeReference reference) {
//				return reference.getComponentType();
//			}
//			@Override
//			public LightweightTypeReference doVisitSynonymTypeReference(JvmSynonymTypeReference synonym) {
//				LightweightTypeReference result = null;
//				for(LightweightTypeReference reference: synonym.getReferences()) {
//					LightweightTypeReference component = visit(reference);
//					if (component != null) {
//						if (result == null) {
//							result = component;
//						} else {
//							if (!(result instanceof JvmSynonymTypeReference)) {
//								JvmSynonymTypeReference newResult = factory.createJvmSynonymTypeReference();
//								if (result.eContainer() == null) {
//									newResult.getReferences().add(result);
//								} else {
//									JvmDelegateTypeReference delegate = factory.createJvmDelegateTypeReference();
//									delegate.setDelegate(component);
//									newResult.getReferences().add(delegate);
//								}
//								result = newResult;
//							}
//							if (component.eContainer() == null) {
//								((JvmSynonymTypeReference) result).getReferences().add(result);
//							} else {
//								JvmDelegateTypeReference delegate = factory.createJvmDelegateTypeReference();
//								delegate.setDelegate(component);
//								((JvmSynonymTypeReference) result).getReferences().add(delegate);
//							}
//						}
//					}
//				}
//				return result;
//			}
//		};
//		List<LightweightTypeReference> result = Lists.newArrayList();
//		for(LightweightTypeReference reference: types) {
//			LightweightTypeReference componentType = componentTypeComputer.visit(reference);
//			result.add(componentType);
//		}
//		return result;
//	}
	
	protected LightweightTypeReference getTypeParametersForSupertype(
			final Multimap<JvmType, LightweightTypeReference> all, 
			JvmType rawType, 
			TypeReferenceOwner owner,
			List<LightweightTypeReference> initiallyRequested) {
		if (rawType instanceof JvmTypeParameterDeclarator) {
			List<JvmTypeParameter> typeParameters = ((JvmTypeParameterDeclarator) rawType).getTypeParameters();
			// if we do not declare any parameters it is safe to return the first candidate
			if (typeParameters.isEmpty()) {
				return getFirstForRawType(all, rawType); 
			}
			List<LightweightTypeReference> parameterSuperTypes = Lists.newArrayList();
			for(int i = 0; i < typeParameters.size(); i++) {
				List<LightweightTypeReference> parameterReferences = Lists.newArrayList();
				for(LightweightTypeReference reference: all.get(rawType)) {
					if (reference instanceof ParameterizedTypeReference) {
						ParameterizedTypeReference parameterized = (ParameterizedTypeReference) reference;
						if (parameterized.getTypeArguments().isEmpty()) { // raw type candidate - best result
							return parameterized;
						}
						LightweightTypeReference parameterReference = parameterized.getTypeArguments().get(i);
						parameterReferences.add(parameterReference);
					} else {
						return null;
					}
				}
				LightweightTypeReference parameterSuperType = getCommonParameterSuperType(parameterReferences, initiallyRequested, owner);
				if (parameterSuperType == null) {
					return null;
				} else {
					parameterSuperTypes.add(parameterSuperType);
				}
			}
			ParameterizedTypeReference result = new ParameterizedTypeReference(owner, rawType);
			for(LightweightTypeReference parameterSuperType: parameterSuperTypes) {
				result.addTypeArgument(parameterSuperType);
			}
			return result;
		} else if (rawType instanceof JvmArrayType) {
			JvmComponentType componentType = ((JvmArrayType) rawType).getComponentType();
			Multimap<JvmType,LightweightTypeReference> copiedMultimap = LinkedHashMultimap.create(all);
			Collection<LightweightTypeReference> originalReferences = all.get(rawType);
			List<LightweightTypeReference> componentReferences = Lists.newArrayListWithCapacity(originalReferences.size());
			for(LightweightTypeReference originalReference: originalReferences) {
				addComponentType(originalReference, componentReferences);
			}
			copiedMultimap.replaceValues(componentType, componentReferences);
			List<LightweightTypeReference> componentRequests = Lists.newArrayListWithCapacity(initiallyRequested.size());
			for(LightweightTypeReference initialRequest: initiallyRequested) {
				addComponentType(initialRequest, componentRequests);
			}
			LightweightTypeReference componentTypeReference = getTypeParametersForSupertype(
					copiedMultimap, 
					componentType,
					owner,
					componentRequests);
			if (componentTypeReference != null) {
				return new ArrayTypeReference(owner, componentTypeReference);
			}
		}
		return null;
	}

	protected void addComponentType(LightweightTypeReference reference, List<LightweightTypeReference> result) {
		if (reference.isArray()) {
			result.add(((ArrayTypeReference) reference).getComponentType());
		} else {
			result.add(reference);
		}
	}

	protected LightweightTypeReference getFirstForRawType(Multimap<JvmType, LightweightTypeReference> all, JvmType rawType) {
		Iterator<LightweightTypeReference> iterator = all.get(rawType).iterator();
		while(iterator.hasNext()) {
			LightweightTypeReference result = iterator.next();
			if (result instanceof ParameterizedTypeReference || result instanceof ArrayTypeReference) {
				return result;
			}
		}
		throw new IllegalStateException(all.toString() + " does not contain a useful type reference for rawtype " + rawType.getQualifiedName());
	}

	protected void initializeDistance(final LightweightTypeReference firstType, Multimap<JvmType, LightweightTypeReference> all,
			Multiset<JvmType> cumulatedDistance) {
		MaxDistanceRawTypeAcceptor acceptor = new MaxDistanceRawTypeAcceptor(cumulatedDistance, all);
		acceptor.accept(firstType, 0);
		firstType.collectSuperTypes(acceptor);
	}

	/**
	 * Keeps the cumulated distance for all the common raw super types of the given references.
	 * Interfaces that are more directly implemented will get a lower total count than more general
	 * interfaces.
	 */
	protected void cumulateDistance(final List<LightweightTypeReference> references, Multimap<JvmType, LightweightTypeReference> all,
			Multiset<JvmType> cumulatedDistance) {
		for(LightweightTypeReference other: references) {
			Multiset<JvmType> otherDistance = LinkedHashMultiset.create();
			initializeDistance(other, all, otherDistance);
			cumulatedDistance.retainAll(otherDistance);
			for(Multiset.Entry<JvmType> typeToDistance: otherDistance.entrySet()) {
				if (cumulatedDistance.contains(typeToDistance.getElement()))
					cumulatedDistance.add(typeToDistance.getElement(), typeToDistance.getCount());
			}
		}
	}

	protected void inplaceSortByDistanceAndName(List<Entry<JvmType>> candidates) {
		Collections.sort(candidates,new Comparator<Entry<JvmType>>() {
			public int compare(Entry<JvmType> o1, Entry<JvmType> o2) {
				if (o1.getCount() == o2.getCount()) {
					JvmType element1 = o1.getElement();
					JvmType element2 = o2.getElement();
					return compare(element1, element2);
				}
				if (o1.getCount() < o2.getCount())
					return -1;
				return 1;
			}

			protected int compare(JvmType element1, JvmType element2) {
				if (element1 instanceof JvmArrayType && element2 instanceof JvmArrayType) {
					return compare(((JvmArrayType) element1).getComponentType(), ((JvmArrayType) element2).getComponentType());
				}
				if (element1 instanceof JvmGenericType && element2 instanceof JvmGenericType) {
					int result = Booleans.compare(((JvmGenericType) element1).isInterface(), ((JvmGenericType) element2).isInterface());
					if (result != 0) {
						return result;
					}
				}
				return element1.getIdentifier().compareTo(element2.getIdentifier());
			}
		});
	}
	
	public LightweightTypeReference getCommonParameterSuperType(List<LightweightTypeReference> types, List<LightweightTypeReference> initiallyRequested, TypeReferenceOwner owner) {
		Set<String> allNames = Sets.newHashSet();
		for(LightweightTypeReference type: types)
			allNames.add(type.getIdentifier());
		if (allNames.size() == 1)
			return types.get(0);
		if (types.size() == initiallyRequested.size()) {
			boolean containsAll = true;
			for(LightweightTypeReference initialRequest: initiallyRequested) {
				if (!allNames.contains(initialRequest.getIdentifier())) {
					containsAll = false;
					break;
				}
			}
			// recursive request - return object wildcard
			if (containsAll) {
				return createObjectWildcardReference(owner);
			}
		}
		LightweightTypeReference superType = getCommonSuperType(types);
		if (superType instanceof WildcardTypeReference)
			return superType;
		if (superType == null) {
			return createObjectWildcardReference(owner);
		}
		WildcardTypeReference result = new WildcardTypeReference(owner);
		result.addUpperBound(superType);
		return result;
	}

	protected LightweightTypeReference createObjectWildcardReference(TypeReferenceOwner owner) {
		JvmType objectType = owner.getServices().getTypeReferences().findDeclaredType(Object.class, owner.getContextResourceSet());
		ParameterizedTypeReference objectReference = new ParameterizedTypeReference(owner, objectType);
		WildcardTypeReference result = new WildcardTypeReference(owner);
		result.addUpperBound(objectReference);
		return result;
	}

	protected boolean conformsToAll(LightweightTypeReference type, final List<LightweightTypeReference> types) {
		boolean conform = true;
		for (int i = 0; conform && i < types.size(); i++) {
			conform = isConformant(type, types.get(i));
		}
		return conform;
	}
	
}