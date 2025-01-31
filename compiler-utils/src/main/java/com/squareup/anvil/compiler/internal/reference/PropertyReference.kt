package com.squareup.anvil.compiler.internal.reference

import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.internal.reference.PropertyReference.Descriptor
import com.squareup.anvil.compiler.internal.reference.PropertyReference.Psi
import com.squareup.anvil.compiler.internal.reference.Visibility.INTERNAL
import com.squareup.anvil.compiler.internal.reference.Visibility.PRIVATE
import com.squareup.anvil.compiler.internal.reference.Visibility.PROTECTED
import com.squareup.anvil.compiler.internal.reference.Visibility.PUBLIC
import com.squareup.anvil.compiler.internal.requireFqName
import com.squareup.kotlinpoet.MemberName
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget.PROPERTY_GETTER
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget.PROPERTY_SETTER
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifierTypeOrDefault
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import kotlin.LazyThreadSafetyMode.NONE

@ExperimentalAnvilApi
public sealed class PropertyReference : AnnotatedReference {

  public abstract val fqName: FqName
  public abstract val declaringClass: ClassReference

  public val module: AnvilModuleDescriptor get() = declaringClass.module

  public abstract val name: String
  public val memberName: MemberName get() = MemberName(declaringClass.asClassName(), name)

  protected abstract val type: TypeReference?

  public abstract val setterAnnotations: List<AnnotationReference>
  public abstract val getterAnnotations: List<AnnotationReference>

  public abstract fun visibility(): Visibility

  public fun typeOrNull(): TypeReference? = type
  public fun type(): TypeReference = type
    ?: throw AnvilCompilationExceptionPropertyReference(
      propertyReference = this,
      message = "Unable to get type for property $fqName."
    )

  override fun toString(): String = "$fqName"

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ClassReference) return false

    if (fqName != other.fqName) return false

    return true
  }

  override fun hashCode(): Int {
    return fqName.hashCode()
  }

  public class Psi internal constructor(
    public val property: KtProperty,
    override val declaringClass: ClassReference.Psi,
    override val fqName: FqName = property.requireFqName(),
    override val name: String = fqName.shortName().asString()
  ) : PropertyReference() {

    override val annotations: List<AnnotationReference.Psi> by lazy(NONE) {
      property.annotationEntries
        .filter {
          val annotationUseSiteTarget = it.useSiteTarget?.getAnnotationUseSiteTarget()
          annotationUseSiteTarget != PROPERTY_SETTER && annotationUseSiteTarget != PROPERTY_GETTER
        }
        .map { it.toAnnotationReference(declaringClass, module) }
        .plus(setterAnnotations)
        .plus(getterAnnotations)
    }

    override val type: TypeReference? by lazy(NONE) {
      property.typeReference?.toTypeReference(declaringClass)
    }

    override val setterAnnotations: List<AnnotationReference.Psi> by lazy(NONE) {
      property.annotationEntries
        .filter { it.useSiteTarget?.getAnnotationUseSiteTarget() == PROPERTY_SETTER }
        .plus(property.setter?.annotationEntries ?: emptyList())
        .map { it.toAnnotationReference(declaringClass, module) }
    }

    override val getterAnnotations: List<AnnotationReference.Psi> by lazy(NONE) {
      property.annotationEntries
        .filter { it.useSiteTarget?.getAnnotationUseSiteTarget() == PROPERTY_GETTER }
        .plus(property.getter?.annotationEntries ?: emptyList())
        .map { it.toAnnotationReference(declaringClass, module) }
    }

    override fun visibility(): Visibility {
      return when (val visibility = property.visibilityModifierTypeOrDefault()) {
        KtTokens.PUBLIC_KEYWORD -> PUBLIC
        KtTokens.INTERNAL_KEYWORD -> INTERNAL
        KtTokens.PROTECTED_KEYWORD -> PROTECTED
        KtTokens.PRIVATE_KEYWORD -> PRIVATE
        else -> throw AnvilCompilationExceptionClassReference(
          classReference = declaringClass,
          message = "Couldn't get visibility $visibility for property $fqName."
        )
      }
    }
  }

  public class Descriptor internal constructor(
    public val property: PropertyDescriptor,
    override val declaringClass: ClassReference.Descriptor,
    override val fqName: FqName = property.fqNameSafe,
    override val name: String = fqName.shortName().asString()
  ) : PropertyReference() {

    override val annotations: List<AnnotationReference.Descriptor> by lazy(NONE) {
      property.annotations
        .plus(property.backingField?.annotations ?: emptyList())
        .map { it.toAnnotationReference(declaringClass, module) }
        .plus(setterAnnotations)
        .plus(getterAnnotations)
    }

    override val setterAnnotations: List<AnnotationReference.Descriptor> by lazy(NONE) {
      property.setter
        ?.annotations
        ?.map { it.toAnnotationReference(declaringClass, module) }
        .orEmpty()
    }

    override val getterAnnotations: List<AnnotationReference.Descriptor> by lazy(NONE) {
      property.getter
        ?.annotations
        ?.map { it.toAnnotationReference(declaringClass, module) }
        .orEmpty()
    }

    override val type: TypeReference by lazy(NONE) {
      property.type.toTypeReference(declaringClass)
    }

    override fun visibility(): Visibility {
      return when (val visibility = property.visibility) {
        DescriptorVisibilities.PUBLIC -> PUBLIC
        DescriptorVisibilities.INTERNAL -> INTERNAL
        DescriptorVisibilities.PROTECTED -> PROTECTED
        DescriptorVisibilities.PRIVATE -> PRIVATE
        else -> throw AnvilCompilationExceptionClassReference(
          classReference = declaringClass,
          message = "Couldn't get visibility $visibility for property $fqName."
        )
      }
    }
  }
}

@ExperimentalAnvilApi
public fun KtProperty.toPropertyReference(
  declaringClass: ClassReference.Psi
): Psi = Psi(this, declaringClass)

@ExperimentalAnvilApi
public fun PropertyDescriptor.toPropertyReference(
  declaringClass: ClassReference.Descriptor
): Descriptor = Descriptor(this, declaringClass)

@ExperimentalAnvilApi
@Suppress("FunctionName")
public fun AnvilCompilationExceptionPropertyReference(
  propertyReference: PropertyReference,
  message: String,
  cause: Throwable? = null
): AnvilCompilationException = when (propertyReference) {
  is Psi -> AnvilCompilationException(
    element = propertyReference.property,
    message = message,
    cause = cause
  )
  is Descriptor -> AnvilCompilationException(
    propertyDescriptor = propertyReference.property,
    message = message,
    cause = cause
  )
}
