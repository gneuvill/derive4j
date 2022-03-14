package org.derive4j.example.jadt;

import org.derive4j.Data;

@Data
public sealed interface Either<A, B> {
    record Left<A, B>(A _value) implements Either<A, B> {}
    record Right<A, B>(B value_) implements Either<A, B> {}
}
