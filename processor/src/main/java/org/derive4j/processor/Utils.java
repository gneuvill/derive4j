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
import org.derive4j.processor.api.DeriveMessage;
import org.derive4j.processor.api.DeriveResult;
import org.derive4j.processor.api.model.*;

import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.SimpleElementVisitor14;
import javax.lang.model.util.SimpleTypeVisitor14;
import javax.lang.model.util.Types;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.derive4j.processor.P2.p2;
import static org.derive4j.processor.api.model.DataArguments.getFieldName;

final class Utils {
  static final TypeVisitor<Optional<DeclaredType>, Unit>                asDeclaredType      = new SimpleTypeVisitor14<>(Optional.empty()) {
                                                                                              @Override
                                                                                              public Optional<DeclaredType> visitDeclared(
                                                                                                  final DeclaredType t,
                                                                                                  final Unit p) {

                                                                                                return Optional.of(t);
                                                                                              }
                                                                                            };
  static final TypeVisitor<Optional<TypeVariable>, Unit>                asTypeVariable      = new SimpleTypeVisitor14<>(Optional.empty()) {
                                                                                              @Override
                                                                                              public Optional<TypeVariable> visitTypeVariable(
                                                                                                  final TypeVariable t,
                                                                                                  final Unit p) {

                                                                                                return Optional.of(t);
                                                                                              }
                                                                                            };
  static final ElementVisitor<Optional<TypeElement>, Unit>              asTypeElement       = new SimpleElementVisitor14<>(Optional.empty()) {

                                                                                              @Override
                                                                                              public Optional<TypeElement> visitType(
                                                                                                  final TypeElement e,
                                                                                                  final Unit p) {

                                                                                                return Optional.of(e);
                                                                                              }

                                                                                            };
  static final SimpleElementVisitor14<PackageElement, Void>              getPackage          = new SimpleElementVisitor14<>() {

                                                                                              @Override
                                                                                              public PackageElement visitPackage(
                                                                                                  final PackageElement e,
                                                                                                  final Void p) {

                                                                                                return e;
                                                                                              }

                                                                                              @Override
                                                                                              protected PackageElement defaultAction(
                                                                                                  final Element e,
                                                                                                  final Void p) {

                                                                                                return e
                                                                                                    .getEnclosingElement()
                                                                                                    .accept(getPackage,
                                                                                                        null);
                                                                                              }

                                                                                            };
  static final SimpleElementVisitor14<Optional<ExecutableElement>, Void> asExecutableElement = new SimpleElementVisitor14<>() {

                                                                                              @Override
                                                                                              public Optional<ExecutableElement> visitExecutable(
                                                                                                  final ExecutableElement e,
                                                                                                  final Void p) {

                                                                                                return Optional.of(e);
                                                                                              }

                                                                                              @Override
                                                                                              protected Optional<ExecutableElement> defaultAction(
                                                                                                  final Element e,
                                                                                                  final Void p) {

                                                                                                return Optional.empty();
                                                                                              }

                                                                                            };

  static final SimpleElementVisitor14<Optional<VariableElement>, Void> asVariableElement = new SimpleElementVisitor14<>() {

    @Override
    public Optional<VariableElement> visitVariable(final VariableElement e, final Void p) {
      return Optional.of(e);
    }

    @Override
    protected Optional<VariableElement> defaultAction(final Element e, final Void p) {
      return Optional.empty();
    }
  };

  static final TypeVisitor<TypeMirror, Types> asBoxedType = new SimpleTypeVisitor14<>() {

    @Override
    public TypeMirror visitPrimitive(PrimitiveType t, Types types) {

      return types.boxedClass(t).asType();
    }

    @Override
    protected TypeMirror defaultAction(TypeMirror e, Types types) {

      return e;
    }
  };

  private Utils() {
  }

  static String capitalize(final CharSequence s) {

    return ((s.length() >= 2) && Character.isHighSurrogate(s.charAt(0)) && Character.isLowSurrogate(s.charAt(1)))
        ? (s.toString().substring(0, 2).toUpperCase(Locale.US) + s.toString().substring(2))
        : (s.toString().substring(0, 1).toUpperCase(Locale.US) + s.toString().substring(1));
  }

  static <A, R> R fold(Optional<A> oa, R none, Function<A, R> some) {

    return oa.map(some).orElse(none);
  }

