package schema.type;

import lombok.Data;
import schema.DataTreeDto;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Data
public class TypeProperty {

    public TypeProperty() {
    }

    public TypeProperty(String name) {
        this.setName(name);
    }

    private String name;

    private Optional<String> defaultValue;
    private Optional<List<BitsType>> bits; // type bits
    private Optional<String> path; // type leafref
    private Optional<String> range; // type int, decimal
    private Optional<Integer> fractionDigits; // type decimal
    private Optional<List<String>> pattern; // type string
    private Optional<String> length;
    private Optional<List<String>> options; // type enumeration, identityref
    private Optional<String> base; // type identityref
    private Optional<List<TypeProperty>> unionTypes; // type union
    // type choice
    private Optional<Map<String, DataTreeDto>> cases;
    private Optional<String> defaultCase;
}
