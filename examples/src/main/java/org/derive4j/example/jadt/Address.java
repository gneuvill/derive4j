package org.derive4j.example.jadt;

import org.derive4j.Data;

@Data //(@Derive(@Instances({ Show.class, Hash.class, Equal.class, Ord.class })))
public record Address(int number, String street) {}
