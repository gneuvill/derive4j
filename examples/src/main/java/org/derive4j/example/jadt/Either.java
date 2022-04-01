package org.derive4j.example.jadt;

import org.derive4j.Data;

@Data
public sealed interface Either<A, B> {
    record Left<A, B>(A lvalue) implements Either<A, B> {}
    record Right<A, B>(B rvalue) implements Either<A, B> {}
}
