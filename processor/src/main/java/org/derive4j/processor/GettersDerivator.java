/*
 * Copyright (c) 2019, Jean-Baptiste Giraudeau <jb@giraudeau.info>
 *
 * This file is part of "Derive4J - Annotation Processor".
 *
 * "Derive4J - Annotation Processor" is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * "Derive4J - Annotation Processor" is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with "Derive4J - Annotation Processor".  If not, see <http://www.gnu.org/licenses/>.
 */
package org.derive4j.processor;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.NameAllocator;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeVariableName;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import org.derive4j.processor.api.Derivator;
import org.derive4j.processor.api.DeriveResult;
import org.derive4j.processor.api.DeriveUtils;
import org.derive4j.processor.api.DerivedCodeSpec;
import org.derive4j.processor.api.OptionModel;
import org.derive4j.processor.api.model.*;
import org.derive4j.processor.api.model.AlgebraicDataType.Variant;

import static org.derive4j.processor.Utils.*;
import static org.derive4j.processor.api.DeriveResult.result;
import static org.derive4j.processor.api.model.DataConstructions.caseOf;

final class GettersDerivator implements Derivator<Variant> {

  private final DeriveUtils deriveUtils;

  GettersDerivator(DeriveUtils deriveUtils) {
    this.deriveUtils = deriveUtils;
  }

  @Override
  public DeriveResult<DerivedCodeSpec> derive(AlgebraicDataType<Variant> adt) {
    return AlgebraicDataTypes.caseOf(adt)
        .adt((deriveConfig, typeConstructor, matchMethod, dataConstruction, fields, eq) ->
            deriveFromAdt(Utils.coerce(adt, eq)))

        .jadt_(result(DerivedCodeSpec.none()));
  }

  private DeriveResult<DerivedCodeSpec> deriveFromAdt(AlgebraicDataType<Variant.Drv4j> adt) {

    return result(
        adt.fields().stream().map(da -> deriveGetter(da, adt)).reduce(DerivedCodeSpec.none(), DerivedCodeSpec::append));
  }

  private DerivedCodeSpec deriveGetter(DataArgument field, AlgebraicDataType<Variant.Drv4j> adt) {

    return isLens(field, AlgebraicDataType.getDataConstruction_(adt).constructors())
        ? generateLensGetter(field, adt)
        : generateOptionalGetter(field, adt);
  }

  private DerivedCodeSpec generateOptionalGetter(DataArgument field, AlgebraicDataType<Variant.Drv4j> adt) {

    String arg = asParameterName(adt);

    OptionModel optionModel = deriveUtils.optionModel(adt.deriveConfig().flavour());

    DeclaredType returnType = deriveUtils.types().getDeclaredType(optionModel.typeElement(),
        field.type().accept(asBoxedType, deriveUtils.types()));

    return caseOf(AlgebraicDataType.getDataConstruction_(adt))
        .multipleConstructors(MultipleConstructorsSupport.cases()
            .visitorDispatch((visitorParam, visitorType, constructors) -> visitorDispatchOptionalGetterImpl(optionModel,
                adt, visitorType, constructors, arg, field, returnType))
            .functionsDispatch(constructors -> functionsDispatchOptionalGetterImpl(optionModel, adt, arg, constructors,
                field, returnType)))
        .otherwise(DerivedCodeSpec::none);
  }

