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

import com.squareup.javapoet.*;
import org.derive4j.processor.api.Derivator;
import org.derive4j.processor.api.DeriveResult;
import org.derive4j.processor.api.DeriveUtils;
import org.derive4j.processor.api.DerivedCodeSpec;
import org.derive4j.processor.api.model.*;
import org.derive4j.processor.api.model.AlgebraicDataType.Variant;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.type.TypeVariable;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.derive4j.processor.Utils.*;
import static org.derive4j.processor.api.DeriveResult.result;
import static org.derive4j.processor.api.model.AlgebraicDataTypes.caseOf;
import static org.derive4j.processor.api.model.DataConstructions.caseOf;
import static org.derive4j.processor.api.model.DeriveVisibilities.caseOf;

final class ModifiersDerivator implements Derivator<Variant> {

  private final DeriveUtils deriveUtils;

  ModifiersDerivator(DeriveUtils deriveUtils) {
    this.deriveUtils = deriveUtils;
  }

  @Override
  public DeriveResult<DerivedCodeSpec> derive(AlgebraicDataType<Variant> adt) {
    return result(adt
        .fields()
        .stream()
        .map(da -> caseOf(adt)
            .adt((deriveConfig, typeConstructor, matchMethod, dataConstruction, fields, eq) -> {
              final var drv4jAdt = Utils.coerce(adt, eq);

              return generateModifier(drv4jAdt, da
                  , AlgebraicDataTypes.getDataConstruction_(drv4jAdt).constructors()
                  , DataConstructor::typeRestrictions
                  , curry(ModifiersDerivator::getTypeVariableName, AlgebraicDataTypes.getMatchMethod_(drv4jAdt)));
            })

            .jadt((deriveConfig, typeConstructor, jDataConstruction, fields, eq) -> {
              final var javaAdt = Utils.coerce(adt, eq);

              return generateModifier(javaAdt, da
                  , AlgebraicDataTypes.getJDataConstruction_(javaAdt).constructors()
                  , __ -> Collections.emptyList()
                  , TypeVariableName::get);
            }))
        .reduce(DerivedCodeSpec.none(), DerivedCodeSpec::append));
  }

