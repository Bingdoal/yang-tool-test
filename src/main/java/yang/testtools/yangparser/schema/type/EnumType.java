package yang.testtools.yangparser.schema.type;

import lombok.Data;

import java.util.Optional;

@Data
public class EnumType {
    public EnumType(String name, Integer value) {
        this.name = name;
        this.value = value;
    }

    private String name;
    private Integer value;

    private Optional<String> description;
}
