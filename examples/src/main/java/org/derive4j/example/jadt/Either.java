package org.derive4j.example.jadt;

import org.derive4j.Data;
import org.derive4j.Derive;
import org.derive4j.Visibility;

@Data(@Derive(withVisibility = Visibility.Smart))
public sealed interface Either<A, B> {
    record Left<A, B>(A lvalue) implements Either<A, B> {}
    record Right<A, B>(B rvalue) implements Either<A, B> {}
}