  private <T, U, V> DerivedCodeSpec generateModifier(AlgebraicDataType<T> adt
      , DataArgument field
      , List<U> constructors
      , Function<U, List<V>> getTypeRestrictions
      , Function<TypeVariable, TypeVariableName> getTypeVarName) {
    final var moderArg = field.fieldName() + "Mod";
    final var f1 = deriveUtils.function1Model(adt.deriveConfig().flavour()).samClass();
    final var f1Apply = deriveUtils.allAbstractMethods(f1).get(0).getSimpleName().toString();
    final var uniqueTypeVariables = getUniqueTypeVariables(field, adt.fields(), deriveUtils);
    final var typeVariables = Stream
        .concat(adt.typeConstructor().typeVariables().stream().map(TypeVariableName::get)
              , uniqueTypeVariables.stream().map(getTypeVarName))
        .distinct()
        .toList();

    final Function<TypeVariable, Optional<TypeName>> polymorphism = tv -> uniqueTypeVariables.stream()
        .filter(utv -> deriveUtils.types().isSameType(tv, utv))
        .findFirst()
        .map(getTypeVarName);

    final var boxedFieldType = field.type().accept(Utils.asBoxedType, deriveUtils.types());
    final var smartSuffix = caseOf(adt.deriveConfig().targetClass().visibility()).Smart_("0").otherwise_("");
    final var modMethodName = "mod" + Utils.capitalize(field.fieldName()) + smartSuffix;

    final var nameAllocator = new NameAllocator();
    nameAllocator.newName(modMethodName);

    final var adtArg = nameAllocator
        .newName(Utils.uncapitalize(adt.typeConstructor().declaredType().asElement().getSimpleName()));

    final var modMethod = MethodSpec.methodBuilder(modMethodName)
        .addModifiers(Modifier.STATIC)
        .addTypeVariables(typeVariables)
        .addParameter(ParameterSpec.builder(ParameterizedTypeName.get(ClassName.get(f1), TypeName.get(boxedFieldType),
            deriveUtils.resolveToTypeName(boxedFieldType, polymorphism)), moderArg).build())
        .returns(ParameterizedTypeName.get(ClassName.get(f1), TypeName.get(adt.typeConstructor().declaredType()),
            deriveUtils.resolveToTypeName(adt.typeConstructor().declaredType(), polymorphism)));

    if (smartSuffix.isEmpty())
      modMethod.addModifiers(Modifier.PUBLIC);

    if (constructors.stream().anyMatch(dc -> !getTypeRestrictions.apply(dc).isEmpty()))
      modMethod.addAnnotation(AnnotationSpec
          .builder(SuppressWarnings.class)
          .addMember("value", "$S", "unchecked")
          .build());

    final var setterArgName = "new" + Utils.capitalize(field.fieldName());
    final var setMethod = MethodSpec.methodBuilder("set" + Utils.capitalize(field.fieldName()) + smartSuffix)
        .addModifiers(Modifier.STATIC)
        .addTypeVariables(typeVariables)
        .addParameter(
            ParameterSpec.builder(deriveUtils.resolveToTypeName(boxedFieldType, polymorphism), setterArgName).build())
        .returns(ParameterizedTypeName.get(ClassName.get(f1), TypeName.get(adt.typeConstructor().declaredType()),
            deriveUtils.resolveToTypeName(adt.typeConstructor().declaredType(), polymorphism)))
        .addStatement("return $L(__ -> $L)", modMethodName, setterArgName);

    if (smartSuffix.isEmpty())
      setMethod.addModifiers(Modifier.PUBLIC);

    return DerivedCodeSpec.methodSpecs(Stream
        .of(Optional.of(setMethod.build()), caseOf(adt)
            .adt((deriveConfig, typeConstructor, matchMethod, dataConstruction, fields, eq) ->
                drv4jModImpl(Utils.coerce(adt, eq), field, adtArg, moderArg, f1Apply, polymorphism, nameAllocator, modMethod))

            .jadt((deriveConfig, typeConstructor, jDataConstruction, fields, eq) ->
                javaModImpl(Utils.coerce(adt, eq), field, adtArg, moderArg, f1Apply, nameAllocator, modMethod)))
        .flatMap(Optional::stream)
        .toList());
  }

  private Optional<MethodSpec> javaModImpl(AlgebraicDataType<Variant.Java> adt
      , DataArgument field
      , String adtArg
      , String moderArg
      , String f1Apply
      , NameAllocator na
      , MethodSpec.Builder modMethod) {

    final var _switch = CodeBlock
        .builder()
        .add("switch($N) {\n", adtArg)
        .indent()
        .add(AlgebraicDataTypes.getJDataConstruction_(adt)
            .constructors()
            .stream()
            .map(rec -> {
              final var elt = JRecords.getElement(rec);
              final var caseVar = na.newName(uncapitalize(elt.getSimpleName()));
              final var contructorCall = "%s.%s"
                  .formatted(adt.deriveConfig().targetClass().className().simpleName()
                           , elt.getSimpleName());
              final var args = Utils.joinStringsAsArguments(JRecords
                  .getComponents(rec)
                  .stream()
                  .map(RecordComponentElement::getSimpleName)
                  .map(cptName -> {
                    final var cptCall = "%s.%s()".formatted(caseVar, cptName);

                    return cptName.contentEquals(field.fieldName())
                        ? "%s.%s(%s)".formatted(moderArg, f1Apply, cptCall)
                        : cptCall;
                  }));

              return CodeBlock.builder()
                  .add("case $T $N -> $L($L);", elt, caseVar, contructorCall, args)
                  .build();
            })
            .reduce((cb1, cb2) -> cb1.toBuilder().add("\n").add(cb2).build())
            .orElse(CodeBlock.of("")))
        .unindent()
        .add("\n}")
        .build();

    return Optional.of(modMethod
        .addStatement("return $L -> $L", adtArg, _switch)
        .build());
  }

