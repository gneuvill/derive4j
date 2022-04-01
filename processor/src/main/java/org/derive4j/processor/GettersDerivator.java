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
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.RecordComponentElement;
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

final class GettersDerivator implements Derivator<Variant> {

  private final DeriveUtils deriveUtils;

  GettersDerivator(DeriveUtils deriveUtils) {
    this.deriveUtils = deriveUtils;
  }

  @Override
  public DeriveResult<DerivedCodeSpec> derive(AlgebraicDataType<Variant> adt) {
    //noinspection OptionalGetWithoutIsPresent
    return AlgebraicDataTypes.getDataConstruction(adt)
        .map(dataConstruction -> deriveImpl(adt
            , dataConstruction.constructors()
            , DataConstructor::arguments
            , DataArgument::fieldName))

        .or(() -> AlgebraicDataTypes.getJDataConstruction(adt)
            .map(jDataConstruction -> deriveImpl(adt
                , jDataConstruction.constructors()
                , JRecords::getComponents
                , f(Name::toString).compose(RecordComponentElement::getSimpleName))))

        .get();
  }

  private <T, U> DeriveResult<DerivedCodeSpec> deriveImpl(AlgebraicDataType<Variant> adt
      , List<T> constructors
      , Function<T, List<U>> getArgs
      , Function<U, String> getName) {
    return result(adt
        .fields()
        .stream()
        .map(da -> deriveGetter(da, adt, constructors, getArgs, getName))
        .reduce(DerivedCodeSpec.none(), DerivedCodeSpec::append));
  }

  private <T, U> DerivedCodeSpec deriveGetter(DataArgument field
      , AlgebraicDataType<Variant> adt
      , List<T> constructors
      , Function<T, List<U>> getArgs
      , Function<U, String> getName) {
    final var genGetter = f2(isLens(field, constructors, getArgs, getName)
        ? this::generateLensGetter : this::generateOptionalGetter);

    return genGetter.apply(field, adt);
  }

  private DerivedCodeSpec generateLensGetter(DataArgument field, AlgebraicDataType<Variant> adt) {
    final var arg = asParameterName(adt);

    return AlgebraicDataTypes.caseOf(adt)
        .adt((deriveConfig, typeConstructor, matchMethod, dataConstruction, fields, eq) -> {
          final var drv4jAdt = Utils.coerce(adt, eq);

          return DataConstructions.caseOf(dataConstruction)
              .multipleConstructors(MultipleConstructorsSupport.cases()
                  .visitorDispatch((visitorParam, visitorType, constructors) ->
                      visitorDispatchLensGetterImpl(drv4jAdt, arg, visitorType, field))
                  .functionsDispatch(constructors -> functionsDispatchLensGetterImpl(drv4jAdt, arg, field)))
              .oneConstructor(constructor -> functionsDispatchLensGetterImpl(drv4jAdt, arg, field))
              .noConstructor(DerivedCodeSpec::none);
        })

        .jadt((deriveConfig, typeConstructor, jDataConstruction, fields, eq) ->
            jLensGetterImpl(field, Utils.coerce(adt, eq), arg));
  }

