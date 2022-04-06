/*
 * Copyright (c) 2019, Jean-Baptiste Giraudeau <jb@giraudeau.info>
 *
 * This file is part of "Derive4J - Processor API".
 *
 * "Derive4J - Processor API" is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * "Derive4J - Processor API" is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with "Derive4J - Processor API".  If not, see <http://www.gnu.org/licenses/>.
 */
package org.derive4j.processor.api.model;

import org.derive4j.Data;
import org.derive4j.Derive;
import org.derive4j.ExportAsPublic;
import org.derive4j.Visibility;
import org.derive4j.processor.api.model.AlgebraicDataType.Variant.Drv4j;
import org.derive4j.processor.api.model.AlgebraicDataType.Variant.Java;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@Data @Derive(withVisibility = Visibility.Smart)
public abstract class AlgebraicDataType<T> {
  public sealed interface Variant {
    enum Drv4j implements Variant {}
    enum Java implements Variant {}
  }

  public interface Cases<R, T> {
    R adt(DeriveConfig deriveConfig, TypeConstructor typeConstructor, MatchMethod matchMethod,
        DataConstruction dataConstruction, List<DataArgument> fields, Function<Drv4j, T> eq);
    R jadt(DeriveConfig deriveConfig, TypeConstructor typeConstructor,
        JDataConstruction jDataConstruction, List<DataArgument> fields, Function<Java, T> jeq);
  }

  public abstract <R> R match(Cases<R, T> adt);

  AlgebraicDataType() {}

  @ExportAsPublic
  static AlgebraicDataType<Drv4j> adt(DeriveConfig deriveConfig, TypeConstructor typeConstructor, MatchMethod matchMethod,
        DataConstruction dataConstruction, List<DataArgument> fields) {
    return AlgebraicDataTypes.adt0(deriveConfig, typeConstructor, matchMethod, dataConstruction, fields, Function.identity());
  }

  @ExportAsPublic
  static AlgebraicDataType<Java> jadt(DeriveConfig deriveConfig, TypeConstructor typeConstructor,
        JDataConstruction jDataConstruction, List<DataArgument> fields) {
    return AlgebraicDataTypes.jadt0(deriveConfig, typeConstructor, jDataConstruction, fields, Function.identity());
  }

  public DeriveConfig deriveConfig() {

    return AlgebraicDataTypes.getDeriveConfig(this);
  }

  public TypeConstructor typeConstructor() {

    return AlgebraicDataTypes.getTypeConstructor(this);
  }

  public Optional<MatchMethod> matchMethod() {
    return AlgebraicDataTypes.getMatchMethod(this);
  }

  public Optional<DataConstruction> dataConstruction() {

    return AlgebraicDataTypes.getDataConstruction(this);
  }

  public List<DataArgument> fields() {

    return AlgebraicDataTypes.getFields(this);
  }

  @ExportAsPublic
  @SuppressWarnings("OptionalGetWithoutIsPresent")
  static MatchMethod getMatchMethod_(AlgebraicDataType<Drv4j> adt) {
    return AlgebraicDataTypes.getMatchMethod(adt).get();
  }

  @ExportAsPublic
  @SuppressWarnings("OptionalGetWithoutIsPresent")
  static DataConstruction getDataConstruction_(AlgebraicDataType<Drv4j> adt) {
    return AlgebraicDataTypes.getDataConstruction(adt).get();
  }

  @ExportAsPublic
  @SuppressWarnings("OptionalGetWithoutIsPresent")
  static JDataConstruction getJDataConstruction_(AlgebraicDataType<Java> adt) {
    return AlgebraicDataTypes.getJDataConstruction(adt).get();
  }
}
