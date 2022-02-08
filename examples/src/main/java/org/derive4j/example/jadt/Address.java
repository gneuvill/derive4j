package org.derive4j.example.jadt;

import fj.Equal;
import fj.Hash;
import fj.Ord;
import fj.Show;
import org.derive4j.Data;
import org.derive4j.Derive;
import org.derive4j.Instances;

@Data(@Derive(@Instances({ Show.class, Hash.class, Equal.class, Ord.class })))
public record Address(int number, String street) {}