  private DerivedCodeSpec generateOptionalGetter(DataArgument field, AlgebraicDataType<Variant> adt) {
    final var arg = asParameterName(adt);
    final var optionModel = deriveUtils.optionModel(adt.deriveConfig().flavour());
    final var returnType = deriveUtils.types().getDeclaredType(optionModel.typeElement(),
        field.type().accept(asBoxedType, deriveUtils.types()));

    return AlgebraicDataTypes.caseOf(adt)
        .adt((deriveConfig, typeConstructor, matchMethod, dataConstruction, fields, eq) -> {
          final var drv4jAdt = Utils.coerce(adt, eq);

          return DataConstructions.caseOf(dataConstruction)
              .multipleConstructors(MultipleConstructorsSupport.cases()
                  .visitorDispatch((visitorParam, visitorType, constructors) -> visitorDispatchOptionalGetterImpl(optionModel,
                      drv4jAdt, visitorType, constructors, arg, field, returnType))
                  .functionsDispatch(constructors -> functionsDispatchOptionalGetterImpl(optionModel, drv4jAdt, arg, constructors,
                      field, returnType)))
              .otherwise(DerivedCodeSpec::none);
        })

        .jadt((deriveConfig, typeConstructor, jDataConstruction, fields, eq) ->
            jOptionalGetterImpl(field, Utils.coerce(adt, eq), arg, optionModel, returnType));
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

    final var matchMethod = AlgebraicDataTypes.getMatchMethod_(adt);
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

  private DerivedCodeSpec visitorDispatchLensGetterImpl(AlgebraicDataType<Variant.Drv4j> adt, String arg, DeclaredType visitorType,
      DataArgument field) {

    final var matchMethod = AlgebraicDataTypes.getMatchMethod_(adt);

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
        .add("return $L.$L(", arg, AlgebraicDataTypes.getMatchMethod_(adt).element().getSimpleName())
        .add(optionalGetterLambdas(arg, optionModel, constructors, field))
        .add(");")
        .build()).build());
  }

  private static MethodSpec.Builder getterBuilder(AlgebraicDataType<?> adt, String arg, DataArgument field,
      TypeMirror returnType) {

    return MethodSpec.methodBuilder("get" + Utils.capitalize(field.fieldName()))
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
        .addTypeVariables(
            adt.typeConstructor().typeVariables().stream().map(TypeVariableName::get).collect(Collectors.toList()))
        .addParameter(TypeName.get(adt.typeConstructor().declaredType()), arg)
        .returns(TypeName.get(returnType));
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
        AlgebraicDataTypes.getMatchMethod_(adt).element().getSimpleName(), lensGetterLambda(arg, adt, field)).build());
  }

  private static String lensGetterLambda(String arg, AlgebraicDataType<Variant.Drv4j> adt, DataArgument field) {

    NameAllocator nameAllocator = new NameAllocator();
    nameAllocator.newName(arg);

    return joinStringsAsArguments(AlgebraicDataTypes.getDataConstruction_(adt)
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

  private static DerivedCodeSpec jLensGetterImpl(DataArgument field
      , AlgebraicDataType<Variant.Java> jadt
      , String arg) {
    return jGetterImpl(field, jadt, arg, field.type(), (caseVar, rec) -> CodeBlock.builder()
        .add("case $1T $2N -> $2N.$3N();", JRecords.getElement(rec), caseVar, field.fieldName())
        .build());
  }

  private DerivedCodeSpec jOptionalGetterImpl(DataArgument field
      , AlgebraicDataType<Variant.Java> jadt
      , String arg
      , OptionModel optionModel
      , DeclaredType returnType) {
    return jGetterImpl(field, jadt, arg, returnType, (caseVar, rec) -> {
      final var fieldPresent = JRecords
          .getComponents(rec)
          .stream()
          .map(f(Name::toString).compose(RecordComponentElement::getSimpleName))
          .anyMatch(p_(curry(String::equalsIgnoreCase, field.fieldName())));

      final var baseBuilder = CodeBlock.builder()
          .add("case $T $N -> $T."
              , JRecords.getElement(rec)
              , caseVar
              , ClassName.get(optionModel.typeElement()));
      
      final var cpltBuilder = fieldPresent
          ? baseBuilder.add("$N($N.$N());"
          , optionModel.someConstructor().getSimpleName()
          , caseVar
          , field.fieldName())
          : baseBuilder.add("$N();", optionModel.noneConstructor().getSimpleName());
      
      return cpltBuilder.build();
    });
  }

  private static DerivedCodeSpec jGetterImpl(DataArgument field
      , AlgebraicDataType<Variant.Java> jadt
      , String arg
      , TypeMirror returnType
      , BiFunction<String, JRecord, CodeBlock> caseImpl) {
    final var na = new NameAllocator();
    na.newName(arg);

    return DerivedCodeSpec.methodSpec(getterBuilder(jadt, arg, field, returnType)
        .addCode(CodeBlock
            .builder()
            .add("return switch($N) {\n", arg)
            .indent()
            .add(AlgebraicDataTypes.getJDataConstruction_(jadt).constructors()
                .stream()
                .map(rec -> {
                  final var elt = JRecords.getElement(rec);
                  final var caseVarName = na.newName(uncapitalize(elt.getSimpleName()));

                  return caseImpl.apply(caseVarName, rec);
                })
                .reduce((cb1, cb2) -> cb1.toBuilder().add("\n").add(cb2).build())
                .orElse(CodeBlock.of("")))
            .unindent()
            .add("\n};\n")
            .build())
        .build());
  }

  private static String  asParameterName(AlgebraicDataType<?> adt) {
    return Utils.uncapitalize(adt.typeConstructor().typeElement().getSimpleName().toString());
  }

  private static <T, U> boolean isLens(DataArgument field
      , List<T> constructors
      , Function<T, List<U>> getArgs
      , Function<U, String> getName)  {
    return constructors.stream()
        .allMatch(dc -> getArgs.apply(dc).stream().anyMatch(da -> getName.apply(da).equals(field.fieldName())));
  }
}