  static <A> Optional<A> findOnlyOne(List<A> as) {

    return (as.size() == 1) ? Optional.of(as.get(0)) : Optional.empty();
  }

  static <A> Stream<A> optionalAsStream(Optional<A> o) {

    return fold(o, Stream.<A>empty(), Stream::of);
  }

  static <A, B> Function<Stream<A>, Stream<B>> lift(Function<A, B> f) {
    return as -> as.map(f);
  }

  static <A, B, C> Function<P2<A, B>, C> tuple(BiFunction<A, B, C> f) {
    return pair -> f.apply(pair._1(), pair._2());
  }

  static <K, V> Optional<V> get(K key, Map<? extends K, ? extends V> map) {
    return Optional.ofNullable(map.get(key));
  }

  static <A, B> Optional<List<B>> traverseOptional(List<A> as, Function<A, Optional<B>> f) {

    List<B> bs = new ArrayList<>();
    for (A a : as) {
      Optional<B> b = f.apply(a);
      if (b.isPresent()) {
        bs.add(b.get());
      } else {
        return Optional.empty();
      }
    }
    return Optional.of(bs);
  }

  static String uncapitalize(final CharSequence s) {

    return ((s.length() >= 2) && Character.isHighSurrogate(s.charAt(0)) && Character.isLowSurrogate(s.charAt(1)))
        ? (s.toString().substring(0, 2).toLowerCase(Locale.US) + s.toString().substring(2))
        : (s.toString().substring(0, 1).toLowerCase(Locale.US) + s.toString().substring(1));
  }

  static String asArgumentsStringOld(final List<? extends VariableElement> parameters) {

    return parameters.stream().map(p -> p.getSimpleName().toString()).reduce((s1, s2) -> s1 + ", " + s2).orElse("");
  }

  static String asArgumentsString(List<DataArgument> arguments, List<TypeRestriction> restrictions) {

    return Stream
        .concat(arguments.stream().map(a -> "this." + a.fieldName()), restrictions.stream().map(tr -> "TypeEq.refl()"))
        .reduce((s1, s2) -> s1 + ", " + s2)
        .orElse("");
  }

  static String asLambdaParametersString(List<DataArgument> arguments, List<TypeRestriction> restrictions) {
    return asLambdaParametersString(arguments, restrictions, "");
  }

  static String asLambdaParametersString(List<DataArgument> arguments, List<TypeRestriction> restrictions,
      String suffix) {
    return joinStringsAsArguments(Stream.concat(arguments.stream(), restrictions.stream().map(TypeRestriction::typeEq))
        .map(da -> getFieldName(da) + suffix));
  }

  static String asLambdaParametersString(List<DataArgument> arguments, List<TypeRestriction> typeRestrictions,
      NameAllocator nameAllocator) {

    return joinStringsAsArguments(
        Stream.concat(arguments.stream(), typeRestrictions.stream().map(TypeRestrictions::getTypeEq))
            .map(DataArguments::getFieldName)
            .map(nameAllocator::newName));
  }

  static String asArgumentsString(List<DataArgument> arguments) {

    return joinStringsAsArguments(arguments.stream().map(DataArgument::fieldName));
  }

  static String joinStringsAsArguments(Stream<String> arguments) {

    return joinStrings(arguments, ", ");
  }

  static String joinStrings(Stream<String> strings, String joiner) {

    return strings.reduce((s1, s2) -> s1 + joiner + s2).orElse("");
  }

  static TypeName typeName(ClassName className, Stream<TypeName> typeArguments) {

    TypeName[] typeArgs = typeArguments.toArray(TypeName[]::new);

    return (typeArgs.length == 0) ? className : ParameterizedTypeName.get(className, typeArgs);
  }

  static Stream<ExecutableElement> getMethods(final List<? extends Element> amongElements) {

    return amongElements.stream().map(asExecutableElement::visit).flatMap(Utils::optionalAsStream);
  }

  static Stream<VariableElement> getFields(final List<? extends Element> amongElements) {

    return amongElements.stream().map(asVariableElement::visit).flatMap(Utils::optionalAsStream);
  }

  static boolean isRecord(TypeElement adtTypeElement) {
    return adtTypeElement.getKind() == ElementKind.RECORD;
  }

  static boolean isInterface(TypeElement adtTypeElement) {
    return adtTypeElement.getKind() == ElementKind.INTERFACE;
  }

