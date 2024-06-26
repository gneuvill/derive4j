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

import org.derive4j.Makes;
import org.derive4j.processor.api.Derivator;
import org.derive4j.processor.api.DeriveResult;
import org.derive4j.processor.api.DeriveUtils;
import org.derive4j.processor.api.DerivedCodeSpec;
import org.derive4j.processor.api.model.AlgebraicDataType;
import org.derive4j.processor.api.model.AlgebraicDataTypes;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Stream.concat;
import static java.util.stream.Stream.of;
import static org.derive4j.processor.Utils.traverseResults;

final class BuiltinDerivator {

  private BuiltinDerivator() {
  }

  static Derivator<AlgebraicDataType.Variant> derivator(DeriveUtils deriveUtils) {

    final Derivator<AlgebraicDataType.Variant> exportDerivator = new ExportDerivator(deriveUtils);

    return adt -> {

      final var makeDerivators = AlgebraicDataTypes.caseOf(adt)
          .adt_(Makes.cases()
              .<Derivator<? extends AlgebraicDataType.Variant>>lambdaVisitor_(new MapperDerivator(deriveUtils))
              .constructors_(new StrictConstructorDerivator(deriveUtils))
              .lazyConstructor_(new LazyConstructorDerivator(deriveUtils))
              .casesMatching_(new PatternMatchingDerivator(deriveUtils, PatternMatchingDerivator.MatchingKind.Cases))
              .caseOfMatching_(new PatternMatchingDerivator(deriveUtils, PatternMatchingDerivator.MatchingKind.CaseOf))
              .getters_(new GettersDerivator(deriveUtils))
              .modifiers_(new ModifiersDerivator(deriveUtils))
              .catamorphism_(new CataDerivator(deriveUtils))
              .factory_(new FactoryDerivator(deriveUtils)))

          .jadt_(Makes.cases()
              .<Derivator<? extends AlgebraicDataType.Variant>>constructors_(new StrictConstructorDerivator(deriveUtils))
              .getters_(new GettersDerivator(deriveUtils))
              .modifiers_(new ModifiersDerivator(deriveUtils))
              .otherwise_(__ -> DeriveResult.result(DerivedCodeSpec.none())))

          .andThen(BuiltinDerivator::invariant);

      return traverseResults(
          concat(of(exportDerivator), adt.deriveConfig().makes().stream().map(makeDerivators)).map(d -> d.derive(adt))
              .collect(toList()))
          .map(codeSpecList -> codeSpecList.stream().reduce(DerivedCodeSpec.none(), DerivedCodeSpec::append));
    };
  }

  @SuppressWarnings("unchecked")
  private static Derivator<AlgebraicDataType.Variant> invariant(Derivator<? extends AlgebraicDataType.Variant> d) {
    return (Derivator<AlgebraicDataType.Variant>) d;
  }

}
