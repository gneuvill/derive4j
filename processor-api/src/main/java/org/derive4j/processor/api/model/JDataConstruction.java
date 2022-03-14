package org.derive4j.processor.api.model;

import org.derive4j.Data;

import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

@Data
public abstract class JDataConstruction {
    JDataConstruction() {}

    interface Cases<R> {
        R multipleConstructors(List<JRecord> records);
        R oneConstructor(JRecord record);
    }
    abstract <R> R match(Cases<R> cases);

    public final List<JRecord> constructors() {
        return JDataConstructions.caseOf(this)
            .multipleConstructors(Function.identity())
            .oneConstructor(Collections::singletonList);
    }
}