  private DerivedCodeSpec visitorDispatchOptionalGetterImpl(OptionModel optionModel, AlgebraicDataType<Variant.Drv4j> adt,
      DeclaredType visitorType, List<DataConstructor> constructors, String arg, DataArgument field,
      DeclaredType returnType) {

    final Function<TypeVariable, Optional<TypeMirror>> returnTypeArg = tv -> adt
      .matchMethod()
      .map(MatchMethod::returnTypeVariable)
      .filter(p_(curry(deriveUtils.types()::isSameType, tv)))
      .map(constant(returnType));

    final Function<TypeVariable, Optional<TypeMirror>> otherTypeArgs = tv -> Optional
        .of(deriveUtils.elements().getTypeElement(Object.class.getName()).asType());

    final FieldSpec getterField = FieldSpec
        .builder(TypeName.get(deriveUtils.resolve(deriveUtils.resolve(visitorType, returnTypeArg), otherTypeArgs)),
            Utils.uncapitalize(field.fieldName() + "Getter"))
        .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
        .initializer("$T.$L($L)", adt.deriveConfig().targetClass().className(),
            MapperDerivator.visitorLambdaFactoryName(adt), optionalGetterLambdas(arg, optionModel, constructors, field))
        .build();

    final var matchMethod = AlgebraicDataType.getMatchMethod_(adt);
    MethodSpec getter;

    if (adt.typeConstructor().typeVariables().isEmpty()) {
      getter = getterBuilder(adt, arg, field, returnType)
          .addStatement("return $L.$L($L)", arg, matchMethod.element().getSimpleName(), getterField.name)
          .build();
    } else {
      getter = getterBuilder(adt, arg, field, returnType)
          .addAnnotation(AnnotationSpec.builder(SuppressWarnings.class)
              .addMember("value", "{$S, $S}", "unchecked", "rawtypes")
              .build())
          .addStatement("return ($T) $L.$L(($T) $L)", TypeName.get(returnType), arg,
              matchMethod.element().getSimpleName(), TypeName.get(deriveUtils.types().erasure(visitorType)),
              getterField.name)
          .build();

    }

    return DerivedCodeSpec.codeSpec(getterField, getter);
  }

  private DerivedCodeSpec generateLensGetter(DataArgument field, AlgebraicDataType<Variant.Drv4j> adt) {

    String arg = asParameterName(adt);

    return caseOf(AlgebraicDataType.getDataConstruction_(adt))
        .multipleConstructors(MultipleConstructorsSupport.cases()
            .visitorDispatch((visitorParam, visitorType, constructors) -> visitorDispatchLensGetterImpl(adt, arg,
                visitorType, field))
            .functionsDispatch(constructors -> functionsDispatchLensGetterImpl(adt, arg, field)))
        .oneConstructor(constructor -> functionsDispatchLensGetterImpl(adt, arg, field))
        .noConstructor(DerivedCodeSpec::none);
  }

  private DerivedCodeSpec visitorDispatchLensGetterImpl(AlgebraicDataType<Variant.Drv4j> adt, String arg, DeclaredType visitorType,
      DataArgument field) {

    final var matchMethod = AlgebraicDataType.getMatchMethod_(adt);

    Function<TypeVariable, Optional<TypeMirror>> returnTypeArg = tv -> deriveUtils.types().isSameType(tv,
        matchMethod.returnTypeVariable())
            ? Optional.of(asBoxedType.visit(field.type(), deriveUtils.types()))
            : Optional.empty();

    Function<TypeVariable, Optional<TypeMirror>> otherTypeArgs = tv -> Optional
        .of(deriveUtils.elements().getTypeElement(Object.class.getName()).asType());

    FieldSpec getterField = FieldSpec
        .builder(TypeName.get(deriveUtils.resolve(deriveUtils.resolve(visitorType, returnTypeArg), otherTypeArgs)),
            Utils.uncapitalize(field.fieldName() + "Getter"))
        .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
        .initializer("$T.$L($L)", adt.deriveConfig().targetClass().className(),
            MapperDerivator.visitorLambdaFactoryName(adt), lensGetterLambda(arg, adt, field))
        .build();

    final MethodSpec getter;

    if (adt.typeConstructor().typeVariables().isEmpty()) {
      getter = getterBuilder(adt, arg, field, field.type())
          .addStatement("return $L.$L($L)", arg, matchMethod.element().getSimpleName(), getterField.name)
          .build();
    } else {

      getter = getterBuilder(adt, arg, field, field.type())
          .addAnnotation(AnnotationSpec.builder(SuppressWarnings.class)
              .addMember("value", "{$S, $S}", "unchecked", "rawtypes")
              .build())
          .addStatement("return ($T) $L.$L(($T) $L)", TypeName.get(field.type()), arg,
              matchMethod.element().getSimpleName(), TypeName.get(deriveUtils.types().erasure(visitorType)),
              getterField.name)
          .build();

    }

    return DerivedCodeSpec.codeSpec(getterField, getter);
  }

