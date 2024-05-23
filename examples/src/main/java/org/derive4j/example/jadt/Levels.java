package org.derive4j.example.jadt;

import org.derive4j.Data;

@Data
public sealed interface Levels {
    record Level0(String id) implements Levels {}

    sealed interface Level1 extends Levels {
        record Level2(String id, Integer level2Num) implements Level1 {}
        record Level3(String id) implements Level1 {}

        sealed interface Level4 extends Level1 {
            record Level5(String id, Integer level5Num) implements Level4 {}
        }
    }
}