  static boolean isSealed(TypeElement adtTypeElement) {
    return adtTypeElement.getModifiers().contains(Modifier.SEALED);
  }

  static boolean isSealedInterface(TypeElement adtTypeElement) {
    return isInterface(adtTypeElement) && isSealed(adtTypeElement);
  }

  static boolean isJADT(TypeElement adtTypeElement) {
    return isRecord(adtTypeElement) || isSealedInterface(adtTypeElement);
  }

  static <T> Predicate<T> p(Predicate<T> p) {
    return p;
  }

  static <T> Predicate<T> p_(Function<T, Boolean> f) {
    return f::apply;
  }

  static <A, B> Function<A, B> f(Function<A, B> f) {
    return f;
  }

  static <A, B, C> BiFunction<A, B, C> f2(BiFunction<A, B, C> f) {
    return f;
  }

  static <A, B, C> Function<A, Function<B, C>> curry(BiFunction<A, B, C> f) {
    return a -> b -> f.apply(a, b);
  }

  static <A, B, C> Function<B, C> curry(BiFunction<A, B, C> f, A a) {
    return b -> f.apply(a, b);
  }

  static <A, B, C> BiFunction<B, A, C> flip(BiFunction<A, B, C> f) {
    return (b, a) -> f.apply(a, b);
  }

  static <A, B> Function<A, B> constant(B b) {
    return a -> b;
  }

  static MethodSpec.Builder overrideMethodBuilder(final ExecutableElement abstractMethod) {

    return MethodSpec.methodBuilder(abstractMethod.getSimpleName().toString())
        .addAnnotation(Override.class)
        .addModifiers(abstractMethod.getModifiers().stream().filter(m -> m != Modifier.ABSTRACT).collect(toList()))
        .addTypeVariables(abstractMethod.getTypeParameters()
            .stream()
            .map(TypeParameterElement::asType)
            .map(asTypeVariable::visit)
            .flatMap(tvOpt -> tvOpt.map(Collections::singleton).orElse(Collections.emptySet()).stream())
            .map(TypeVariableName::get)
            .collect(toList()))
        .returns(TypeName.get(abstractMethod.getReturnType()))
        .addParameters(abstractMethod.getParameters()
            .stream()
            .map(ve -> ParameterSpec.builder(TypeName.get(ve.asType()), ve.getSimpleName().toString()).build())
            .collect(toList()));
  }

  static <A, B> DeriveResult<List<B>> traverseResults(Collection<A> as, Function<A, DeriveResult<B>> f) {

    return traverseResults(as.stream().map(f).collect(toList()));
  }

  static <A> DeriveResult<List<A>> traverseResults(List<DeriveResult<A>> as) {

    List<A> results = new ArrayList<>();
    for (DeriveResult<A> a : as) {
      DeriveMessage errorMsg = a.match(err -> err, result -> {
        results.add(result);
        return null;
      });
      if (errorMsg != null) {
        return DeriveResult.error(errorMsg);
      }
    }
    return DeriveResult.result(results);
  }

  static <A, B> List<P2<A, B>> zip(List<? extends A> as, List<? extends B> bs) {

    return IntStream.range(0, Math.min(as.size(), bs.size())).<P2<A, B>>mapToObj(i -> p2(as.get(i), bs.get(i))).collect(
        toList());
  }

  static <A> List<P2<A, Integer>> zipWithIndex(List<? extends A> as) {
    return zip(as, IntStream.range(0, as.size()).boxed().collect(toList()));
  }

  static <A> List<A> concat(List<A> xs, List<A> ys) {
    final var res = new ArrayList<>(xs);
    res.addAll(ys);
    return res;
  }

  static <A> P2<List<A>, List<A>> partition(List<A> as, Function<A, Boolean> f) {
      return as.stream()
        .reduce(P2.p2(List.of(), List.of())
              , (acc, a) ->  f.apply(a) ? P2.p2(append(acc._1(), a), acc._2()) : P2.p2(acc._1(), append(acc._2(), a))
              , (__, p) -> p);
  }

  static <A> List<A> append(List<A> as, A a) {
    final var res = new ArrayList<>(as);
    res.add(a);
    return res;
  }

  @SuppressWarnings("unchecked")
  static <S, T> AlgebraicDataType<T> coerce(AlgebraicDataType<S> adt, Function<T, S> ignoredEq) {
    return (AlgebraicDataType<T>) adt;
  }
}