  private Optional<MethodSpec> drv4jModImpl(AlgebraicDataType<Variant.Drv4j> adt
      , DataArgument field
      , String adtArg
      , String moderArg
      , String f1Apply
      , Function<TypeVariable, Optional<TypeName>> polymorphism
      , NameAllocator nameAllocator
      , MethodSpec.Builder modMethod) {
    final var dataConstruction = AlgebraicDataTypes.getDataConstruction_(adt);
    final var matchMethod = AlgebraicDataTypes.getMatchMethod_(adt);

    final var lambdas = dataConstruction.constructors()
        .stream()
        .map(constructor -> {
          final var arguments = constructor.arguments();
          final var typeRestrictions = constructor.typeRestrictions();
          final var constructorName = StrictConstructorDerivator.smartConstructor(constructor, adt.deriveConfig())
              ? (constructor.name() + '0')
              : constructor.name();

          return arguments.stream().map(DataArgument::fieldName).anyMatch(fn -> fn.equals(field.fieldName()))
              ? CodeBlock.builder()
              .add("($L) -> " + "$L($L)", joinStringsAsArguments(Stream.concat(
                      arguments.stream().map(DataArgument::fieldName).map(
                          fn -> nameAllocator.clone().newName(fn, fn + " field")),
                      typeRestrictions.stream().map(TypeRestriction::typeEq).map(DataArgument::fieldName).map(
                          fn -> nameAllocator.clone().newName(fn, fn + " field")))),
                  constructorName,
                  joinStringsAsArguments(Stream
                      .concat(arguments.stream().map(DataArgument::fieldName),
                          typeRestrictions.stream().map(TypeRestriction::typeEq).map(DataArgument::fieldName))
                      .map(fn -> fn.equals(field.fieldName())
                          ? (moderArg + '.' + f1Apply + '(' + nameAllocator.clone().newName(fn, fn + " field") + ')')
                          : nameAllocator.clone().newName(fn, fn + " field"))))
              .build()
              : CodeBlock.of("$T::$L", adt.deriveConfig().targetClass().className(), constructorName);
        })
        .reduce((cb1, cb2) -> CodeBlock.builder().add(cb1).add(",\n").add(cb2).build())
        .orElse(CodeBlock.builder().build());

    return caseOf(dataConstruction)
        .multipleConstructors(MultipleConstructorsSupport.cases()
            .visitorDispatch((visitorParam, visitorType, constructors) -> {
              final var visitorVarName = Utils.uncapitalize(visitorType.asElement().getSimpleName());

              return modMethod
                  .addStatement("$T $L = $L($L)",
                      deriveUtils.resolveToTypeName(visitorType,
                          tv -> deriveUtils.types().isSameType(tv, matchMethod.returnTypeVariable())
                              ? Optional.of(deriveUtils
                              .resolveToTypeName(adt.typeConstructor().declaredType(), polymorphism))
                              : Optional.empty()),
                      visitorVarName,
                      adt.deriveConfig().targetClass().className().nestedClass(
                          MapperDerivator.visitorLambdaFactoryName(adt)),
                      lambdas)
                  .addStatement("return $1L -> $1L.$2L($3L)", adtArg,
                      matchMethod.element().getSimpleName(), visitorVarName)
                  .build();
            })
            .functionsDispatch(constructors -> modMethod
                .addStatement("return $1L -> $1L.$2L($3L)", adtArg, matchMethod.element().getSimpleName(),
                    lambdas)
                .build()))
        .oneConstructor(constructor -> modMethod
            .addStatement("return $1L -> $1L.$2L($3L)", adtArg, matchMethod.element().getSimpleName(),
                lambdas)
            .build())
        .otherwiseEmpty();
  }

  private static TypeVariableName getTypeVariableName(MatchMethod matchMethod, TypeVariable utv) {
    return TypeVariableName.get(matchMethod.returnTypeVariable().toString() + utv.toString());
  }

  private static List<TypeVariable> getUniqueTypeVariables(DataArgument field, List<DataArgument> allFields,
      DeriveUtils deriveUtils) {

    return deriveUtils.typeVariablesIn(field.type())
        .stream()
        .filter(tv -> allFields.stream()
            .filter(da -> !field.fieldName().equals(da.fieldName()))
            .flatMap(da -> deriveUtils.typeVariablesIn(da.type()).stream())
            .noneMatch(tv2 -> deriveUtils.types().isSameType(tv, tv2)))
        .collect(Collectors.toList());
  }

}
