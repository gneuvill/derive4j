package org.derive4j.processor.api.model;

import org.derive4j.Data;
import org.derive4j.ExportAsPublic;

import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import java.util.List;

@Data
public abstract class JRecord {
    JRecord() {}

    interface Cases<R> {
        R JRecord(TypeElement element, int index, List<RecordComponentElement> components);
    }
    abstract <R> R match(Cases<R> cases);

    @ExportAsPublic
    @SuppressWarnings("unchecked")
    static JRecord JRecord(TypeElement element, int index) {
        return JRecords.JRecord(element, index, (List<RecordComponentElement>) element.getRecordComponents());
    }
}
