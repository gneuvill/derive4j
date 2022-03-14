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
import org.derive4j.ExportAsPublic;
import org.derive4j.hkt.TypeEq;
import org.derive4j.hkt.__;
import org.derive4j.processor.api.model.AlgebraicDataType.Variant.Drv4j;
import org.derive4j.processor.api.model.AlgebraicDataType.Variant.Java;

import java.util.List;
import java.util.Optional;

@Data
public abstract class AlgebraicDataType<T> implements __<AlgebraicDataType.µ, T> {
  public sealed interface Variant {
    enum Drv4j implements Variant {}
    enum Java implements Variant {}
  }
  public enum µ {}

  public interface Cases<R, T> {
    R adt(DeriveConfig deriveConfig, TypeConstructor typeConstructor, MatchMethod matchMethod,
        DataConstruction dataConstruction, List<DataArgument> fields, TypeEq<Drv4j, T> eq);
    R jadt(DeriveConfig deriveConfig, TypeConstructor typeConstructor,
        JDataConstruction jDataConstruction, List<DataArgument> fields, TypeEq<Java, T> eq);
  }

  AlgebraicDataType() {
  }

  public abstract <R> R match(Cases<R, T> adt);

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