  private static DerivedCodeSpec functionsDispatchOptionalGetterImpl(OptionModel optionModel, AlgebraicDataType<Variant.Drv4j> adt,
      String arg, List<DataConstructor> constructors, DataArgument field, DeclaredType returnType) {

    return DerivedCodeSpec.methodSpec(getterBuilder(adt, arg, field, returnType).addCode(CodeBlock.builder()
        .add("return $L.$L(", arg, AlgebraicDataType.getMatchMethod_(adt).element().getSimpleName())
        .add(optionalGetterLambdas(arg, optionModel, constructors, field))
        .add(");")
        .build()).build());
  }

  private static MethodSpec.Builder getterBuilder(AlgebraicDataType<Variant.Drv4j> adt, String arg, DataArgument field,
      TypeMirror type) {

    return MethodSpec.methodBuilder("get" + Utils.capitalize(field.fieldName()))
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
        .addTypeVariables(
            adt.typeConstructor().typeVariables().stream().map(TypeVariableName::get).collect(Collectors.toList()))
        .addParameter(TypeName.get(adt.typeConstructor().declaredType()), arg)
        .returns(TypeName.get(type));
  }

  private static CodeBlock optionalGetterLambdas(String arg, OptionModel optionModel,
      List<DataConstructor> constructors, DataArgument field) {

    NameAllocator nameAllocator = new NameAllocator();
    nameAllocator.newName(arg);

    return constructors.stream().map(constructor -> {
      CodeBlock.Builder caseImplBuilder = CodeBlock.builder().add("($L) -> $T.",
          joinStringsAsArguments(Stream.concat(
              constructor.arguments().stream().map(DataArgument::fieldName).map(
                  fn -> nameAllocator.clone().newName(fn, fn + " field")),
              constructor.typeRestrictions().stream().map(TypeRestriction::typeEq).map(DataArgument::fieldName).map(
                  fn -> nameAllocator.clone().newName(fn, fn + " field")))),
          ClassName.get(optionModel.typeElement()));
      if (constructor.arguments().stream().anyMatch(da -> da.fieldName().equals(field.fieldName()))) {
        caseImplBuilder.add("$L($L)", optionModel.someConstructor().getSimpleName(),
            nameAllocator.clone().newName(field.fieldName(), field.fieldName() + " field"));
      } else {
        caseImplBuilder.add("$L()", optionModel.noneConstructor().getSimpleName());
      }
      return caseImplBuilder.build();
    }).reduce((cb1, cb2) -> CodeBlock.builder().add(cb1).add(",\n").add(cb2).build()).orElse(
        CodeBlock.builder().build());
  }

  private static DerivedCodeSpec functionsDispatchLensGetterImpl(AlgebraicDataType<Variant.Drv4j> adt, String arg,
      DataArgument field) {

    return DerivedCodeSpec.methodSpec(getterBuilder(adt, arg, field, field.type()).addStatement("return $L.$L($L)", arg,
        AlgebraicDataType.getMatchMethod_(adt).element().getSimpleName(), lensGetterLambda(arg, adt, field)).build());
  }

  private static String lensGetterLambda(String arg, AlgebraicDataType<Variant.Drv4j> adt, DataArgument field) {

    NameAllocator nameAllocator = new NameAllocator();
    nameAllocator.newName(arg);

    return joinStringsAsArguments(AlgebraicDataType.getDataConstruction_(adt)
        .constructors()
        .stream()
        .map(dc -> '('
            + joinStringsAsArguments(Stream.concat(
                dc.arguments().stream().map(DataArgument::fieldName).map(
                    fn -> nameAllocator.clone().newName(fn, fn + " field")),
                dc.typeRestrictions().stream().map(TypeRestriction::typeEq).map(DataArgument::fieldName).map(
                    fn -> nameAllocator.clone().newName(fn, fn + " field"))))
            + ") -> " + nameAllocator.clone().newName(field.fieldName(), field.fieldName() + " field")));
  }

  private static String asParameterName(AlgebraicDataType<Variant.Drv4j> adt) {

    return Utils.uncapitalize(adt.typeConstructor().typeElement().getSimpleName().toString());
  }

  private static boolean isLens(DataArgument field, List<DataConstructor> constructors) {

    return constructors.stream()
        .allMatch(dc -> dc.arguments().stream().anyMatch(da -> da.fieldName().equals(field.fieldName())));
  }

}
